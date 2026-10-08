package nl.bis.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket

/**
 * Je foto's en video's beheren op je telefoon. Het adres blijft altijd hetzelfde, zodat je
 * het als bladwijzer of op je beginscherm kunt zetten. Bereikbaar zolang de tv aan staat
 * en de app of de screensaver draait.
 */
class PhoneLibrary(context: Context) : PhoneForm(context, libraryToken(context), LIBRARY_PORTS) {
    private val library = MediaLibrary(context)
    private val settings = Settings(context)
    private val auth = GoogleAuth(settings)
    private val thumbs = File(context.cacheDir, "duimnagels").apply { mkdirs() }

    override fun render(form: Map<String, String>?): String =
        context.assets.open("beheer.html").bufferedReader().readText()

    override fun route(method: String, sub: String, query: Map<String, String>, body: String, client: Socket): Boolean {
        when {
            sub == "lijst" -> respondJson(client, list().toString())
            sub.startsWith("beeld/") -> {
                val item = library.items().firstOrNull { it.fileName == sub.removePrefix("beeld/") } ?: return false
                val bytes = thumbnail(item) ?: return false
                respondBytes(client, 200, "image/jpeg", bytes, cacheable = true)
            }
            sub == "verwijder" && method == "POST" -> {
                val ids = runCatching { JSONArray(body) }.getOrNull()
                    ?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty()
                library.items().filter { it.id in ids }.forEach { File(thumbs, "${it.fileName}.jpg").delete() }
                library.delete(ids)
                respondJson(client, JSONObject().put("verwijderd", ids.size).toString())
            }
            sub == "toevoegen" && method == "POST" -> respondJson(client, startAdding().toString())
            sub == "status" -> respondJson(client, Adding.status().toString())
            else -> return false
        }
        return true
    }

    private fun list(): JSONObject {
        val items = JSONArray()
        library.items().forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("video", item.isVideo)
                    .put("opname", item.createdAt)
                    .put("toegevoegd", item.addedAt)
                    .put("getoond", library.plays(item))
                    .put("grootte", library.file(item).length())
                    .put("beeld", "beeld/${item.fileName}"),
            )
        }
        return JSONObject()
            .put("items", items)
            .put("vrij", library.freeBytes())
            .put("gebruikt", library.usedBytes())
            .put("ingelogd", auth.isSignedIn)
    }

    /** Een kleine jpg van ongeveer 360 pixels breed; video's krijgen een beeld van na 1 seconde. */
    private fun thumbnail(item: LocalMedia): ByteArray? {
        val cached = File(thumbs, "${item.fileName}.jpg")
        if (cached.exists()) return cached.readBytes()
        val file = library.file(item)
        val bitmap = runCatching {
            if (item.isVideo) {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(file.path)
                        getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SIZE, SIZE)
                    } finally {
                        release()
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull() ?: return null
        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, it)
            it.toByteArray()
        }
        bitmap.recycle()
        runCatching { cached.writeBytes(bytes) }
        return bytes
    }

    /** Toevoegen vanaf de telefoon: de fotokiezer opent op de telefoon, de tv haalt daarna alles op. */
    private fun startAdding(): JSONObject {
        if (!auth.isConfigured) return JSONObject().put("fout", "Google Foto's is nog niet gekoppeld. Zie GOOGLE-FOTOS.md.")
        if (!auth.isSignedIn) {
            return JSONObject().put("fout", "Log eerst één keer in op de tv: Foto's beheren › Foto's en video's toevoegen.")
        }
        Adding.current?.let { if (it.isActive) return Adding.status() }
        val picker = PhotosPicker(auth)
        val session = runCatching { runBlocking { picker.createSession() } }
            .getOrElse { return JSONObject().put("fout", "Google Foto's reageerde niet: ${it.message}") }
        Adding.text = "Kies je foto's en video's op je telefoon en tik op Klaar."
        Adding.pickerUri = session.pickerUri + "/autoclose"
        Adding.done = false
        Adding.current = Adding.scope.launch {
            try {
                if (!picker.waitForSelection(session)) {
                    Adding.text = "Er is niets gekozen binnen de tijd."
                    return@launch
                }
                val picked = picker.items(session.id)
                val (added, message) = library.import(picked, { auth.accessToken() }) { done, total ->
                    Adding.text = "Ophalen op de tv… $done van $total"
                }
                runCatching { picker.deleteSession(session.id) }
                Adding.text = listOfNotNull(
                    when (added) {
                        0 -> "Niets nieuws toegevoegd."
                        1 -> "1 nieuw item toegevoegd."
                        else -> "$added nieuwe items toegevoegd."
                    },
                    message,
                ).joinToString(" ")
            } catch (e: Exception) {
                Adding.text = "Er ging iets mis: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                Adding.pickerUri = null
                Adding.done = true
            }
        }
        return Adding.status()
    }

    /** Toevoegen loopt door als je de pagina sluit; de status is voor iedereen hetzelfde. */
    private object Adding {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile var current: Job? = null
        @Volatile var text: String? = null
        @Volatile var pickerUri: String? = null
        @Volatile var done = false

        fun status(): JSONObject = JSONObject()
            .put("bezig", current?.isActive == true)
            .put("klaar", done)
            .put("tekst", text ?: JSONObject.NULL)
            .put("kiezer", pickerUri ?: JSONObject.NULL)
    }

    companion object {
        private const val SIZE = 360
        private val LIBRARY_PORTS = listOf(8770, 8771, 8772, 0)

        private fun libraryToken(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences("bis_telefoon", Context.MODE_PRIVATE)
            return prefs.getString("token", null) ?: PhoneForm.newToken().also { prefs.edit().putString("token", it).apply() }
        }
    }
}

/**
 * Houdt één beheerpagina in de lucht zolang er iets van de app op het scherm is
 * (keuzescherm, fotobeheer of screensaver).
 */
object PhoneLibraryHost {
    private var server: PhoneLibrary? = null
    private var users = 0

    @Volatile var address: String? = null
        private set

    @Synchronized
    fun acquire(context: Context) {
        users++
        if (server == null || address == null) {
            server?.stop()
            server = PhoneLibrary(context.applicationContext).also { address = it.start() }
        }
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            server?.stop()
            server = null
            address = null
        }
    }
}
