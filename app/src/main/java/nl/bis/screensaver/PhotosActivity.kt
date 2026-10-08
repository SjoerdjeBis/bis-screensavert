package nl.bis.screensaver

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Foto's en video's uit Google Foto's toevoegen en beheren. */
class PhotosActivity : ComponentActivity() {

    private sealed interface Step {
        data object Overview : Step
        data class SignIn(val code: GoogleAuth.DeviceCode) : Step
        data class Picking(val uri: String) : Step
        data class Downloading(val done: Int, val total: Int) : Step
        data class Message(val text: String) : Step
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhotosScreen() }
    }

    @Composable
    private fun PhotosScreen() {
        val settings = remember { Settings(this) }
        val auth = remember { GoogleAuth(settings) }
        val picker = remember { PhotosPicker(auth) }
        val library = remember { MediaLibrary(this) }
        val scope = rememberCoroutineScope()

        var step by remember { mutableStateOf<Step>(Step.Overview) }
        var items by remember { mutableStateOf(library.items().sortedByDescending { it.createdAt }) }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var job by remember { mutableStateOf<Job?>(null) }

        fun startAdding(attempt: Int = 0) {
            job?.cancel()
            job = scope.launch {
                try {
                    if (!auth.isSignedIn) {
                        val code = auth.startDeviceFlow()
                        step = Step.SignIn(code)
                        auth.waitForLogin(code)
                    }
                    val session = picker.createSession()
                    step = Step.Picking(session.pickerUri)
                    if (!picker.waitForSelection(session)) {
                        step = Step.Message("Er is niets gekozen binnen de tijd. Probeer het gerust opnieuw.")
                        return@launch
                    }
                    val picked = picker.items(session.id)
                    step = Step.Downloading(0, picked.size)
                    val (added, message) = library.import(picked, { auth.accessToken() }) { done, total ->
                        step = Step.Downloading(done, total)
                    }
                    picker.deleteSession(session.id)
                    items = library.items().sortedByDescending { it.createdAt }
                    step = Step.Message(
                        listOfNotNull(
                            when (added) {
                                0 -> "Niets nieuws toegevoegd."
                                1 -> "1 nieuw item toegevoegd."
                                else -> "$added nieuwe items toegevoegd."
                            },
                            message,
                        ).joinToString(" "),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NeedsLoginException) {
                    if (attempt == 0) startAdding(attempt + 1) else step = Step.Message("Inloggen bij Google lukte niet. Probeer het later opnieuw.")
                } catch (e: AuthException) {
                    step = Step.Message(e.message ?: "Inloggen bij Google lukte niet.")
                } catch (e: Exception) {
                    step = Step.Message("Er ging iets mis: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

        BackHandler(enabled = step != Step.Overview) {
            job?.cancel()
            step = Step.Overview
        }

        Box(Modifier.fillMaxSize().background(Bis.Emaille).padding(horizontal = 48.dp, vertical = 36.dp)) {
            when (val s = step) {
                Step.Overview -> Overview(
                    configured = auth.isConfigured,
                    items = items,
                    selected = selected,
                    library = library,
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    onAdd = { startAdding() },
                    onDelete = {
                        library.delete(selected)
                        selected = emptySet()
                        items = library.items().sortedByDescending { it.createdAt }
                    },
                    onDone = { finish() },
                )
                is Step.SignIn -> QrStep(
                    eyebrow = "STAP 1 VAN 2 · INLOGGEN BIJ GOOGLE",
                    title = "Log in op je telefoon",
                    qr = s.code.verificationUrl,
                    lines = listOf(
                        "Scan de code, of ga op je telefoon naar ${s.code.verificationUrl.removePrefix("https://")}.",
                        "Vul daar deze code in, kies je Google-account en geef toestemming:",
                    ),
                    bigCode = s.code.userCode,
                    footnote = "Dit hoeft maar af en toe; de tv onthoudt het.",
                    onCancel = { job?.cancel(); step = Step.Overview },
                )
                is Step.Picking -> QrStep(
                    eyebrow = "STAP 2 VAN 2 · KIEZEN",
                    title = "Kies je foto's en video's",
                    qr = s.uri,
                    lines = listOf(
                        "Scan de code met je telefoon. Google Foto's opent vanzelf.",
                        "Vink aan wat je op de tv wilt zien en tik op Klaar. De tv haalt het daarna op.",
                    ),
                    bigCode = null,
                    footnote = "Tip: kies liever korte video's. De Chromecast heeft weinig opslag.",
                    onCancel = { job?.cancel(); step = Step.Overview },
                )
                is Step.Downloading -> Column(Modifier.align(Alignment.CenterStart)) {
                    Text("OPHALEN", style = Bis.eyebrow())
                    Spacer(Modifier.height(8.dp))
                    Text("Bezig met ophalen… ${s.done} van ${s.total}", style = Bis.heading(34.sp))
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.width(520.dp).height(8.dp).clip(RoundedCornerShape(50)).background(Bis.Emaille3)) {
                        val fraction = if (s.total == 0) 0f else s.done.toFloat() / s.total
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Bis.Boter))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Video's duren langer. Je kunt dit scherm gewoon laten staan.", style = Bis.body(13.sp, color = Bis.RoomDim))
                }
                is Step.Message -> Column(Modifier.align(Alignment.CenterStart).width(620.dp)) {
                    Text("MIJN FOTO'S & VIDEO'S", style = Bis.eyebrow())
                    Spacer(Modifier.height(8.dp))
                    Text(s.text, style = Bis.heading(28.sp))
                    Spacer(Modifier.height(24.dp))
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    FocusPill("Terug", onClick = { step = Step.Overview }, modifier = Modifier.focusRequester(focus))
                }
            }
        }
    }

    @Composable
    private fun Overview(
        configured: Boolean,
        items: List<LocalMedia>,
        selected: Set<String>,
        library: MediaLibrary,
        onToggle: (String) -> Unit,
        onAdd: () -> Unit,
        onDelete: () -> Unit,
        onDone: () -> Unit,
    ) {
        val photos = items.count { !it.isVideo }
        val videos = items.size - photos
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                Text("MIJN FOTO'S & VIDEO'S", style = Bis.eyebrow())
                Spacer(Modifier.height(8.dp))
                Text("Uit Google Foto's", style = Bis.heading(34.sp))
                Spacer(Modifier.height(10.dp))
                Text("$photos foto's · $videos video's", style = Bis.body(15.sp, FontWeight.Medium))
                Text(
                    "${Formatter.formatShortFileSize(this@PhotosActivity, library.usedBytes())} van " +
                        "${Formatter.formatShortFileSize(this@PhotosActivity, Storage.MAX_MEDIA_BYTES)} gebruikt · " +
                        "${Formatter.formatShortFileSize(this@PhotosActivity, library.freeBytes())} vrij op de tv",
                    style = Bis.body(12.sp, color = Bis.RoomDim),
                )
                Spacer(Modifier.height(24.dp))
                if (configured) {
                    FocusCard(onClick = onAdd, modifier = Modifier.focusRequester(firstFocus), background = Bis.Tomaat, shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "+  Foto's en video's toevoegen",
                            style = Bis.body(14.sp, FontWeight.Bold, Color.White),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                        )
                    }
                } else {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(Bis.Emaille2)
                            .border(2.dp, Bis.Rim, RoundedCornerShape(16.dp)).padding(16.dp),
                    ) {
                        Text(
                            "Google Foto's is nog niet gekoppeld. Kies in het keuzescherm 'Sleutels invullen' en plak je " +
                                "Client-ID en Clientgeheim via je telefoon. Hoe je die maakt, staat in GOOGLE-FOTOS.md.",
                            style = Bis.body(13.sp, color = Bis.Room),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (selected.isNotEmpty()) {
                    FocusPill("Verwijder ${selected.size} gekozen", onClick = onDelete, accent = Bis.Tomaat)
                    Spacer(Modifier.height(10.dp))
                }
                FocusPill("Klaar", onClick = onDone, modifier = if (configured) Modifier else Modifier.focusRequester(firstFocus))
                Spacer(Modifier.weight(1f))
                Text(
                    "Kies OK op een foto om hem te selecteren; daarna kun je de selectie verwijderen.",
                    style = Bis.body(11.sp, color = Bis.RoomDim),
                )
            }
            Spacer(Modifier.width(36.dp))
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nog geen foto's of video's.\nKies 'Toevoegen' om er een paar uit te zoeken.",
                        style = Bis.heading(22.sp, Bis.RoomDim),
                    )
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("nl", "NL")) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        FocusCard(
                            onClick = { onToggle(item.id) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.aspectRatio(4f / 3f),
                        ) {
                            if (item.isVideo) {
                                Box(Modifier.fillMaxSize().background(Bis.Emaille3), contentAlignment = Alignment.Center) {
                                    Text("▶", style = Bis.body(26.sp, color = Bis.Boter))
                                }
                            } else {
                                AsyncImage(
                                    model = library.file(item),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (item.createdAt > 0) {
                                Text(
                                    dateFormat.format(Date(item.createdAt)),
                                    style = Bis.body(10.sp, FontWeight.Medium, Bis.Room),
                                    modifier = Modifier.align(Alignment.BottomStart)
                                        .background(Bis.Emaille.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            if (isSelected) {
                                Box(Modifier.fillMaxSize().background(Bis.Tomaat.copy(alpha = 0.35f)))
                                Tag("✓ GEKOZEN", color = Bis.Tomaat, textColor = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun QrStep(
        eyebrow: String,
        title: String,
        qr: String,
        lines: List<String>,
        bigCode: String?,
        footnote: String,
        onCancel: () -> Unit,
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            QrCode(qr, 260.dp)
            Spacer(Modifier.width(48.dp))
            Column(Modifier.width(520.dp)) {
                Text(eyebrow, style = Bis.eyebrow())
                Spacer(Modifier.height(8.dp))
                Text(title, style = Bis.heading(36.sp))
                Spacer(Modifier.height(14.dp))
                lines.forEach {
                    Text(it, style = Bis.body(15.sp, color = Bis.Room.copy(alpha = 0.9f)))
                    Spacer(Modifier.height(6.dp))
                }
                if (bigCode != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bigCode,
                        style = Bis.body(44.sp, FontWeight.Bold, Bis.Boter).copy(letterSpacing = 4.sp),
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Bis.Emaille2)
                            .border(2.dp, Bis.Rim, RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(footnote, style = Bis.body(12.sp, color = Bis.RoomDim))
                Spacer(Modifier.height(20.dp))
                FocusPill("Annuleren", onClick = onCancel, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}
