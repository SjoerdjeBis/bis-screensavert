package nl.bis.screensaver

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.URLEncoder

class NeedsLoginException : Exception("Opnieuw inloggen bij Google is nodig")

class AuthException(message: String) : Exception(message)

/**
 * Inloggen bij Google via je telefoon. De tv toont een QR-code naar het inlogscherm van
 * Google. Na het inloggen stuurt Google je telefoon naar een klein doorgeefpagina op GitHub,
 * die de inlogcode doorgeeft aan de tv in je eigen wifi. Daarna bewaart de app een sleutel
 * om zelf in te loggen. (Inloggen met een code op google.com/device mag niet voor de
 * fotokiezer; vandaar deze route.)
 */
class GoogleAuth(private val settings: Settings) {
    private var accessToken: String? = null
    private var accessTokenValidUntil = 0L

    val isConfigured get() = !settings.googleClientId.isNullOrBlank() && !settings.googleClientSecret.isNullOrBlank()
    val isSignedIn get() = settings.googleRefreshToken != null

    /** Het inlogadres voor de QR-code; [tvAddress] is het formulier op de tv dat de code ontvangt. */
    fun authUrl(tvAddress: String): String = "https://accounts.google.com/o/oauth2/v2/auth?" + listOf(
        "client_id" to clientId(),
        "redirect_uri" to REDIRECT,
        "response_type" to "code",
        "scope" to SCOPE,
        "access_type" to "offline",
        "prompt" to "consent",
        "state" to tvAddress,
    ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    /** Wisselt de inlogcode van de telefoon in voor een blijvende sleutel. */
    suspend fun exchange(code: String) {
        val response = Http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId(),
                "client_secret" to clientSecret(),
                "code" to code,
                "redirect_uri" to REDIRECT,
                "grant_type" to "authorization_code",
            ),
        )
        val json = JSONObject(response.body.ifEmpty { "{}" })
        if (!response.ok) throw AuthException(explain(json.optString("error"), json.optString("error_description")))
        settings.googleRefreshToken = json.optStringOrNull("refresh_token")
            ?: throw AuthException("Google gaf geen blijvende sleutel. Probeer het opnieuw.")
        remember(json)
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
        "invalid_grant" -> "De inlogcode was al gebruikt of verlopen. Probeer het opnieuw."
        "invalid_client" -> "De Google-sleutels kloppen niet. Vul ze opnieuw in via Sleutels invullen."
        "redirect_uri_mismatch" -> "Het doorstuuradres in Google Cloud klopt niet. Zie GOOGLE-FOTOS.md."
        else -> listOf("Google gaf een fout", error, description).filter { it.isNotBlank() }.joinToString(": ")
    }

    private companion object {
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"

        /** De doorgeefpagina (docs/google.html), via GitHub Pages. Moet exact zo in Google Cloud staan. */
        const val REDIRECT = "https://sjoerdjebis.github.io/bis-screensavert/google.html"
    }
}

/** Ontvangt de inlogcode die de doorgeefpagina vanaf je telefoon naar de tv stuurt. */
class GoogleLoginForm(context: Context, private val auth: GoogleAuth) : PhoneForm(context) {
    /** Klaar als de tv is ingelogd; mislukt met een [AuthException]. */
    val result = CompletableDeferred<Unit>()

    @Volatile private var signedIn = false

    override fun render(form: Map<String, String>?): String {
        val code = form?.get("code")
        val error = form?.get("error")
        val (ok, message) = when {
            signedIn ->
                true to "De tv is al ingelogd. Kijk weer op de tv."
            code != null -> try {
                runBlocking { auth.exchange(code) }
                signedIn = true
                result.complete(Unit)
                true to "Gelukt! Kijk weer op de tv: daar verschijnt een nieuwe QR-code om je foto's en video's te kiezen."
            } catch (e: Exception) {
                val text = e.message ?: "Inloggen bij Google lukte niet."
                result.completeExceptionally(AuthException(text))
                false to text
            }
            error != null -> {
                val text = if (error == "access_denied") "Je hebt de toegang geweigerd." else "Google gaf een fout: $error"
                result.completeExceptionally(AuthException(text))
                false to text
            }
            else -> false to "Scan de QR-code op de tv om in te loggen bij Google."
        }
        val safe = message.replace("&", "&amp;").replace("<", "&lt;")
        return PhoneForm.page(
            "Bis Screensavert – Google Foto's",
            "<h1>Google Foto's</h1><p class='melding${if (ok) "" else " fout"}'>$safe</p>",
        )
    }
}
