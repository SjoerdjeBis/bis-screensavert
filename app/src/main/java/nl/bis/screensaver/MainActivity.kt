package nl.bis.screensaver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Het keuzescherm: kies wat je wilt zien en welke muziek erbij hoort. De achtergrond en de
 * uitleg volgen de kaart waar je op staat.
 */
class MainActivity : ComponentActivity() {
    private lateinit var music: MusicMonitor
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        music = MusicMonitor(this)
        setContent { ChooserScreen(refresh) }
    }

    override fun onStart() {
        super.onStart()
        music.start()
        refresh++ // Na terugkomst uit foto's of voorbeeld de tellers opnieuw lezen.
    }

    override fun onStop() {
        music.stop()
        super.onStop()
    }

    private fun isActiveScreensaver(): Boolean? = try {
        Secure.getString(contentResolver, "screensaver_components")?.contains(packageName)
    } catch (e: SecurityException) {
        null
    }

    @Composable
    private fun ChooserScreen(refreshKey: Int) {
        val settings = remember { Settings(this) }
        val library = remember { MediaLibrary(this) }
        val art = remember { ArtCollection.load(this) }
        val heroArt = remember { art.randomOrNull() }
        val nowPlaying by music.state.collectAsState()

        var program by remember { mutableStateOf(settings.program) }
        var focused by remember { mutableStateOf(settings.program) }
        var slideSeconds by remember { mutableIntStateOf(settings.slideSeconds) }
        var showCaptions by remember { mutableStateOf(settings.showCaptions) }
        var showClock by remember { mutableStateOf(settings.showClock) }
        var customModes by remember { mutableStateOf(settings.customModes) }
        var musicSource by remember { mutableStateOf(settings.musicSource) }
        var jazzLevel by remember { mutableIntStateOf(settings.jazzLevel) }
        var editingMix by remember { mutableStateOf(false) }
        val hasJamendo = remember(refreshKey) { !settings.jamendoClientId.isNullOrBlank() }

        val media = remember(refreshKey) { library.items() }
        val photoCount = media.count { !it.isVideo }
        val videoCount = media.count { it.isVideo }
        val latestPhoto = media.filter { !it.isVideo }.maxByOrNull { it.createdAt }?.let { library.file(it) }
        val screensaverActive = remember(refreshKey) { isActiveScreensaver() }
        val hour = remember(refreshKey) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
        val hasAmbient = remember(refreshKey) { settings.hasAmbientKeys }
        var smartTubeFocused by remember { mutableStateOf(false) }
        var update by remember { mutableStateOf<AvailableUpdate?>(null) }
        var updating by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(refreshKey) { update = Updater.check(this@MainActivity) }

        fun summary(p: Program) = when (p) {
            Program.ART -> "${art.size} kunstwerken uit ${art.map { it.museum }.distinct().size} musea, elk met een korte Nederlandse toelichting."
            Program.AERIALS -> "De luchtopnames van de Apple TV: steden, kusten en bergen van bovenaf."
            Program.PHOTOS -> if (media.isEmpty()) "Nog leeg. Kies OK om foto's en video's uit Google Foto's toe te voegen."
            else "$photoCount foto's en $videoCount video's uit je eigen Google Foto's."
            Program.AMBIENT -> if (hasAmbient) "Haardvuur, regen, zee, sterren en meer, in hoge resolutie. In het voorbeeld stem je clips weg met ▼."
            else "Nog niet gekoppeld: kies OK en vul je gratis Pexels- of Pixabay-sleutel in via je telefoon."
            Program.CUSTOM, Program.SPOTIFY -> MixPlan.plan(p, customModes, media.isNotEmpty(), hasAmbient).summary
        }

        fun choose(p: Program) {
            if (p == Program.CUSTOM) {
                editingMix = true
                return
            }
            if (p == Program.PHOTOS && media.isEmpty()) {
                startActivity(Intent(this, PhotosActivity::class.java))
                return
            }
            if (p == Program.SPOTIFY && !music.hasAccess) {
                // Zonder muziektoegang ziet de app niet wat Spotify speelt.
                if (!SystemSettings.openMusicAccess(this)) startActivity(Intent(this, SelfSetupActivity::class.java))
                return
            }
            if (p == Program.AMBIENT && !hasAmbient) {
                startActivity(Intent(this, KeysActivity::class.java))
                return
            }
            program = p
            settings.program = p
            PreviewActivity.start(this, p)
        }

        val firstFocus = remember { FocusRequester() }

        if (editingMix) {
            MixEditor(
                initial = customModes,
                hasPhotos = media.isNotEmpty(),
                hasAmbient = hasAmbient,
                onClose = { editingMix = false },
                onSave = { modes, preview ->
                    customModes = modes
                    settings.customModes = modes
                    program = Program.CUSTOM
                    settings.program = Program.CUSTOM
                    editingMix = false
                    if (preview) PreviewActivity.start(this, Program.CUSTOM)
                },
            )
            return
        }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }

        Box(Modifier.fillMaxSize().background(Bis.Emaille)) {
            // Achtergrond die meekleurt met de kaart waarop je staat.
            Crossfade(targetState = focused, label = "achtergrond") { p ->
                Box(Modifier.fillMaxSize()) {
                    when (p) {
                        Program.ART, Program.CUSTOM -> heroArt?.let {
                            AsyncImage(
                                model = it.fallbackUrl ?: it.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(6.dp).alpha(0.55f),
                            )
                        }
                        Program.PHOTOS -> latestPhoto?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(6.dp).alpha(0.5f),
                            )
                        }
                        Program.AERIALS -> Canvas(Modifier.fillMaxSize().alpha(0.6f)) { drawLandscape(this, hour) }
                        Program.AMBIENT -> Canvas(Modifier.fillMaxSize().alpha(0.6f)) { drawFire(this) }
                        Program.SPOTIFY -> Canvas(Modifier.fillMaxSize().alpha(0.6f)) { drawRecord(this) }
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Bis.Emaille, 0.55f to Bis.Emaille.copy(alpha = 0.85f), 1f to Bis.Emaille.copy(alpha = 0.35f)),
                ),
            )

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Bis.Boter, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text("Bis Screensavert", style = Bis.heading(18.sp))
                    Spacer(Modifier.weight(1f))
                    update?.let { u ->
                        FocusPill(
                            text = updating ?: "Nieuwe versie ${u.versionName}: bijwerken",
                            accent = Bis.Boter,
                            onClick = {
                                if (!SystemSettings.canInstallUpdates(this@MainActivity)) {
                                    // Eerst toestemming; daarna nog een keer op de knop drukken.
                                    SystemSettings.openInstallPermission(this@MainActivity)
                                } else if (updating == null) {
                                    updating = "Downloaden…"
                                    scope.launch {
                                        updating = try {
                                            Updater.install(this@MainActivity, u)
                                            "Bevestig op het scherm"
                                        } catch (e: Exception) {
                                            null.also {
                                                Toast.makeText(this@MainActivity, "Downloaden lukte niet. Probeer het later opnieuw.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    when (screensaverActive) {
                        true -> Text("✓  Ingesteld als screensaver", style = Bis.body(12.sp, FontWeight.Medium, Bis.RoomDim))
                        false -> FocusPill(
                            "Nog geen screensaver: instellen",
                            accent = Bis.Boter,
                            onClick = { startActivity(Intent(this@MainActivity, SelfSetupActivity::class.java)) },
                        )
                        null -> FocusPill(
                            "Screensaver instellen",
                            onClick = { startActivity(Intent(this@MainActivity, SelfSetupActivity::class.java)) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Wat wil je zien?", style = Bis.heading(28.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (smartTubeFocused) "Opent je afspeellijst in SmartTube. Met Terug kom je hier weer uit." else summary(focused),
                    style = Bis.body(13.sp, color = Bis.Room.copy(alpha = 0.85f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(820.dp),
                )

                Spacer(Modifier.height(4.dp))
                // Rij 1: wat je los kunt kiezen. Rij 2: combinaties en andere apps.
                val rows = listOf(
                    listOf(Program.ART, Program.AERIALS, Program.AMBIENT, Program.PHOTOS),
                    listOf(Program.CUSTOM, Program.SPOTIFY),
                )
                rows.forEachIndexed { index, row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        row.forEach { p ->
                            ProgramCard(
                                title = p.title,
                                active = p == program,
                                status = cardStatus(p, art.size, photoCount, videoCount, customModes, hasAmbient, nowPlaying, music.hasAccess),
                                modifier = if (p == program) Modifier.focusRequester(firstFocus) else Modifier,
                                onFocus = {
                                    focused = p
                                    smartTubeFocused = false
                                },
                                onClick = { choose(p) },
                            ) {
                                when (p) {
                                    Program.ART -> AsyncImage(
                                        model = heroArt?.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Program.AERIALS -> Canvas(Modifier.fillMaxSize()) { drawLandscape(this, hour) }
                                    Program.PHOTOS -> if (latestPhoto != null) {
                                        AsyncImage(
                                            model = latestPhoto,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize().background(Bis.Emaille3), contentAlignment = Alignment.Center) {
                                            Text("+", style = Bis.heading(30.sp, Bis.Boter))
                                        }
                                    }
                                    Program.AMBIENT -> Canvas(Modifier.fillMaxSize()) { drawFire(this) }
                                    Program.CUSTOM -> Canvas(Modifier.fillMaxSize()) { drawMixStripes(this, customModes) }
                                    Program.SPOTIFY -> Canvas(Modifier.fillMaxSize()) { drawRecord(this) }
                                }
                            }
                        }
                        if (index == rows.lastIndex) {
                            ProgramCard(
                                title = "SmartTube",
                                active = false,
                                status = "Je YouTube-afspeellijst",
                                modifier = Modifier,
                                onFocus = { smartTubeFocused = true },
                                onClick = { openSmartTube(settings.smartTubePlaylist) },
                            ) {
                                Canvas(Modifier.fillMaxSize()) { drawPlaylist(this) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("MUZIEK", style = Bis.eyebrow(Bis.RoomDim))
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    items(MusicSource.entries) { source ->
                        val missingKey = source == MusicSource.JAZZ && !hasJamendo
                        val chosen = source == musicSource
                        FocusPill(
                            text = when {
                                missingKey -> "Jazz: nog geen sleutel"
                                chosen -> "✓  ${source.label}"
                                else -> source.label
                            },
                            accent = if (missingKey) Bis.Boter else if (chosen) Bis.Room else null,
                            onClick = {
                                if (missingKey) {
                                    startActivity(Intent(this@MainActivity, KeysActivity::class.java))
                                } else {
                                    musicSource = source
                                    settings.musicSource = source
                                }
                            },
                        )
                    }
                    if (musicSource == MusicSource.JAZZ && hasJamendo) {
                        item {
                            FocusPill("Volume: ${levelLabel(jazzLevel)}", onClick = {
                                jazzLevel = jazzLevel % 3 + 1
                                settings.jazzLevel = jazzLevel
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("INSTELLINGEN", style = Bis.eyebrow(Bis.RoomDim))
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    item {
                        FocusPill("Tijd per beeld: ${secondsLabel(slideSeconds)}", onClick = {
                            val options = Settings.SLIDE_SECONDS_OPTIONS
                            slideSeconds = options[(options.indexOf(slideSeconds) + 1) % options.size]
                            settings.slideSeconds = slideSeconds
                        })
                    }
                    item {
                        FocusPill("Uitleg bij kunst: ${if (showCaptions) "aan" else "uit"}", onClick = {
                            showCaptions = !showCaptions
                            settings.showCaptions = showCaptions
                        })
                    }
                    item {
                        FocusPill("Klok: ${if (showClock) "aan" else "uit"}", onClick = {
                            showClock = !showClock
                            settings.showClock = showClock
                        })
                    }
                    item {
                        FocusPill("Sfeerthema's", onClick = {
                            startActivity(Intent(this@MainActivity, AmbientActivity::class.java))
                        })
                    }
                    item {
                        FocusPill("Foto's beheren", onClick = {
                            startActivity(Intent(this@MainActivity, PhotosActivity::class.java))
                        })
                    }
                    item {
                        FocusPill("Sleutels invullen", onClick = {
                            startActivity(Intent(this@MainActivity, KeysActivity::class.java))
                        })
                    }
                }
            }
        }
    }

    /** Eigen mix samenstellen: vink aan wat je wilt zien. Minstens één onderdeel blijft aan. */
    @Composable
    private fun MixEditor(
        initial: Set<Mode>,
        hasPhotos: Boolean,
        hasAmbient: Boolean,
        onClose: () -> Unit,
        onSave: (Set<Mode>, Boolean) -> Unit,
    ) {
        var modes by remember { mutableStateOf(initial.ifEmpty { setOf(Mode.ART) }) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Bis.Emaille).padding(horizontal = 48.dp, vertical = 36.dp)) {
            Column(Modifier.align(Alignment.CenterStart).width(720.dp)) {
                Text("EIGEN MIX", style = Bis.eyebrow())
                Spacer(Modifier.height(8.dp))
                Text("Wat wil je zien?", style = Bis.heading(36.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vink aan wat er in je mix komt. De screensaver wisselt ze af.",
                    style = Bis.body(15.sp, color = Bis.Room.copy(alpha = 0.85f)),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Mode.entries.forEachIndexed { i, mode ->
                        val on = mode in modes
                        FocusPill(
                            text = (if (on) "✓  " else "○  ") + mode.label,
                            accent = if (on) Bis.Room else null,
                            modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
                            onClick = {
                                val next = if (on) modes - mode else modes + mode
                                if (next.isNotEmpty()) modes = next
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val notes = listOfNotNull(
                    "Je hebt nog geen eigen foto's of video's; die komen erbij zodra je ze toevoegt.".takeIf { Mode.PHOTOS in modes && !hasPhotos },
                    "Sfeer werkt pas als je een Pixabay- of Pexels-sleutel hebt ingevuld.".takeIf { Mode.AMBIENT in modes && !hasAmbient },
                )
                notes.forEach { Text(it, style = Bis.body(13.sp, color = Bis.Boter)) }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusPill("Opslaan en bekijken", accent = Bis.Boter, onClick = { onSave(modes, true) })
                    FocusPill("Opslaan", onClick = { onSave(modes, false) })
                    FocusPill("Annuleren", onClick = onClose)
                }
            }
        }
    }

    /**
     * Opent de afspeellijst in SmartTube. Nooit in een andere app: Downloader en browsers
     * openen ook YouTube-links, en daar heb je op de tv niets aan.
     */
    private fun openSmartTube(playlist: String) {
        val uri = Uri.parse("https://www.youtube.com/playlist?list=$playlist")
        val pkg = smartTubePackage(uri)
        if (pkg == null) {
            Toast.makeText(this, "SmartTube niet gevonden op deze tv.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Deze versie opent geen links; dan de app gewoon starten.
            (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))?.let {
                startActivity(it)
                Toast.makeText(this, "SmartTube opent geen afspeellijsten via een link. Zoek de lijst in de bibliotheek.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** SmartTube heeft door de jaren verschillende pakketnamen gehad; zoek op naam als de bekende niet bestaan. */
    private fun smartTubePackage(uri: Uri): String? {
        val handlers = packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, uri), 0).map { it.activityInfo.packageName }
        SMARTTUBE_PACKAGES.firstOrNull { it in handlers }?.let { return it }
        return handlers.firstOrNull { pkg ->
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("")
            label.contains("SmartTube", ignoreCase = true) || pkg.contains("smarttube", ignoreCase = true)
        }
    }

    @Composable
    private fun ProgramCard(
        title: String,
        active: Boolean,
        status: String,
        modifier: Modifier,
        onFocus: () -> Unit,
        onClick: () -> Unit,
        picture: @Composable () -> Unit,
    ) {
        FocusCard(onClick = onClick, onFocus = onFocus, modifier = modifier.width(150.dp).height(108.dp)) { focused ->
            Column {
                Box(Modifier.fillMaxWidth().height(58.dp)) {
                    picture()
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (active) Tag("ACTIEF")
                    }
                }
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        title,
                        style = Bis.body(13.sp, FontWeight.Bold, if (focused) Bis.Room else Bis.Room.copy(alpha = 0.9f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(status, style = Bis.body(10.sp, color = Bis.RoomDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    private fun cardStatus(
        p: Program,
        artCount: Int,
        photos: Int,
        videos: Int,
        custom: Set<Mode>,
        hasAmbient: Boolean,
        nowPlaying: NowPlaying?,
        hasMusicAccess: Boolean,
    ) = when (p) {
        Program.AMBIENT -> if (hasAmbient) "Haardvuur, regen, zee en meer" else "Nog niet gekoppeld"
        Program.ART -> "$artCount werken met Nederlandse uitleg"
        Program.AERIALS -> "Apple TV-luchtopnames, via internet"
        Program.PHOTOS -> if (photos + videos == 0) "Nog leeg: voeg toe" else "$photos foto's · $videos video's"
        Program.CUSTOM -> customLabel(custom).replaceFirstChar { it.uppercase() }
        Program.SPOTIFY -> when {
            !hasMusicAccess -> "Nog geen toegang: kies OK"
            nowPlaying != null -> "Nu: ${nowPlaying.title}"
            else -> "Hoes en foto's van de artiest"
        }
    }

    private fun levelLabel(level: Int) = when (level) {
        1 -> "zacht"
        3 -> "luid"
        else -> "middel"
    }

    private fun secondsLabel(seconds: Int) =
        if (seconds < 60) "$seconds s" else if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"

    private fun customLabel(modes: Set<Mode>) =
        Mode.entries.filter { it in modes }.joinToString(" + ") { shortLabel(it) }.ifEmpty { "leeg" }

    private fun shortLabel(mode: Mode) = when (mode) {
        Mode.ART -> "kunst"
        Mode.AERIALS -> "luchtopnames"
        Mode.PHOTOS -> "foto's"
        Mode.AMBIENT -> "sfeer"
    }
}

private val SMARTTUBE_PACKAGES = listOf(
    "com.teamsmart.videomanager.tv",
    "com.liskovsoft.smarttubetv.beta",
    "com.liskovsoft.smarttubetv",
    "org.smartteam.smarttube.beta",
    "org.smartteam.smarttube",
)

// ---- Getekende plaatjes voor de kaarten ----

/** Vlammen in de haard, voor de sfeerkaart. */
private fun drawFire(scope: DrawScope) = with(scope) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0904), Color(0xFF3A1206))))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFD27A), Color(0xFFF07A2E), Color(0x007A2410)), center = Offset(size.width / 2, size.height * 0.95f), radius = size.minDimension * 0.75f),
        radius = size.minDimension * 0.75f,
        center = Offset(size.width / 2, size.height * 0.95f),
    )
    val flame = Path().apply {
        moveTo(size.width * 0.38f, size.height * 0.9f)
        cubicTo(size.width * 0.3f, size.height * 0.6f, size.width * 0.5f, size.height * 0.5f, size.width * 0.5f, size.height * 0.25f)
        cubicTo(size.width * 0.62f, size.height * 0.5f, size.width * 0.72f, size.height * 0.62f, size.width * 0.62f, size.height * 0.9f)
        close()
    }
    drawPath(flame, Color(0xFFFFE3A3).copy(alpha = 0.85f))
    drawRoundRect(Color(0xFF2A160C), topLeft = Offset(size.width * 0.2f, size.height * 0.86f), size = Size(size.width * 0.6f, size.height * 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
}

/** Een grammofoonplaat met een groen etiket, voor de Spotify-kaart. */
private fun drawRecord(scope: DrawScope) = with(scope) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0F1A17), Color(0xFF16302A))))
    val c = Offset(size.width * 0.5f, size.height * 0.55f)
    val r = size.minDimension * 0.42f
    drawCircle(Color(0xFF111111), radius = r, center = c)
    for (i in 1..3) drawCircle(Color(0xFF2A2A2A), radius = r * (0.55f + i * 0.12f), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
    drawCircle(Color(0xFF1DB954), radius = r * 0.36f, center = c)
    drawCircle(Color(0xFF111111), radius = r * 0.06f, center = c)
}

/** Een afspeellijst: regels met een afspeelknop. */
private fun drawPlaylist(scope: DrawScope) = with(scope) {
    drawRect(Bis.Emaille3)
    val h = size.height
    for (i in 0..2) {
        val y = h * (0.25f + i * 0.22f)
        drawRoundRect(Bis.Room.copy(alpha = 0.85f), topLeft = Offset(size.width * 0.12f, y), size = Size(size.width * 0.42f, h * 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.04f))
    }
    val c = Offset(size.width * 0.74f, h * 0.5f)
    drawCircle(Bis.Tomaat, radius = h * 0.22f, center = c)
    val play = Path().apply {
        moveTo(c.x - h * 0.07f, c.y - h * 0.1f)
        lineTo(c.x + h * 0.11f, c.y)
        lineTo(c.x - h * 0.07f, c.y + h * 0.1f)
        close()
    }
    drawPath(play, Color.White)
}

/** Een landschap van bovenaf gezien, voor de luchtopnames. */
private fun drawLandscape(scope: DrawScope, hour: Int) = with(scope) {
    val evening = hour !in 7..18
    drawRect(Brush.verticalGradient(if (evening) listOf(Color(0xFF1B2B4A), Color(0xFFB0675A)) else listOf(Color(0xFF8DB7D6), Color(0xFFE9E3D0))))
    drawCircle(Color.White.copy(alpha = 0.55f), size.minDimension * 0.09f, Offset(size.width * 0.25f, size.height * 0.28f))
    drawCircle(Color.White.copy(alpha = 0.55f), size.minDimension * 0.12f, Offset(size.width * 0.34f, size.height * 0.26f))
    drawHills(this, Color(0xFF4F7A5A), Color(0xFF24493D))
    // Een rivier die door het dal slingert.
    val river = Path().apply {
        moveTo(size.width * 0.42f, size.height)
        cubicTo(size.width * 0.5f, size.height * 0.85f, size.width * 0.36f, size.height * 0.78f, size.width * 0.5f, size.height * 0.7f)
        lineTo(size.width * 0.54f, size.height * 0.7f)
        cubicTo(size.width * 0.44f, size.height * 0.8f, size.width * 0.6f, size.height * 0.86f, size.width * 0.52f, size.height)
        close()
    }
    drawPath(river, Color(0xFF9CC3D5))
}

private fun drawHills(scope: DrawScope, back: Color, front: Color) = with(scope) {
    val far = Path().apply {
        moveTo(0f, size.height * 0.72f)
        cubicTo(size.width * 0.25f, size.height * 0.55f, size.width * 0.45f, size.height * 0.62f, size.width * 0.6f, size.height * 0.68f)
        cubicTo(size.width * 0.75f, size.height * 0.6f, size.width * 0.9f, size.height * 0.58f, size.width, size.height * 0.66f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(far, back)
    val near = Path().apply {
        moveTo(0f, size.height * 0.86f)
        cubicTo(size.width * 0.3f, size.height * 0.74f, size.width * 0.6f, size.height * 0.92f, size.width, size.height * 0.8f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(near, front)
}

/** Drie banen in de kleuren van de gekozen onderdelen. */
private fun drawMixStripes(scope: DrawScope, modes: Set<Mode>) = with(scope) {
    drawRect(Bis.Emaille3)
    val colors = Mode.entries.filter { it in modes }.map {
        when (it) {
            Mode.ART -> Bis.Tomaat
            Mode.AERIALS -> Bis.IJsblauw
            Mode.PHOTOS -> Bis.Boter
            Mode.AMBIENT -> Color(0xFFD9822B)
        }
    }
    val band = size.height / (colors.size + 1)
    colors.forEachIndexed { i, color ->
        val y = band * (i + 0.6f)
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.12f + i * 10f, y),
            size = Size(size.width * 0.7f, band * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(band * 0.31f),
        )
    }
}
