package fr.fredonne.core.music

/**
 * Lecture et écriture au format standard MusicXML 4.0 (`<score-partwise version="4.0">`,
 * Michael Good, 2001 / W3C Music Notation Community Group).
 *
 * - À l'export : si un [Instrument] transpositeur est fourni, la partition est écrite dans la
 *   tonalité de l'instrument et accompagnée de la balise `<transpose>` (`<diatonic>`, `<chromatic>`,
 *   `<octave-change>`) afin que MuseScore, Dorico, LilyPond ou Sibelius affichent la partition
 *   transposée tout en jouant les sons réels.
 * - À l'import : lit les mesures, l'armure, le chiffrage, les divisions, les notes pointées,
 *   les tuplets (`<time-modification>`) et la transposition `<transpose>`, et restitue la [Melody]
 *   en sons réels ainsi que l'[Instrument] correspondant.
 */
object MusicXmlCodec {

    private const val DEFAULT_DIVISIONS = 120 // Multiple de 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 24, 30, 40, 60

    fun export(
        melody: Melody,
        instrument: Instrument? = null,
        title: String = "Mélodie Fredonne",
    ): String {
        val writtenMelody = if (instrument != null && instrument.transposition != Interval.UNISON) {
            melody.transposed(-instrument.transposition).respellPractical()
        } else {
            melody.respellPractical()
        }

        val divisions = DEFAULT_DIVISIONS
        val partName = instrumentPartName(instrument)
        val sb = StringBuilder()

        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">""")
        sb.appendLine("""<score-partwise version="4.0">""")
        sb.appendLine("""  <work>""")
        sb.appendLine("""    <work-title>${escapeXml(title)}</work-title>""")
        sb.appendLine("""  </work>""")
        sb.appendLine("""  <identification>""")
        sb.appendLine("""    <encoding>""")
        sb.appendLine("""      <software>Fredonne (Pilmil Studio)</software>""")
        sb.appendLine("""    </encoding>""")
        sb.appendLine("""  </identification>""")
        sb.appendLine("""  <part-list>""")
        sb.appendLine("""    <score-part id="P1">""")
        sb.appendLine("""      <part-name>${escapeXml(partName)}</part-name>""")
        if (instrument != null) {
            sb.appendLine("""      <score-instrument id="P1-I1">""")
            sb.appendLine("""        <instrument-name>${escapeXml(instrument.id)}</instrument-name>""")
            sb.appendLine("""      </score-instrument>""")
        }
        sb.appendLine("""    </score-part>""")
        sb.appendLine("""  </part-list>""")
        sb.appendLine("""  <part id="P1">""")

        val measures = writtenMelody.measures.ifEmpty { listOf(Measure(emptyList())) }
        measures.forEachIndexed { index, measure ->
            sb.appendLine("""    <measure number="${index + 1}">""")
            if (index == 0) {
                sb.appendLine("""      <attributes>""")
                sb.appendLine("""        <divisions>$divisions</divisions>""")
                sb.appendLine("""        <key>""")
                sb.appendLine("""          <fifths>${writtenMelody.key.fifths}</fifths>""")
                sb.appendLine("""          <mode>${if (writtenMelody.key.mode == Mode.MAJOR) "major" else "minor"}</mode>""")
                sb.appendLine("""        </key>""")
                sb.appendLine("""        <time>""")
                sb.appendLine("""          <beats>${writtenMelody.time.beats}</beats>""")
                sb.appendLine("""          <beat-type>${writtenMelody.time.unit}</beat-type>""")
                sb.appendLine("""        </time>""")
                val clef = Clef.resolveFor(writtenMelody, instrument)
                sb.appendLine("""        <clef>""")
                sb.appendLine("""          <sign>${clef.sign}</sign>""")
                sb.appendLine("""          <line>${clef.line}</line>""")
                if (clef.octaveChange != 0) {
                    sb.appendLine("""          <clef-octave-change>${clef.octaveChange}</clef-octave-change>""")
                }
                sb.appendLine("""        </clef>""")
                if (instrument != null && instrument.transposition != Interval.UNISON) {
                    val octChange = instrument.transposition.semitones / 12
                    val remDiatonic = instrument.transposition.steps - octChange * 7
                    val remChromatic = instrument.transposition.semitones - octChange * 12
                    sb.appendLine("""        <transpose>""")
                    sb.appendLine("""          <diatonic>$remDiatonic</diatonic>""")
                    sb.appendLine("""          <chromatic>$remChromatic</chromatic>""")
                    if (octChange != 0) {
                        sb.appendLine("""          <octave-change>$octChange</octave-change>""")
                    }
                    sb.appendLine("""        </transpose>""")
                }
                sb.appendLine("""      </attributes>""")
            }

            measure.events.forEach { event ->
                // Durée en divisions de noire : 1 ronde = 4 * divisions
                val durationUnits = ((event.length.numerator * 4L * divisions) / event.length.denominator)
                    .coerceAtLeast(1L)
                val (typeStr, isDotted) = writtenTypeAndDot(event.written)

                sb.appendLine("""      <note>""")
                when (event) {
                    is Note -> {
                        sb.appendLine("""        <pitch>""")
                        sb.appendLine("""          <step>${event.pitch.step.name}</step>""")
                        if (event.pitch.alter != 0) {
                            sb.appendLine("""          <alter>${event.pitch.alter}</alter>""")
                        }
                        sb.appendLine("""          <octave>${event.pitch.octave}</octave>""")
                        sb.appendLine("""        </pitch>""")
                    }
                    is Rest -> {
                        sb.appendLine("""        <rest/>""")
                    }
                }
                sb.appendLine("""        <duration>$durationUnits</duration>""")
                sb.appendLine("""        <type>$typeStr</type>""")
                if (isDotted) {
                    sb.appendLine("""        <dot/>""")
                }
                val tuplet = event.tuplet
                if (tuplet != null) {
                    sb.appendLine("""        <time-modification>""")
                    sb.appendLine("""          <actual-notes>${tuplet.actual}</actual-notes>""")
                    sb.appendLine("""          <normal-notes>${tuplet.normal}</normal-notes>""")
                    sb.appendLine("""        </time-modification>""")
                }
                sb.appendLine("""      </note>""")
            }

            if (index == measures.lastIndex) {
                sb.appendLine("""      <barline location="right">""")
                sb.appendLine("""        <bar-style>light-heavy</bar-style>""")
                sb.appendLine("""      </barline>""")
            }
            sb.appendLine("""    </measure>""")
        }

        sb.appendLine("""  </part>""")
        sb.append("""</score-partwise>""")
        return sb.toString()
    }

    /**
     * Analyse un document MusicXML (`<score-partwise>`) et renvoie la [Melody] en sons réels
     * ainsi que l'[Instrument] détecté (si précisé par `<instrument-name>` ou `<transpose>`).
     */
    fun import(xml: String): Pair<Melody, Instrument?> {
        var divisions = 1
        var fifths = 0
        var mode = Mode.MAJOR
        var beats = 4
        var beatType = 4
        var transposeDiatonic = 0
        var transposeChromatic = 0
        var transposeOctave = 0
        var hasTranspose = false

        val instIdMatch = Regex("""<instrument-name>\s*([^<]+?)\s*</instrument-name>""").find(xml)
            ?.groupValues?.get(1)?.trim()
        val explicitInstrument = instIdMatch?.let { Instruments.byId(it) }

        val measureBlocks = Regex("""<measure\b[^>]*>([\s\S]*?)</measure>""").findAll(xml).toList()
        val parsedMeasures = mutableListOf<Measure>()

        for (mMatch in measureBlocks) {
            val mContent = mMatch.groupValues[1]

            // Attributs globaux s'ils sont présents dans cette mesure
            Regex("""<divisions>\s*(\d+)\s*</divisions>""").find(mContent)?.let {
                divisions = it.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: divisions
            }
            Regex("""<fifths>\s*(-?\d+)\s*</fifths>""").find(mContent)?.let {
                fifths = it.groupValues[1].toIntOrNull() ?: fifths
            }
            Regex("""<mode>\s*([a-zA-Z]+)\s*</mode>""").find(mContent)?.let {
                mode = if (it.groupValues[1].lowercase() == "minor") Mode.MINOR else Mode.MAJOR
            }
            Regex("""<beats>\s*(\d+)\s*</beats>""").find(mContent)?.let {
                beats = it.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: beats
            }
            Regex("""<beat-type>\s*(\d+)\s*</beat-type>""").find(mContent)?.let {
                val u = it.groupValues[1].toIntOrNull() ?: beatType
                if (u in setOf(1, 2, 4, 8, 16, 32, 64)) beatType = u
            }
            Regex("""<transpose>([\s\S]*?)</transpose>""").find(mContent)?.let { tMatch ->
                val tBlock = tMatch.groupValues[1]
                hasTranspose = true
                transposeDiatonic = Regex("""<diatonic>\s*(-?\d+)\s*</diatonic>""")
                    .find(tBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                transposeChromatic = Regex("""<chromatic>\s*(-?\d+)\s*</chromatic>""")
                    .find(tBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                transposeOctave = Regex("""<octave-change>\s*(-?\d+)\s*</octave-change>""")
                    .find(tBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            val events = mutableListOf<Event>()
            val noteBlocks = Regex("""<note\b[^>]*>([\s\S]*?)</note>""").findAll(mContent)
            for (nMatch in noteBlocks) {
                val nContent = nMatch.groupValues[1]
                // Ignore les notes secondaires d'accords (<chord/>) ou les notes de grâce (<grace/>)
                if (Regex("""<chord\s*/?>""").containsMatchIn(nContent)) continue
                if (Regex("""<grace\b""").containsMatchIn(nContent)) continue

                val actualNotes = Regex("""<actual-notes>\s*(\d+)\s*</actual-notes>""")
                    .find(nContent)?.groupValues?.get(1)?.toIntOrNull()
                val normalNotes = Regex("""<normal-notes>\s*(\d+)\s*</normal-notes>""")
                    .find(nContent)?.groupValues?.get(1)?.toIntOrNull()
                val tuplet = if (actualNotes != null && normalNotes != null && actualNotes > 0 && normalNotes > 0) {
                    Tuplet(actualNotes, normalNotes)
                } else {
                    null
                }

                val typeStr = Regex("""<type>\s*([^<]+?)\s*</type>""").find(nContent)?.groupValues?.get(1)?.trim()
                val isDotted = Regex("""<dot\s*/?>""").containsMatchIn(nContent)
                val durUnits = Regex("""<duration>\s*(\d+)\s*</duration>""")
                    .find(nContent)?.groupValues?.get(1)?.toLongOrNull() ?: divisions.toLong()

                val writtenFraction = if (typeStr != null) {
                    val base = typeToFraction(typeStr)
                    if (isDotted) Durations.dotted(base) else base
                } else {
                    val rawLength = Fraction.of(durUnits, divisions.toLong() * 4L)
                    if (tuplet != null) rawLength / tuplet.factor else rawLength
                }

                val isRest = Regex("""<rest\b""").containsMatchIn(nContent)
                if (isRest) {
                    events += Rest(writtenFraction, tuplet)
                } else {
                    val stepChar = Regex("""<step>\s*([A-G])\s*</step>""")
                        .find(nContent)?.groupValues?.get(1) ?: "C"
                    val step = Step.valueOf(stepChar)
                    val octave = Regex("""<octave>\s*(-?\d+)\s*</octave>""")
                        .find(nContent)?.groupValues?.get(1)?.toIntOrNull() ?: 4
                    val alter = Regex("""<alter>\s*(-?\d+)\s*</alter>""")
                        .find(nContent)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    events += Note(Pitch(step, octave, alter), writtenFraction, tuplet)
                }
            }
            parsedMeasures += Measure(events)
        }

        val writtenKey = Key.fromFifths(fifths.coerceIn(-7, 7), mode)
        val writtenMelody = Melody(
            key = writtenKey,
            time = TimeSignature(beats, beatType),
            measures = parsedMeasures.ifEmpty { listOf(Measure(emptyList())) },
        )

        val transpositionInterval = when {
            explicitInstrument != null -> explicitInstrument.transposition
            hasTranspose -> Interval(
                steps = transposeDiatonic + transposeOctave * 7,
                semitones = transposeChromatic + transposeOctave * 12,
            )
            else -> Interval.UNISON
        }

        val resolvedInstrument = explicitInstrument ?: if (hasTranspose && transpositionInterval != Interval.UNISON) {
            Instruments.ALL.firstOrNull { it.transposition == transpositionInterval }
        } else {
            null
        }

        val soundingMelody = if (transpositionInterval != Interval.UNISON) {
            writtenMelody.transposed(transpositionInterval).respellPractical()
        } else {
            writtenMelody.respellPractical()
        }

        return soundingMelody to resolvedInstrument
    }

    private fun writtenTypeAndDot(written: Fraction): Pair<String, Boolean> {
        val candidates = listOf(
            Durations.WHOLE to "whole",
            Durations.HALF to "half",
            Durations.QUARTER to "quarter",
            Durations.EIGHTH to "eighth",
            Durations.SIXTEENTH to "16th",
            Durations.THIRTY_SECOND to "32nd",
        )
        for ((dur, name) in candidates) {
            if (written == dur) return name to false
            if (written == Durations.dotted(dur)) return name to true
        }
        return when {
            written >= Durations.WHOLE -> "whole" to false
            written >= Durations.HALF -> "half" to false
            written >= Durations.QUARTER -> "quarter" to false
            written >= Durations.EIGHTH -> "eighth" to false
            written >= Durations.SIXTEENTH -> "16th" to false
            else -> "32nd" to false
        }
    }

    private fun typeToFraction(type: String): Fraction = when (type.lowercase()) {
        "whole" -> Durations.WHOLE
        "half" -> Durations.HALF
        "quarter" -> Durations.QUARTER
        "eighth" -> Durations.EIGHTH
        "16th" -> Durations.SIXTEENTH
        "32nd" -> Durations.THIRTY_SECOND
        else -> Durations.QUARTER
    }

    private fun instrumentPartName(instrument: Instrument?): String = when (instrument?.id) {
        null -> "Voix / Ut (Sons réels)"
        "flute" -> "Flûte"
        "oboe" -> "Hautbois"
        "clarinet_bb" -> "Clarinette en Sib"
        "sax_soprano" -> "Saxophone soprano en Sib"
        "sax_alto" -> "Saxophone alto en Mib"
        "sax_tenor" -> "Saxophone ténor en Sib"
        "sax_baritone" -> "Saxophone baryton en Mib"
        "trumpet_bb" -> "Trompette en Sib"
        "horn_f" -> "Cor en Fa"
        "trombone" -> "Trombone"
        "violin" -> "Violon"
        "harp_celtic" -> "Harpe celtique"
        "harp_pedal" -> "Harpe à pédales (Concert)"
        "guitar" -> "Guitare"
        "bass_electric" -> "Basse électrique"
        "contrabass" -> "Contrebasse"
        "voice_soprano" -> "Soprano"
        "voice_alto" -> "Alto"
        "voice_tenor" -> "Ténor"
        "voice_bass" -> "Basse"
        else -> instrument.id
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
