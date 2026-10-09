package nl.bis.screensaver

import android.content.Context
import org.json.JSONArray
import java.util.Calendar

/** Een goedgekeurd beeld uit Nederland van toen of Natuur. */
data class Curated(
    val id: String,
    val title: String,
    val line: String?,
    val explanation: String?,
    val credit: String,
    val date: String?,
    val imageUrl: String,
    val thumbnailUrl: String,
)

/**
 * De beelden per categorie, vooraf gekeurd op GitHub (tools/fetch_extra.py en build_extra.py)
 * en meegeleverd als lijstje in de app. De beelden zelf komen live van internet.
 */
object CuratedCollection {
    private val cache = mutableMapOf<String, List<Curated>>()

    @Synchronized
    fun load(context: Context, asset: String): List<Curated> = cache.getOrPut(asset) {
        val json = runCatching { context.assets.open("$asset.json").use { it.readBytes().toString(Charsets.UTF_8) } }
            .getOrDefault("[]")
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            Curated(
                id = o.optStringOrNull("id") ?: return@mapNotNull null,
                title = o.optStringOrNull("titel") ?: return@mapNotNull null,
                line = o.optStringOrNull("onder"),
                explanation = o.optStringOrNull("uitleg"),
                credit = o.optStringOrNull("bron") ?: "",
                date = o.optStringOrNull("datum"),
                imageUrl = o.optStringOrNull("afbeelding") ?: return@mapNotNull null,
                thumbnailUrl = o.optStringOrNull("klein") ?: o.getString("afbeelding"),
            )
        }
    }

    fun assetFor(mode: Mode): String? = when (mode) {
        Mode.HISTORY -> "toen"
        Mode.NATURE -> "natuur"
        else -> null
    }
}

/**
 * Kiest steeds een beeld dat je de laatste tijd niet zag en dat je niet hebt weggestemd.
 * Bij Nederland van toen bij voorkeur een foto van deze dag, in een ander jaar.
 */
class CuratedSource(private val context: Context, private val asset: String, private val onThisDay: Boolean = false) : SlideSource {
    private val settings = Settings(context)
    private val picker = FreshPicker(context, asset)

    override suspend fun next(): Slide? {
        val blocked = settings.blockedImages
        val all = CuratedCollection.load(context, asset).filter { "beeld:${it.id}" !in blocked }
        val today = Calendar.getInstance()
        val nearToday = if (onThisDay) all.filter { daysFromToday(it.date, today) <= 1 } else emptyList()
        val item = picker.pick(nearToday.ifEmpty { all }, { it.id }) ?: return null
        val sameDay = onThisDay && daysFromToday(item.date, today) == 0
        val ago = if (onThisDay) yearsAgo(item.date, today, sameDay) else null
        return Slide.Image(
            url = item.imageUrl,
            fallbackUrl = item.thumbnailUrl,
            title = item.title,
            subtitle = listOfNotNull(item.line, ago, item.credit.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null },
            body = item.explanation,
            source = if (sameDay) "OP DEZE DAG" else null,
            voteId = "beeld:${item.id}",
        )
    }

    /** "62 jaar geleden", of "precies 62 jaar geleden" op dezelfde dag. */
    private fun yearsAgo(date: String?, today: Calendar, sameDay: Boolean): String? {
        val parts = date?.split("-") ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val day = parts.getOrNull(2)?.take(2)?.toIntOrNull() ?: 1
        val beforeBirthday = today.get(Calendar.MONTH) + 1 < month ||
            (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)
        val years = today.get(Calendar.YEAR) - year - if (beforeBirthday) 1 else 0
        if (years < 1) return null
        return (if (sameDay) "precies " else "") + (if (years == 1) "1 jaar geleden" else "$years jaar geleden")
    }

    /** Aantal dagen tussen de dag van het jaar van [date] (jjjj-mm-dd) en vandaag; jaar telt niet mee. */
    private fun daysFromToday(date: String?, today: Calendar): Int {
        val parts = date?.split("-") ?: return Int.MAX_VALUE
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return Int.MAX_VALUE
        val day = parts.getOrNull(2)?.take(2)?.toIntOrNull() ?: return Int.MAX_VALUE
        val then = Calendar.getInstance().apply {
            set(Calendar.YEAR, today.get(Calendar.YEAR))
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val diff = kotlin.math.abs(then.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR))
        return minOf(diff, 365 - diff)
    }
}
