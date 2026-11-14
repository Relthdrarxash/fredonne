package fr.fredonne.core.music

/**
 * Format compact et lisible de partage de partition hors-ligne (`FR1|...`), conçu pour tenir
 * dans un QR code vectoriel imprimé sur la partition PDF ou échangé par texte.
 *
 * Structure :
 * `FR1|<Tonic><Alter>:<M|m>:<beats>/<unit>:<bpm>:<instrumentId>|<mesure1>/<mesure2>/...`
 * Les mesures identiques à une mesure précédente sont compressées par référence `=<index>`
 * afin de réduire la densité du QR code (ex. forme A-A-B-A sur 16 mesures en ~80 octets).
 */
object ShareCodec {

    private const val PREFIX = "FR1|"

    data class SharedScore(
        val melody: Melody,
        val instrument: Instrument?,
        val tempoBpm: Int,
    )

    fun encode(
        melody: Melody,
        instrument: Instrument? = null,
        tempoBpm: Int = 96,
    ): String {
        val alterStr = when {
            melody.key.tonicAlter > 0 -> "+${melody.key.tonicAlter}"
            melody.key.tonicAlter < 0 -> "${melody.key.tonicAlter}"
            else -> "0"
        }
        val modeChar = if (melody.key.mode == Mode.MAJOR) "M" else "m"
        val instStr = instrument?.id ?: "-"
        val header = "$PREFIX${melody.key.tonicStep.name}$alterStr:$modeChar:${melody.time.beats}/${melody.time.unit}:$tempoBpm:$instStr|"

        val encodedMeasures = mutableListOf<String>()
        val rawByIndex = mutableListOf<String>()

        for (measure in melody.measures) {
            val raw = measure.events.joinToString(",") { encodeEvent(it) }
            val existingIdx = rawByIndex.indexOf(raw)
            if (existingIdx >= 0 && raw.isNotEmpty()) {
                encodedMeasures += "=$existingIdx"
            } else {
                encodedMeasures += raw
            }
            rawByIndex += raw
        }

        return header + encodedMeasures.joinToString("/")
    }

    fun decode(code: String): SharedScore {
        val trimmed = code.trim()
        require(trimmed.startsWith(PREFIX)) { "Code Fredonne invalide (préfixe FR1| attendu)" }
        val withoutPrefix = trimmed.removePrefix(PREFIX)
        val pipeIdx = withoutPrefix.indexOf('|')
        require(pipeIdx > 0) { "En-tête de code Fredonne manquant" }

        val headerParts = withoutPrefix.substring(0, pipeIdx).split(":")
        require(headerParts.size >= 5) { "En-tête de code Fredonne incomplet" }

        val tonicRaw = headerParts[0]
        val tonicStep = Step.valueOf(tonicRaw.substring(0, 1))
        val tonicAlter = tonicRaw.substring(1).toIntOrNull() ?: 0
        val mode = if (headerParts[1] == "m") Mode.MINOR else Mode.MAJOR
        val key = Key(tonicStep, tonicAlter, mode)

        val timeParts = headerParts[2].split("/")
        val beats = timeParts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: 4
        val unit = timeParts.getOrNull(1)?.toIntOrNull() ?: 4
        val time = TimeSignature(beats, if (unit in setOf(1, 2, 4, 8, 16, 32, 64)) unit else 4)

        val bpm = headerParts[3].toIntOrNull()?.coerceIn(40, 200) ?: 96
        val instId = headerParts[4]
        val instrument = if (instId == "-" || instId.isBlank()) null else Instruments.byId(instId)

        val body = withoutPrefix.substring(pipeIdx + 1)
        val measureTokens = if (body.isEmpty()) listOf("") else body.split("/")
        val resolvedMeasures = mutableListOf<Measure>()

        for (token in measureTokens) {
            if (token.startsWith("=")) {
                val refIdx = token.removePrefix("=").toIntOrNull()
                if (refIdx != null && refIdx in resolvedMeasures.indices) {
                    resolvedMeasures += resolvedMeasures[refIdx]
                } else {
                    resolvedMeasures += Measure(emptyList())
                }
            } else if (token.isBlank()) {
                resolvedMeasures += Measure(emptyList())
            } else {
                val events = token.split(",").filter { it.isNotBlank() }.map { decodeEvent(it) }
                resolvedMeasures += Measure(events)
            }
        }

        return SharedScore(
            melody = Melody(key, time, resolvedMeasures.ifEmpty { listOf(Measure(emptyList())) }),
            instrument = instrument,
            tempoBpm = bpm,
        )
    }

    private fun encodeEvent(event: Event): String {
        val durStr = encodeDuration(event.written)
        val tupStr = event.tuplet?.let { "*${it.actual}:${it.normal}" } ?: ""
        return when (event) {
            is Rest -> "R$durStr$tupStr"
            is Note -> {
                val p = event.pitch
                val acc = when {
                    p.alter == 1 -> "#"
                    p.alter == 2 -> "##"
                    p.alter == -1 -> "b"
                    p.alter == -2 -> "bb"
                    else -> ""
                }
                "${p.step.name}$acc${p.octave}$durStr$tupStr"
            }
        }
    }

    private fun decodeEvent(token: String): Event {
        val starIdx = token.indexOf('*')
        val mainPart = if (starIdx >= 0) token.substring(0, starIdx) else token
        val tuplet = if (starIdx >= 0) {
            val tupParts = token.substring(starIdx + 1).split(":")
            val act = tupParts.getOrNull(0)?.toIntOrNull() ?: 3
            val norm = tupParts.getOrNull(1)?.toIntOrNull() ?: 2
            Tuplet(act, norm)
        } else {
            null
        }

        if (mainPart.startsWith("R")) {
            val dur = decodeDuration(mainPart.removePrefix("R"))
            return Rest(dur, tuplet)
        }

        val step = Step.valueOf(mainPart.substring(0, 1))
        var pos = 1
        var alter = 0
        while (pos < mainPart.length && (mainPart[pos] == '#' || mainPart[pos] == 'b')) {
            alter += if (mainPart[pos] == '#') 1 else -1
            pos++
        }
        val octStart = pos
        if (pos < mainPart.length && mainPart[pos] == '-') pos++
        while (pos < mainPart.length && mainPart[pos].isDigit()) pos++
        val octave = mainPart.substring(octStart, pos).toIntOrNull() ?: 4
        val durStr = mainPart.substring(pos)
        val written = decodeDuration(durStr)
        return Note(Pitch(step, octave, alter), written, tuplet)
    }

    private fun encodeDuration(written: Fraction): String = when (written) {
        Durations.WHOLE -> "w"
        Durations.dotted(Durations.WHOLE) -> "w."
        Durations.HALF -> "h"
        Durations.dotted(Durations.HALF) -> "h."
        Durations.QUARTER -> "q"
        Durations.dotted(Durations.QUARTER) -> "q."
        Durations.EIGHTH -> "e"
        Durations.dotted(Durations.EIGHTH) -> "e."
        Durations.SIXTEENTH -> "s"
        Durations.dotted(Durations.SIXTEENTH) -> "s."
        Durations.THIRTY_SECOND -> "t"
        else -> "${written.numerator}_${written.denominator}"
    }

    private fun decodeDuration(code: String): Fraction = when (code) {
        "w" -> Durations.WHOLE
        "w." -> Durations.dotted(Durations.WHOLE)
        "h" -> Durations.HALF
        "h." -> Durations.dotted(Durations.HALF)
        "q" -> Durations.QUARTER
        "q." -> Durations.dotted(Durations.QUARTER)
        "e" -> Durations.EIGHTH
        "e." -> Durations.dotted(Durations.EIGHTH)
        "s" -> Durations.SIXTEENTH
        "s." -> Durations.dotted(Durations.SIXTEENTH)
        "t" -> Durations.THIRTY_SECOND
        else -> {
            val parts = code.split("_")
            val n = parts.getOrNull(0)?.toLongOrNull() ?: 1L
            val d = parts.getOrNull(1)?.toLongOrNull() ?: 4L
            Fraction.of(n, d)
        }
    }
}
