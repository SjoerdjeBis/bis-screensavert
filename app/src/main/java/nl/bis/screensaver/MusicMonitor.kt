package nl.bis.screensaver

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wat er nu speelt, zoals Spotify het aan Android doorgeeft. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String?,
    val art: Bitmap?,
    val artUri: String?,
    val playing: Boolean,
    val durationMs: Long,
    private val positionMs: Long,
    private val positionUpdatedAt: Long,
    private val speed: Float,
) {
    /** Huidige positie, doorgerekend sinds de laatste update. */
    fun positionNow(): Long {
        if (!playing) return positionMs
        val elapsed = SystemClock.elapsedRealtime() - positionUpdatedAt
        return (positionMs + elapsed * speed).toLong().coerceIn(0, durationMs.coerceAtLeast(0))
    }
}

/** Volgt de mediasessies op de tv (Spotify en andere muziekapps) en kan ze bedienen. */
class MusicMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(appContext, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var started = false

    val hasAccess: Boolean
        get() = NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        if (started || !hasAccess) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            started = true
            refresh()
        } catch (e: SecurityException) {
            // Toegang is ingetrokken; dan blijft muziek gewoon uit beeld.
        }
    }

    fun stop() {
        if (started) manager.removeOnActiveSessionsChangedListener(sessionsListener)
        started = false
        controller?.unregisterCallback(callback)
        controller = null
        _state.value = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()

    fun previous() = controller?.transportControls?.skipToPrevious()

    private fun refresh() {
        val sessions = try {
            manager.getActiveSessions(component)
        } catch (e: SecurityException) {
            emptyList()
        }
        pick(sessions)
    }

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == controller?.packageName }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        update()
    }

    private fun update() {
        val c = controller
        val metadata = c?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || metadata == null || title.isNullOrBlank()) {
            _state.value = null
            return
        }
        val playback = c.playbackState
        _state.value = NowPlaying(
            title = title,
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
            artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
            playing = playback?.state == PlaybackState.STATE_PLAYING,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = playback?.position ?: 0,
            positionUpdatedAt = playback?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed ?: 1f,
        )
    }
}
