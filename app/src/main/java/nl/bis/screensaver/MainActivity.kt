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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Calendar

/**
 * Het keuzescherm: kies wat je wilt zien. De achtergrond en de uitleg volgen de kaart
 * waar je op staat, en de slimme mix vertelt wat hij op dit moment van de dag kiest.
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
        var musicTakesOver by remember { mutableStateOf(settings.musicTakesOver) }
        var customModes by remember { mutableStateOf(settings.customModes) }

        val media = remember(refreshKey) { library.items() }
        val photoCount = media.count { !it.isVideo }
        val videoCount = media.count { it.isVideo }
        val latestPhoto = media.filter { !it.isVideo }.maxByOrNull { it.createdAt }?.let { library.file(it) }
        val screensaverActive = remember(refreshKey) { isActiveScreensaver() }
        val hour = remember(refreshKey) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
        val hasAmbient = remember(refreshKey) { settings.hasAmbientKeys }
        var smartTubeFocused by remember { mutableStateOf(false) }

        fun summary(p: Program) = when (p) {
            Program.SMART -> SmartMix.plan(p, customModes, media.isNotEmpty(), hasAmbient, hour).summary
            Program.ART -> "${art.size} kunstwerken uit ${art.map { it.museum }.distinct().size} musea, elk met een korte Nederlandse toelichting."
            Program.AERIALS -> "De luchtopnames van de Apple TV: steden, kusten en bergen van bovenaf."
            Program.PHOTOS -> if (media.isEmpty()) "Nog leeg. Kies OK om foto's en video's uit Google Foto's toe te voegen."
            else "$photoCount foto's en $videoCount video's uit je eigen Google Foto's."
            Program.AMBIENT -> if (hasAmbient) "Haardvuur, regen, zee, sterren en meer, in hoge resolutie. In het voorbeeld stem je clips weg met ▼."
            else "Nog niet gekoppeld: draai het installatiescript en vul je gratis Pexels- en Pixabay-sleutels in."
            Program.CUSTOM -> SmartMix.plan(p, customModes, media.isNotEmpty(), hasAmbient, hour).summary
        }

        fun choose(p: Program) {
            if (p == Program.PHOTOS && media.isEmpty()) {
                startActivity(Intent(this, PhotosActivity::class.java))
                return
            }
            if (p == Program.AMBIENT && !hasAmbient) {
                startActivity(Intent(this, AmbientActivity::class.java))
                return
            }
            program = p
            settings.program = p
            PreviewActivity.start(this, p)
        }

        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

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
                        Program.SMART -> Canvas(Modifier.fillMaxSize().alpha(0.6f)) { drawSky(this, hour) }
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Bis.Emaille, 0.55f to Bis.Emaille.copy(alpha = 0.85f), 1f to Bis.Emaille.copy(alpha = 0.35f)),
                ),
            )

            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Bis.Boter, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text("Bis Screensavert", style = Bis.heading(18.sp))
                    Spacer(Modifier.weight(1f))
                    when (screensaverActive) {
                        true -> Text("✓  Ingesteld als screensaver", style = Bis.body(12.sp, FontWeight.Medium, Bis.RoomDim))
                        false -> Text("Nog niet ingesteld als screensaver: draai het installatiescript", style = Bis.body(12.sp, FontWeight.Medium, Bis.Boter))
                        null -> Unit
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text("Wat wil je zien?", style = Bis.heading(40.sp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (smartTubeFocused) "Opent je afspeellijst in SmartTube. Met Terug kom je hier weer uit." else summary(focused),
                    style = Bis.body(15.sp, color = Bis.Room.copy(alpha = 0.85f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(620.dp).height(44.dp),
                )

                Spacer(Modifier.height(18.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
                ) {
                    items(Program.entries) { p ->
                        ProgramCard(
                            title = p.title,
                            active = p == program,
                            recommended = p == Program.SMART,
                            status = cardStatus(p, art.size, photoCount, videoCount, customModes, hasAmbient),
                            modifier = if (p == settings.program) Modifier.focusRequester(firstFocus) else Modifier,
                            onFocus = {
                                focused = p
                                smartTubeFocused = false
                            },
                            onClick = { choose(p) },
                        ) {
                            when (p) {
                                Program.SMART -> Canvas(Modifier.fillMaxSize()) { drawSky(this, hour) }
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
                                        Text("+", style = Bis.heading(44.sp, Bis.Boter))
                                    }
                                }
                                Program.AMBIENT -> Canvas(Modifier.fillMaxSize()) { drawFire(this) }
                                Program.CUSTOM -> Canvas(Modifier.fillMaxSize()) { drawMixStripes(this, customModes) }
                            }
                        }
                    }
                    item {
                        ProgramCard(
                            title = "Sfeerlijst",
                            active = false,
                            recommended = false,
                            status = "Je YouTube-afspeellijst, in SmartTube",
                            modifier = Modifier,
                            onFocus = { smartTubeFocused = true },
                            onClick = { openSmartTube(settings.smartTubePlaylist) },
                        ) {
                            Canvas(Modifier.fillMaxSize()) { drawPlaylist(this) }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Text("INSTELLINGEN", style = Bis.eyebrow(Bis.RoomDim))
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    item {
                        FocusPill(
                            text = musicLabel(musicTakesOver, nowPlaying, music.hasAccess),
                            accent = if (!music.hasAccess) Bis.Boter else null,
                            onClick = {
                                musicTakesOver = !musicTakesOver
                                settings.musicTakesOver = musicTakesOver
                            },
                        )
                    }
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
                        FocusPill("Eigen mix: ${customLabel(customModes)}", onClick = {
                            customModes = nextCustomMix(customModes)
                            settings.customModes = customModes
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
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "OK op een kaart start hem meteen en maakt hem je screensaver. In het voorbeeld: ▶ volgende, ▼ sfeerclip wegstemmen. Muziek bedien je met ◀ OK ▶.",
                    style = Bis.body(11.sp, color = Bis.RoomDim),
                )
            }
        }
    }

    /** Opent de afspeellijst in SmartTube; valt terug op elke app die YouTube-links opent. */
    private fun openSmartTube(playlist: String) {
        val uri = Uri.parse("https://www.youtube.com/playlist?list=$playlist")
        for (pkg in SMARTTUBE_PACKAGES) {
            if (packageManager.getLaunchIntentForPackage(pkg) == null) continue
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // Deze versie opent geen links; dan de app gewoon starten.
                packageManager.getLaunchIntentForPackage(pkg)?.let {
                    startActivity(it)
                    Toast.makeText(this, "SmartTube opent geen afspeellijsten via een link. Zoek de lijst in de bibliotheek.", Toast.LENGTH_LONG).show()
                    return
                }
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "SmartTube niet gevonden op deze tv.", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun ProgramCard(
        title: String,
        active: Boolean,
        recommended: Boolean,
        status: String,
        modifier: Modifier,
        onFocus: () -> Unit,
        onClick: () -> Unit,
        picture: @Composable () -> Unit,
    ) {
        FocusCard(onClick = onClick, onFocus = onFocus, modifier = modifier.width(160.dp).height(214.dp)) { focused ->
            Column {
                Box(Modifier.fillMaxWidth().height(118.dp)) {
                    picture()
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (active) Tag("ACTIEF")
                        if (recommended) Tag("AANBEVOLEN", color = Bis.Tomaat, textColor = Color.White)
                    }
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        title,
                        style = Bis.body(15.sp, FontWeight.Bold, if (focused) Bis.Room else Bis.Room.copy(alpha = 0.9f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(status, style = Bis.body(11.sp, color = Bis.RoomDim), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    private fun cardStatus(p: Program, artCount: Int, photos: Int, videos: Int, custom: Set<Mode>, hasAmbient: Boolean) = when (p) {
        Program.AMBIENT -> if (hasAmbient) "Haardvuur, regen, zee en meer" else "Nog niet gekoppeld"
        Program.SMART -> "Kiest zelf, passend bij het moment van de dag"
        Program.ART -> "$artCount werken met Nederlandse uitleg"
        Program.AERIALS -> "Apple TV-luchtopnames, via internet"
        Program.PHOTOS -> if (photos + videos == 0) "Nog leeg: voeg toe" else "$photos foto's · $videos video's"
        Program.CUSTOM -> customLabel(custom).replaceFirstChar { it.uppercase() }
    }

    private fun secondsLabel(seconds: Int) =
        if (seconds < 60) "$seconds s" else if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"

    private fun musicLabel(takesOver: Boolean, nowPlaying: NowPlaying?, hasAccess: Boolean) = when {
        !hasAccess -> "♪ Muziek: nog geen toegang"
        !takesOver -> "♪ Muziek: niet tonen"
        nowPlaying != null -> "♪ Nu: ${nowPlaying.title} – ${nowPlaying.artist}".take(48)
        else -> "♪ Muziek neemt het over"
    }

    private fun customLabel(modes: Set<Mode>) =
        Mode.entries.filter { it in modes }.joinToString(" + ") { shortLabel(it) }.ifEmpty { "leeg" }

    private fun shortLabel(mode: Mode) = when (mode) {
        Mode.ART -> "kunst"
        Mode.AERIALS -> "luchtopnames"
        Mode.PHOTOS -> "foto's"
        Mode.AMBIENT -> "sfeer"
    }

    /** Loopt alle combinaties van minstens twee onderdelen langs. */
    private fun nextCustomMix(current: Set<Mode>): Set<Mode> {
        val options = listOf(
            setOf(Mode.ART, Mode.AERIALS),
            setOf(Mode.ART, Mode.PHOTOS),
            setOf(Mode.AERIALS, Mode.PHOTOS),
            setOf(Mode.ART, Mode.AERIALS, Mode.PHOTOS),
            setOf(Mode.ART, Mode.AMBIENT),
            setOf(Mode.AERIALS, Mode.AMBIENT),
            setOf(Mode.AMBIENT, Mode.PHOTOS),
            setOf(Mode.ART, Mode.AERIALS, Mode.AMBIENT, Mode.PHOTOS),
        )
        return options[(options.indexOf(current) + 1) % options.size]
    }
}

private val SMARTTUBE_PACKAGES = listOf(
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

/** Lucht in de kleur van het moment: ochtendgloed, dag, avondrood of nacht. */
private fun drawSky(scope: DrawScope, hour: Int) = with(scope) {
    val (top, bottom, sun) = when (hour) {
        in 6..10 -> Triple(Color(0xFF7FA7C9), Color(0xFFF4C59A), Color(0xFFFFE3A3))
        in 11..17 -> Triple(Color(0xFF3F7CB0), Color(0xFFA9CFE6), Color(0xFFFFF4D6))
        in 18..22 -> Triple(Color(0xFF2B2A5C), Color(0xFFE2724F), Color(0xFFF0B23F))
        else -> Triple(Color(0xFF07131F), Color(0xFF1D3550), Color(0xFFE8E4D8))
    }
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val night = hour !in 6..22
    val center = Offset(size.width * 0.68f, size.height * if (night) 0.32f else 0.5f)
    drawCircle(sun.copy(alpha = 0.25f), radius = size.minDimension * 0.32f, center = center)
    drawCircle(sun, radius = size.minDimension * 0.17f, center = center)
    if (night) drawCircle(top, radius = size.minDimension * 0.15f, center = center + Offset(size.minDimension * 0.08f, -size.minDimension * 0.04f))
    drawHills(this, Bis.Emaille3, Bis.Emaille)
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
