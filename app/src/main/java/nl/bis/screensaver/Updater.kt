package nl.bis.screensaver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Een nieuwere versie die klaarstaat op GitHub. */
data class AvailableUpdate(val versionCode: Long, val versionName: String, val apkUrl: String)

/**
 * Bijwerken vanaf de tv zelf: de app kijkt op de downloadpagina of er een nieuwere versie is,
 * downloadt die en vraagt Android om hem te installeren. Jij drukt alleen nog op "Bijwerken".
 */
object Updater {
    private const val RELEASE = "https://api.github.com/repos/SjoerdjeBis/Claude-rest/releases/tags/laatste"
    private const val APK_NAME = "BisScreensavert.apk"
    private const val VERSION_NAME = "versie.json"

    fun installedVersion(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** Geeft een update terug als die er is; null als er niets nieuws is of de pagina niet bereikbaar is. */
    suspend fun check(context: Context): AvailableUpdate? = runCatching {
        val release = JSONObject(Http.getString(RELEASE))
        val assets = release.getJSONArray("assets")
        val urls = (0 until assets.length()).associate {
            val a = assets.getJSONObject(it)
            a.getString("name") to a.getString("browser_download_url")
        }
        val apk = urls[APK_NAME] ?: return null
        val version = JSONObject(Http.getString(urls[VERSION_NAME] ?: return null))
        val code = version.getLong("versionCode")
        if (code <= installedVersion(context)) return null
        AvailableUpdate(code, version.optString("versionName", "nieuw"), apk)
    }.getOrNull()

    /** Downloadt de nieuwe versie en start de installatie; Android toont daarna zelf de bevestiging. */
    suspend fun install(context: Context, update: AvailableUpdate) {
        val file = File(context.cacheDir, "update.apk")
        Http.download(update.apkUrl, file)
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("bis", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        }
    }
}

/** Ontvangt de uitkomst van de installatie en toont zo nodig de bevestiging van Android. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Toast.makeText(
                context,
                "Bijwerken lukte niet: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "onbekende fout"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
