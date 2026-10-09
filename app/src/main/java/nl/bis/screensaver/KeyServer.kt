package nl.bis.screensaver

import android.content.Context

/** Het sleutelformulier: je telefoon opent het via de QR-code; wat je invult, slaat de tv direct op. */
class KeyServer(context: Context, private val onSaved: (List<String>) -> Unit) : PhoneForm(context) {
    private val settings = Settings(context)

    override fun render(form: Map<String, String>?): String {
        val saved = form?.let { save(it).also(onSaved) }
        return keysPage(saved)
    }

    /** Slaat alleen ingevulde velden op; geeft terug wat er is opgeslagen. */
    private fun save(form: Map<String, String>): List<String> {
        val saved = mutableListOf<String>()
        form["pexels"]?.takeIf { it.isNotEmpty() }?.let { settings.pexelsKey = it; saved += "Pexels" }
        form["pixabay"]?.takeIf { it.isNotEmpty() }?.let { settings.pixabayKey = it; saved += "Pixabay" }
        form["jamendo"]?.takeIf { it.isNotEmpty() }?.let { settings.jamendoClientId = it; saved += "Jamendo" }
        val googleId = form["google_id"]?.takeIf { it.isNotEmpty() }
        val googleSecret = form["google_secret"]?.takeIf { it.isNotEmpty() }
        if (googleId != null && googleSecret != null) {
            if (googleId != settings.googleClientId) settings.googleRefreshToken = null
            settings.googleClientId = googleId
            settings.googleClientSecret = googleSecret
            saved += "Google Foto's"
        }
        form["lijst"]?.takeIf { it.isNotEmpty() }?.let { input ->
            val id = Regex("[?&]list=([A-Za-z0-9_-]+)").find(input)?.groupValues?.get(1) ?: input.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,}")) }
            if (id != null) {
                settings.smartTubePlaylist = id
                saved += "SmartTube-afspeellijst"
            }
        }
        return saved
    }

    private fun status(set: Boolean) = if (set) "<span class=ok>ingesteld</span>" else "<span class=nee>nog niet</span>"

    private fun keysPage(saved: List<String>?): String {
        val notice = when {
            saved == null -> ""
            saved.isEmpty() -> "<p class=melding>Er was niets ingevuld; er is niets veranderd.</p>"
            else -> "<p class=melding>Opgeslagen op de tv: ${saved.joinToString(", ")}. Je kunt dit venster sluiten.</p>"
        }
        fun field(name: String, label: String, isSet: Boolean, help: String, placeholder: String = "") = """
            <label for=$name>$label ${status(isSet)}</label>
            <input id=$name name=$name autocomplete=off autocapitalize=off spellcheck=false placeholder="$placeholder">
            <small>$help</small>"""
        return PhoneForm.page("Bis Screensavert – sleutels", """<h1>Sleutels voor je tv</h1>
<p>Plak hier de sleutels; lege velden laat de tv zoals ze zijn. Alles blijft op je eigen tv.</p>
$notice
<form method=post>
<fieldset><legend>Sfeerbeelden</legend>
${field("pexels", "Pexels API-sleutel", !settings.pexelsKey.isNullOrBlank(), "Maak er een op <a href=https://www.pexels.com/api/new/>pexels.com/api/new</a>.")}
${field("pixabay", "Pixabay API-sleutel", !settings.pixabayKey.isNullOrBlank(), "Ingelogd te vinden op <a href=https://pixabay.com/api/docs/>pixabay.com/api/docs</a>, bij \"key\".")}
</fieldset>
<fieldset><legend>Muziek</legend>
${field("jamendo", "Jamendo Client ID", !settings.jamendoClientId.isNullOrBlank(), "Maak een app aan op <a href=https://devportal.jamendo.com>devportal.jamendo.com</a>.")}
</fieldset>
<fieldset><legend>Google Foto's</legend>
${field("google_id", "Client-ID", !settings.googleClientId.isNullOrBlank(), "Uit je Google Cloud-project, type \"Tv's en apparaten met beperkte invoer\".", "…apps.googleusercontent.com")}
${field("google_secret", "Clientgeheim", !settings.googleClientSecret.isNullOrBlank(), "Hoort bij de Client-ID hierboven; vul ze altijd samen in.")}
</fieldset>
<fieldset><legend>SmartTube</legend>
${field("lijst", "YouTube-afspeellijst", true, "Plak de link van de afspeellijst; nu ingesteld: ${settings.smartTubePlaylist}", "https://youtube.com/playlist?list=…")}
</fieldset>
<button type=submit>Opslaan op de tv</button>
</form>""")
    }
}
