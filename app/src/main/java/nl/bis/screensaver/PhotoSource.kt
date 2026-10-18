package nl.bis.screensaver

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Je eigen foto's en video's: opgeslagen op de tv, of (video's uit Drive) rechtstreeks uit Google Drive. */
class PhotoSource(context: Context) : SlideSource {
    private val library = MediaLibrary(context)
    private val auth = GoogleAuth(Settings(context))
    private val picker = FreshPicker(context, "fotos")
    private val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale("nl", "NL"))

    override suspend fun next(): Slide? {
        val item = picker.pick(library.items(), { it.id }) ?: return null
        val date = if (item.createdAt > 0) dateFormat.format(Date(item.createdAt)) else "Mijn foto's"
        if (item.streamed) {
            val driveId = item.driveId ?: return null
            // Zonder geldige koppeling (of internet) slaat de diavoorstelling deze video over.
            val token = runCatching { auth.accessToken() }.getOrNull() ?: return null
            library.countPlay(item)
            return Slide.Video(url = DriveClient.mediaUrl(driveId), title = date, subtitle = null, bearer = token)
        }
        val file = library.file(item)
        if (!file.exists()) return null
        library.countPlay(item)
        val uri = "file://" + file.absolutePath
        return if (item.isVideo) {
            Slide.Video(url = uri, title = date, subtitle = null)
        } else {
            Slide.Image(url = uri, fallbackUrl = null, title = date, subtitle = null, body = null)
        }
    }
}
