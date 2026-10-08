package nl.bis.screensaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * De app stelt zichzelf in als screensaver, zonder computer. Hij koppelt via Draadloze
 * foutopsporing met de tv waar hij zelf op draait en geeft daarna dezelfde opdrachten als
 * het installatiescript. De koppelcode vul je in op je telefoon, omdat het koppelvenster
 * op de tv open moet blijven.
 */
object SelfSetup {
    /** Adres van het telefoonformulier, of null als het niet draait. */
    val address = MutableStateFlow<String?>(null)

    /** Laatste melding, voor op de tv. */
    val status = MutableStateFlow<String?>(null)

    class Result(val ok: Boolean, val lines: List<String>)

    /** Koppelt (als er een code is), verbindt en stelt alles in. Draait op een achtergronddraad. */
    fun configure(context: Context, code: String, pairPort: Int?, connectPort: Int?): Result {
        val host = PhoneForm.localIp(context) ?: "127.0.0.1"
        val pkg = context.packageName
        val lines = mutableListOf<String>()
        fun fail(message: String) = Result(false, lines + message).also { status.value = message }
        return try {
            BisAdb(context).use { adb ->
                if (code.isNotEmpty()) {
                    if (pairPort == null) return fail("Vul ook de koppelpoort in, het getal achter de dubbele punt in het koppelvenster.")
                    status.value = "Koppelen…"
                    if (!adb.pair(host, pairPort, code)) return fail("Koppelen lukte niet. Klopt de code, en staat het koppelvenster nog open?")
                    lines += "Gekoppeld met de tv."
                }
                status.value = "Verbinden…"
                val connected = if (connectPort != null) adb.connect(host, connectPort) else adb.connectTls(context, 10_000)
                if (!connected) return fail("Verbinden lukte niet. Vul de poort in die bij Draadloze foutopsporing achter het IP-adres staat.")
                status.value = "Instellen…"
                adb.shell("settings put secure screensaver_enabled 1")
                adb.shell("settings put secure screensaver_components $pkg/.ScreenSaverDream")
                adb.shell("cmd notification allow_listener $pkg/$pkg.MediaListener")
                adb.shell("appops set $pkg REQUEST_INSTALL_PACKAGES allow")
                val screensaver = adb.shell("settings get secure screensaver_components").contains(pkg)
                val music = adb.shell("settings get secure enabled_notification_listeners").contains(pkg)
                lines += if (screensaver) "Bis Screensavert is nu de screensaver." else "De screensaver kon niet worden ingesteld."
                lines += if (music) "Muziektoegang staat aan." else "Muziektoegang kon niet worden aangezet."
                lines += "Bijwerken met één knop is toegestaan."
                status.value = if (screensaver) "Klaar: Bis Screensavert is de screensaver." else "De screensaver kon niet worden ingesteld."
                Result(screensaver, lines)
            }
        } catch (e: Exception) {
            fail("Er ging iets mis: ${e.message ?: e.javaClass.simpleName}. Probeer het opnieuw met een nieuw koppelvenster.")
        }
    }
}

/** ADB-verbinding met de eigen tv; de sleutel blijft bewaard, zodat koppelen maar één keer hoeft. */
private class BisAdb(context: Context) : AbsAdbConnectionManager() {
    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
        val keyFile = File(context.filesDir, "adb_sleutel.pk8")
        val certFile = File(context.filesDir, "adb_certificaat.der")
        val stored = runCatching {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes())) to
                certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        }.getOrNull()
        if (stored != null) {
            key = stored.first
            cert = stored.second
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val bc = BouncyCastleProvider()
            val name = X500Name("CN=Bis Screensavert")
            val now = System.currentTimeMillis()
            val day = 86_400_000L
            val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - day), Date(now + 3650 * day), name, pair.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(bc).build(pair.private))
            key = pair.private
            cert = JcaX509CertificateConverter().setProvider(bc).getCertificate(holder)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "Bis Screensavert"

    fun shell(command: String): String =
        openStream("shell:$command").use { it.openInputStream().bufferedReader().readText().trim() }
}

/** Het telefoonformulier voor de koppelcode. */
private class PairForm(context: Context) : PhoneForm(context) {
    override fun render(form: Map<String, String>?): String {
        val result = form?.let {
            val code = it["code"].orEmpty().filter(Char::isDigit)
            SelfSetup.configure(context, code, it["koppelpoort"]?.toIntOrNull(), it["poort"]?.toIntOrNull())
        }
        val notice = when {
            result == null -> ""
            result.ok -> "<p class=melding>${result.lines.joinToString("<br>")}<br><br>Je kunt Draadloze foutopsporing op de tv weer uitzetten en dit venster sluiten.</p>"
            else -> "<p class='melding fout'>${result.lines.joinToString("<br>")}</p>"
        }
        return PhoneForm.page("Bis Screensavert – koppelen", """<h1>Koppelen met je tv</h1>
<p>Hiermee stelt de app zichzelf in als screensaver. Dit hoef je maar één keer te doen.</p>
$notice
<ol>
<li>Ga op de tv naar <b>Instellingen › Systeem › Ontwikkelaarsopties › Draadloze foutopsporing</b> en zet het aan.</li>
<li>Onder <b>IP-adres en poort</b> staat iets als <b>192.168.68.61:41363</b>. Vul het getal na de dubbele punt hieronder in bij <b>Poort</b>.</li>
<li>Kies op de tv <b>Apparaat koppelen met koppelingscode</b>. Laat dat venster open staan.</li>
<li>Vul de code en de poort uit dat venster hieronder in en tik op <b>Koppelen en instellen</b>.</li>
</ol>
<form method=post>
<label for=poort>Poort (uit stap 2)</label>
<input id=poort name=poort inputmode=numeric autocomplete=off placeholder="bijv. 41363">
<label for=code>Koppelingscode (uit het venster)</label>
<input id=code name=code inputmode=numeric autocomplete=off placeholder="6 cijfers">
<label for=koppelpoort>Koppelpoort (uit het venster)</label>
<input id=koppelpoort name=koppelpoort inputmode=numeric autocomplete=off placeholder="bijv. 42471">
<small>Al eerder gekoppeld? Dan is alleen de poort uit stap 2 genoeg.</small>
<button type=submit>Koppelen en instellen</button>
</form>""")
    }
}

/**
 * Houdt het telefoonformulier in leven terwijl je op de tv in Instellingen zit. Zonder
 * voorgronddienst zet Android de app dan stil. Stopt vanzelf na een kwartier.
 */
class SelfSetupService : Service() {
    private var form: PairForm? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (form == null) {
            SelfSetup.status.value = null
            form = PairForm(this).also { SelfSetup.address.value = it.start() }
            handler.postDelayed({ stopSelf() }, 15 * 60_000L)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        form?.stop()
        form = null
        SelfSetup.address.value = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Instellen", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Bis Screensavert")
            .setContentText("Wacht op de koppelcode van je telefoon")
            .build()
    }

    private companion object {
        const val CHANNEL = "instellen"
        const val NOTIFICATION_ID = 7
    }
}
