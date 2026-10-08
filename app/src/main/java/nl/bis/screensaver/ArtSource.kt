package nl.bis.screensaver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Eén kunstwerk uit de meegeleverde collectie, met Nederlandse uitleg. */
data class Artwork(
    val id: String,
    val museum: String,
    val title: String,
    val artist: String?,
    val date: String?,
    val explanation: String,
    val imageUrl: String,
    val fallbackUrl: String?,
    val thumbnailUrl: String,
)

/**
 * De kunstcollectie uit meerdere musea, met Nederlandse toelichtingen en zonder
 * dubbelingen (assets/kunst_nl.json, gemaakt door tools/build_kunst_nl.py).
 * De afbeeldingen komen live van de servers van de musea.
 */
object ArtCollection {
    @Volatile private var cached: List<Artwork>? = null

    fun load(context: Context): List<Artwork> = cached ?: synchronized(this) {
        cached ?: parse(context.assets.open("kunst_nl.json").use { it.readBytes().toString(Charsets.UTF_8) })
            .also { cached = it }
    }

    private fun parse(json: String): List<Artwork> {
        val array = JSONArray(json)
        val seen = mutableSetOf<String>()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val id = o.optStringOrNull("id") ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val image = o.optStringOrNull("afbeelding") ?: return@mapNotNull null
            Artwork(
                id = id,
                museum = o.optStringOrNull("bron") ?: "Museum",
                title = o.optStringOrNull("titel") ?: "Zonder titel",
                artist = o.optStringOrNull("kunstenaar"),
                date = o.optStringOrNull("datum"),
                explanation = o.optStringOrNull("uitleg") ?: return@mapNotNull null,
                imageUrl = image,
                fallbackUrl = o.optStringOrNull("afbeelding_reserve"),
                thumbnailUrl = o.optStringOrNull("afbeelding_klein") ?: image,
            )
        }
    }
}

/** Kiest steeds een werk dat je de laatste tijd niet zag, en niet twee keer dezelfde kunstenaar na elkaar. */
class ArtSource(private val context: Context) : SlideSource {
    private val picker = FreshPicker(context, "kunst")

    override suspend fun next(): Slide? {
        val art = picker.pick(ArtCollection.load(context), { it.id }, { it.artist }) ?: return null
        return Slide.Image(
            url = art.imageUrl,
            fallbackUrl = art.fallbackUrl,
            title = art.title,
            subtitle = listOfNotNull(art.artist, art.date).joinToString(" · ").ifEmpty { null },
            body = art.explanation,
            source = art.museum,
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
