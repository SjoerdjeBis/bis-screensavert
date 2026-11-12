package nl.bis.screensaver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Een clip van een minuut: de vlucht door een wereld en door het portaal de volgende in. */
data class PortalClip(val id: String, val name: String, val url: String)

/**
 * Portalen: vluchten door de 3D-werelden van Portals (github.com/emollick/ai-portals, CC BY-NC-SA 4.0).
 * Die site tekent live en wil een grafische kaart, wat een Chromecast niet trekt. Daarom zijn de vluchten
 * vooraf opgenomen (tools/portalen, workflow portalen.yml) en staan ze als gewone 1080p-video's op de
 * GitHub-release "portalen"; de tv streamt ze en slaat niets op.
 */
class PortalSource(private val context: Context) : SlideSource {
    private val settings = Settings(context)
    private val picker = FreshPicker(context, "portalen")
    private var clips: List<PortalClip>? = null

    override suspend fun next(): Slide? {
        val all = clips ?: load().also { if (it.isNotEmpty()) clips = it }
        val blocked = settings.blockedClips
        val clip = picker.pick(all.filter { it.id !in blocked }, { it.id }) ?: return null
        return Slide.Video(
            url = clip.url,
            title = clip.name,
            subtitle = "Portals · Ethan Mollick · CC BY-NC-SA",
            clipId = clip.id,
        )
    }

    /** De lijst van de release; een dag bewaard, en bij geen verbinding de vorige lijst. */
    private suspend fun load(): List<PortalClip> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "portalen.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_MS
        val cached = if (cache.exists()) runCatching { parse(cache.readText()) }.getOrDefault(emptyList()) else emptyList()
        if (fresh && cached.isNotEmpty()) return@withContext cached
        runCatching {
            val json = Http.getString(RELEASE)
            parse(json).also { if (it.isNotEmpty()) cache.writeText(json) }
        }.getOrDefault(emptyList()).ifEmpty { cached }
    }

    companion object {
        private const val RELEASE = "https://api.github.com/repos/SjoerdjeBis/bis-screensavert/releases/tags/portalen"
        private const val CACHE_MS = 24 * 60 * 60 * 1000L

        /** De mp4's van de release; de titel staat in het label van elk bestand. */
        fun parse(release: String): List<PortalClip> {
            val assets = JSONObject(release).optJSONArray("assets") ?: JSONArray()
            return (0 until assets.length()).mapNotNull { i ->
                val a = assets.getJSONObject(i)
                val file = a.optString("name")
                if (!file.endsWith(".mp4")) return@mapNotNull null
                val id = file.removeSuffix(".mp4")
                val url = a.optStringOrNull("browser_download_url") ?: return@mapNotNull null
                PortalClip("portaal:$id", a.optStringOrNull("label") ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }, url)
            }
        }
    }
}
