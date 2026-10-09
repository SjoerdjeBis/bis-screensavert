package nl.bis.screensaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sleutels invullen via je telefoon: QR-code scannen, plakken, opslaan. */
class KeysActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KeysScreen() }
    }

    @Composable
    private fun KeysScreen() {
        val settings = remember { Settings(this) }
        var address by remember { mutableStateOf<String?>(null) }
        var started by remember { mutableStateOf(false) }
        var lastSaved by remember { mutableStateOf<List<String>>(emptyList()) }
        var refresh by remember { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }

        DisposableEffect(Unit) {
            val server = KeyServer(this@KeysActivity) { saved ->
                runOnUiThread {
                    lastSaved = saved
                    refresh++
                }
            }
            address = server.start()
            started = true
            onDispose { server.stop() }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Bis.Emaille).padding(horizontal = 48.dp, vertical = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val url = address
                if (url != null) QrCode(url, 260.dp)
                Spacer(Modifier.width(48.dp))
                Column(Modifier.width(520.dp)) {
                    Text("SLEUTELS", style = Bis.eyebrow())
                    Spacer(Modifier.height(8.dp))
                    Text("Vul je sleutels in op je telefoon", style = Bis.heading(32.sp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            !started -> "Even geduld…"
                            url == null -> "De tv heeft geen netwerkverbinding. Controleer de wifi en open dit scherm opnieuw."
                            else -> "Scan de QR-code met je telefoon. Die moet op hetzelfde wifi-netwerk zitten als de tv. " +
                                "Plak de sleutels in het formulier en tik op Opslaan. Dit scherm moet open blijven tot je klaar bent."
                        },
                        style = Bis.body(15.sp, color = Bis.Room.copy(alpha = 0.9f)),
                    )
                    if (url != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Of typ in de browser: $url", style = Bis.body(12.sp, color = Bis.RoomDim))
                    }
                    Spacer(Modifier.height(18.dp))
                    // Opnieuw lezen na elke opslag.
                    val status = remember(refresh) {
                        listOf(
                            "Pexels" to !settings.pexelsKey.isNullOrBlank(),
                            "Pixabay" to !settings.pixabayKey.isNullOrBlank(),
                            "Jamendo (muziek)" to !settings.jamendoClientId.isNullOrBlank(),
                            "Google Foto's" to (settings.googleClientId != null && settings.googleClientSecret != null),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        status.forEach { (name, ok) ->
                            Tag(
                                (if (ok) "✓ " else "– ") + name.uppercase(),
                                color = if (ok) Bis.Boter else Bis.Emaille3,
                                textColor = if (ok) androidx.compose.ui.graphics.Color(0xFF20180A) else Bis.RoomDim,
                            )
                        }
                    }
                    if (lastSaved.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Opgeslagen: ${lastSaved.joinToString(", ")}", style = Bis.body(14.sp, FontWeight.Medium, Bis.Boter))
                    }
                    Spacer(Modifier.height(22.dp))
                    FocusPill("Klaar", onClick = { finish() }, modifier = Modifier.focusRequester(focus))
                }
            }
        }
    }
}
