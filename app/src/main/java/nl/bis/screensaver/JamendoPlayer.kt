package nl.bis.screensaver

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Een nummer van Jamendo (Creative Commons). */
data class JamendoTrack(val id: String, val title: String, val artist: String, val url: String)

/**
 * Speelt muziek van Jamendo onder de screensaver als je bij Muziek Piano, Gitaar of Country hebt
 * gekozen. Vraagt nooit de "audiofocus", zodat Spotify nooit door de app wordt gepauzeerd;
 * speelt er muziek, dan zwijgt deze speler zelf.
 */
class JamendoPlayer(private val context: Context, private val scope: CoroutineScope) {
    private val settings = Settings(context)
    private val source = settings.musicSource
    private val trackPicker = FreshPicker(context, "muziek_${source.key}")
    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var tracks: List<JamendoTrack>? = null
    private var muted = false

    private val _nowPlaying = MutableStateFlow<JamendoTrack?>(null)
    val nowPlaying: StateFlow<JamendoTrack?> = _nowPlaying.asStateFlow()

    fun start() = apply()

    fun release() {
        loadJob?.cancel()
        player?.release()
        player = null
        _nowPlaying.value = null
    }

    /** Stil als er andere muziek (Spotify) speelt. */
    fun setMuted(value: Boolean) {
        if (muted == value) return
        muted = value
        apply()
    }

    /** Het huidige nummer wegstemmen en doorgaan met het volgende. */
    fun blockCurrentTrack() {
        val track = _nowPlaying.value ?: return
        settings.blockedTracks = settings.blockedTracks + track.id
        playNextTrack()
    }

    private fun volume(): Float {
        if (muted || source.query == null) return 0f
        return when (settings.musicLevel) {
            1 -> 0.18f
            3 -> 0.75f
            else -> 0.4f
        }
    }

    private fun apply() {
        val volume = volume()
        if (volume > 0f) {
            val current = player
            if (current == null) {
                if (loadJob?.isActive != true) loadJob = scope.launch { startPlaying() }
            } else {
                current.volume = volume
                current.play()
            }
        } else {
            player?.pause()
        }
    }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Geen audiofocus: Spotify en andere apps worden nooit door ons onderbroken.
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
    }

    private suspend fun startPlaying() {
        val list = tracks ?: loadTracks().also { tracks = it }
        if (list.isEmpty()) return
        if (player == null) {
            player = newPlayer().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) playNextTrack()
                    }

                    override fun onPlayerError(error: PlaybackException) = playNextTrack()
                })
            }
        }
        playNextTrack()
    }

    private fun playNextTrack() {
        val current = player ?: return
        val blocked = settings.blockedTracks
        val track = trackPicker.pick(tracks.orEmpty().filter { it.id !in blocked }, { it.id }) ?: return
        _nowPlaying.value = track
        current.setMediaItem(MediaItem.fromUri(track.url))
        current.prepare()
        current.volume = volume()
        if (current.volume > 0f) current.play()
    }

    /**
     * Rustige, instrumentale nummers in de gekozen stijl, de populairste eerst; een dag bewaard.
     * Nummers met een tag uit [EXCLUDED_TAGS] vallen af.
     */
    private suspend fun loadTracks(): List<JamendoTrack> = withContext(Dispatchers.IO) {
        val query = source.query ?: return@withContext emptyList()
        val clientId = settings.jamendoClientId?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        File(context.cacheDir, "jazz.json").delete() // Van de vroegere jazz.
        val cache = File(context.cacheDir, "muziek_${source.key}.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 24 * 60 * 60 * 1000L
        val json = if (fresh) {
            cache.readText()
        } else {
            runCatching {
                Http.getString(
                    "https://api.jamendo.com/v3.0/tracks/?format=json&limit=200&audioformat=mp32" +
                        "&order=popularity_total&vocalinstrumental=instrumental&include=musicinfo" +
                        query + "&client_id=" + Http.encode(clientId),
                ).also { cache.writeText(it) }
            }.getOrElse { if (cache.exists()) cache.readText() else return@withContext emptyList() }
        }
        val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i ->
            val t = results.getJSONObject(i)
            val audio = t.optStringOrNull("audio") ?: return@mapNotNull null
            if (tagsOf(t).any { it in EXCLUDED_TAGS }) return@mapNotNull null
            JamendoTrack("jamendo:${t.optString("id")}", t.optString("name"), t.optString("artist_name"), audio)
        }
    }

    /** Genres, instrumenten en overige tags van een nummer, in kleine letters. */
    private fun tagsOf(track: JSONObject): List<String> {
        val tags = track.optJSONObject("musicinfo")?.optJSONObject("tags") ?: return emptyList()
        return listOf("genres", "instruments", "vartags").flatMap { key ->
            val list = tags.optJSONArray(key) ?: JSONArray()
            (0 until list.length()).map { list.optString(it).lowercase() }
        }
    }

    private companion object {
        /** Past niet onder een rustige screensaver. */
        val EXCLUDED_TAGS = setOf("filmscore", "energetic", "rnb", "hiphop", "rock", "electronic")
    }
}
