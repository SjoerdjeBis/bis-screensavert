package nl.bis.screensaver

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Je eigen foto's en video's uit Google Foto's, zoals ze op de tv zijn opgeslagen. */
class PhotoSource(context: Context) : SlideSource {
    private val library = MediaLibrary(context)
    private val queue = ArrayDeque<LocalMedia>()
    private val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale("nl", "NL"))

    override suspend fun next(): Slide? {
        if (queue.isEmpty()) queue.addAll(library.items().shuffled())
        val item = queue.removeFirstOrNull() ?: return null
        val file = library.file(item)
        if (!file.exists()) return null
        val date = if (item.createdAt > 0) dateFormat.format(Date(item.createdAt)) else "Mijn foto's"
        val uri = "file://" + file.absolutePath
        return if (item.isVideo) {
            Slide.Video(url = uri, title = date, subtitle = null)
        } else {
            Slide.Image(url = uri, fallbackUrl = null, title = date, subtitle = null, body = null)
        }
    }
}
