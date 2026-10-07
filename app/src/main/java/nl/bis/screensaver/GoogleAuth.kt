package nl.bis.screensaver

import android.os.SystemClock
import kotlinx.coroutines.delay
import org.json.JSONObject

class NeedsLoginException : Exception("Opnieuw inloggen bij Google is nodig")

class AuthException(message: String) : Exception(message)

/**
 * Inloggen bij Google zonder toetsenbord: de tv toont een code, jij vult die in op je
 * telefoon ("OAuth voor tv's"). Daarna bewaart de app een sleutel om zelf in te loggen.
 */
class GoogleAuth(private val settings: Settings) {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
    )

    private var accessToken: String? = null
    private var accessTokenValidUntil = 0L

    val isConfigured get() = !settings.googleClientId.isNullOrBlank() && !settings.googleClientSecret.isNullOrBlank()
    val isSignedIn get() = settings.googleRefreshToken != null

    suspend fun startDeviceFlow(): DeviceCode {
        val response = Http.postForm(
            "https://oauth2.googleapis.com/device/code",
            mapOf("client_id" to clientId(), "scope" to SCOPE),
        )
        val json = JSONObject(response.body.ifEmpty { "{}" })
        if (!response.ok) throw AuthException(explain(json.optString("error"), json.optString("error_description")))
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.optStringOrNull("verification_url") ?: "https://www.google.com/device",
            intervalSeconds = json.optInt("interval", 5),
            expiresInSeconds = json.optInt("expires_in", 1800),
        )
    }

    /** Wacht tot de code op de telefoon is ingevuld; gooit een [AuthException] als het misgaat. */
    suspend fun waitForLogin(code: DeviceCode) {
        var interval = code.intervalSeconds
        val deadline = SystemClock.elapsedRealtime() + code.expiresInSeconds * 1000L
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(interval * 1000L)
            val response = Http.postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId(),
                    "client_secret" to clientSecret(),
                    "device_code" to code.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
            val json = JSONObject(response.body.ifEmpty { "{}" })
            if (response.ok) {
                settings.googleRefreshToken = json.getString("refresh_token")
                remember(json)
                return
            }
            when (json.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                else -> throw AuthException(explain(json.optString("error"), json.optString("error_description")))
            }
        }
        throw AuthException("De code is verlopen. Probeer het opnieuw.")
    }

    suspend fun accessToken(): String {
        accessToken?.let { if (SystemClock.elapsedRealtime() < accessTokenValidUntil) return it }
        val refreshToken = settings.googleRefreshToken ?: throw NeedsLoginException()
        val response = Http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId(),
                "client_secret" to clientSecret(),
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            ),
        )
        val json = JSONObject(response.body.ifEmpty { "{}" })
        if (!response.ok) {
            if (json.optString("error") == "invalid_grant") {
                // In de testmodus van Google verloopt de koppeling na 7 dagen.
                signOut()
                throw NeedsLoginException()
            }
            throw AuthException(explain(json.optString("error"), json.optString("error_description")))
        }
        return remember(json)
    }

    fun signOut() {
        settings.googleRefreshToken = null
        accessToken = null
    }

    private fun remember(json: JSONObject): String {
        val token = json.getString("access_token")
        accessToken = token
        accessTokenValidUntil = SystemClock.elapsedRealtime() + (json.optLong("expires_in", 3600) - 120) * 1000
        return token
    }

    private fun clientId() = settings.googleClientId ?: throw AuthException("Google Foto's is nog niet gekoppeld.")
    private fun clientSecret() = settings.googleClientSecret ?: throw AuthException("Google Foto's is nog niet gekoppeld.")

    private fun explain(error: String, description: String): String = when (error) {
        "access_denied" -> "Je hebt de toegang geweigerd op je telefoon."
        "expired_token" -> "De code is verlopen. Probeer het opnieuw."
        "invalid_client" -> "De Google-sleutels kloppen niet. Draai het installatiescript opnieuw en plak ze nog eens."
        "invalid_scope" -> "Google staat de fotokiezer niet toe voor dit soort sleutel. Zie GOOGLE-FOTOS.md, kopje 'Als het niet lukt'."
        else -> listOf("Google gaf een fout", error, description).filter { it.isNotBlank() }.joinToString(": ")
    }

    private companion object {
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    }
}
