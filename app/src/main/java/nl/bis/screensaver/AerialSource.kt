package nl.bis.screensaver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * De luchtopnames van de Apple TV-screensaver. Apple publiceert de lijst als tar-bestand
 * met daarin entries.json; dat lezen we uit en bewaren we als reservekopie.
 */
class AerialSource(context: Context) : SlideSource {
    private val cacheFile = File(context.cacheDir, "aerials-entries.json")
    private var all: List<Slide.Video>? = null
    private val queue = ArrayDeque<Slide.Video>()

    override suspend fun next(): Slide? {
        val videos = all ?: load().also { all = it }
        if (queue.isEmpty()) queue.addAll(videos.shuffled())
        return queue.removeFirstOrNull()
    }

    private suspend fun load(): List<Slide.Video> = withContext(Dispatchers.IO) {
        val json = try {
            downloadEntries().also { cacheFile.writeText(it) }
        } catch (e: IOException) {
            if (cacheFile.exists()) cacheFile.readText() else throw e
        }
        parse(json)
    }

    private fun downloadEntries(): String {
        var lastError: IOException? = null
        for (url in MANIFESTS) {
            try {
                return Http.open(url) { readTarEntry(it, "entries.json") }
                    ?: throw IOException("entries.json niet gevonden in $url")
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Geen aerial-lijst bereikbaar")
    }

    private fun parse(json: String): List<Slide.Video> {
        val assets = JSONObject(json).getJSONArray("assets")
        return (0 until assets.length()).mapNotNull { i ->
            val asset = assets.getJSONObject(i)
            val url = URL_KEYS.firstNotNullOfOrNull { asset.optStringOrNull(it) } ?: return@mapNotNull null
            Slide.Video(
                url = url,
                title = asset.optStringOrNull("accessibilityLabel") ?: "Aerial",
                subtitle = "Luchtopname · Apple TV",
            )
        }
    }

    /** Minimale tar-lezer: zoekt één bestand op naam en geeft de inhoud terug. */
    private fun readTarEntry(input: InputStream, wanted: String): String? {
        val header = ByteArray(512)
        while (true) {
            if (!input.readFully(header)) return null
            if (header.all { it == 0.toByte() }) return null
            val name = String(header, 0, 100, Charsets.US_ASCII).substringBefore('\u0000')
            val size = String(header, 124, 12, Charsets.US_ASCII)
                .substringBefore('\u0000').trim().ifEmpty { "0" }.toLong(8)
            val padded = (size + 511) / 512 * 512
            if (name.substringAfterLast('/') == wanted) {
                val content = ByteArray(size.toInt())
                if (!input.readFully(content)) return null
                return content.toString(Charsets.UTF_8)
            }
            input.skipFully(padded)
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining--
            } else {
                return
            }
        }
    }

    private companion object {
        // Nieuwste lijst eerst; http als terugval voor het Apple-certificaat.
        val MANIFESTS = listOf(
            "https://sylvan.apple.com/Aerials/resources-16.tar",
            "http://sylvan.apple.com/Aerials/resources-16.tar",
            "https://sylvan.apple.com/Aerials/resources-15.tar",
            "http://sylvan.apple.com/Aerials/resources-15.tar",
        )

        // 1080p SDR speelt soepel op elke Chromecast met Google TV.
        val URL_KEYS = listOf("url-1080-SDR", "url-1080-H264", "url-4K-SDR")
    }
}
