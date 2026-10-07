package nl.bis.screensaver

import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Draait de diavoorstelling in een view met layout [R.layout.slideshow].
 * Wordt gedeeld door de screensaver en het voorbeeld vanuit het keuzescherm.
 *
 * @param forcedProgram een programma om te tonen in plaats van het opgeslagen programma.
 */
class SlideshowController(
    root: View,
    private val scope: CoroutineScope,
    private val forcedProgram: Program? = null,
) {
    private val context = root.context
    private val settings = Settings(context)
    private val library = MediaLibrary(context)
    private val music = MusicMonitor(context)

    private val video = root.findViewById<SurfaceView>(R.id.video)
    private val imageLayer = root.findViewById<View>(R.id.image_layer)
    private val imageBackground = root.findViewById<ImageView>(R.id.image_background)
    private val image = root.findViewById<ImageView>(R.id.image)
    private val caption = root.findViewById<View>(R.id.caption)
    private val captionEyebrow = root.findViewById<TextView>(R.id.caption_eyebrow)
    private val captionTitle = root.findViewById<TextView>(R.id.caption_title)
    private val captionSubtitle = root.findViewById<TextView>(R.id.caption_subtitle)
    private val captionBody = root.findViewById<TextView>(R.id.caption_body)
    private val clock = root.findViewById<View>(R.id.clock)
    private val status = root.findViewById<TextView>(R.id.status)

    private val musicLayer = root.findViewById<View>(R.id.music_layer)
    private val musicBackdrops = listOf(
        root.findViewById<ImageView>(R.id.music_backdrop_a),
        root.findViewById<ImageView>(R.id.music_backdrop_b),
    )
    private val musicCover = root.findViewById<ImageView>(R.id.music_cover)
    private val musicTitle = root.findViewById<TextView>(R.id.music_title)
    private val musicArtist = root.findViewById<TextView>(R.id.music_artist)
    private val musicAlbum = root.findViewById<TextView>(R.id.music_album)
    private val musicProgress = root.findViewById<ProgressBar>(R.id.music_progress)
    private val musicHint = root.findViewById<TextView>(R.id.music_hint)

    private val player = ExoPlayer.Builder(context).build().apply {
        volume = 0f
        setVideoSurfaceView(video)
    }

    private val sources: Map<Mode, SlideSource> = mapOf(
        Mode.ART to ArtSource(context),
        Mode.AERIALS to AerialSource(context),
        Mode.PHOTOS to PhotoSource(context),
    )

    private val skip = Channel<Unit>(Channel.CONFLATED)
    private val musicVisible = MutableStateFlow(false)
    private var quiet = false

    private var job: Job? = null
    private var musicJob: Job? = null
    private var captionJob: Job? = null
    private var hideMusicJob: Job? = null
    private var backdropJob: Job? = null
    private var progressJob: Job? = null
    private var currentArtist: String? = null
    private var currentCoverKey: String? = null
    private var frontBackdrop = 0

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageBackground.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP))
        }
        musicCover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 18f * view.resources.displayMetrics.density)
            }
        }
        musicCover.clipToOutline = true
    }

    fun start() {
        if (job != null) return
        clock.visibility = if (settings.showClock) View.VISIBLE else View.GONE
        job = scope.launch { run() }
        if (settings.musicTakesOver) {
            music.start()
            musicJob = scope.launch { followMusic() }
        }
    }

    fun release() {
        listOf(job, musicJob, captionJob, hideMusicJob, backdropJob, progressJob).forEach { it?.cancel() }
        music.stop()
        player.release()
    }

    /**
     * Afstandsbediening. Bij muziek: OK = pauze, links/rechts = vorige/volgende.
     * In het voorbeeld (niet in de screensaver) springt rechts naar het volgende beeld.
     */
    fun handleKey(event: KeyEvent, inScreensaver: Boolean): Boolean {
        val up = event.action == KeyEvent.ACTION_UP
        if (musicVisible.value) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (up) music.playPause()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (up) music.previous()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (up) music.next()
                    return true
                }
            }
            return false
        }
        if (!inScreensaver && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (up) skip.trySend(Unit)
            return true
        }
        return false
    }

    // ---- Diavoorstelling ----

    private suspend fun run() {
        var failedInARow = 0
        while (scope.isActive) {
            val plan = SmartMix.plan(
                forcedProgram ?: settings.program,
                settings.customModes,
                hasPhotos = library.items().isNotEmpty(),
            )
            quiet = plan.quiet
            for ((mode, count) in plan.rotation) {
                repeat(count) {
                    musicVisible.first { !it }
                    val shown = try {
                        sources.getValue(mode).next()?.let { show(it, mode) } ?: false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                    if (shown) {
                        failedInARow = 0
                    } else if (++failedInARow >= MAX_FAILURES) {
                        showStatus(context.getString(R.string.no_connection))
                        delay(RETRY_DELAY_MS)
                        failedInARow = 0
                    }
                }
            }
        }
    }

    private suspend fun show(slide: Slide, mode: Mode): Boolean = when (slide) {
        is Slide.Image -> showImage(slide, mode)
        is Slide.Video -> playVideo(slide, mode)
    }

    private suspend fun showImage(slide: Slide.Image, mode: Mode): Boolean {
        val drawable = loadImage(slide.url) ?: slide.fallbackUrl?.let { loadImage(it) } ?: return false
        player.stop()
        captionJob?.cancel()
        hideStatus()
        fade(caption, 0f)
        fade(imageLayer, 0f)
        delay(FADE_MS)
        image.setImageDrawable(drawable)
        imageBackground.setImageDrawable(drawable)
        setCaption(slide, mode)
        val duration = settings.slideSeconds * 1000L
        kenBurns(image, duration)
        fade(imageLayer, 1f)
        if (showsCaption()) fade(caption, 1f)
        waitOrSkip(duration)
        return true
    }

    private suspend fun playVideo(slide: Slide.Video, mode: Mode): Boolean {
        var played = playUntilEnd(slide, mode, slide.url)
        if (!played && slide.url.startsWith("https://")) {
            // Apple's certificaat wordt soms niet vertrouwd; probeer dan zonder https.
            played = playUntilEnd(slide, mode, "http://" + slide.url.removePrefix("https://"))
        }
        return played
    }

    private suspend fun playUntilEnd(slide: Slide.Video, mode: Mode, url: String): Boolean =
        withTimeoutOrNull(MAX_VIDEO_MS) {
            suspendCancellableCoroutine { continuation ->
                var skipJob: Job? = null
                lateinit var listener: Player.Listener
                fun finish(result: Boolean) {
                    skipJob?.cancel()
                    player.removeListener(listener)
                    if (continuation.isActive) continuation.resume(result)
                }
                listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        hideStatus()
                        fade(imageLayer, 0f)
                        setCaption(slide, mode)
                        if (showsCaption()) {
                            fade(caption, 1f)
                            captionJob?.cancel()
                            captionJob = scope.launch {
                                delay(VIDEO_CAPTION_MS)
                                fade(caption, 0f)
                            }
                        } else {
                            fade(caption, 0f)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) finish(true)
                    }

                    override fun onPlayerError(error: PlaybackException) = finish(false)
                }
                skipJob = scope.launch {
                    skip.receive()
                    finish(true)
                }
                continuation.invokeOnCancellation {
                    scope.launch {
                        skipJob?.cancel()
                        player.removeListener(listener)
                    }
                }
                player.addListener(listener)
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        } ?: true // Te lange video: na de maximale tijd gewoon door naar de volgende.

    private suspend fun waitOrSkip(ms: Long) {
        withTimeoutOrNull(ms) { skip.receive() }
    }

    private suspend fun loadImage(url: String): Drawable? {
        // Nooit groter dan een 1080p-scherm: scheelt veel geheugen op de Chromecast.
        val request = ImageRequest.Builder(context).data(url).size(1920, 1080).build()
        return (context.imageLoader.execute(request) as? SuccessResult)?.drawable
    }

    private fun showsCaption() = !quiet

    private fun setCaption(slide: Slide, mode: Mode) {
        captionEyebrow.text = when (mode) {
            Mode.ART -> "KUNST · ART INSTITUTE OF CHICAGO"
            Mode.AERIALS -> "LUCHTOPNAME"
            Mode.PHOTOS -> if (slide is Slide.Video) "MIJN VIDEO'S" else "MIJN FOTO'S"
        }
        captionTitle.text = slide.title
        captionSubtitle.text = slide.subtitle.orEmpty()
        captionSubtitle.visibility = if (slide.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        val body = slide.body.takeIf { settings.showCaptions }
        captionBody.text = body.orEmpty()
        captionBody.visibility = if (body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // ---- Muziek ----

    private suspend fun followMusic() {
        music.state.collect { nowPlaying ->
            when {
                nowPlaying == null -> hideMusic()
                nowPlaying.playing -> {
                    hideMusicJob?.cancel()
                    hideMusicJob = null
                    showMusic(nowPlaying)
                }
                musicVisible.value -> {
                    // Gepauzeerd: nog even laten staan, dan terug naar de diavoorstelling.
                    showMusic(nowPlaying)
                    if (hideMusicJob == null) {
                        hideMusicJob = scope.launch {
                            delay(PAUSED_MUSIC_MS)
                            hideMusic()
                        }
                    }
                }
            }
        }
    }

    private fun showMusic(nowPlaying: NowPlaying) {
        if (!musicVisible.value) {
            musicVisible.value = true
            player.pause()
            fade(caption, 0f)
            musicLayer.visibility = View.VISIBLE
            fade(musicLayer, 1f)
            progressJob = scope.launch {
                while (isActive) {
                    val state = music.state.value
                    if (state != null && state.durationMs > 0) {
                        musicProgress.progress = (state.positionNow() * 1000 / state.durationMs).toInt()
                    }
                    delay(500)
                }
            }
        }
        musicTitle.text = nowPlaying.title
        musicArtist.text = nowPlaying.artist
        musicAlbum.text = nowPlaying.album.orEmpty()
        musicAlbum.visibility = if (nowPlaying.album.isNullOrBlank()) View.GONE else View.VISIBLE
        musicProgress.visibility = if (nowPlaying.durationMs > 0) View.VISIBLE else View.GONE
        musicHint.text = if (nowPlaying.playing) "◀  vorige      OK  pauze      volgende  ▶"
        else "◀  vorige      OK  verder spelen      volgende  ▶"

        val coverKey = nowPlaying.artUri ?: "${nowPlaying.title}|${nowPlaying.album}"
        if (coverKey != currentCoverKey) {
            currentCoverKey = coverKey
            when {
                nowPlaying.art != null -> musicCover.setImageBitmap(nowPlaying.art)
                nowPlaying.artUri != null -> scope.launch { loadImage(nowPlaying.artUri)?.let { musicCover.setImageDrawable(it) } }
                else -> musicCover.setImageDrawable(null)
            }
        }
        if (nowPlaying.artist != currentArtist) {
            currentArtist = nowPlaying.artist
            startBackdrops(nowPlaying)
        }
    }

    private fun startBackdrops(nowPlaying: NowPlaying) {
        backdropJob?.cancel()
        backdropJob = scope.launch {
            val urls = ArtistImages.find(nowPlaying.artist)
            if (urls.isEmpty()) {
                // Geen artiestfoto's: dan de hoes, groot en wazig.
                val cover = nowPlaying.art?.let { BitmapDrawable(context.resources, it) }
                    ?: nowPlaying.artUri?.let { loadImage(it) }
                showBackdrop(cover, blurred = true)
                return@launch
            }
            var index = 0
            while (isActive) {
                loadImage(urls[index % urls.size])?.let { showBackdrop(it, blurred = false) }
                index++
                delay(BACKDROP_MS)
            }
        }
    }

    private fun showBackdrop(drawable: Drawable?, blurred: Boolean) {
        val next = musicBackdrops[1 - frontBackdrop]
        val current = musicBackdrops[frontBackdrop]
        next.setImageDrawable(drawable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            next.setRenderEffect(if (blurred) RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP) else null)
        }
        next.bringToFront()
        kenBurns(next, BACKDROP_MS + FADE_MS)
        next.alpha = 0f
        fade(next, 1f)
        current.animate().alpha(0f).setStartDelay(FADE_MS).setDuration(10).start()
        frontBackdrop = 1 - frontBackdrop
        // De scrim en tekst moeten boven de achtergronden blijven liggen.
        (musicLayer as? android.view.ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child !in musicBackdrops) child.bringToFront()
            }
        }
    }

    private fun hideMusic() {
        hideMusicJob?.cancel()
        hideMusicJob = null
        if (!musicVisible.value) return
        progressJob?.cancel()
        backdropJob?.cancel()
        currentArtist = null
        currentCoverKey = null
        musicLayer.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            musicLayer.visibility = View.GONE
        }.start()
        musicVisible.value = false
        if (player.mediaItemCount > 0) player.play()
    }

    // ---- Hulpjes ----

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun fade(view: View, alpha: Float) {
        view.animate().alpha(alpha).setStartDelay(0).setDuration(FADE_MS).start()
    }

    /** Langzaam inzoomen, zodat een stilstaand beeld leeft (en het scherm niet inbrandt). */
    private fun kenBurns(view: View, durationMs: Long) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate().scaleX(1.06f).scaleY(1.06f).setStartDelay(0).setDuration(durationMs).setInterpolator(LinearInterpolator()).start()
    }

    private companion object {
        const val VIDEO_CAPTION_MS = 10_000L
        const val MAX_VIDEO_MS = 10 * 60_000L
        const val FADE_MS = 1_200L
        const val BACKDROP_MS = 25_000L
        const val PAUSED_MUSIC_MS = 60_000L
        const val MAX_FAILURES = 6
        const val RETRY_DELAY_MS = 60_000L
    }
}
