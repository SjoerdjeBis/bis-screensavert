package nl.bis.screensaver

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** Start de diavoorstelling direct vanaf het startscherm; met Terug ga je eruit. */
class MainActivity : Activity() {
    private val scope = MainScope()
    private var controller: SlideshowController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.slideshow)
    }

    override fun onStart() {
        super.onStart()
        controller = SlideshowController(findViewById(R.id.root), scope).also { it.start() }
    }

    override fun onStop() {
        controller?.release()
        controller = null
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
