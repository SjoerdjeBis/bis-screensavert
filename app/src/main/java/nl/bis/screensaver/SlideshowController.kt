package nl.bis.screensaver

import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Draait de diavoorstelling in een view met layout [R.layout.slideshow].
 * Wordt gedeeld door de screensaver en het voorbeeld vanuit het keuzescherm.
 *
 * @param forcedProgram een programma om te tonen in plaats van het opgeslagen programma.
 */
class SlideshowController(
    private val root: View,
    private val scope: CoroutineScope,
    private val forcedProgram: Program? = null,
    /** In het voorbeeld (niet de screensaver) mag je sfeerclips wegstemmen en doorspoelen. */
    private val isPreview: Boolean = false,
) {
    private val context = root.context
    private val settings = Settings(context)
    private val library = MediaLibrary(context)
    private val music = MusicMonitor(context)
    private val mixer = JamendoPlayer(context, scope)

    private val video = root.findViewById<SurfaceView>(R.id.video)
    /** Twee fotolagen (b ligt boven a), om de ene foto in de andere over te laten gaan. */
    private class ImageLayer(val layer: View, val background: ImageView, val image: ImageView)
    private val imageLayers = listOf(
        ImageLayer(root.findViewById(R.id.image_layer), root.findViewById(R.id.image_background), root.findViewById(R.id.image)),
        ImageLayer(root.findViewById(R.id.image_layer_b), root.findViewById(R.id.image_background_b), root.findViewById(R.id.image_b)),
    )
    private var frontImage = 0

    /** Of er nu een foto in beeld is (en geen video). */
    private var imageShowing = false
    private val caption = root.findViewById<View>(R.id.caption)
    private val captionEyebrow = root.findViewById<TextView>(R.id.caption_eyebrow)
    private val captionTitle = root.findViewById<TextView>(R.id.caption_title)
    private val captionSubtitle = root.findViewById<TextView>(R.id.caption_subtitle)
    private val captionBody = root.findViewById<TextView>(R.id.caption_body)
    private val clock = root.findViewById<View>(R.id.clock)
    private val status = root.findViewById<TextView>(R.id.status)
    private val soundCredit = root.findViewById<TextView>(R.id.sound_credit)

    private val musicLayer = root.findViewById<View>(R.id.music_layer)
    private val musicBackdrops = listOf(
        root.findViewById<ImageView>(R.id.music_backdrop_a),
        root.findViewById<ImageView>(R.id.music_backdrop_b),
    )
    private val musicCover = root.findViewById<ImageView>(R.id.music_cover)
    private val musicTitle = root.findViewById<TextView>(R.id.music_title)
    private val musicArtist = root.findViewById<TextView>(R.id.music_artist)
    private val musicAlbum = root.findViewById<TextView>(R.id.music_album)
    private val musicProgress = root.findViewById<ProgressBar>(R.id.music_progress)
    private val musicHint = root.findViewById<TextView>(R.id.music_hint)

    /** Inlogsleutel voor de video die nu uit Google Drive speelt; gaat alleen naar Google mee. */
    @Volatile private var videoBearer: String? = null

    // Kleinere buffer dan standaard: genoeg voor soepel afspelen, zonder veel werkgeheugen.
    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec ->
                    val token = videoBearer
                    if (token != null && spec.uri.host == DriveClient.HOST) {
                        spec.withAdditionalHeaders(mapOf("Authorization" to "Bearer $token"))
                    } else {
                        spec
                    }
                },
            ),
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(10_000, 25_000, 1_500, 3_000)
                .build(),
        )
        .build().apply {
        volume = 0f
        setVideoSurfaceView(video)
        addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) = fitVideo(videoSize)
        })
    }

    /**
     * Video in de juiste verhouding, met zwarte randen waar nodig. Zonder dit rekt een
     * staande telefoonvideo uit tot het hele brede scherm.
     */
    private fun fitVideo(size: VideoSize) {
        if (size.width == 0 || size.height == 0) return
        root.post {
            val width = size.width * size.pixelWidthHeightRatio
            val height = size.height.toFloat()
            val scale = minOf(root.width / width, root.height / height)
            if (scale <= 0f) return@post
            video.layoutParams = FrameLayout.LayoutParams((width * scale).toInt(), (height * scale).toInt(), Gravity.CENTER)
        }
    }

    private val sources: Map<Mode, SlideSource> = mapOf(
        Mode.ART to ArtSource(context),
        Mode.AERIALS to AerialSource(context),
        Mode.PHOTOS to PhotoSource(context),
        Mode.AMBIENT to AmbientSource(context),
        Mode.NATURE to CuratedSource(context, "natuur"),
        Mode.HISTORY to CuratedSource(context, "toen", onThisDay = true),
    )
    private var currentClipId: String? = null

    /** Het kunstwerk of beeld dat nu in beeld is, om in het voorbeeld weg te kunnen stemmen. */
    private var currentImageVote: String? = null

    private val skip = Channel<Unit>(Channel.CONFLATED)

    /** De laatst getoonde beelden, nieuwste achteraan, om met ◀ naar terug te gaan. */
    private val history = ArrayDeque<Pair<Slide, Mode>>()

    /** Welk beeld uit [history] er nu (opnieuw) te zien is; null = gewoon nieuwe beelden. */
    private var cursor: Int? = null

    /** Wat er echt in beeld is; het volgende kan al aan het laden zijn. */
    private var visibleSlide: Slide? = null

    /** ◀ of ▶ ingedrukt terwijl er iets laadde: die druk niet vergeten, zoals een gewone ▶. */
    private var navPending = false
    private val musicVisible = MutableStateFlow(false)

    private var job: Job? = null
    private var musicJob: Job? = null
    private var captionJob: Job? = null
    private var hideMusicJob: Job? = null
    private var backdropJob: Job? = null
    private var progressJob: Job? = null
    private var soundJob: Job? = null
    private var upkeepJob: Job? = null
    private var currentArtist: String? = null
    private var currentCoverKey: String? = null
    private var frontBackdrop = 0

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageLayers.forEach { it.background.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP)) }
        }
        musicCover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 18f * view.resources.displayMetrics.density)
            }
        }
        musicCover.clipToOutline = true
    }

    fun start() {
        if (job != null) return
        clock.visibility = if (settings.showClock) View.VISIBLE else View.GONE
        job = scope.launch { run() }
        upkeepJob = scope.launch {
            // Tekst en klok staan anders urenlang op dezelfde plek; op een OLED-tv brandt dat in.
            launch {
                while (true) {
                    delay(SHIFT_MS)
                    shiftOverlays()
                }
            }
            // Ook als de screensaver de hele avond draait, komen nieuwe foto's uit de Drive-map erbij.
            launch {
                while (true) {
                    delay(DRIVE_SYNC_MS)
                    DriveSync.syncIfDue(context)
                }
            }
        }
        // De muziekmonitor draait altijd: ook om de Jamendo-muziek stil te zetten als Spotify speelt.
        music.start()
        // Alleen bij de kaart Spotify neemt de muziek het scherm over.
        if ((forcedProgram ?: settings.program) == Program.SPOTIFY) musicJob = scope.launch { followMusic() }
        mixer.start()
        soundJob = scope.launch {
            launch { music.state.collect { mixer.setMuted(it?.playing == true) } }
            // Bij elk nieuw nummer van Spotify (of een andere muziekapp) kort in beeld wat het is,
            // ongeacht wat er te zien is. Niet als het grote muziekscherm al openstaat.
            launch {
                music.state
                    .map { it?.takeIf { now -> now.playing }?.let { now -> now.title to now.artist } }
                    .distinctUntilChanged()
                    .collect { track ->
                        if (track != null && !musicVisible.value) showCredit("♪  ${track.first} · ${track.second}", TRACK_CREDIT_MS)
                    }
            }
            mixer.nowPlaying.collect { track -> if (track != null) showSoundCredit(track) }
        }
    }

    fun release() {
        listOf(job, musicJob, captionJob, hideMusicJob, backdropJob, progressJob, soundJob, upkeepJob).forEach { it?.cancel() }
        mixer.release()
        music.stop()
        player.release()
    }

    /**
     * Afstandsbediening. Bij muziek: OK = pauze, links/rechts = vorige/volgende.
     * Rechts springt naar het volgende beeld en links terug naar het vorige, ook in de
     * screensaver; daar maken de andere knoppen de tv wakker.
     */
    fun handleKey(event: KeyEvent, inScreensaver: Boolean): Boolean {
        val up = event.action == KeyEvent.ACTION_UP
        if (musicVisible.value) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (up) music.playPause()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (up) music.previous()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (up) music.next()
                    return true
                }
            }
            return false
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (up) forward()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (up) back()
            return true
        }
        // ▲ bij een beeld met tekst: tekst weg (om het werk goed te zien) of weer terug.
        // In het voorbeeld met muziek: ▲ ingedrukt houden stemt het nummer weg.
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && imageShowing && captionAvailable) {
            if (!inScreensaver && mixer.nowPlaying.value != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 1) {
                upHeld = true
                blockTrack()
            } else if (up) {
                if (!upHeld) toggleCaption()
                upHeld = false
            }
            return true
        }
        if (!inScreensaver && event.keyCode == KeyEvent.KEYCODE_DPAD_UP && mixer.nowPlaying.value != null) {
            if (up) blockTrack()
            return true
        }
        val clip = currentClipId
        val image = currentImageVote
        if (!inScreensaver && (clip != null || image != null) && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (up) {
                if (clip != null) settings.blockedClips = settings.blockedClips + clip
                if (image != null) settings.blockedImages = settings.blockedImages + image
                showStatus("Weggestemd: dit beeld komt niet meer terug")
                scope.launch {
                    delay(2_500)
                    hideStatus()
                }
                skip.trySend(Unit)
            }
            return true
        }
        return false
    }

    /** Of het beeld dat nu te zien is een onderschrift heeft dat je met ▲ kunt tonen en verbergen. */
    private var captionAvailable = false

    /** ▲ wordt ingedrukt gehouden (nummer wegstemmen), dus bij loslaten de tekst niet omschakelen. */
    private var upHeld = false

    private fun toggleCaption() {
        captionJob?.cancel()
        fade(caption, if (caption.alpha > 0.5f) 0f else 1f)
    }

    private fun blockTrack() {
        mixer.blockCurrentTrack()
        showStatus("Nummer weggestemd")
        scope.launch {
            delay(2_000)
            hideStatus()
        }
    }

    private fun back() {
        val here = cursor ?: history.indexOfLast { it.first === visibleSlide }.takeIf { it >= 0 } ?: history.lastIndex
        if (here <= 0) {
            showStatus("Verder terug gaat niet")
            scope.launch {
                delay(2_000)
                hideStatus()
            }
            return
        }
        cursor = here - 1
        navPending = true
        skip.trySend(Unit)
    }

    /** Bij teruggaan: een beeld vooruit, tot je weer bij de nieuwe beelden bent. */
    private fun forward() {
        cursor?.let { cursor = (it + 1).takeIf { next -> next <= history.lastIndex } }
        navPending = cursor != null
        skip.trySend(Unit)
    }

    // ---- Diavoorstelling ----

    /** De onderdelen voor de komende beelden, volgens het plan van deze ronde. */
    private val modeQueue = ArrayDeque<Mode>()

    private fun nextMode(): Mode {
        if (modeQueue.isEmpty()) {
            val plan = MixPlan.plan(
                forcedProgram ?: settings.program,
                settings.customModes,
                hasPhotos = library.items().isNotEmpty(),
                hasAmbient = settings.hasAmbientKeys,
                emptyModes = Mode.entries.filter { mode ->
                    CuratedCollection.assetFor(mode)?.let { CuratedCollection.load(context, it).isEmpty() } == true
                }.toSet(),
            )
            plan.rotation.forEach { (mode, count) -> repeat(count) { modeQueue.addLast(mode) } }
        }
        return modeQueue.removeFirst()
    }

    /** Het volgende beeld kiezen en de afbeelding alvast laden; null als de bron niets gaf. */
    private suspend fun fetchNext(): Pair<Slide, Mode>? = try {
        val mode = nextMode()
        sources.getValue(mode).next()?.also { preload(it) }?.let { it to mode }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Laadt een afbeelding in de cache, zodat hij straks direct in beeld kan. */
    private suspend fun preload(slide: Slide) {
        if (slide is Slide.Image) loadImage(slide.url) ?: slide.fallbackUrl?.let { loadImage(it) }
    }

    private suspend fun run() = coroutineScope {
        var failedInARow = 0
        // Terwijl het ene beeld te zien is, wordt het volgende al opgehaald: geen wachten tussendoor.
        var upcoming: Deferred<Pair<Slide, Mode>?> = async { fetchNext() }
        while (isActive) {
            musicVisible.first { !it }
            replayHistory()
            val item = upcoming.await()
            upcoming = async { fetchNext() }
            val shown = item?.let { (slide, mode) ->
                history.addLast(slide to mode)
                if (history.size > HISTORY_SIZE) history.removeFirst()
                runCatching { show(slide, mode) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(false)
                    .also { if (!it) history.remove(slide to mode) }
            } ?: false
            if (shown) {
                failedInARow = 0
            } else if (++failedInARow >= MAX_FAILURES) {
                showStatus(context.getString(R.string.no_connection))
                delay(RETRY_DELAY_MS)
                failedInARow = 0
            }
        }
    }

    /** Na ◀: de eerdere beelden opnieuw, en daarna vanzelf verder tot je weer bij het nieuwste bent. */
    private suspend fun replayHistory() {
        while (true) {
            navPending = false
            val index = cursor ?: return
            val (slide, mode) = history.getOrNull(index) ?: run {
                cursor = null
                return
            }
            show(slide, mode)
            if (cursor == index) cursor = (index + 1).takeIf { it <= history.lastIndex }
        }
    }

    private suspend fun show(slide: Slide, mode: Mode): Boolean {
        return showSlide(slide, mode)
    }

    private suspend fun showSlide(slide: Slide, mode: Mode): Boolean = when (slide) {
        is Slide.Image -> showImage(slide, mode)
        is Slide.Video -> playVideo(slide, mode)
    }

    private suspend fun showImage(slide: Slide.Image, mode: Mode): Boolean {
        val drawable = loadImage(slide.url) ?: slide.fallbackUrl?.let { loadImage(it) } ?: return false
        player.stop()
        captionJob?.cancel()
        hideStatus()
        fade(caption, 0f)
        val duration = settings.slideSeconds * 1000L
        // Een schilderij of historische foto hoort stil en helemaal in beeld te staan; alleen de rest zoomt langzaam in.
        crossfadeTo(drawable, duration, zoom = mode != Mode.ART && mode != Mode.HISTORY)
        visibleSlide = slide
        currentImageVote = slide.voteId
        delay(FADE_MS)
        captionAvailable = setCaption(slide, mode)
        if (captionAvailable) {
            fade(caption, 1f)
            // Na een tijdje verdwijnt de tekst, zodat je daarna het hele werk ziet.
            if (duration > IMAGE_CAPTION_MS + FADE_MS) {
                captionJob = scope.launch {
                    delay(IMAGE_CAPTION_MS)
                    fade(caption, 0f)
                }
            }
        }
        waitOrSkip(duration)
        return true
    }

    private suspend fun playVideo(slide: Slide.Video, mode: Mode): Boolean {
        videoBearer = slide.bearer
        var played = playUntilEnd(slide, mode, slide.url)
        if (!played && slide.bearer == null && slide.url.startsWith("https://")) {
            // Apple's certificaat wordt soms niet vertrouwd; probeer dan zonder https.
            played = playUntilEnd(slide, mode, "http://" + slide.url.removePrefix("https://"))
        }
        return played
    }

    private suspend fun playUntilEnd(slide: Slide.Video, mode: Mode, url: String): Boolean =
        withTimeoutOrNull(slide.playForMs ?: MAX_VIDEO_MS) {
            suspendCancellableCoroutine { continuation ->
                var skipJob: Job? = null
                lateinit var listener: Player.Listener
                fun finish(result: Boolean) {
                    skipJob?.cancel()
                    player.removeListener(listener)
                    if (continuation.isActive) continuation.resume(result)
                }
                listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        visibleSlide = slide
                        hideStatus()
                        imageShowing = false
                        imageLayers.forEach { fade(it.layer, 0f) }
                        captionJob?.cancel()
                        if (setCaption(slide, mode)) {
                            fade(caption, 1f)
                            captionJob = scope.launch {
                                delay(VIDEO_CAPTION_MS)
                                fade(caption, 0f)
                            }
                        } else {
                            fade(caption, 0f)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) finish(true)
                    }

                    override fun onPlayerError(error: PlaybackException) = finish(false)
                }
                clearSkips()
                skipJob = scope.launch {
                    skip.receive()
                    finish(true)
                }
                continuation.invokeOnCancellation {
                    scope.launch {
                        skipJob?.cancel()
                        player.removeListener(listener)
                    }
                }
                player.addListener(listener)
                // Sfeerclips duren vaak maar 10 tot 30 seconden: in een lus tot de tijd om is.
                player.repeatMode = if (slide.playForMs != null) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                currentClipId = slide.clipId
                currentImageVote = null
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        }.also { currentClipId = null } ?: true // Tijd om: gewoon door naar de volgende.

    private suspend fun waitOrSkip(ms: Long) {
        clearSkips()
        withTimeoutOrNull(ms) { skip.receive() }
    }

    /**
     * Vergeet ▶-drukken van tijdens de overgang. Anders telt een tweede druk, omdat het
     * volgende beeld nog aan het laden was, als skip voor dat volgende beeld: twee verder.
     */
    private fun clearSkips() {
        if (navPending) return
        while (skip.tryReceive().isSuccess) Unit
    }

    /**
     * Zet de nieuwe foto op de achterste laag en laat die over de huidige heen komen. Nooit
     * beide lagen tegelijk doorzichtig: anders schijnt het laatste beeld van een eerdere video
     * erdoorheen. Na een video komt de foto gewoon over het stilstaande videobeeld heen.
     */
    private fun crossfadeTo(drawable: Drawable, durationMs: Long, zoom: Boolean) {
        val current = imageLayers[frontImage]
        val nextIndex = 1 - frontImage
        val next = imageLayers[nextIndex]
        next.image.setImageDrawable(drawable)
        next.background.setImageDrawable(drawable)
        if (zoom) {
            kenBurns(next.image, durationMs + FADE_MS)
        } else {
            next.image.animate().cancel()
            next.image.scaleX = 1f
            next.image.scaleY = 1f
        }
        current.layer.animate().cancel()
        next.layer.animate().cancel()
        when {
            !imageShowing -> {
                current.layer.alpha = 0f
                next.layer.alpha = 0f
                fade(next.layer, 1f)
            }
            // De nieuwe laag ligt boven: die komt eroverheen, daarna mag de oude weg.
            nextIndex == 1 -> {
                next.layer.alpha = 0f
                next.layer.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MS).withEndAction {
                    current.layer.alpha = 0f
                }.start()
            }
            // De nieuwe laag ligt onder: die staat al klaar, de oude gaat weg.
            else -> {
                next.layer.alpha = 1f
                fade(current.layer, 0f)
            }
        }
        frontImage = nextIndex
        imageShowing = true
    }

    private suspend fun loadImage(url: String): Drawable? {
        // Nooit groter dan een 1080p-scherm: scheelt veel geheugen op de Chromecast.
        val request = ImageRequest.Builder(context).data(url).size(1920, 1080).build()
        return (context.imageLoader.execute(request) as? SuccessResult)?.drawable
    }


    /** Waar het onderschrift bij het volgende beeld komt te staan (tegen inbranden). */
    private var captionShiftX = 0f
    private var captionShiftY = 0f

    /**
     * Schuift alles wat op het beeld staat een paar pixels op; je ziet het niet, het scherm wel.
     * Het onderschrift verspringt pas bij het volgende beeld, als het onzichtbaar is; klok en
     * muziek glijden zo langzaam dat je het niet ziet bewegen.
     */
    private fun shiftOverlays() {
        val range = SHIFT_DP * root.resources.displayMetrics.density
        fun offset() = (Math.random().toFloat() * 2 - 1) * range
        captionShiftX = offset()
        captionShiftY = offset()
        listOf(soundCredit, status).forEach { view ->
            if (view.alpha == 0f || view.visibility != View.VISIBLE) {
                view.translationX = offset()
                view.translationY = offset()
            }
        }
        listOf(clock, musicCover.parent as? View).filterNotNull().forEach { view ->
            view.animate().translationX(offset()).translationY(offset()).setDuration(SHIFT_GLIDE_MS).start()
        }
    }

    /** Vult het onderschrift; geeft false als er bij dit beeld niets in beeld hoort. */
    private fun setCaption(slide: Slide, mode: Mode): Boolean {
        // Sfeerbeelden zonder tekst; alleen in het voorbeeld de knoppen om weg te stemmen.
        val bare = mode == Mode.AMBIENT
        if (bare && !isPreview) return false
        // Het onderschrift is hier nog onzichtbaar: het mag nu ongezien een paar pixels verspringen.
        caption.translationX = captionShiftX
        caption.translationY = captionShiftY
        captionEyebrow.visibility = if (bare) View.GONE else View.VISIBLE
        captionTitle.visibility = if (bare) View.GONE else View.VISIBLE
        captionEyebrow.text = when (mode) {
            Mode.ART -> "KUNST · " + ((slide as? Slide.Image)?.source ?: "MUSEUM").uppercase()
            Mode.AERIALS -> "LUCHTOPNAME"
            Mode.PHOTOS -> if (slide is Slide.Video) "MIJN VIDEO'S" else "MIJN FOTO'S"
            Mode.AMBIENT -> "SFEER"
            Mode.NATURE -> "NATUUR IN NEDERLAND"
            Mode.HISTORY -> (slide as? Slide.Image)?.source ?: "NEDERLAND VAN TOEN"
        } + if (isPreview && slide is Slide.Image && slide.voteId != null) "     ◀ ▶ bladeren · ▲ tekst · ▼ weg" else ""
        captionTitle.text = slide.title
        captionSubtitle.text = slide.subtitle.orEmpty()
        captionSubtitle.visibility = if (bare || slide.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        val body = if (isPreview && slide is Slide.Video && slide.clipId != null) {
            "◀ ▶ bladeren · ▼ weg"
        } else {
            slide.body.takeIf { settings.showCaptions }
        }
        captionBody.text = body.orEmpty()
        captionBody.visibility = if (body.isNullOrBlank()) View.GONE else View.VISIBLE
        return true
    }

    // ---- Muziek ----

    private suspend fun followMusic() {
        music.state.collect { nowPlaying ->
            when {
                nowPlaying == null -> hideMusic()
                nowPlaying.playing -> {
                    hideMusicJob?.cancel()
                    hideMusicJob = null
                    showMusic(nowPlaying)
                }
                musicVisible.value -> {
                    // Gepauzeerd: nog even laten staan, dan terug naar de diavoorstelling.
                    showMusic(nowPlaying)
                    if (hideMusicJob == null) {
                        hideMusicJob = scope.launch {
                            delay(PAUSED_MUSIC_MS)
                            hideMusic()
                        }
                    }
                }
            }
        }
    }

    private fun showMusic(nowPlaying: NowPlaying) {
        if (!musicVisible.value) {
            musicVisible.value = true
            player.pause()
            fade(caption, 0f)
            musicLayer.visibility = View.VISIBLE
            fade(musicLayer, 1f)
            progressJob = scope.launch {
                while (isActive) {
                    val state = music.state.value
                    if (state != null && state.durationMs > 0) {
                        musicProgress.progress = (state.positionNow() * 1000 / state.durationMs).toInt()
                    }
                    delay(500)
                }
            }
        }
        musicTitle.text = nowPlaying.title
        musicArtist.text = nowPlaying.artist
        musicAlbum.text = nowPlaying.album.orEmpty()
        musicAlbum.visibility = if (nowPlaying.album.isNullOrBlank()) View.GONE else View.VISIBLE
        musicProgress.visibility = if (nowPlaying.durationMs > 0) View.VISIBLE else View.GONE
        musicHint.text = if (nowPlaying.playing) "◀  vorige      OK  pauze      volgende  ▶"
        else "◀  vorige      OK  verder spelen      volgende  ▶"

        val coverKey = nowPlaying.artUri ?: "${nowPlaying.title}|${nowPlaying.album}"
        if (coverKey != currentCoverKey) {
            currentCoverKey = coverKey
            when {
                nowPlaying.art != null -> musicCover.setImageBitmap(nowPlaying.art)
                nowPlaying.artUri != null -> scope.launch { loadImage(nowPlaying.artUri)?.let { musicCover.setImageDrawable(it) } }
                else -> musicCover.setImageDrawable(null)
            }
        }
        if (nowPlaying.artist != currentArtist) {
            currentArtist = nowPlaying.artist
            startBackdrops(nowPlaying)
        }
    }

    private fun startBackdrops(nowPlaying: NowPlaying) {
        backdropJob?.cancel()
        backdropJob = scope.launch {
            val urls = ArtistImages.find(nowPlaying.artist)
            if (urls.isEmpty()) {
                // Geen artiestfoto's: dan de hoes, groot en wazig.
                val cover = nowPlaying.art?.let { BitmapDrawable(context.resources, it) }
                    ?: nowPlaying.artUri?.let { loadImage(it) }
                showBackdrop(cover, blurred = true)
                return@launch
            }
            var index = 0
            while (isActive) {
                loadImage(urls[index % urls.size])?.let { showBackdrop(it, blurred = false) }
                index++
                delay(BACKDROP_MS)
            }
        }
    }

    private fun showBackdrop(drawable: Drawable?, blurred: Boolean) {
        val next = musicBackdrops[1 - frontBackdrop]
        val current = musicBackdrops[frontBackdrop]
        next.setImageDrawable(drawable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            next.setRenderEffect(if (blurred) RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP) else null)
        }
        next.bringToFront()
        kenBurns(next, BACKDROP_MS + FADE_MS)
        next.alpha = 0f
        fade(next, 1f)
        current.animate().alpha(0f).setStartDelay(FADE_MS).setDuration(10).start()
        frontBackdrop = 1 - frontBackdrop
        // De scrim en tekst moeten boven de achtergronden blijven liggen.
        (musicLayer as? android.view.ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child !in musicBackdrops) child.bringToFront()
            }
        }
    }

    private fun hideMusic() {
        hideMusicJob?.cancel()
        hideMusicJob = null
        if (!musicVisible.value) return
        progressJob?.cancel()
        backdropJob?.cancel()
        currentArtist = null
        currentCoverKey = null
        musicLayer.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            musicLayer.visibility = View.GONE
        }.start()
        musicVisible.value = false
        if (player.mediaItemCount > 0) player.play()
    }

    // ---- Geluid ----

    /** Klein, linksboven: welk nummer er speelt en van wie (naamsvermelding voor Jamendo). */
    private fun showSoundCredit(track: JamendoTrack) {
        val hint = if (isPreview) "      ▲ nummer weg" else ""
        showCredit("♪  ${track.title} · ${track.artist} (Jamendo)$hint", SOUND_CREDIT_MS)
    }

    /** Linksboven even een regel tekst, die daarna vanzelf wegfadet. */
    private fun showCredit(text: String, visibleMs: Long) {
        soundCredit.text = text
        soundCredit.animate().cancel()
        soundCredit.alpha = 0f
        soundCredit.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MS).withEndAction {
            soundCredit.animate().alpha(0f).setStartDelay(visibleMs).setDuration(FADE_MS).start()
        }.start()
    }

    // ---- Hulpjes ----

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun fade(view: View, alpha: Float) {
        view.animate().alpha(alpha).setStartDelay(0).setDuration(FADE_MS).start()
    }

    /** Langzaam inzoomen, zodat een stilstaand beeld leeft (en het scherm niet inbrandt). */
    private fun kenBurns(view: View, durationMs: Long) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate().scaleX(1.06f).scaleY(1.06f).setStartDelay(0).setDuration(durationMs).setInterpolator(LinearInterpolator()).start()
    }

    private companion object {
        const val VIDEO_CAPTION_MS = 10_000L
        const val IMAGE_CAPTION_MS = 20_000L
        const val SOUND_CREDIT_MS = 8_000L
        const val TRACK_CREDIT_MS = 5_000L
        const val MAX_VIDEO_MS = 10 * 60_000L
        const val FADE_MS = 1_200L
        const val BACKDROP_MS = 25_000L
        const val PAUSED_MUSIC_MS = 60_000L
        const val MAX_FAILURES = 6
        const val HISTORY_SIZE = 30
        const val SHIFT_MS = 3 * 60_000L
        const val SHIFT_DP = 10f
        const val SHIFT_GLIDE_MS = 30_000L
        const val DRIVE_SYNC_MS = 30 * 60_000L
        const val RETRY_DELAY_MS = 60_000L
    }
}
