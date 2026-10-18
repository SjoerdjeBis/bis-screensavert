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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Bronnen controleren: per bron of hij werkt, en zo niet, wat er mis is. */
class SourcesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SourcesScreen() }
    }

    @Composable
    private fun SourcesScreen() {
        val check = remember { SourceCheck(this) }
        val results = remember { mutableStateMapOf<String, SourceResult>() }
        var round by remember { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }

        // Alle bronnen tegelijk; elke uitkomst verschijnt zodra hij binnen is.
        LaunchedEffect(round) {
            results.clear()
            check.tests.forEach { test -> launch { results[test.name] = check.run(test) } }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Bis.Emaille).padding(horizontal = 48.dp, vertical = 32.dp)) {
            Column {
                Text("BRONNEN", style = Bis.eyebrow())
                Spacer(Modifier.height(6.dp))
                Text("Werkt alles?", style = Bis.heading(30.sp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "De tv probeert elke bron één keer, zoals de screensaver dat ook doet.",
                    style = Bis.body(13.sp, color = Bis.RoomDim),
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    check.tests.forEach { test -> ResultRow(test.name, results[test.name]) }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusPill("Opnieuw testen", onClick = { round++ })
                    FocusPill("Klaar", onClick = { finish() }, modifier = Modifier.focusRequester(focus))
                }
            }
        }
    }

    @Composable
    private fun ResultRow(name: String, result: SourceResult?) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Bis.body(14.sp, FontWeight.Bold, Bis.Room), modifier = Modifier.width(210.dp))
            val (label, color, text) = when (result?.state) {
                null -> Triple("BEZIG", Bis.Emaille3, Bis.RoomDim)
                SourceResult.State.OK -> Triple("WERKT", Bis.Boter, Color(0xFF20180A))
                SourceResult.State.FAILED -> Triple("FOUT", Bis.Tomaat, Color.White)
                SourceResult.State.OFF -> Triple("UIT", Bis.Emaille3, Bis.RoomDim)
            }
            Box(Modifier.width(78.dp)) { Tag(label, color = color, textColor = text) }
            Text(
                result?.detail ?: "Even geduld…",
                style = Bis.body(13.sp, color = if (result?.state == SourceResult.State.FAILED) Bis.Room else Bis.RoomDim),
                maxLines = 2,
                modifier = Modifier.width(560.dp),
            )
        }
    }
}
