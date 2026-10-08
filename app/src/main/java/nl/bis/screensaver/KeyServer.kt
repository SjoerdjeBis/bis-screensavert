package nl.bis.screensaver

import android.content.Context
import android.net.ConnectivityManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * Een piepklein webformulier op de tv zelf, alleen bereikbaar in je eigen wifi en alleen
 * zolang het sleutelscherm open staat. Je telefoon opent het via de QR-code; wat je
 * invult, slaat de tv direct op. Een geheime code in het adres houdt anderen buiten.
 */
class KeyServer(private val context: Context, private val onSaved: (List<String>) -> Unit) {
    private val settings = Settings(context)
    private val token = ByteArray(9).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    private var server: ServerSocket? = null

    /** Start de server en geeft het adres voor de QR-code terug, of null als er geen netwerk is. */
    fun start(): String? {
        val ip = localIp() ?: return null
        val socket = (PORTS.firstNotNullOfOrNull { port -> runCatching { ServerSocket(port) }.getOrNull() }) ?: return null
        server = socket
        Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread { runCatching { handle(client) }; runCatching { client.close() } }.start()
            }
        }.start()
        return "http://$ip:${socket.localPort}/$token"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val (method, path) = requestLine.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(":").trim().toIntOrNull() ?: 0
        }
        if (path.substringBefore("?") != "/$token") {
            respond(client, 404, "<p>Niet gevonden.</p>")
            return
        }
        if (method == "POST") {
            val body = CharArray(length.coerceAtMost(20_000)).let { buf ->
                var read = 0
                while (read < buf.size) {
                    val n = reader.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            }
            val saved = save(parse(body))
            onSaved(saved)
            respond(client, 200, page(saved))
        } else {
            respond(client, 200, page(null))
        }
    }

    private fun parse(body: String): Map<String, String> = body.split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size != 2) null else URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8").trim()
    }.toMap()

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
                saved += "Sfeerlijst"
            }
        }
        return saved
    }

    private fun respond(client: Socket, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun status(set: Boolean) = if (set) "<span class=ok>ingesteld</span>" else "<span class=nee>nog niet</span>"

    private fun page(saved: List<String>?): String {
        val notice = when {
            saved == null -> ""
            saved.isEmpty() -> "<p class=melding>Er was niets ingevuld; er is niets veranderd.</p>"
            else -> "<p class=melding>Opgeslagen op de tv: ${saved.joinToString(", ")}. Je kunt dit venster sluiten.</p>"
        }
        fun field(name: String, label: String, isSet: Boolean, help: String, placeholder: String = "") = """
            <label for=$name>$label ${status(isSet)}</label>
            <input id=$name name=$name autocomplete=off autocapitalize=off spellcheck=false placeholder="$placeholder">
            <small>$help</small>"""
        return """<!doctype html><html lang=nl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>Bis Screensavert – sleutels</title>
<style>
body{margin:0;background:#0E2E2A;color:#F6EFE0;font:16px/1.5 system-ui,-apple-system,sans-serif;padding:20px}
main{max-width:520px;margin:0 auto}
h1{font:italic 600 28px/1.1 Georgia,serif;margin:8px 0 4px}
p{color:#C9D3CC}
form{display:grid;gap:6px;margin-top:18px}
label{font-weight:600;margin-top:12px;display:flex;justify-content:space-between;gap:8px}
input{font:inherit;padding:12px;border-radius:12px;border:2px solid #08201D;background:#16403A;color:#F6EFE0}
small{color:#A9B8B0;font-size:13px}
small a{color:#F0B23F}
button{margin-top:20px;font:inherit;font-weight:700;padding:14px;border-radius:14px;border:2px solid #08201D;background:#E2523C;color:#fff}
.ok{color:#7FC08C;font-weight:500;font-size:14px}.nee{color:#F0B23F;font-weight:500;font-size:14px}
.melding{background:#16403A;border:2px solid #F0B23F;border-radius:12px;padding:12px;color:#F6EFE0}
fieldset{border:none;padding:0;margin:0;display:grid;gap:6px}
legend{font:italic 600 19px Georgia,serif;margin-top:22px;color:#F0B23F}
</style></head><body><main>
<h1>Sleutels voor je tv</h1>
<p>Plak hier de sleutels; lege velden laat de tv zoals ze zijn. Alles blijft op je eigen tv.</p>
$notice
<form method=post>
<fieldset><legend>Sfeerbeelden</legend>
${field("pexels", "Pexels API-sleutel", !settings.pexelsKey.isNullOrBlank(), "Maak er een op <a href=https://www.pexels.com/api/new/>pexels.com/api/new</a>.")}
${field("pixabay", "Pixabay API-sleutel", !settings.pixabayKey.isNullOrBlank(), "Ingelogd te vinden op <a href=https://pixabay.com/api/docs/>pixabay.com/api/docs</a>, bij \"key\".")}
</fieldset>
<fieldset><legend>Jazz</legend>
${field("jamendo", "Jamendo Client ID", !settings.jamendoClientId.isNullOrBlank(), "Maak een app aan op <a href=https://devportal.jamendo.com>devportal.jamendo.com</a>.")}
</fieldset>
<fieldset><legend>Google Foto's</legend>
${field("google_id", "Client-ID", !settings.googleClientId.isNullOrBlank(), "Uit je Google Cloud-project, type \"Tv's en apparaten met beperkte invoer\".", "…apps.googleusercontent.com")}
${field("google_secret", "Clientgeheim", !settings.googleClientSecret.isNullOrBlank(), "Hoort bij de Client-ID hierboven; vul ze altijd samen in.")}
</fieldset>
<fieldset><legend>Sfeerlijst (SmartTube)</legend>
${field("lijst", "YouTube-afspeellijst", true, "Plak de link van de afspeellijst; nu ingesteld: ${settings.smartTubePlaylist}", "https://youtube.com/playlist?list=…")}
</fieldset>
<button type=submit>Opslaan op de tv</button>
</form></main></body></html>"""
    }

    private fun localIp(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    private companion object {
        val PORTS = listOf(8765, 8766, 8767, 0)
    }
}
