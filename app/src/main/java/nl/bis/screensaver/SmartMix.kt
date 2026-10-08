package nl.bis.screensaver

import java.util.Calendar

/**
 * Bepaalt per ronde wat er te zien is. De slimme mix kijkt naar het tijdstip
 * en naar wat er beschikbaar is (zonder eigen foto's geen fotoblok).
 */
data class Plan(
    val rotation: List<Pair<Mode, Int>>,
    val summary: String,
    /** 's Nachts: geen tekst in beeld, alleen rustige beelden. */
    val quiet: Boolean = false,
)

object SmartMix {
    fun plan(
        program: Program,
        customModes: Set<Mode>,
        hasPhotos: Boolean,
        hasAmbient: Boolean = false,
        hour: Int = currentHour(),
    ): Plan {
        val plan = when (program) {
            Program.SMART -> smart(hour, hasPhotos, hasAmbient)
            Program.ART -> Plan(listOf(Mode.ART to 1), "Alleen kunst, met Nederlandse uitleg.")
            Program.AERIALS -> Plan(listOf(Mode.AERIALS to 1), "Alleen luchtopnames.")
            Program.PHOTOS -> Plan(listOf(Mode.PHOTOS to 1), "Alleen je eigen foto's en video's.")
            Program.AMBIENT -> Plan(listOf(Mode.AMBIENT to 1), "Sfeerbeelden: telkens een paar clips van hetzelfde thema na elkaar.")
            Program.CUSTOM -> {
                val modes = customModes.ifEmpty { setOf(Mode.ART) }
                Plan(
                    Mode.entries.filter { it in modes }.map { it to if (it == Mode.AERIALS) 1 else if (it == Mode.AMBIENT) 3 else 2 },
                    "Je eigen mix: " + Mode.entries.filter { it in modes }.joinToString(", ") { it.label.lowercase() } + ".",
                )
            }
        }
        // Zonder foto's of sfeersleutels vallen die blokken weg; blijft er niets over, dan kunst.
        val rotation = plan.rotation.filter { (mode, _) ->
            (mode != Mode.PHOTOS || hasPhotos) && (mode != Mode.AMBIENT || hasAmbient)
        }
        return plan.copy(rotation = rotation.ifEmpty { listOf(Mode.ART to 1) })
    }

    private fun smart(hour: Int, hasPhotos: Boolean, hasAmbient: Boolean): Plan = when (hour) {
        in 6..10 -> Plan(
            listOf(Mode.ART to 2, Mode.PHOTOS to 2, Mode.AERIALS to 1),
            if (hasPhotos) "Ochtend: kunst om bij wakker te worden, afgewisseld met je eigen foto's."
            else "Ochtend: kunst om bij wakker te worden, af en toe een luchtopname.",
        )
        in 11..17 -> Plan(
            listOf(Mode.ART to 3, Mode.AERIALS to 1, Mode.PHOTOS to 2),
            "Overdag: vooral kunst met uitleg, met luchtopnames" + if (hasPhotos) " en je eigen foto's." else ".",
        )
        in 18..22 -> Plan(
            listOf(Mode.AMBIENT to 3, Mode.PHOTOS to 3, Mode.AERIALS to 1, Mode.ART to 1),
            "Avond: rustiger" + listOfNotNull(
                "sfeerbeelden".takeIf { hasAmbient },
                "je eigen foto's".takeIf { hasPhotos },
                "luchtopnames",
            ).joinToString(", ", prefix = ", met ", postfix = ".").replaceLast(", ", " en "),
        )
        else -> Plan(
            listOf(Mode.AMBIENT to 3, Mode.AERIALS to 1),
            if (hasAmbient) "Nacht: rustige sfeerbeelden en luchtopnames, zonder tekst in beeld."
            else "Nacht: alleen rustige luchtopnames, zonder tekst in beeld.",
            quiet = true,
        )
    }

    private fun String.replaceLast(old: String, new: String): String {
        val i = lastIndexOf(old)
        return if (i < 0) this else substring(0, i) + new + substring(i + old.length)
    }

    private fun currentHour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
