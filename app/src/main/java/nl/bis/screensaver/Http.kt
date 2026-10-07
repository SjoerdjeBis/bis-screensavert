package nl.bis.screensaver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    private const val USER_AGENT = "BisScreensavert/1.0 (persoonlijke screensaver)"

    class Response(val code: Int, val body: String) {
        val ok get() = code in 200..299
    }

    suspend fun getString(url: String, bearer: String? = null): String = withContext(Dispatchers.IO) {
        open(url, bearer) { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Een verzoek waarbij ook foutantwoorden worden teruggegeven, zodat de aanroeper ze kan lezen. */
    suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json",
        bearer: String? = null,
    ): Response = withContext(Dispatchers.IO) {
        val connection = connect(url, bearer)
        try {
            connection.requestMethod = method
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            Response(code, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    suspend fun postForm(url: String, params: Map<String, String>): Response =
        request(
            "POST",
            url,
            params.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" },
            "application/x-www-form-urlencoded",
        )

    /** Downloadt naar [target] via een tijdelijk bestand, zodat er nooit een half bestand achterblijft. */
    suspend fun download(url: String, target: File, bearer: String? = null): Long = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, target.name + ".deel")
        try {
            val size = open(url, bearer) { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(target)) throw IOException("Kon ${target.name} niet opslaan")
            size
        } finally {
            partial.delete()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Opent [url] en geeft de inhoud als stream aan [block]; altijd op een achtergrondthread aanroepen. */
    fun <T> open(url: String, bearer: String? = null, block: (InputStream) -> T): T {
        val connection = connect(url, bearer)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code voor $url")
            return connection.inputStream.use(block)
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(url: String, bearer: String?): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        // Het Art Institute of Chicago vraagt apps zich zo te identificeren.
        connection.setRequestProperty("AIC-User-Agent", USER_AGENT)
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        return connection
    }
}
