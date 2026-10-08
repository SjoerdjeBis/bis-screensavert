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
 * zolang het nodig is. Je telefoon opent het via een QR-code. Een geheime code in het
 * adres houdt anderen buiten.
 */
abstract class PhoneForm(protected val context: Context) {
    private val token = ByteArray(9).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    private var server: ServerSocket? = null

    /** De pagina: [form] is null bij openen, en bevat de ingevulde velden na versturen. */
    protected abstract fun render(form: Map<String, String>?): String

    /** Start de server en geeft het adres voor de QR-code terug, of null als er geen netwerk is. */
    fun start(): String? {
        val ip = localIp(context) ?: return null
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
            respond(client, 200, render(parse(body)))
        } else {
            respond(client, 200, render(null))
        }
    }

    private fun parse(body: String): Map<String, String> = body.split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size != 2) null else URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8").trim()
    }.toMap()

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

    companion object {
        private val PORTS = listOf(8765, 8766, 8767, 0)

        fun localIp(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }

        /** Gedeelde opmaak voor de telefoonpagina's, in Bis-kleuren. */
        fun page(title: String, content: String) = """<!doctype html><html lang=nl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>$title</title>
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
.fout{border-color:#E2523C}
ol{padding-left:20px;color:#C9D3CC}li{margin:6px 0}b{color:#F6EFE0}
</style></head><body><main>
$content
</main></body></html>"""
    }
}
