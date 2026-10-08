package nl.bis.screensaver

import android.content.Context
import java.io.File

/** Grenzen aan wat de app op de Chromecast mag gebruiken. */
object Storage {
    /** Eigen foto's en video's samen. */
    const val MAX_MEDIA_BYTES = 2_000_000_000L

    /**
     * Altijd minstens zoveel vrij laten voor de tv zelf en andere apps. Een Chromecast heeft
     * vaak maar een paar honderd MB vrij; meer reserveren betekent dat er niets bij kan.
     */
    const val MIN_FREE_BYTES = 200_000_000L

    /** Tijdelijk bewaarde kunstwerken en artiestfoto's. */
    const val IMAGE_CACHE_BYTES = 100L * 1024 * 1024

    fun cacheBytes(context: Context): Long = size(context.cacheDir)

    fun size(file: File): Long =
        if (file.isDirectory) file.listFiles()?.sumOf { size(it) } ?: 0 else file.length()
}

/** Ruimt bij het starten op wat er niet meer nodig is. */
object Housekeeping {
    fun run(context: Context) {
        Thread {
            runCatching {
                MediaLibrary(context).removeOrphans()
                // Oude sfeerlijsten (ouder dan een week) en achtergebleven updates.
                val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                context.cacheDir.listFiles()
                    ?.filter { (it.name.startsWith("sfeer-") && it.lastModified() < weekAgo) || it.name.endsWith(".apk") }
                    ?.forEach { it.delete() }
            }
        }.start()
    }
}
