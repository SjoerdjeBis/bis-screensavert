package nl.bis.screensaver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** Speelt een programma direct af vanuit het keuzescherm; met Terug ga je terug. */
class PreviewActivity : Activity() {
    private val scope = MainScope()
    private var controller: SlideshowController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.slideshow)
    }

    override fun onStart() {
        super.onStart()
        val program = Program.entries.firstOrNull { it.key == intent.getStringExtra(EXTRA_PROGRAM) }
        controller = SlideshowController(findViewById(R.id.root), scope, program).also { it.start() }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        controller?.handleKey(event, inScreensaver = false) == true || super.dispatchKeyEvent(event)

    companion object {
        private const val EXTRA_PROGRAM = "program"

        fun start(context: Context, program: Program) {
            context.startActivity(Intent(context, PreviewActivity::class.java).putExtra(EXTRA_PROGRAM, program.key))
        }
    }
}
