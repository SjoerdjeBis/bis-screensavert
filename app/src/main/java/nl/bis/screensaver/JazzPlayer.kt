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

/** Een jazznummer van Jamendo (Creative Commons). */
data class JazzTrack(val id: String, val title: String, val artist: String, val url: String)

/**
 * Speelt jazz onder de screensaver als je bij Muziek "Jazz" hebt gekozen. Vraagt nooit de
 * "audiofocus", zodat Spotify nooit door de app wordt gepauzeerd; speelt er muziek, dan
 * zwijgt de jazz zelf.
 */
class JazzPlayer(private val context: Context, private val scope: CoroutineScope) {
    private val settings = Settings(context)
    private val trackPicker = FreshPicker(context, "jazz")
    private var jazz: ExoPlayer? = null
    private var jazzJob: Job? = null
    private var tracks: List<JazzTrack>? = null
    private var muted = false

    private val _nowPlaying = MutableStateFlow<JazzTrack?>(null)
    val nowPlaying: StateFlow<JazzTrack?> = _nowPlaying.asStateFlow()

    fun start() = apply()

    fun release() {
        jazzJob?.cancel()
        jazz?.release()
        jazz = null
        _nowPlaying.value = null
    }

    /** Stil als er andere muziek (Spotify) speelt. */
    fun setMuted(value: Boolean) {
        if (muted == value) return
        muted = value
        apply()
    }

    /** Het huidige jazznummer wegstemmen en doorgaan met het volgende. */
    fun blockCurrentTrack() {
        val track = _nowPlaying.value ?: return
        settings.blockedTracks = settings.blockedTracks + track.id
        playNextTrack()
    }

    private fun volume(): Float {
        if (muted || settings.musicSource != MusicSource.JAZZ) return 0f
        return when (settings.jazzLevel) {
            1 -> 0.18f
            3 -> 0.75f
            else -> 0.4f
        }
    }

    private fun apply() {
        val volume = volume()
        if (volume > 0f) {
            val player = jazz
            if (player == null) {
                if (jazzJob?.isActive != true) jazzJob = scope.launch { startJazz() }
            } else {
                player.volume = volume
                player.play()
            }
        } else {
            jazz?.pause()
        }
    }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Geen audiofocus: Spotify en andere apps worden nooit door ons onderbroken.
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
    }

    private suspend fun startJazz() {
        val list = tracks ?: loadTracks().also { tracks = it }
        if (list.isEmpty()) return
        if (jazz == null) {
            jazz = newPlayer().apply {
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
        val player = jazz ?: return
        val blocked = settings.blockedTracks
        val track = trackPicker.pick(tracks.orEmpty().filter { it.id !in blocked }, { it.id }) ?: return
        _nowPlaying.value = track
        player.setMediaItem(MediaItem.fromUri(track.url))
        player.prepare()
        player.volume = volume()
        if (player.volume > 0f) player.play()
    }

    /** Rustige, instrumentale jazz, de populairste eerst; een dag bewaard. */
    private suspend fun loadTracks(): List<JazzTrack> = withContext(Dispatchers.IO) {
        val clientId = settings.jamendoClientId?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val cache = File(context.cacheDir, "jazz.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 24 * 60 * 60 * 1000L
        val json = if (fresh) {
            cache.readText()
        } else {
            runCatching {
                Http.getString(
                    "https://api.jamendo.com/v3.0/tracks/?format=json&limit=200&audioformat=mp32" +
                        "&order=popularity_total&vocalinstrumental=instrumental&speed=low+medium" +
                        "&fuzzytags=jazz+lounge+smoothjazz+easylistening&client_id=" + Http.encode(clientId),
                ).also { cache.writeText(it) }
            }.getOrElse { if (cache.exists()) cache.readText() else return@withContext emptyList() }
        }
        val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i ->
            val t = results.getJSONObject(i)
            val audio = t.optStringOrNull("audio") ?: return@mapNotNull null
            JazzTrack("jamendo:${t.optString("id")}", t.optString("name"), t.optString("artist_name"), audio)
        }
    }
}
