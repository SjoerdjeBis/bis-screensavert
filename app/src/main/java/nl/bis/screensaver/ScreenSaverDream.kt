package nl.bis.screensaver

import android.service.dreams.DreamService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** De screensaver die Google TV start als de tv een tijdje niets doet. */
class ScreenSaverDream : DreamService() {
    private val scope = MainScope()
    private var controller: SlideshowController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        setContentView(R.layout.slideshow)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        controller = SlideshowController(findViewById(R.id.root), scope).also { it.start() }
    }

    override fun onDreamingStopped() {
        controller?.release()
        controller = null
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }
}
