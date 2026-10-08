package nl.bis.screensaver

import android.content.Context

/** Wat er in de screensaver te zien kan zijn. */
enum class Mode(val key: String, val label: String) {
    ART("kunst", "Kunst"),
    AERIALS("aerials", "Luchtopnames"),
    PHOTOS("fotos", "Mijn foto's & video's"),
    AMBIENT("sfeer", "Sfeer"),
}

/** De keuzes op het keuzescherm. */
enum class Program(val key: String, val title: String) {
    SMART("slim", "Slimme mix"),
    ART("kunst", "Kunst"),
    AERIALS("aerials", "Luchtopnames"),
    AMBIENT("sfeer", "Sfeer"),
    PHOTOS("fotos", "Mijn foto's & video's"),
    CUSTOM("eigen", "Eigen mix"),
}

/** Alle instellingen van de app, bewaard op de tv zelf. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bis", Context.MODE_PRIVATE)

    var program: Program
        get() = Program.entries.firstOrNull { it.key == prefs.getString("program", null) } ?: Program.SMART
        set(value) = prefs.edit().putString("program", value.key).apply()

    var customModes: Set<Mode>
        get() {
            val keys = prefs.getStringSet("custom_modes", null) ?: return setOf(Mode.ART, Mode.AERIALS)
            return Mode.entries.filter { it.key in keys }.toSet()
        }
        set(value) = prefs.edit().putStringSet("custom_modes", value.map { it.key }.toSet()).apply()

    var musicTakesOver: Boolean
        get() = prefs.getBoolean("music_takes_over", true)
        set(value) = prefs.edit().putBoolean("music_takes_over", value).apply()

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

    /** Clips die je hebt weggestemd ("bron:id"). */
    var blockedClips: Set<String>
        get() = prefs.getStringSet("weggestemd", null).orEmpty()
        set(value) = prefs.edit().putStringSet("weggestemd", value).apply()

    var smartTubePlaylist: String
        get() = prefs.getString("smarttube_lijst", null) ?: DEFAULT_PLAYLIST
        set(value) = prefs.edit().putString("smarttube_lijst", value).apply()

    companion object {
        val SLIDE_SECONDS_OPTIONS = listOf(20, 30, 45, 60, 90, 120, 180, 300)
        const val DEFAULT_PLAYLIST = "PLda8vj-D2CNsgAYRi18ZZ2u3fJyAdqqTG"
    }
}
