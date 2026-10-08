package nl.bis.screensaver

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * Opent instelschermen van Google TV. Sommige zijn op Google TV verborgen; we proberen de
 * bekende ingangen na elkaar en melden het als geen enkele bestaat. De screensaver kiezen kan
 * op Google TV helemaal niet via een scherm; dat doet [SelfSetup].
 */
object SystemSettings {
    /** Het scherm "Meldingstoegang", nodig voor het muziekscherm bij Spotify. */
    fun openMusicAccess(context: Context): Boolean {
        val component = ComponentName(context, MediaListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }
        return open(
            context,
            "Google TV laat de meldingstoegang niet zien. Dat regelt de app nu zelf via koppelen.",
            *listOfNotNull(detail, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")).toTypedArray(),
        )
    }

    /** Toestemming om zichzelf bij te werken ("onbekende apps installeren"). */
    fun canInstallUpdates(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) = open(
        context,
        "Zet bij Instellingen › Apps › Bis Screensavert 'Onbekende apps installeren' aan.",
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
    )

    private fun open(context: Context, failMessage: String, vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        Toast.makeText(context, failMessage, Toast.LENGTH_LONG).show()
        return false
    }
}
