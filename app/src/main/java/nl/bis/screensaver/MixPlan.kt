package nl.bis.screensaver

/** Bepaalt per ronde wat er te zien is, en hoeveel beelden van elk onderdeel. */
data class Plan(
    val rotation: List<Pair<Mode, Int>>,
    val summary: String,
)

object MixPlan {
    fun plan(
        program: Program,
        customModes: Set<Mode>,
        hasPhotos: Boolean,
        hasAmbient: Boolean = false,
        emptyModes: Set<Mode> = emptySet(),
    ): Plan {
        val plan = when (program) {
            Program.ART -> Plan(listOf(Mode.ART to 1), "Alleen kunst, met Nederlandse uitleg.")
            Program.AERIALS -> Plan(listOf(Mode.AERIALS to 1), "Alleen luchtopnames.")
            Program.PHOTOS -> Plan(listOf(Mode.PHOTOS to 1), "Alleen je eigen foto's en video's.")
            Program.AMBIENT -> Plan(listOf(Mode.AMBIENT to 1), "Sfeerbeelden: telkens een paar clips van hetzelfde thema na elkaar.")
            Program.SPACE -> Plan(listOf(Mode.SPACE to 1), "Nevels, sterrenstelsels en de aarde van bovenaf, met Nederlandse uitleg (NASA).")
            Program.NATURE -> Plan(listOf(Mode.NATURE to 1), "De mooiste natuurfoto's van vogels, insecten en planten in Nederland, met de Nederlandse naam.")
            Program.HISTORY -> Plan(listOf(Mode.HISTORY to 1), "Zwart-witfoto's uit het Nationaal Archief, bij voorkeur van deze dag in een ander jaar.")
            Program.CUSTOM, Program.SPOTIFY -> {
                val modes = customModes.ifEmpty { setOf(Mode.ART) }
                val names = Mode.entries.filter { it in modes }.joinToString(", ") { it.label.lowercase() }
                Plan(
                    Mode.entries.filter { it in modes }.map { it to if (it == Mode.AERIALS) 1 else if (it == Mode.AMBIENT) 3 else 2 },
                    if (program == Program.SPOTIFY) {
                        "Speelt Spotify op de tv, dan zie je de hoes, het nummer en foto's van de artiest. Speelt er niets, dan je eigen mix ($names)."
                    } else {
                        "Je eigen mix: $names. Kies OK om aan te passen."
                    },
                )
            }
        }
        // Zonder foto's of sfeersleutels vallen die blokken weg; blijft er niets over, dan kunst.
        val rotation = plan.rotation.filter { (mode, _) ->
            (mode != Mode.PHOTOS || hasPhotos) && (mode != Mode.AMBIENT || hasAmbient) && mode !in emptyModes
        }
        return plan.copy(rotation = rotation.ifEmpty { listOf(Mode.ART to 1) })
    }
}
