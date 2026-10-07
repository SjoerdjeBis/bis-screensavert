package nl.bis.screensaver

import androidx.core.text.HtmlCompat
import org.json.JSONObject
import kotlin.random.Random

/**
 * Kunstwerken uit de open collectie van het Art Institute of Chicago:
 * alleen werken in het publieke domein, met afbeelding en beschrijving.
 */
class ArtSource : SlideSource {
    private val queue = ArrayDeque<Slide.Image>()

    override suspend fun next(): Slide? {
        if (queue.isEmpty()) queue.addAll(fetchBatch().shuffled())
        return queue.removeFirstOrNull()
    }

    private suspend fun fetchBatch(): List<Slide.Image> {
        // De zoek-API geeft maximaal 1000 resultaten; met 50 per pagina zijn dat 20 pagina's.
        val page = Random.nextInt(1, 21)
        val url = "https://api.artic.edu/api/v1/artworks/search" +
            "?query%5Bterm%5D%5Bis_public_domain%5D=true" +
            "&fields=id,title,artist_display,date_display,image_id,description,short_description" +
            "&limit=50&page=$page"
        val data = JSONObject(Http.getString(url)).getJSONArray("data")
        return (0 until data.length()).mapNotNull { i -> toSlide(data.getJSONObject(i)) }
    }

    private fun toSlide(artwork: JSONObject): Slide.Image? {
        val imageId = artwork.optStringOrNull("image_id") ?: return null
        val description = artwork.optStringOrNull("description")
            ?: artwork.optStringOrNull("short_description")
            ?: return null
        val text = HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isEmpty()) return null

        val artist = artwork.optStringOrNull("artist_display")?.lines()?.firstOrNull()
        val date = artwork.optStringOrNull("date_display")
        return Slide.Image(
            url = "https://www.artic.edu/iiif/2/$imageId/full/1686,/0/default.jpg",
            fallbackUrl = "https://www.artic.edu/iiif/2/$imageId/full/843,/0/default.jpg",
            title = artwork.optStringOrNull("title") ?: "Zonder titel",
            subtitle = listOfNotNull(artist, date).joinToString(" · ").ifEmpty { null },
            body = text,
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
