package nl.bis.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Locale

data class DriveFolder(val id: String, val name: String)

/** Een foto of video in de gekoppelde Drive-map. */
data class DriveFile(
    val id: String,
    val name: String,
    val isVideo: Boolean,
    val size: Long,
    /** Opnamedatum uit de foto of de bestandsnaam; 0 als die onbekend is. */
    val createdAt: Long,
    val width: Int,
    val height: Int,
    /** Wanneer het in Drive is gezet; geen opnamedatum, alleen om oude fouten te herkennen. */
    val uploadedAt: Long = 0,
)

/** Leest mappen en bestanden uit je Google Drive; alleen lezen, nooit iets veranderen. */
class DriveClient(private val auth: GoogleAuth) {

    /** Je mappen, de laatst gewijzigde eerst. */
    suspend fun folders(): List<DriveFolder> {
        val result = mutableListOf<DriveFolder>()
        list("mimeType = '$FOLDER' and trashed = false", "files(id,name)", "modifiedTime desc") { o ->
            result += DriveFolder(o.getString("id"), o.optString("name").ifBlank { "Naamloze map" })
        }
        return result
    }

    /** Alle foto's en video's in de map, ook in submappen (tot een paar lagen diep). */
    suspend fun files(folderId: String): List<DriveFile> {
        val result = mutableListOf<DriveFile>()
        val seen = mutableSetOf<String>()
        var level = listOf(folderId)
        repeat(MAX_DEPTH) {
            val next = mutableListOf<String>()
            for (id in level) {
                if (!seen.add(id)) continue
                list(
                    "'$id' in parents and trashed = false and " +
                        "(mimeType = '$FOLDER' or mimeType contains 'image/' or mimeType contains 'video/')",
                    "files(id,name,mimeType,size,createdTime,imageMediaMetadata(width,height,time),videoMediaMetadata(width,height))",
                ) { o ->
                    val mime = o.optString("mimeType")
                    if (mime == FOLDER) {
                        next += o.getString("id")
                        return@list
                    }
                    val image = o.optJSONObject("imageMediaMetadata")
                    val video = o.optJSONObject("videoMediaMetadata")
                    result += DriveFile(
                        id = o.getString("id"),
                        name = o.optString("name"),
                        isVideo = mime.startsWith("video/"),
                        size = o.optString("size").toLongOrNull() ?: 0,
                        // Niet de uploaddatum: wie een foto eerst downloadt en dan uploadt, krijgt anders de dag van vandaag.
                        createdAt = exifTime(image?.optStringOrNull("time")) ?: nameTime(o.optString("name")) ?: 0,
                        width = image?.optInt("width") ?: video?.optInt("width") ?: 0,
                        height = image?.optInt("height") ?: video?.optInt("height") ?: 0,
                        uploadedAt = isoTime(o.optStringOrNull("createdTime")),
                    )
                }
            }
            if (next.isEmpty()) return result
            level = next
        }
        return result
    }

    suspend fun download(file: DriveFile, target: File) {
        Http.download(mediaUrl(file.id), target, auth.accessToken())
    }

    private suspend fun list(query: String, fields: String, orderBy: String? = null, each: (JSONObject) -> Unit) {
        var pageToken: String? = null
        do {
            var url = "$API/files?pageSize=200&supportsAllDrives=true&includeItemsFromAllDrives=true" +
                "&q=${Http.encode(query)}&fields=${Http.encode("nextPageToken,$fields")}"
            if (orderBy != null) url += "&orderBy=${Http.encode(orderBy)}"
            if (pageToken != null) url += "&pageToken=${Http.encode(pageToken)}"
            val response = Http.request("GET", url, bearer = auth.accessToken())
            if (response.code == 401) {
                auth.signOut()
                throw NeedsLoginException()
            }
            if (!response.ok) throw IOException(explain(response))
            val json = JSONObject(response.body)
            val files = json.optJSONArray("files")
            for (i in 0 until (files?.length() ?: 0)) each(files!!.getJSONObject(i))
            pageToken = json.optStringOrNull("nextPageToken")
        } while (pageToken != null)
    }

    private fun explain(response: Http.Response): String = when {
        response.code == 403 && ("accessNotConfigured" in response.body || "SERVICE_DISABLED" in response.body) ->
            "Zet in Google Cloud de Google Drive API aan (zie GOOGLE-FOTOS.md, stap 2)."
        response.code == 403 -> "Google Drive weigert de toegang. Log opnieuw in en vink Drive aan."
        response.code == 404 -> "De Drive-map bestaat niet meer. Kies een andere map."
        else -> "Google Drive gaf fout ${response.code}"
    }

    private fun isoTime(value: String?): Long =
        value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val FOLDER = "application/vnd.google-apps.folder"
        private const val MAX_DEPTH = 4

        /** De inhoud van een bestand; voor afspelen of downloaden met de inlogsleutel in de header. */
        fun mediaUrl(fileId: String) = "$API/files/$fileId?alt=media&supportsAllDrives=true"

        /** Alleen naar dit adres gaat de inlogsleutel mee. */
        const val HOST = "www.googleapis.com"

        /** Een datum als 20230815 of 2023-08-15 in de naam, zoals PXL_20230815_142233.jpg of IMG-20230815-WA0001.jpg. */
        private val DATE_IN_NAME = Regex("""(?<!\d)((?:19|20)\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\d|3[01])(?!\d)""")

        /** De opnametijd zoals camera's die in de foto zetten: "2019:05:12 14:03:22". */
        fun exifTime(value: String?): Long? = value?.takeIf { it.isNotBlank() }?.let {
            runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it.trim())?.time }.getOrNull()
                ?.takeIf { time -> time > 0 && time <= System.currentTimeMillis() + DAY_MS }
        }

        /** De datum uit de bestandsnaam, om 12 uur 's middags; null als er geen (geloofwaardige) datum in staat. */
        fun nameTime(name: String): Long? {
            val match = DATE_IN_NAME.find(name) ?: return null
            val (year, month, day) = match.destructured
            val time = Calendar.getInstance().apply {
                clear()
                set(year.toInt(), month.toInt() - 1, day.toInt(), 12, 0)
            }.timeInMillis
            return time.takeIf { it <= System.currentTimeMillis() + DAY_MS }
        }

        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * Houdt de foto's en video's op de tv gelijk met de gekozen Drive-map: wat erbij komt, komt op
 * de tv; wat eruit gaat, verdwijnt. Wat je op de tv zelf verwijdert, blijft weg. Foto's worden
 * verkleind opgeslagen; video's niet, die speelt de tv rechtstreeks uit Drive af.
 * Loopt op de achtergrond, ook als je het scherm sluit.
 */
object DriveSync {
    data class State(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        /** Uitkomst van de laatste keer, of waarom het niet lukte. */
        val message: String? = null,
        val needsLogin: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    @Volatile private var job: Job? = null

    /** Bijwerken als het een tijd geleden is; voor bij het openen van de app of de screensaver. */
    fun syncIfDue(context: Context) {
        val settings = Settings(context)
        if (System.currentTimeMillis() - settings.driveLastSync < INTERVAL_MS) return
        start(context)
    }

    /** Begint met bijwerken, of geeft de lopende keer terug; met [restart] (andere map gekozen) begint hij opnieuw. */
    @Synchronized
    fun start(context: Context, restart: Boolean = false): Job? {
        if (restart) job?.cancel() else job?.let { if (it.isActive) return it }
        val app = context.applicationContext
        val settings = Settings(app)
        val folderId = settings.driveFolderId ?: return null
        val auth = GoogleAuth(settings)
        if (!auth.hasDrive) {
            _state.value = State(message = "Log opnieuw in bij Google om de Drive-map bij te werken.", needsLogin = true)
            return null
        }
        return scope.launch {
            _state.value = State(running = true)
            _state.value = try {
                State(message = sync(app, DriveClient(auth), folderId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsLoginException) {
                State(message = "De koppeling met Google is verlopen. Log opnieuw in.", needsLogin = true)
            } catch (e: Exception) {
                State(message = e.message ?: "Bijwerken lukte niet. Probeer het later opnieuw.")
            }
        }.also { job = it }
    }

    private suspend fun sync(context: Context, drive: DriveClient, folderId: String): String {
        val settings = Settings(context)
        val library = MediaLibrary(context)
        val files = drive.files(folderId)
        val hidden = library.hiddenDriveIds()
        val wanted = files.filter { MediaLibrary.DRIVE_PREFIX + it.id !in hidden }
        val wantedIds = wanted.map { MediaLibrary.DRIVE_PREFIX + it.id }.toSet()

        // Niet meer in de map (of uit een eerder gekozen map): weg van de tv.
        val gone = library.items().filter { it.id.startsWith(MediaLibrary.DRIVE_PREFIX) && it.id !in wantedIds }
        library.remove(gone.map { it.id }.toSet())

        // Video's uit Drive worden gestreamd. Eerder opgeslagen exemplaren maken plaats, met behoud van hun tellingen.
        var freed = 0L
        val converted = library.items().filter { it.driveId != null && it.isVideo && !it.streamed && it.id in wantedIds }
        converted.forEach { item ->
            val stored = library.file(item)
            val size = stored.length()
            library.replace(item.copy(fileName = streamName(item.driveId!!), streamed = true, size = size))
            stored.delete()
            freed += size
        }

        // Eerder kregen foto's zonder opnamedatum de uploaddatum; verbeter die waar het nu beter kan.
        val byId = wanted.associateBy { MediaLibrary.DRIVE_PREFIX + it.id }
        library.items().forEach { item ->
            val file = byId[item.id] ?: return@forEach
            val date = when {
                file.createdAt > 0 -> file.createdAt
                item.createdAt == file.uploadedAt -> 0
                else -> item.createdAt
            }
            if (date != item.createdAt) library.replace(item.copy(createdAt = date))
        }

        val present = library.items().map { it.id }.toSet()
        val todo = wanted.filter { MediaLibrary.DRIVE_PREFIX + it.id !in present }
        val batch = System.currentTimeMillis()
        val (newVideos, photos) = todo.partition { it.isVideo }
        newVideos.forEach { file ->
            library.add(
                LocalMedia(
                    MediaLibrary.DRIVE_PREFIX + file.id, streamName(file.id), true, file.createdAt,
                    file.width, file.height, batch, streamed = true, size = file.size,
                ),
            )
        }
        var added = newVideos.size
        var problem: String? = null
        photos.forEachIndexed { index, file ->
            _state.value = State(running = true, done = index, total = photos.size)
            val room = minOf(library.freeBytes() - Storage.MIN_FREE_BYTES, Storage.MAX_MEDIA_BYTES - library.usedBytes())
            if (minOf(file.size, PHOTO_BYTES) > room) {
                problem = "De tv is vol (of de grens van ${Storage.MAX_MEDIA_BYTES / 1_000_000_000} GB is bereikt). Niet alle foto's zijn opgehaald."
                return@forEachIndexed
            }
            val ok = runCatching { fetch(context, drive, library, file, batch) }
                .onFailure { if (it is CancellationException || it is NeedsLoginException) throw it }
                .getOrDefault(false)
            if (ok) added++ else if (problem == null) problem = "Een paar foto's konden niet worden opgehaald."
        }
        settings.driveLastSync = System.currentTimeMillis()
        return listOfNotNull(
            when {
                added == 0 && gone.isEmpty() -> "Alles is bijgewerkt."
                else -> listOfNotNull(
                    "$added nieuw".takeIf { added > 0 },
                    "${gone.size} weggehaald".takeIf { gone.isNotEmpty() },
                ).joinToString(", ").replaceFirstChar { it.uppercase() } + "."
            },
            "${converted.size} opgeslagen video('s) worden voortaan gestreamd; ${freed / 1_000_000} MB vrijgemaakt."
                .takeIf { converted.isNotEmpty() },
            problem,
        ).joinToString(" ")
    }

    /** Een gestreamde video heeft geen bestand op de tv; deze naam dient alleen als sleutel. */
    private fun streamName(driveId: String) = "drive-$driveId.stream"

    /** Haalt één foto op, verkleind tot tv-formaat en rechtop gezet. */
    private suspend fun fetch(context: Context, drive: DriveClient, library: MediaLibrary, file: DriveFile, batch: Long): Boolean {
        val fileName = "drive-${file.id}.jpg"
        val target = File(library.dir, fileName)
        // Eerst naar de cache, zodat een half bestand nooit in de fotomap belandt.
        val download = File(context.cacheDir, "drive-download")
        var createdAt = file.createdAt
        try {
            drive.download(file, download)
            withContext(Dispatchers.IO) {
                // Wist Drive de opnamedatum niet (bijvoorbeeld bij HEIC), lees hem dan zelf uit de foto.
                if (createdAt == 0L) createdAt = photoTime(download) ?: 0L
                shrink(download, target)
            }
        } finally {
            download.delete()
        }
        if (!target.exists()) return false
        library.add(LocalMedia(MediaLibrary.DRIVE_PREFIX + file.id, fileName, false, createdAt, file.width, file.height, batch))
        return true
    }

    private fun photoTime(source: File): Long? = runCatching {
        val exif = ExifInterface(source.path)
        DriveClient.exifTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            ?: DriveClient.exifTime(exif.getAttribute(ExifInterface.TAG_DATETIME))
    }.getOrNull()

    /** Verkleint tot hooguit 1920×1080 (of 1080×1920 staand) en draait volgens de camera-informatie. */
    private fun shrink(source: File, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Geen foto: ${source.name}")
        val rotation = runCatching {
            when (ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val sideways = rotation == 90f || rotation == 270f
        val width = if (sideways) bounds.outHeight else bounds.outWidth
        val height = if (sideways) bounds.outWidth else bounds.outHeight
        val (maxW, maxH) = if (width >= height) 1920 to 1080 else 1080 to 1920
        var sample = 1
        while (width / (sample * 2) >= maxW && height / (sample * 2) >= maxH) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Kon ${source.name} niet lezen")
        val scale = minOf(1f, maxW.toFloat() / (if (sideways) decoded.height else decoded.width), maxH.toFloat() / (if (sideways) decoded.width else decoded.height))
        val matrix = Matrix().apply {
            postRotate(rotation)
            postScale(scale, scale)
        }
        val result = if (rotation == 0f && scale == 1f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val partial = File(target.parentFile, target.name + ".deel")
        try {
            partial.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (!partial.renameTo(target)) throw IOException("Kon ${target.name} niet opslaan")
        } finally {
            partial.delete()
            if (result !== decoded) result.recycle()
            decoded.recycle()
        }
    }

    /** Een verkleinde foto is hooguit zo groot. */
    private const val PHOTO_BYTES = 2_000_000L
    private const val INTERVAL_MS = 30 * 60 * 1000L
}
