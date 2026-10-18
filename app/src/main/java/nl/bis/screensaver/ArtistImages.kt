package nl.bis.screensaver

import org.json.JSONObject

/**
 * Zoekt sfeerfoto's van een artiest. Eerst brede "fanart" van TheAudioDB (mooi voor
 * een tv-scherm), anders het artiestportret van Deezer. Beide hebben geen sleutel nodig.
 */
object ArtistImages {
    private val cache = mutableMapOf<String, List<String>>()

    suspend fun find(artist: String): List<String> {
        val name = mainArtist(artist)
        if (name.isEmpty()) return emptyList()
        cache[name.lowercase()]?.let { return it }
        val found = runCatching { audioDb(name) }.getOrDefault(emptyList())
            .ifEmpty { runCatching { deezer(name) }.getOrDefault(emptyList()) }
        cache[name.lowercase()] = found
        return found
    }

    /** "Artiest A, Artiest B feat. C" → "Artiest A". */
    private fun mainArtist(artist: String): String =
        artist.split(",", " & ", " feat. ", " ft. ", " x ", " met ").first().trim()

    private suspend fun audioDb(name: String): List<String> {
        val json = JSONObject(Http.getString("https://www.theaudiodb.com/api/v1/json/123/search.php?s=${Http.encode(name)}"))
        if (json.isNull("artists")) return emptyList()
        val artist = json.getJSONArray("artists").getJSONObject(0)
        return listOf("strArtistFanart", "strArtistFanart2", "strArtistFanart3", "strArtistFanart4", "strArtistWideThumb")
            .mapNotNull { artist.optStringOrNull(it) }
            .distinct()
    }

    private suspend fun deezer(name: String): List<String> {
        val data = JSONObject(Http.getString("https://api.deezer.com/search/artist?limit=1&q=${Http.encode(name)}"))
            .optJSONArray("data") ?: return emptyList()
        if (data.length() == 0) return emptyList()
        return listOfNotNull(data.getJSONObject(0).optStringOrNull("picture_xl"))
    }
}
