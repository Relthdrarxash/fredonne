package fr.fredonne.core.music

/**
 * Partition lue dans un fichier. [melody] est en sons réels, comme partout dans le moteur ;
 * [transposition] est celle que le fichier déclarait (de la hauteur écrite à la hauteur réelle),
 * utile pour retrouver l'instrument.
 */
data class ImportedScore(val melody: Melody, val title: String?, val transposition: Interval)

class MusicXmlException(message: String) : Exception(message)

/**
 * Lecture et écriture MusicXML (`score-partwise`, non compressé) pour une mélodie à une voix.
 *
 * Limites connues, assumées pour la première version :
 * - une seule partie et une seule voix : à la lecture, on garde la première partie, la première
 *   voix et la première note de chaque accord ;
 * - une seule armure et un seul chiffrage : les changements en cours de morceau sont ignorés ;
 * - les crochets de tuplets et les ligatures ne sont pas écrits (les logiciels les recalculent).
 */
object MusicXml {

    private val TYPES = listOf(
        "whole" to 1, "half" to 2, "quarter" to 4, "eighth" to 8,
        "16th" to 16, "32nd" to 32, "64th" to 64,
    )

    // ---------------------------------------------------------------- Écriture

    /**
     * @param instrument si fourni, la partie est écrite pour cet instrument : hauteurs et armure
     *   transposées, élément `transpose` pour que la lecture sonore reste juste.
     */
    fun write(
        melody: Melody,
        title: String? = null,
        instrument: Instrument? = null,
        partName: String = "Mélodie",
    ): String {
        val transposition = instrument?.transposition ?: Interval.UNISON
        val written = if (transposition == Interval.UNISON) melody else melody.transposed(-transposition)
        val divisions = divisionsFor(written)
        val out = StringBuilder()

        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" ")
        out.append("\"http://www.musicxml.org/dtds/partwise.dtd\">\n")
        out.append("<score-partwise version=\"4.0\">\n")
        if (!title.isNullOrBlank()) {
            out.append("  <work>\n    <work-title>").append(Xml.escape(title)).append("</work-title>\n  </work>\n")
        }
        out.append("  <identification>\n    <encoding>\n      <software>Fredonne</software>\n    </encoding>\n  </identification>\n")
        out.append("  <part-list>\n    <score-part id=\"P1\">\n      <part-name>")
            .append(Xml.escape(partName)).append("</part-name>\n    </score-part>\n  </part-list>\n")
        out.append("  <part id=\"P1\">\n")

        var previousTied: Note? = null
        val measures = written.measures.ifEmpty { listOf(Measure(emptyList())) }
        measures.forEachIndexed { index, measure ->
            out.append("    <measure number=\"").append(index + 1).append("\">\n")
            if (index == 0) writeAttributes(out, written, divisions, instrument, transposition)
            for (event in measure.events) {
                val stop = event is Note && previousTied != null && previousTied!!.pitch.midi == event.pitch.midi
                writeEvent(out, event, divisions, stop)
                previousTied = (event as? Note)?.takeIf { it.tiedToNext }
            }
            out.append("    </measure>\n")
        }
        out.append("  </part>\n</score-partwise>\n")
        return out.toString()
    }

    private fun writeAttributes(
        out: StringBuilder,
        melody: Melody,
        divisions: Long,
        instrument: Instrument?,
        transposition: Interval,
    ) {
        out.append("      <attributes>\n")
        out.append("        <divisions>").append(divisions).append("</divisions>\n")
        out.append("        <key>\n          <fifths>").append(melody.key.fifths).append("</fifths>\n")
        out.append("          <mode>").append(if (melody.key.mode == Mode.MINOR) "minor" else "major").append("</mode>\n        </key>\n")
        out.append("        <time>\n          <beats>").append(melody.time.beats).append("</beats>\n")
        out.append("          <beat-type>").append(melody.time.unit).append("</beat-type>\n        </time>\n")
        // Clef de fa pour les instruments graves, clef de sol sinon.
        val bass = instrument != null && instrument.writtenHigh.midi < 72
        out.append("        <clef>\n          <sign>").append(if (bass) "F" else "G").append("</sign>\n")
        out.append("          <line>").append(if (bass) 4 else 2).append("</line>\n        </clef>\n")
        if (transposition != Interval.UNISON) {
            val octaves = transposition.semitones / 12
            out.append("        <transpose>\n")
            out.append("          <diatonic>").append(transposition.steps - 7 * octaves).append("</diatonic>\n")
            out.append("          <chromatic>").append(transposition.semitones - 12 * octaves).append("</chromatic>\n")
            if (octaves != 0) out.append("          <octave-change>").append(octaves).append("</octave-change>\n")
            out.append("        </transpose>\n")
        }
        out.append("      </attributes>\n")
    }

    private fun writeEvent(out: StringBuilder, event: Event, divisions: Long, tieStop: Boolean) {
        val duration = event.length * Fraction.of(4) * Fraction.of(divisions, 1)
        out.append("      <note>\n")
        when (event) {
            is Note -> {
                out.append("        <pitch>\n          <step>").append(event.pitch.step.name).append("</step>\n")
                if (event.pitch.alter != 0) out.append("          <alter>").append(event.pitch.alter).append("</alter>\n")
                out.append("          <octave>").append(event.pitch.octave).append("</octave>\n        </pitch>\n")
            }
            is Rest -> out.append("        <rest/>\n")
        }
        out.append("        <duration>").append(duration.numerator).append("</duration>\n")
        val tieStart = event is Note && event.tiedToNext
        if (tieStop) out.append("        <tie type=\"stop\"/>\n")
        if (tieStart) out.append("        <tie type=\"start\"/>\n")
        out.append("        <voice>1</voice>\n")
        typeAndDots(event.written)?.let { (type, dots) ->
            out.append("        <type>").append(type).append("</type>\n")
            repeat(dots) { out.append("        <dot/>\n") }
        }
        event.tuplet?.let {
            out.append("        <time-modification>\n          <actual-notes>").append(it.actual).append("</actual-notes>\n")
            out.append("          <normal-notes>").append(it.normal).append("</normal-notes>\n        </time-modification>\n")
        }
        if (tieStop || tieStart) {
            out.append("        <notations>\n")
            if (tieStop) out.append("          <tied type=\"stop\"/>\n")
            if (tieStart) out.append("          <tied type=\"start\"/>\n")
            out.append("        </notations>\n")
        }
        out.append("      </note>\n")
    }

    /** Plus petit nombre de divisions de la noire qui rend toutes les durées entières. */
    private fun divisionsFor(melody: Melody): Long {
        var divisions = 1L
        for (measure in melody.measures) {
            for (event in measure.events) {
                val quarters = event.length * Fraction.of(4)
                divisions = lcm(divisions, quarters.denominator)
            }
        }
        return divisions
    }

    /** Figure et nombre de points pour une valeur écrite, ou `null` si elle ne s'écrit pas d'un seul signe. */
    internal fun typeAndDots(written: Fraction): Pair<String, Int>? {
        for ((name, denominator) in TYPES) {
            for (dots in 0..2) {
                if (valueOf(denominator, dots) == written) return name to dots
            }
        }
        return null
    }

    private fun valueOf(denominator: Int, dots: Int): Fraction =
        Fraction.of(1, denominator) * Fraction.of((1 shl (dots + 1)) - 1, 1 shl dots)

    // ----------------------------------------------------------------- Lecture

    fun read(source: String): ImportedScore {
        val root = try {
            Xml.parse(source)
        } catch (e: XmlFormatException) {
            throw MusicXmlException(e.message ?: "XML invalide")
        }
        if (root.name != "score-partwise") {
            throw MusicXmlException("Format non pris en charge : <${root.name}> (attendu : score-partwise)")
        }
        val part = root.child("part") ?: throw MusicXmlException("Aucune partie dans le fichier")
        val title = root.child("work")?.textOf("work-title") ?: root.textOf("movement-title")

        var divisions = 1
        var key: Key? = null
        var time: TimeSignature? = null
        var transposition = Interval.UNISON
        var voice: String? = null
        val measures = ArrayList<Measure>()

        for (measure in part.children("measure")) {
            val events = ArrayList<Event>()
            for (child in measure.children) {
                when (child.name) {
                    "attributes" -> {
                        child.intOf("divisions")?.takeIf { it > 0 }?.let { divisions = it }
                        if (key == null) key = child.child("key")?.let(::readKey)
                        if (time == null) time = child.child("time")?.let(::readTime)
                        child.child("transpose")?.let { transposition = readTranspose(it) }
                    }
                    "note" -> {
                        if (child.child("grace") != null || child.child("chord") != null) continue
                        val noteVoice = child.textOf("voice") ?: "1"
                        if (voice == null) voice = noteVoice
                        if (noteVoice != voice) continue
                        events += readNote(child, divisions)
                    }
                }
            }
            measures += Measure(events)
        }

        val written = Melody(
            key = key ?: Key(Step.C, 0, Mode.MAJOR),
            time = time ?: TimeSignature(4, 4),
            measures = measures,
        )
        val sounding = if (transposition == Interval.UNISON) written else written.transposed(transposition)
        return ImportedScore(sounding, title, transposition)
    }

    private fun readKey(element: XmlElement): Key? {
        val fifths = element.intOf("fifths") ?: return null
        val mode = if (element.textOf("mode") == "minor") Mode.MINOR else Mode.MAJOR
        return Key.fromFifths(fifths, mode)
    }

    private fun readTime(element: XmlElement): TimeSignature? {
        // « 3+2 » pour une mesure à cinq temps groupés : on additionne.
        val beats = element.textOf("beats")?.split('+')?.map { it.trim().toIntOrNull() ?: return null }?.sum()
        val unit = element.intOf("beat-type")
        if (beats == null || unit == null) return null
        return try {
            TimeSignature(beats, unit)
        } catch (e: IllegalArgumentException) {
            throw MusicXmlException("Chiffrage de mesure non pris en charge : $beats/$unit")
        }
    }

    private fun readTranspose(element: XmlElement): Interval {
        val octaves = element.intOf("octave-change") ?: 0
        val diatonic = element.intOf("diatonic") ?: 0
        val chromatic = element.intOf("chromatic") ?: 0
        return Interval(diatonic + 7 * octaves, chromatic + 12 * octaves)
    }

    private fun readNote(element: XmlElement, divisions: Int): Event {
        val tuplet = element.child("time-modification")?.let {
            val actual = it.intOf("actual-notes")
            val normal = it.intOf("normal-notes")
            if (actual != null && normal != null && actual > 0 && normal > 0) Tuplet(actual, normal) else null
        }
        val written = writtenValue(element, divisions, tuplet)
        val pitch = element.child("pitch") ?: return Rest(written, tuplet)

        val step = pitch.textOf("step")?.let { name -> Step.entries.firstOrNull { it.name == name } }
            ?: throw MusicXmlException("Note sans nom (élément step manquant ou inconnu)")
        val octave = pitch.intOf("octave") ?: throw MusicXmlException("Note sans octave")
        // Les quarts de ton (0.5) n'existent pas dans le modèle : on arrondit au demi-ton.
        val alter = pitch.textOf("alter")?.toDoubleOrNull()?.let { kotlin.math.round(it).toInt() } ?: 0
        val tied = element.children("tie").any { it.attributes["type"] == "start" }
        return Note(Pitch(step, octave, alter), written, tuplet, tied)
    }

    private fun writtenValue(element: XmlElement, divisions: Int, tuplet: Tuplet?): Fraction {
        val type = element.textOf("type")
        val denominator = TYPES.firstOrNull { it.first == type }?.second
        if (denominator != null) {
            val dots = element.children("dot").size.coerceAtMost(4)
            return valueOf(denominator, dots)
        }
        // Pas de figure indiquée : on repart de la durée, en retirant l'effet du tuplet.
        val duration = element.intOf("duration")?.takeIf { it > 0 }
            ?: throw MusicXmlException("Note sans durée ni figure")
        val length = Fraction.of(duration, 4 * divisions)
        return if (tuplet == null) length else length / tuplet.factor
    }

    private fun lcm(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return a / x * b
    }
}
