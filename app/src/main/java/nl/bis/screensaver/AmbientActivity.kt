package nl.bis.screensaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sfeerthema's aan- en uitzetten, en weggestemde clips terugzetten. */
class AmbientActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AmbientScreen() }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun AmbientScreen() {
        val settings = remember { Settings(this) }
        var themes by remember { mutableStateOf(settings.ambientThemes) }
        var blocked by remember { mutableStateOf(settings.blockedClips.size) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Bis.Emaille).padding(horizontal = 48.dp, vertical = 36.dp)) {
            Column {
                Text("SFEER", style = Bis.eyebrow())
                Spacer(Modifier.height(8.dp))
                Text("Welke sferen wil je zien?", style = Bis.heading(34.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Zet thema's aan of uit met OK. Zie je in het voorbeeld een clip die je niet mooi vindt, druk dan op ▼: die komt nooit meer terug.",
                    style = Bis.body(14.sp, color = Bis.Room.copy(alpha = 0.85f)),
                    modifier = Modifier.width(680.dp),
                )
                Spacer(Modifier.height(22.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(820.dp),
                ) {
                    AmbientTheme.entries.forEachIndexed { i, theme ->
                        val on = theme in themes
                        FocusPill(
                            text = (if (on) "✓  " else "") + theme.label,
                            accent = if (on) Bis.Boter else null,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = {
                                themes = if (on) themes - theme else themes + theme
                                settings.ambientThemes = themes
                            },
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusPill("Alles aan", onClick = {
                        themes = AmbientTheme.entries.toSet()
                        settings.ambientThemes = themes
                    })
                    if (blocked > 0) {
                        FocusPill("$blocked weggestemde clips terugzetten", onClick = {
                            settings.blockedClips = emptySet()
                            blocked = 0
                        })
                    }
                    FocusPill("Klaar", onClick = { finish() })
                }
                Spacer(Modifier.weight(1f))
                val keys = listOfNotNull(
                    "Pexels".takeIf { !settings.pexelsKey.isNullOrBlank() },
                    "Pixabay".takeIf { !settings.pixabayKey.isNullOrBlank() },
                )
                Text(
                    if (keys.isEmpty()) "Nog niet gekoppeld. Kies in het keuzescherm 'Sleutels invullen' en plak je gratis sleutels via je telefoon (zie SFEER.md)."
                    else "Gekoppeld: ${keys.joinToString(" en ")}. Clips van ${keys.joinToString(" en ")}, maximaal 1080p.",
                    style = Bis.body(12.sp, FontWeight.Medium, if (keys.isEmpty()) Bis.Boter else Bis.RoomDim),
                )
            }
        }
    }
}
