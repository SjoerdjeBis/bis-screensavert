package nl.bis.screensaver

import android.content.Context
import org.json.JSONArray

/**
 * Kiest het volgende item zo dat je niet snel hetzelfde terugziet, ook niet na een nieuwe
 * sessie of een herstart van de tv: wat recent getoond is, wordt bewaard en overgeslagen.
 *
 * Per bron (sleutel) onthoudt hij de laatste [MAX_HISTORY] getoonde items. Een item komt pas
 * terug als minstens [FRESH_SHARE] van de hele verzameling sindsdien voorbij is gekomen.
 */
class FreshPicker(context: Context, private val key: String) {
    private val prefs = context.applicationContext.getSharedPreferences("bis_geschiedenis", Context.MODE_PRIVATE)

    fun <T> pick(
        pool: List<T>,
        id: (T) -> String,
        /** Optioneel: liever niet twee keer achter elkaar dezelfde groep (bijvoorbeeld kunstenaar). */
        group: ((T) -> String?)? = null,
    ): T? {
        if (pool.isEmpty()) return null
        val history = load()
        val window = (pool.size * FRESH_SHARE).toInt().coerceIn(1, MAX_HISTORY)
        val recent = history.takeLast(window).toSet()
        val last = history.lastOrNull()
        var candidates = pool.filter { id(it) !in recent }
        if (candidates.isEmpty()) candidates = pool.filter { id(it) != last }
        if (candidates.isEmpty()) candidates = pool
        if (group != null) {
            val lastGroup = prefs.getString("$key.groep", null)
            candidates.filter { group(it) == null || group(it) != lastGroup }.ifEmpty { null }?.let { candidates = it }
        }
        val choice = candidates.random()
        remember(id(choice), group?.invoke(choice))
        return choice
    }

    private fun load(): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun remember(id: String, group: String?) {
        val history = (load().filter { it != id } + id).takeLast(MAX_HISTORY)
        prefs.edit()
            .putString(key, JSONArray(history).toString())
            .putString("$key.groep", group)
            .apply()
    }

    private companion object {
        const val MAX_HISTORY = 600
        const val FRESH_SHARE = 0.7
    }
}
