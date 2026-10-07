package nl.bis.screensaver

import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Draait de diavoorstelling in een view met layout [R.layout.slideshow].
 * Wordt gedeeld door de screensaver en de gewone app.
 */
class SlideshowController(root: View, private val scope: CoroutineScope) {
    private val context = root.context
    private val video = root.findViewById<SurfaceView>(R.id.video)
    private val imageLayer = root.findViewById<View>(R.id.image_layer)
    private val imageBackground = root.findViewById<ImageView>(R.id.image_background)
    private val image = root.findViewById<ImageView>(R.id.image)
    private val caption = root.findViewById<View>(R.id.caption)
    private val captionTitle = root.findViewById<TextView>(R.id.caption_title)
    private val captionSubtitle = root.findViewById<TextView>(R.id.caption_subtitle)
    private val captionBody = root.findViewById<TextView>(R.id.caption_body)
    private val status = root.findViewById<TextView>(R.id.status)

    private val player = ExoPlayer.Builder(context).build().apply {
        volume = 0f
        setVideoSurfaceView(video)
    }

    // Per ronde: zoveel slides uit elke bron, daarna de volgende bron.
    private val rotation: List<Pair<SlideSource, Int>> = listOf(
        ArtSource() to ARTWORKS_PER_ROUND,
        AerialSource(context) to AERIALS_PER_ROUND,
    )

    private var job: Job? = null
    private var captionJob: Job? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageBackground.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP))
        }
    }

    fun start() {
        if (job != null) return
        job = scope.launch { run() }
    }

    fun release() {
        job?.cancel()
        captionJob?.cancel()
        player.release()
    }

    private suspend fun run() {
        var failedInARow = 0
        while (scope.isActive) {
            for ((source, count) in rotation) {
                repeat(count) {
                    val shown = try {
                        source.next()?.let { show(it) } ?: false
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

    private suspend fun show(slide: Slide): Boolean = when (slide) {
        is Slide.Image -> showImage(slide)
        is Slide.Video -> playVideo(slide)
    }

    private suspend fun showImage(slide: Slide.Image): Boolean {
        val drawable = loadImage(slide.url) ?: slide.fallbackUrl?.let { loadImage(it) } ?: return false
        player.stop()
        captionJob?.cancel()
        hideStatus()
        fade(caption, 0f)
        fade(imageLayer, 0f)
        delay(FADE_MS)
        image.setImageDrawable(drawable)
        imageBackground.setImageDrawable(drawable)
        setCaption(slide)
        fade(imageLayer, 1f)
        fade(caption, 1f)
        delay(ARTWORK_DURATION_MS)
        return true
    }

    private suspend fun loadImage(url: String): Drawable? {
        val request = ImageRequest.Builder(context).data(url).build()
        return (context.imageLoader.execute(request) as? SuccessResult)?.drawable
    }

    private suspend fun playVideo(slide: Slide.Video): Boolean {
        var played = playUntilEnd(slide, slide.url)
        if (!played && slide.url.startsWith("https://")) {
            // Apple's certificaat wordt soms niet vertrouwd; probeer dan zonder https.
            played = playUntilEnd(slide, "http://" + slide.url.removePrefix("https://"))
        }
        return played
    }

    private suspend fun playUntilEnd(slide: Slide.Video, url: String): Boolean =
        withTimeoutOrNull(MAX_VIDEO_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        hideStatus()
                        fade(imageLayer, 0f)
                        setCaption(slide)
                        fade(caption, 1f)
                        captionJob?.cancel()
                        captionJob = scope.launch {
                            delay(VIDEO_CAPTION_MS)
                            fade(caption, 0f)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) finish(true)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        finish(false)
                    }

                    fun finish(result: Boolean) {
                        player.removeListener(this)
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation {
                    scope.launch { player.removeListener(listener) }
                }
                player.addListener(listener)
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        } ?: true // Te lange video: na de maximale tijd gewoon door naar de volgende.

    private fun setCaption(slide: Slide) {
        captionTitle.text = slide.title
        captionSubtitle.text = slide.subtitle.orEmpty()
        captionSubtitle.visibility = if (slide.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        captionBody.text = slide.body.orEmpty()
        captionBody.visibility = if (slide.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun fade(view: View, alpha: Float) {
        view.animate().alpha(alpha).setDuration(FADE_MS).start()
    }

    private companion object {
        const val ARTWORKS_PER_ROUND = 3
        const val AERIALS_PER_ROUND = 1
        const val ARTWORK_DURATION_MS = 45_000L
        const val VIDEO_CAPTION_MS = 10_000L
        const val MAX_VIDEO_MS = 10 * 60_000L
        const val FADE_MS = 1_200L
        const val MAX_FAILURES = 6
        const val RETRY_DELAY_MS = 60_000L
    }
}
