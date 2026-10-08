package nl.bis.screensaver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Een foto of video die op de tv is opgeslagen. */
data class LocalMedia(
    val id: String,
    val fileName: String,
    val isVideo: Boolean,
    val createdAt: Long,
    val width: Int,
    val height: Int,
    /** Wanneer je het toevoegde; alles uit één keer kiezen heeft dezelfde tijd. */
    val addedAt: Long = 0,
)

/** De eigen foto's en video's op de tv, met een eenvoudige index in JSON. */
class MediaLibrary(context: Context) {
    val dir = File(context.filesDir, "media").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val plays = context.applicationContext.getSharedPreferences("bis_afgespeeld", Context.MODE_PRIVATE)

    /** Hoe vaak dit item in de screensaver te zien was (geteld sinds versie 0.1.38). */
    fun plays(item: LocalMedia): Int = plays.getInt(item.id, 0)

    fun countPlay(item: LocalMedia) = plays.edit().putInt(item.id, plays(item) + 1).apply()

    @Synchronized
    fun items(): List<LocalMedia> {
        if (!indexFile.exists()) return emptyList()
        val array = runCatching { JSONArray(indexFile.readText()) }.getOrElse { return emptyList() }
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LocalMedia(
                id = o.getString("id"),
                fileName = o.getString("file"),
                isVideo = o.optBoolean("video"),
                createdAt = o.optLong("created"),
                width = o.optInt("width"),
                height = o.optInt("height"),
                addedAt = o.optLong("added"),
            )
        }.filter { file(it).exists() }.map { if (it.addedAt > 0) it else it.copy(addedAt = file(it).lastModified()) }
    }

    fun file(item: LocalMedia) = File(dir, item.fileName)

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    /** Bestanden die niet (meer) in de index staan, bijvoorbeeld na een afgebroken download. */
    @Synchronized
    fun removeOrphans() {
        val known = items().map { it.fileName }.toSet() + indexFile.name
        dir.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
    }

    fun freeBytes(): Long = dir.usableSpace

    /**
     * Downloadt de gekozen items. Stopt als de tv te vol raakt; geeft het aantal nieuwe
     * items en een eventuele melding terug.
     */
    suspend fun import(
        picked: List<PickedItem>,
        token: suspend () -> String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Pair<Int, String?> {
        val existing = items().map { it.id }.toSet()
        val todo = picked.filter { it.id !in existing }
        var added = 0
        var message: String? = null
        val skippedVideos = picked.count { !it.ready }
        val batch = System.currentTimeMillis()
        todo.forEachIndexed { index, item ->
            onProgress(index, todo.size)
            if (!item.ready) return@forEachIndexed
            if (freeBytes() < Storage.MIN_FREE_BYTES) {
                message = "De tv is bijna vol (nog ${freeBytes() / 1_000_000} MB vrij). Niet alles is opgeslagen."
                return Pair(added, message)
            }
            if (usedBytes() >= Storage.MAX_MEDIA_BYTES) {
                message = "De grens van ${Storage.MAX_MEDIA_BYTES / 1_000_000_000} GB voor eigen foto's en video's is bereikt. Verwijder eerst iets."
                return Pair(added, message)
            }
            val extension = if (item.isVideo) "mp4" else "jpg"
            val fileName = "${item.id.hashCode().toUInt()}.$extension"
            val url = if (item.isVideo) "${item.baseUrl}=dv" else "${item.baseUrl}=w1920-h1080"
            val target = File(dir, fileName)
            runCatching { Http.download(url, target, token()) }
                .onSuccess {
                    // Een grote video kan de tv alsnog te vol maken; dan die niet bewaren.
                    if (freeBytes() < Storage.MIN_FREE_BYTES / 2) {
                        target.delete()
                        message = "Deze video is te groot voor de vrije ruimte op de tv (nog ${freeBytes() / 1_000_000} MB vrij)."
                        return Pair(added, message)
                    }
                    add(LocalMedia(item.id, fileName, item.isVideo, parseTime(item.createTime), item.width, item.height, batch))
                    added++
                }
                .onFailure { message = "Niet alles kon worden gedownload. Probeer het later nog eens." }
        }
        onProgress(todo.size, todo.size)
        if (skippedVideos > 0 && message == null) {
            message = "$skippedVideos video('s) werden nog verwerkt door Google en zijn overgeslagen."
        }
        return Pair(added, message)
    }

    @Synchronized
    fun delete(ids: Set<String>) {
        val (gone, keep) = items().partition { it.id in ids }
        gone.forEach {
            file(it).delete()
            plays.edit().remove(it.id).apply()
        }
        write(keep)
    }

    @Synchronized
    private fun add(item: LocalMedia) = write(items() + item)

    private fun write(items: List<LocalMedia>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("file", it.fileName)
                    .put("video", it.isVideo)
                    .put("created", it.createdAt)
                    .put("width", it.width)
                    .put("height", it.height)
                    .put("added", it.addedAt),
            )
        }
        indexFile.writeText(array.toString())
    }

    private fun parseTime(value: String?): Long =
        value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L


}
