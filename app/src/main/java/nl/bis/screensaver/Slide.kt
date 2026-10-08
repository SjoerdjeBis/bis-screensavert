package nl.bis.screensaver

/** Eén item in de diavoorstelling: een afbeelding met uitleg of een video. */
sealed interface Slide {
    val title: String
    val subtitle: String?
    val body: String?

    data class Image(
        val url: String,
        val fallbackUrl: String?,
        override val title: String,
        override val subtitle: String?,
        override val body: String?,
        /** Herkomst, bijvoorbeeld het museum; staat boven de titel. */
        val source: String? = null,
        /** Id om dit beeld in het voorbeeld weg te kunnen stemmen ("beeld:<bron>:<id>"). */
        val voteId: String? = null,
    ) : Slide

    data class Video(
        val url: String,
        override val title: String,
        override val subtitle: String?,
        override val body: String? = null,
        /** Voor sfeerclips: id om weg te kunnen stemmen. */
        val clipId: String? = null,
        /** Korte clips in een lus laten lopen tot deze tijd om is. */
        val playForMs: Long? = null,
    ) : Slide
}

/** Een bron van slides, zoals kunstwerken of aerials. */
interface SlideSource {
    /** Geeft de volgende slide, of gooit een exception als de bron onbereikbaar is. */
    suspend fun next(): Slide?
}
