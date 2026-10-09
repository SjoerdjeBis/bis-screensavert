package nl.bis.screensaver

import android.content.Context

/** Wat er in de screensaver te zien kan zijn. */
enum class Mode(val key: String, val label: String) {
    ART("kunst", "Kunst"),
    AERIALS("aerials", "Luchtopnames"),
    PHOTOS("fotos", "Mijn foto's & video's"),
    AMBIENT("sfeer", "Sfeer"),
    NATURE("natuur", "Natuur"),
    HISTORY("toen", "Nederland van toen"),
}

/** De keuzes op het keuzescherm. */
enum class Program(val key: String, val title: String) {
    ART("kunst", "Kunst"),
    AERIALS("aerials", "Luchtopnames"),
    AMBIENT("sfeer", "Sfeer"),
    PHOTOS("fotos", "Mijn foto's & video's"),
    NATURE("natuur", "Natuur"),
    HISTORY("toen", "Toen"),
    CUSTOM("eigen", "Eigen mix"),
    SPOTIFY("spotify", "Spotify"),
}

/**
 * Spotify: de app speelt zelf niets en toont wat Spotify op de tv speelt.
 * Jazz: rustige jazz van Jamendo; gaat Spotify spelen, dan zwijgt de jazz.
 */
enum class MusicSource(val key: String, val label: String) {
    SPOTIFY("spotify", "Spotify"),
    JAZZ("jazz", "Jazz"),
}

/** Alle instellingen van de app, bewaard op de tv zelf. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bis", Context.MODE_PRIVATE)

    var program: Program
        get() = Program.entries.firstOrNull { it.key == prefs.getString("program", null) } ?: Program.CUSTOM
        set(value) = prefs.edit().putString("program", value.key).apply()

    var customModes: Set<Mode>
        get() {
            val keys = prefs.getStringSet("custom_modes", null) ?: return setOf(Mode.ART, Mode.AERIALS)
            return Mode.entries.filter { it.key in keys }.toSet()
        }
        set(value) = prefs.edit().putStringSet("custom_modes", value.map { it.key }.toSet()).apply()

    var showClock: Boolean
        get() = prefs.getBoolean("show_clock", true)
        set(value) = prefs.edit().putBoolean("show_clock", value).apply()

    var showCaptions: Boolean
        get() = prefs.getBoolean("show_captions", true)
        set(value) = prefs.edit().putBoolean("show_captions", value).apply()

    var slideSeconds: Int
        get() = prefs.getInt("slide_seconds", 45)
        set(value) = prefs.edit().putInt("slide_seconds", value).apply()

    var googleClientId: String?
        get() = prefs.getString("google_client_id", null)
        set(value) = prefs.edit().putString("google_client_id", value).apply()

    var googleClientSecret: String?
        get() = prefs.getString("google_client_secret", null)
        set(value) = prefs.edit().putString("google_client_secret", value).apply()

    var googleRefreshToken: String?
        get() = prefs.getString("google_refresh_token", null)
        set(value) = prefs.edit().putString("google_refresh_token", value).apply()

    var pexelsKey: String?
        get() = prefs.getString("pexels_key", null)
        set(value) = prefs.edit().putString("pexels_key", value).apply()

    var pixabayKey: String?
        get() = prefs.getString("pixabay_key", null)
        set(value) = prefs.edit().putString("pixabay_key", value).apply()

    val hasAmbientKeys get() = !pexelsKey.isNullOrBlank() || !pixabayKey.isNullOrBlank()

    /** Thema's die aan staan; standaard alle. */
    var ambientThemes: Set<AmbientTheme>
        get() {
            val keys = prefs.getStringSet("sfeer_themas", null) ?: return AmbientTheme.entries.toSet()
            return AmbientTheme.entries.filter { it.key in keys }.toSet()
        }
        set(value) = prefs.edit().putStringSet("sfeer_themas", value.map { it.key }.toSet()).apply()

    /** Beelden die je in het voorbeeld hebt weggestemd ("beeld:bron:id"). */
    var blockedImages: Set<String>
        get() = prefs.getStringSet("weggestemde_beelden", null).orEmpty()
        set(value) = prefs.edit().putStringSet("weggestemde_beelden", value).apply()

    /** Clips die je hebt weggestemd ("bron:id"). */
    var blockedClips: Set<String>
        get() = prefs.getStringSet("weggestemd", null).orEmpty()
        set(value) = prefs.edit().putStringSet("weggestemd", value).apply()

    var smartTubePlaylist: String
        get() = prefs.getString("smarttube_lijst", null) ?: DEFAULT_PLAYLIST
        set(value) = prefs.edit().putString("smarttube_lijst", value).apply()

    var jamendoClientId: String?
        get() = prefs.getString("jamendo_id", null)
        set(value) = prefs.edit().putString("jamendo_id", value).apply()

    /** Welke muziek de screensaver begeleidt. */
    var musicSource: MusicSource
        get() = MusicSource.entries.firstOrNull { it.key == prefs.getString("muziekbron", null) } ?: MusicSource.SPOTIFY
        set(value) = prefs.edit().putString("muziekbron", value.key).apply()

    /** Volume van de jazz: 1 = zacht, 2 = middel, 3 = luid. */
    var jazzLevel: Int
        get() = prefs.getInt("jazz_volume", 2).coerceIn(1, 3)
        set(value) = prefs.edit().putInt("jazz_volume", value.coerceIn(1, 3)).apply()

    /** Jazznummers die je hebt weggestemd ("jamendo:id"). */
    var blockedTracks: Set<String>
        get() = prefs.getStringSet("weggestemde_nummers", null).orEmpty()
        set(value) = prefs.edit().putStringSet("weggestemde_nummers", value).apply()

    companion object {
        val SLIDE_SECONDS_OPTIONS = listOf(20, 30, 45, 60, 90, 120, 180, 300)
        const val DEFAULT_PLAYLIST = "PLda8vj-D2CNsgAYRi18ZZ2u3fJyAdqqTG"
    }
}
