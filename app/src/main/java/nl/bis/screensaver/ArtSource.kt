package nl.bis.screensaver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Eén kunstwerk uit de meegeleverde collectie, met Nederlandse uitleg. */
data class Artwork(
    val id: Long,
    val imageId: String,
    val title: String,
    val artist: String?,
    val date: String?,
    val explanation: String,
) {
    val imageUrl get() = iiif(1686)
    val fallbackUrl get() = iiif(843)
    val thumbnailUrl get() = iiif(400)

    private fun iiif(width: Int) = "https://www.artic.edu/iiif/2/$imageId/full/$width,/0/default.jpg"
}

/**
 * De kunstcollectie: werken uit het publieke domein van het Art Institute of Chicago,
 * met vooraf vertaalde toelichtingen (assets/kunst_nl.json). De afbeeldingen komen live
 * van de server van het museum.
 */
object ArtCollection {
    @Volatile private var cached: List<Artwork>? = null

    fun load(context: Context): List<Artwork> = cached ?: synchronized(this) {
        cached ?: parse(context.assets.open("kunst_nl.json").use { it.readBytes().toString(Charsets.UTF_8) })
            .also { cached = it }
    }

    private fun parse(json: String): List<Artwork> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            Artwork(
                id = o.optLong("id"),
                imageId = o.optStringOrNull("image_id") ?: return@mapNotNull null,
                title = o.optStringOrNull("titel") ?: "Zonder titel",
                artist = o.optStringOrNull("kunstenaar"),
                date = o.optStringOrNull("datum"),
                explanation = o.optStringOrNull("uitleg") ?: return@mapNotNull null,
            )
        }
    }
}

class ArtSource(private val context: Context) : SlideSource {
    private val queue = ArrayDeque<Artwork>()

    override suspend fun next(): Slide? {
        if (queue.isEmpty()) queue.addAll(ArtCollection.load(context).shuffled())
        val art = queue.removeFirstOrNull() ?: return null
        return Slide.Image(
            url = art.imageUrl,
            fallbackUrl = art.fallbackUrl,
            title = art.title,
            subtitle = listOfNotNull(art.artist, art.date).joinToString(" · ").ifEmpty { null },
            body = art.explanation,
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
