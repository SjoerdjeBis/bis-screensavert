package nl.bis.screensaver

import android.os.SystemClock
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException

/** Een foto of video die je in de fotokiezer van Google Foto's hebt aangevinkt. */
data class PickedItem(
    val id: String,
    val isVideo: Boolean,
    val baseUrl: String,
    val filename: String,
    val createTime: String?,
    val width: Int,
    val height: Int,
    val ready: Boolean,
)

/** De Google Photos Picker API: jij kiest op je telefoon, de tv haalt de keuze op. */
class PhotosPicker(private val auth: GoogleAuth) {
    data class Session(val id: String, val pickerUri: String, val pollMs: Long, val timeoutMs: Long)

    suspend fun createSession(): Session {
        val json = call("POST", "$API/sessions", "{}")
        return session(json)
    }

    /** Wacht tot je op je telefoon op "Klaar" hebt gedrukt. Geeft false bij een time-out. */
    suspend fun waitForSelection(session: Session): Boolean {
        val deadline = SystemClock.elapsedRealtime() + session.timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(session.pollMs)
            val json = call("GET", "$API/sessions/${session.id}")
            if (json.optBoolean("mediaItemsSet")) return true
        }
        return false
    }

    suspend fun items(sessionId: String): List<PickedItem> {
        val result = mutableListOf<PickedItem>()
        var pageToken: String? = null
        do {
            var url = "$API/mediaItems?sessionId=${Http.encode(sessionId)}&pageSize=100"
            if (pageToken != null) url += "&pageToken=${Http.encode(pageToken)}"
            val json = call("GET", url)
            val items = json.optJSONArray("mediaItems")
            for (i in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(i)
                val file = item.optJSONObject("mediaFile") ?: continue
                val meta = file.optJSONObject("mediaFileMetadata")
                val isVideo = item.optString("type") == "VIDEO"
                val status = meta?.optJSONObject("videoMetadata")?.optString("processingStatus")
                result += PickedItem(
                    id = item.getString("id"),
                    isVideo = isVideo,
                    baseUrl = file.getString("baseUrl"),
                    filename = file.optString("filename"),
                    createTime = item.optStringOrNull("createTime"),
                    width = meta?.optInt("width") ?: 0,
                    height = meta?.optInt("height") ?: 0,
                    ready = !isVideo || status == null || status == "READY",
                )
            }
            pageToken = json.optStringOrNull("nextPageToken")
        } while (pageToken != null)
        return result
    }

    suspend fun deleteSession(sessionId: String) {
        runCatching { call("DELETE", "$API/sessions/$sessionId") }
    }

    private suspend fun call(method: String, url: String, body: String? = null): JSONObject {
        val response = Http.request(method, url, body, bearer = auth.accessToken())
        if (response.code == 401) {
            auth.signOut()
            throw NeedsLoginException()
        }
        if (!response.ok) throw IOException("Google Foto's gaf fout ${response.code}: ${response.body.take(200)}")
        return JSONObject(response.body.ifEmpty { "{}" })
    }

    private fun session(json: JSONObject): Session {
        val polling = json.optJSONObject("pollingConfig")
        return Session(
            id = json.getString("id"),
            pickerUri = json.getString("pickerUri"),
            pollMs = duration(polling?.optString("pollInterval"), 5_000).coerceAtLeast(2_000),
            timeoutMs = duration(polling?.optString("timeoutIn"), 30 * 60_000),
        )
    }

    /** Google geeft tijden als "5s" of "1799.5s". */
    private fun duration(value: String?, default: Long): Long =
        value?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: default

    private companion object {
        const val API = "https://photospicker.googleapis.com/v1"
    }
}
