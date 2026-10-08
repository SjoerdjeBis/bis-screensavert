package nl.bis.screensaver

import android.service.dreams.DreamService
import android.view.KeyEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** De screensaver die Google TV start als de tv een tijdje niets doet. */
class ScreenSaverDream : DreamService() {
    private val scope = MainScope()
    private var controller: SlideshowController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Interactief, zodat je de muziek met de afstandsbediening kunt bedienen.
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        setContentView(R.layout.slideshow)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        controller = SlideshowController(findViewById(R.id.root), scope).also { it.start() }
        // Zolang de screensaver draait, kun je je foto's op je telefoon beheren.
        PhoneLibraryHost.acquire(this)
    }

    override fun onDreamingStopped() {
        PhoneLibraryHost.release()
        controller?.release()
        controller = null
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controller?.handleKey(event, inScreensaver = true) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return super.dispatchKeyEvent(event)
        }
        // Elke andere knop maakt de tv weer wakker.
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }
}
