package nl.bis.screensaver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Een sfeerthema met de Engelse zoekterm die Pexels en Pixabay begrijpen. */
enum class AmbientTheme(val key: String, val label: String, val query: String) {
    FIRE("haardvuur", "Haardvuur", "fireplace"),
    RAIN("regen", "Regen op het raam", "rain on window"),
    FOREST("bos", "Bos en mist", "foggy forest"),
    SEA("zee", "Zee en golven", "ocean waves"),
    SNOW("sneeuw", "Sneeuw", "snowfall"),
    STARS("sterren", "Sterrenhemel", "starry night sky"),
    CITY("stad", "Steden bij nacht", "city at night"),
    EARTH("aarde", "Aarde vanuit de ruimte", "earth from space"),
    FLOWERS("bloemen", "Bloemen en tuin", "flowers garden"),
    UNDERWATER("onderwater", "Onderwater", "underwater"),
    AUTUMN("herfst", "Herfstbladeren", "autumn leaves"),
    CANDLE("kaarslicht", "Kaarslicht", "candle light"),
}

/** Een sfeerclip van Pexels of Pixabay. */
data class AmbientClip(
    val id: String,
    val url: String,
    val theme: AmbientTheme,
    val maker: String?,
    val source: String,
)

/**
 * Sfeerbeelden van Pexels en Pixabay: gratis 4K-clips, zonder reclame. Per thema worden een
 * paar clips na elkaar getoond. Korte clips lopen in een lus, zodat elke clip lang genoeg staat.
 * Weggestemde clips en uitgezette thema's komen niet meer voorbij.
 */
class AmbientSource(private val context: Context) : SlideSource {
    private val settings = Settings(context)
    private val clipPicker = FreshPicker(context, "sfeer")
    private val themePicker = FreshPicker(context, "sfeer_thema")
    private val cache = mutableMapOf<AmbientTheme, List<AmbientClip>>()
    private var currentTheme: AmbientTheme? = null
    private var clipsLeftInTheme = 0

    override suspend fun next(): Slide? {
        val themes = settings.ambientThemes.toList().ifEmpty { return null }
        if (currentTheme == null || clipsLeftInTheme <= 0 || currentTheme !in themes) {
            currentTheme = themePicker.pick(themes, { it.key })
            clipsLeftInTheme = CLIPS_PER_THEME
        }
        val theme = currentTheme ?: return null
        clipsLeftInTheme--
        val blocked = settings.blockedClips
        val clips = clips(theme).filter { it.id !in blocked }
        val clip = clipPicker.pick(clips, { it.id }) ?: return null
        return Slide.Video(
            url = clip.url,
            title = theme.label,
            subtitle = listOfNotNull(clip.maker?.let { "Beeld: $it" }, clip.source).joinToString(" · "),
            clipId = clip.id,
            playForMs = settings.slideSeconds * 1000L,
            sound = when (theme) {
                AmbientTheme.FIRE, AmbientTheme.CANDLE -> SoundLayer.FIRE
                AmbientTheme.RAIN -> SoundLayer.RAIN
                AmbientTheme.SEA, AmbientTheme.UNDERWATER -> SoundLayer.SEA
                else -> null
            },
        )
    }

    private suspend fun clips(theme: AmbientTheme): List<AmbientClip> {
        cache[theme]?.let { return it }
        val file = File(context.cacheDir, "sfeer-${theme.key}.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MS
        val clips = if (fresh) {
            read(file, theme)
        } else {
            val fetched = runCatching { pexels(theme) }.getOrDefault(emptyList()) +
                runCatching { pixabay(theme) }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) write(file, fetched) else if (file.exists()) return read(file, theme)
            fetched
        }
        cache[theme] = clips
        return clips
    }

    private suspend fun pexels(theme: AmbientTheme): List<AmbientClip> {
        val key = settings.pexelsKey?.takeIf { it.isNotBlank() } ?: return emptyList()
        val url = "https://api.pexels.com/videos/search?orientation=landscape&size=large&per_page=40&query=" +
            Http.encode(theme.query)
        val response = Http.request("GET", url, headers = mapOf("Authorization" to key))
        if (!response.ok) return emptyList()
        val videos = JSONObject(response.body).optJSONArray("videos") ?: return emptyList()
        return (0 until videos.length()).mapNotNull { i ->
            val v = videos.getJSONObject(i)
            val files = v.optJSONArray("video_files") ?: return@mapNotNull null
            // Hoogste resolutie tot en met 1080p: scherp genoeg en soepel op de Chromecast.
            val best = (0 until files.length()).map { files.getJSONObject(it) }
                .filter { it.optString("file_type") == "video/mp4" && it.optInt("width") in 1280..1920 }
                .maxByOrNull { it.optInt("width") } ?: return@mapNotNull null
            AmbientClip(
                id = "pexels:${v.getLong("id")}",
                url = best.getString("link"),
                theme = theme,
                maker = v.optJSONObject("user")?.optStringOrNull("name"),
                source = "Pexels",
            )
        }
    }

    private suspend fun pixabay(theme: AmbientTheme): List<AmbientClip> {
        val key = settings.pixabayKey?.takeIf { it.isNotBlank() } ?: return emptyList()
        val url = "https://pixabay.com/api/videos/?safesearch=true&per_page=40&key=${Http.encode(key)}&q=" +
            Http.encode(theme.query)
        val hits = JSONObject(Http.getString(url)).optJSONArray("hits") ?: return emptyList()
        return (0 until hits.length()).mapNotNull { i ->
            val h = hits.getJSONObject(i)
            val videos = h.optJSONObject("videos") ?: return@mapNotNull null
            val best = listOf("large", "medium").mapNotNull { videos.optJSONObject(it) }
                .firstOrNull { it.optInt("width") in 1280..1920 && it.optString("url").isNotBlank() }
                ?: return@mapNotNull null
            AmbientClip(
                id = "pixabay:${h.getLong("id")}",
                url = best.getString("url"),
                theme = theme,
                maker = h.optStringOrNull("user"),
                source = "Pixabay",
            )
        }
    }

    private suspend fun write(file: File, clips: List<AmbientClip>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        clips.forEach {
            array.put(JSONObject().put("id", it.id).put("url", it.url).put("maker", it.maker).put("source", it.source))
        }
        file.writeText(array.toString())
    }

    private suspend fun read(file: File, theme: AmbientTheme): List<AmbientClip> = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                AmbientClip(o.getString("id"), o.getString("url"), theme, o.optStringOrNull("maker"), o.optString("source"))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val CLIPS_PER_THEME = 3
        const val CACHE_MS = 24 * 60 * 60 * 1000L
    }
}
