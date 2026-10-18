package nl.bis.screensaver

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Zelf instellen als screensaver: koppelcode invullen op je telefoon terwijl het koppelvenster op de tv open staat. */
class SelfSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SelfSetup.loadLog(this)
        setContent { SetupScreen() }
    }

    // Ook bij terugkomen uit Instellingen: draait het formulier niet meer, dan start het opnieuw op hetzelfde adres.
    override fun onResume() {
        super.onResume()
        startForegroundService(Intent(this, SelfSetupService::class.java))
    }

    @Composable
    private fun SetupScreen() {
        val address by SelfSetup.address.collectAsState()
        val status by SelfSetup.status.collectAsState()
        val log by SelfSetup.log.collectAsState()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Bis.Emaille).padding(horizontal = 48.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                address?.let { QrCode(it, 240.dp) }
                Spacer(Modifier.width(44.dp))
                Column(Modifier.width(580.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SCREENSAVER INSTELLEN", style = Bis.eyebrow())
                    Text("De app stelt zichzelf in", style = Bis.heading(30.sp))
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        "Scan de QR-code met je telefoon. Die moet op dezelfde wifi zitten als de tv.",
                        "Druk op Home en ga naar Instellingen › Systeem › Ontwikkelaarsopties › Draadloze foutopsporing. Zet het aan.",
                        "Vul op je telefoon de poort in die achter het IP-adres staat.",
                        "Kies op de tv Apparaat koppelen met koppelingscode. Vul de code en poort uit dat venster in op je telefoon en tik op Koppelen en instellen.",
                        "Je telefoon laat zien of het gelukt is. Daarna mag Draadloze foutopsporing weer uit.",
                    ).forEachIndexed { i, step ->
                        Text("${i + 1}.  $step", style = Bis.body(15.sp, color = Bis.Room.copy(alpha = 0.9f)))
                    }
                    when {
                        address == null && status == null -> Text("Even geduld… Lukt het niet, controleer dan de wifi.", style = Bis.body(13.sp, color = Bis.RoomDim))
                        address != null -> Text("Of typ in de browser: $address", style = Bis.body(12.sp, color = Bis.RoomDim))
                    }
                    status?.let { Text(it, style = Bis.body(15.sp, FontWeight.Medium, Bis.Boter)) }
                    if (log.isNotEmpty()) {
                        Text(log.joinToString("\n"), style = Bis.body(11.sp, color = Bis.RoomDim))
                    }
                    Spacer(Modifier.height(10.dp))
                    FocusPill("Klaar", onClick = {
                        SelfSetup.stopReason = "je drukte op Klaar"
                        stopService(Intent(this@SelfSetupActivity, SelfSetupService::class.java))
                        finish()
                    }, modifier = Modifier.focusRequester(focus))
                }
            }
        }
    }
}
