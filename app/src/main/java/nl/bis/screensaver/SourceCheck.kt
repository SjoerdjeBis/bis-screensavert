package nl.bis.screensaver

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** De uitkomst van één controle. */
data class SourceResult(val state: State, val detail: String) {
    enum class State { OK, FAILED, OFF }
}

/** Een bron die je kunt controleren, met de naam zoals op het scherm. */
data class SourceTest(val name: String, val run: suspend () -> SourceResult)

/**
 * Probeert elke bron één keer echt, zoals de screensaver dat ook doet, en vertaalt de fout in
 * gewone taal. Zo zie je meteen of een sleutel niet klopt, een limiet bereikt is of een koppeling
 * verlopen is, in plaats van dat een bron stilletjes niets oplevert.
 */
class SourceCheck(context: Context) {
    private val app = context.applicationContext
    private val settings = Settings(app)

    val tests: List<SourceTest> = listOf(
        SourceTest("Pexels (sfeer)") { pexels() },
        SourceTest("Pixabay (sfeer)") { pixabay() },
        SourceTest("Coverr (sfeer)") { coverr() },
        SourceTest("Jamendo (muziek)") { jamendo() },
        SourceTest("Google-koppeling") { google() },
        SourceTest("Drive-map") { drive() },
        SourceTest("Luchtopnames (Apple)") { aerials() },
    )

    suspend fun run(test: SourceTest): SourceResult = try {
        test.run()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failed("Geen verbinding: ${e.message ?: e.javaClass.simpleName}")
    }

    private suspend fun pexels(): SourceResult {
        val key = settings.pexelsKey?.takeIf { it.isNotBlank() } ?: return off("Geen sleutel ingevuld.")
        val response = Http.request(
            "GET",
            "https://api.pexels.com/videos/search?per_page=1&query=" + Http.encode("ocean waves"),
            headers = mapOf("Authorization" to key),
        )
        return when {
            response.ok -> ok("Werkt: ${JSONObject(response.body).optInt("total_results")} clips bij 'zee'.")
            response.code == 401 || response.code == 403 ->
                failed("Pexels weigert de sleutel (fout ${response.code}). Plak hem opnieuw via Sleutels invullen.")
            response.code == 429 -> failed("Limiet bereikt (fout 429). Probeer het over een uur opnieuw.")
            else -> failed("Pexels gaf fout ${response.code}.")
        }
    }

    private suspend fun pixabay(): SourceResult {
        val key = settings.pixabayKey?.takeIf { it.isNotBlank() } ?: return off("Geen sleutel ingevuld.")
        val response = Http.request(
            "GET",
            "https://pixabay.com/api/videos/?per_page=3&key=${Http.encode(key)}&q=" + Http.encode("ocean waves"),
        )
        return when {
            response.ok -> ok("Werkt: ${JSONObject(response.body).optInt("totalHits")} clips bij 'zee'.")
            response.code == 400 && "key" in response.body.lowercase() ->
                failed("Pixabay herkent de sleutel niet. Plak hem opnieuw via Sleutels invullen.")
            response.code == 429 -> failed("Limiet bereikt (fout 429). Probeer het zo opnieuw.")
            else -> failed("Pixabay gaf fout ${response.code}.")
        }
    }

    private suspend fun coverr(): SourceResult {
        val key = settings.coverrKey?.takeIf { it.isNotBlank() } ?: return off("Geen sleutel ingevuld.")
        val response = Http.request(
            "GET",
            "https://api.coverr.co/videos?page_size=1&query=" + Http.encode("ocean waves"),
            bearer = key,
        )
        return when {
            response.ok -> ok("Werkt: ${JSONObject(response.body).optInt("total")} clips bij 'zee'.")
            response.code == 401 || response.code == 403 ->
                failed("Coverr weigert de sleutel (fout ${response.code}). Gebruik de API Key, niet de ID.")
            response.code == 429 -> failed("Limiet van 50 verzoeken per uur bereikt. Probeer het over een uur opnieuw.")
            else -> failed("Coverr gaf fout ${response.code}.")
        }
    }

    /** Precies wat de speler doet: dezelfde aanvraag en hetzelfde filter, zo nodig ook zonder sortering. */
    private suspend fun jamendo(): SourceResult {
        val id = settings.jamendoClientId?.takeIf { it.isNotBlank() } ?: return off("Geen Client ID ingevuld.")
        val source = settings.musicSource
        // Bij Spotify zoekt de app niets bij Jamendo; test dan de sleutel met piano.
        val style = source.takeIf { it.query != null } ?: MusicSource.PIANO
        val label = style.label.lowercase()
        val steps = mutableListOf<String>()
        for (ordered in listOf(true, false)) {
            val response = Http.request("GET", JamendoPlayer.tracksUrl(style.query!!, id, ordered))
            val how = if (ordered) "populairste eerst" else "zonder sortering"
            if (!response.ok) {
                steps += "$how: fout ${response.code}"
                continue
            }
            val json = JSONObject(response.body)
            val headers = json.optJSONObject("headers")
            if (headers?.optString("status") != "success") {
                steps += "$how: ${headers?.optString("error_message")?.ifBlank { null } ?: "geweigerd"}"
                continue
            }
            val found = json.optJSONArray("results")?.length() ?: 0
            val playable = JamendoPlayer.playable(json).size
            if (playable > 0) {
                val note = if (source == MusicSource.SPOTIFY) "; de muziekbron staat nu op Spotify" else ""
                return ok("Werkt: $playable speelbare $label-nummers ($how, $found gevonden)$note.")
            }
            steps += "$how: $found gevonden, 0 speelbaar"
        }
        return failed("Geen speelbare $label-nummers. ${steps.joinToString("; ")}.")
    }

    private suspend fun google(): SourceResult {
        val auth = GoogleAuth(settings)
        if (!auth.isConfigured) return off("Geen Google-sleutels ingevuld.")
        if (!auth.isSignedIn) return off("Nog niet ingelogd. Doe dat via Foto's beheren.")
        return try {
            auth.accessToken()
            ok(if (auth.hasDrive) "Ingelogd, met toegang tot de fotokiezer en Drive." else "Ingelogd voor de fotokiezer; nog niet voor Drive.")
        } catch (e: NeedsLoginException) {
            failed("De koppeling is verlopen. Log opnieuw in via Foto's beheren.")
        } catch (e: AuthException) {
            failed(e.message ?: "Inloggen lukte niet.")
        }
    }

    private suspend fun drive(): SourceResult {
        val folder = settings.driveFolderId ?: return off("Geen Drive-map gekoppeld.")
        val auth = GoogleAuth(settings)
        if (!auth.hasDrive) return failed("Geen toegang tot Drive. Log opnieuw in via Foto's beheren › Drive-map.")
        return try {
            val files = DriveClient(auth).files(folder)
            val videos = files.count { it.isVideo }
            ok("Map '${settings.driveFolderName ?: "?"}': ${files.size - videos} foto's en $videos video's.")
        } catch (e: NeedsLoginException) {
            failed("De koppeling is verlopen. Log opnieuw in via Foto's beheren.")
        } catch (e: java.io.IOException) {
            failed(e.message ?: "Drive gaf een fout.")
        }
    }

    private suspend fun aerials(): SourceResult {
        // Net als de bron zelf: lukt https niet (Apple's certificaat), dan via http.
        val response = runCatching { Http.request("HEAD", "https://sylvan.apple.com/Aerials/resources-16.tar") }
            .getOrElse { Http.request("HEAD", "http://sylvan.apple.com/Aerials/resources-16.tar") }
        return if (response.ok) ok("De lijst van Apple is bereikbaar.") else failed("Apple gaf fout ${response.code}.")
    }

    private fun ok(text: String) = SourceResult(SourceResult.State.OK, text)
    private fun failed(text: String) = SourceResult(SourceResult.State.FAILED, text)
    private fun off(text: String) = SourceResult(SourceResult.State.OFF, text)
}
