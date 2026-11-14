package fr.fredonne.core.music

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Modèles issus de la recherche en informatique musicale et psychoacoustique :
 * - Détection de tonalité par profils cognitifs (Krumhansl & Schmuckler, 1990)
 * - Orthographe enharmonique contextuelle (inspirée de Meredith PS13, 2006)
 * - Espacement rythmique logarithmique (Gourlay, 1987)
 * - Rugosité / dissonance sensorielle (Plomp & Levelt, 1965 ; Sethares, 1993)
 */
object MusicTheory {

    // Profils cognitifs de Krumhansl-Kessler (1982 / 1990) du degré chromatique 0 (tonique) à 11.
    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88,
    )
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17,
    )

    /**
     * Estime la tonalité la plus probable d'une mélodie selon l'algorithme de Krumhansl-Schmuckler,
     * en pondérant chaque classe de hauteur par sa durée cumulée.
     */
    fun detectKey(melody: Melody): Key {
        val histogram = DoubleArray(12)
        melody.measures.forEach { measure ->
            measure.events.forEach { event ->
                if (event is Note) {
                    val pc = event.pitch.midi.mod(12)
                    histogram[pc] += event.length.toDouble()
                }
            }
        }
        if (histogram.all { it == 0.0 }) return melody.key

        var bestKey = melody.key
        var bestScore = Double.NEGATIVE_INFINITY

        for (fifths in -6..6) {
            for (mode in listOf(Mode.MAJOR, Mode.MINOR)) {
                val candidate = Key.fromFifths(fifths, mode)
                val tonicPc = Pitch(candidate.tonicStep, 4, candidate.tonicAlter).midi.mod(12)
                val profile = if (mode == Mode.MAJOR) MAJOR_PROFILE else MINOR_PROFILE
                val rotated = DoubleArray(12) { pc -> profile[(pc - tonicPc).mod(12)] }
                // Léger bonus de parcimonie d'armure pour départager deux corrélations quasi égales.
                val score = pearsonCorrelation(histogram, rotated) - abs(fifths) * 0.002
                if (score > bestScore) {
                    bestScore = score
                    bestKey = candidate
                }
            }
        }
        return bestKey
    }

    private fun pearsonCorrelation(x: DoubleArray, y: DoubleArray): Double {
        val meanX = x.average()
        val meanY = y.average()
        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        val den = sqrt(denX * denY)
        return if (den < 1e-9) 0.0 else num / den
    }

    /**
     * Choisit l'orthographe enharmonique la plus lisible pour un numéro [midi] dans la tonalité [key],
     * en tenant compte du mouvement mélodique local ([prevMidi], [nextMidi]) à la manière de PS13 (Meredith, 2006).
     */
    fun spellPitch(midi: Int, key: Key, prevMidi: Int? = null, nextMidi: Int? = null): Pitch {
        val practicalKey = practicalEnharmonicKey(key)
        val scalePitches = diatonicScale(practicalKey)
        val pc = midi.mod(12)

        // 1. Si la classe de hauteur appartient à la gamme diatonique de la tonalité, on garde son degré exact.
        val diatonicMatch = scalePitches.firstOrNull { (it.step.semitone + it.alter).mod(12) == pc }
        if (diatonicMatch != null) {
            return pitchAtMidiWithStep(midi, diatonicMatch.step, diatonicMatch.alter)
        }

        // 2. En mineur, le 7e degré haussé (sensible) est privilégié.
        if (practicalKey.mode == Mode.MINOR) {
            val leadingStep = Step.entries[(practicalKey.tonicStep.ordinal - 1).mod(7)]
            val tonicPc = (practicalKey.tonicStep.semitone + practicalKey.tonicAlter).mod(12)
            val leadingPc = (tonicPc - 1).mod(12)
            if (pc == leadingPc) {
                val alter = normalizedAlter(leadingPc - leadingStep.semitone)
                if (alter in -2..2) return pitchAtMidiWithStep(midi, leadingStep, alter)
            }
        }

        // 3. Note chromatique : orientation selon la résolution mélodique locale (demi-ton ascendant -> dièse, descendant -> bémol),
        // ou à défaut selon le signe de l'armure.
        val preferSharp = when {
            nextMidi != null && nextMidi - midi == 1 -> true
            nextMidi != null && nextMidi - midi == -1 -> false
            prevMidi != null && midi - prevMidi == 1 -> true
            prevMidi != null && midi - prevMidi == -1 -> false
            else -> practicalKey.fifths >= 0
        }

        return if (preferSharp) {
            spellSharp(midi)
        } else {
            spellFlat(midi)
        }
    }

    /**
     * Ramène une tonalité théorique (ex. Sol♯ majeur à +8 dièses) vers son équivalent enharmonique
     * pratique (La♭ majeur à -4 bémols, dans [-6..6] altérations).
     */
    fun practicalEnharmonicKey(key: Key): Key {
        val f = key.fifths
        if (f in -6..6) return key
        val wrapped = ((f + 6).mod(12)) - 6
        return Key.fromFifths(wrapped, key.mode)
    }

    private fun diatonicScale(key: Key): List<Pitch> {
        val majorIntervals = listOf(
            Interval.UNISON,
            Interval(1, 2),
            Interval(2, 4),
            Interval(3, 5),
            Interval(4, 7),
            Interval(5, 9),
            Interval(6, 11),
        )
        val minorIntervals = listOf(
            Interval.UNISON,
            Interval(1, 2),
            Interval(2, 3),
            Interval(3, 5),
            Interval(4, 7),
            Interval(5, 8),
            Interval(6, 10),
        )
        val tonic = Pitch(key.tonicStep, 4, key.tonicAlter)
        val intervals = if (key.mode == Mode.MAJOR) majorIntervals else minorIntervals
        return intervals.map { tonic.transpose(it) }
    }

    private fun normalizedAlter(rawDiff: Int): Int {
        val mod = (rawDiff + 6).mod(12) - 6
        return mod
    }

    private fun pitchAtMidiWithStep(midi: Int, step: Step, alter: Int): Pitch {
        val baseMidi = midi - alter - step.semitone
        val octave = baseMidi.floorDiv(12) - 1
        return Pitch(step, octave, alter)
    }

    private fun spellSharp(midi: Int): Pitch {
        val pc = midi.mod(12)
        val octave = midi.floorDiv(12) - 1
        return when (pc) {
            0 -> Pitch(Step.C, octave, 0)
            1 -> Pitch(Step.C, octave, 1)
            2 -> Pitch(Step.D, octave, 0)
            3 -> Pitch(Step.D, octave, 1)
            4 -> Pitch(Step.E, octave, 0)
            5 -> Pitch(Step.F, octave, 0)
            6 -> Pitch(Step.F, octave, 1)
            7 -> Pitch(Step.G, octave, 0)
            8 -> Pitch(Step.G, octave, 1)
            9 -> Pitch(Step.A, octave, 0)
            10 -> Pitch(Step.A, octave, 1)
            else -> Pitch(Step.B, octave, 0)
        }
    }

    private fun spellFlat(midi: Int): Pitch {
        val pc = midi.mod(12)
        val octave = midi.floorDiv(12) - 1
        return when (pc) {
            0 -> Pitch(Step.C, octave, 0)
            1 -> Pitch(Step.D, octave, -1)
            2 -> Pitch(Step.D, octave, 0)
            3 -> Pitch(Step.E, octave, -1)
            4 -> Pitch(Step.E, octave, 0)
            5 -> Pitch(Step.F, octave, 0)
            6 -> Pitch(Step.G, octave, -1)
            7 -> Pitch(Step.G, octave, 0)
            8 -> Pitch(Step.A, octave, -1)
            9 -> Pitch(Step.A, octave, 0)
            10 -> Pitch(Step.B, octave, -1)
            else -> Pitch(Step.B, octave, 0)
        }
    }

    /**
     * Coefficient d'espacement horizontal non linéaire selon l'algorithme de Gourlay (1987) :
     * une ronde ne prend pas 16 fois la largeur d'une double-croche, mais suit une progression logarithmique.
     * Renvoie un multiplicateur centré sur `1.0` pour une noire (`1/4`).
     */
    fun gourlaySpacingFactor(duration: Fraction): Float {
        val quarterRatio = (duration.toDouble() / 0.25).coerceIn(0.125, 8.0)
        val log2 = ln(quarterRatio) / ln(2.0)
        return (1.0 + 0.26 * log2).coerceIn(0.62, 1.85).toFloat()
    }

    /**
     * Calcule la rugosité / dissonance sensorielle entre deux notes MIDI selon le modèle de
     * Plomp & Levelt (1965) et la paramétrisation spectrale de Sethares (1993) sur 6 partiels.
     * Renvoie une valeur positive (0.0 = unisson parfait, > 0.35 = forte rugosité de bande critique).
     */
    fun sensoryDissonance(midiA: Int, midiB: Int): Double {
        val fA = 440.0 * 2.0.pow((midiA - 69) / 12.0)
        val fB = 440.0 * 2.0.pow((midiB - 69) / 12.0)
        val amps = doubleArrayOf(1.0, 0.45, 0.25, 0.14, 0.08, 0.04)
        var roughness = 0.0

        for (i in amps.indices) {
            val f1 = fA * (i + 1)
            val a1 = amps[i]
            for (j in amps.indices) {
                val f2 = fB * (j + 1)
                val a2 = amps[j]
                val fMin = minOf(f1, f2)
                val df = abs(f1 - f2)
                val s = 0.24 / (0.0207 * fMin + 18.96)
                val pairRoughness = a1 * a2 * (exp(-3.51 * s * df) - exp(-5.75 * s * df))
                if (pairRoughness > 0.0) roughness += pairRoughness
            }
        }
        return roughness
    }
}

/**
 * Ré-orthographie une mélodie si sa tonalité dépasse 6 altérations à la clef ou si certaines notes
 * comportent des altérations difficiles à lire à vue (doubles dièses/bémols, Mi♯, Si♯, Do♭, Fa♭).
 */
fun Melody.respellPractical(): Melody {
    val targetKey = MusicTheory.practicalEnharmonicKey(key)
    val needsKeyRespell = targetKey != key
    val allNotes = measures.flatMap { m -> m.events.mapIndexed { idx, e -> e } }
    if (!needsKeyRespell && allNotes.none { it is Note && !it.pitch.isEasySightReading }) {
        return this
    }

    // Collecte les hauteurs MIDI avec leurs voisines pour le contexte PS13.
    val flatEvents = measures.flatMap { it.events }
    val noteIndices = flatEvents.mapIndexedNotNull { idx, e -> if (e is Note) idx else null }
    val respelledByFlatIdx = HashMap<Int, Pitch>()

    for (k in noteIndices.indices) {
        val flatIdx = noteIndices[k]
        val note = flatEvents[flatIdx] as Note
        val prevMidi = if (k > 0) (flatEvents[noteIndices[k - 1]] as Note).pitch.midi else null
        val nextMidi = if (k + 1 < noteIndices.size) (flatEvents[noteIndices[k + 1]] as Note).pitch.midi else null
        respelledByFlatIdx[flatIdx] = if (needsKeyRespell || !note.pitch.isEasySightReading) {
            MusicTheory.spellPitch(note.pitch.midi, targetKey, prevMidi, nextMidi)
        } else {
            note.pitch
        }
    }

    var cursor = 0
    val newMeasures = measures.map { measure ->
        Measure(
            measure.events.map { event ->
                val idx = cursor++
                if (event is Note) {
                    event.copy(pitch = respelledByFlatIdx[idx] ?: event.pitch)
                } else {
                    event
                }
            },
        )
    }
    return copy(key = targetKey, measures = newMeasures)
}

private val Pitch.isEasySightReading: Boolean
    get() {
        if (alter !in -1..1) return false
        if (step == Step.E && alter == 1) return false
        if (step == Step.B && alter == 1) return false
        if (step == Step.C && alter == -1) return false
        if (step == Step.F && alter == -1) return false
        return true
    }
