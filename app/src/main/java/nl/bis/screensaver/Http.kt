package nl.bis.screensaver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Http {
    private const val USER_AGENT = "BisScreenSaver/0.1 (persoonlijke screensaver)"

    suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        open(url) { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Opent [url] en geeft de inhoud als stream aan [block]; altijd op een achtergrondthread aanroepen. */
    fun <T> open(url: String, block: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        // Het Art Institute of Chicago vraagt apps zich zo te identificeren.
        connection.setRequestProperty("AIC-User-Agent", USER_AGENT)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code voor $url")
            return connection.inputStream.use(block)
        } finally {
            connection.disconnect()
        }
    }
}
