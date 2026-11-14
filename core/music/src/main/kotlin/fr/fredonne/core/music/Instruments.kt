package fr.fredonne.core.music

/**
 * Clé de lecture sur la portée à 5 lignes :
 * - [TREBLE] : Clé de Sol 2e ligne (ligne du bas = Mi4).
 * - [TREBLE_8VB] : Clé de Sol octaviée à l'octave inférieure `G8vb` (ligne du bas = Mi3, pupitre Ténor).
 * - [BASS] : Clé de Fa 4e ligne (ligne du bas = Sol2, Basse, Contrebasse, Trombone, Voix Basse, Harpe main gauche).
 */
enum class Clef(
    val sign: String,
    val line: Int,
    val octaveChange: Int,
    val bottomLinePitch: Pitch,
    val displaySymbol: String,
) {
    TREBLE("G", 2, 0, Pitch(Step.E, 4), "G"),
    TREBLE_8VB("G", 2, -1, Pitch(Step.E, 3), "G8"),
    BASS("F", 4, 0, Pitch(Step.G, 2), "F:");

    companion object {
        /**
         * Détermine la clé adaptée à la mélodie écrite et à l'instrument choisi.
         * Pour la harpe celtique (C2–A6) ou en sons réels sans instrument forcé, bascule
         * automatiquement en clé de Fa lorsque le registre médian descend sous Sol3.
         */
        fun resolveFor(writtenMelody: Melody, instrument: Instrument?): Clef {
            if (instrument != null && instrument.id != "harp_celtic") {
                return instrument.clef
            }
            val notes = writtenMelody.measures.flatMap { it.events }.filterIsInstance<Note>()
            if (notes.isEmpty()) return instrument?.clef ?: TREBLE
            val sortedDiatonic = notes.map { it.pitch.diatonic }.sorted()
            val medianDiatonic = sortedDiatonic[sortedDiatonic.size / 2]
            return if (medianDiatonic < Pitch(Step.G, 3).diatonic) BASS else TREBLE
        }
    }
}

/**
 * Instrument avec sa transposition, sa tessiture courante et sa clé de lecture.
 *
 * [transposition] mène de la hauteur écrite à la hauteur réelle : une clarinette en si bémol
 * sonne une seconde majeure plus bas que ce qui est écrit ; une basse électrique ou contrebasse
 * sonne une octave plus bas que ce qui est écrit en clé de Fa.
 * [diatonicOnly] signale un instrument diatonique à leviers (ex. harpe celtique) où toute
 * altération hors armure nécessite un changement de levier.
 */
data class Instrument(
    val id: String,
    val transposition: Interval,
    val writtenLow: Pitch,
    val writtenHigh: Pitch,
    val clef: Clef = Clef.TREBLE,
    val diatonicOnly: Boolean = false,
) {
    val soundingLow: Pitch get() = writtenLow.transpose(transposition)
    val soundingHigh: Pitch get() = writtenHigh.transpose(transposition)

    /** Hauteur à écrire pour que l'instrumentiste produise [sounding]. */
    fun toWritten(sounding: Pitch): Pitch = sounding.transpose(-transposition)

    /** Hauteur réellement entendue quand l'instrumentiste lit [written]. */
    fun toSounding(written: Pitch): Pitch = written.transpose(transposition)
}

object Instruments {
    private val BB = Interval(-1, -2)

    val FLUTE = Instrument("flute", Interval.UNISON, Pitch(Step.C, 4), Pitch(Step.C, 7))
    val OBOE = Instrument("oboe", Interval.UNISON, Pitch(Step.B, 3, -1), Pitch(Step.A, 6))
    val CLARINET_BB = Instrument("clarinet_bb", BB, Pitch(Step.E, 3), Pitch(Step.C, 7))
    val SAX_SOPRANO = Instrument("sax_soprano", BB, Pitch(Step.B, 3, -1), Pitch(Step.F, 6))
    val SAX_ALTO = Instrument("sax_alto", Interval(-5, -9), Pitch(Step.B, 3, -1), Pitch(Step.F, 6))
    val SAX_TENOR = Instrument("sax_tenor", Interval(-8, -14), Pitch(Step.B, 3, -1), Pitch(Step.F, 6))
    val SAX_BARITONE = Instrument("sax_baritone", Interval(-12, -21), Pitch(Step.B, 3, -1), Pitch(Step.F, 6))
    val TRUMPET_BB = Instrument("trumpet_bb", BB, Pitch(Step.F, 3, 1), Pitch(Step.C, 6))
    val HORN_F = Instrument("horn_f", Interval(-4, -7), Pitch(Step.F, 2, 1), Pitch(Step.C, 6))
    val TROMBONE = Instrument("trombone", Interval.UNISON, Pitch(Step.E, 2), Pitch(Step.B, 4, -1), clef = Clef.BASS)
    val VIOLIN = Instrument("violin", Interval.UNISON, Pitch(Step.G, 3), Pitch(Step.E, 7))
    val HARP_CELTIC = Instrument("harp_celtic", Interval.UNISON, Pitch(Step.C, 2), Pitch(Step.A, 6), clef = Clef.TREBLE, diatonicOnly = true)
    val GUITAR = Instrument("guitar", -Interval.OCTAVE, Pitch(Step.E, 3), Pitch(Step.B, 6))
    val BASS_ELECTRIC = Instrument("bass_electric", -Interval.OCTAVE, Pitch(Step.E, 2), Pitch(Step.G, 4), clef = Clef.BASS)
    val CONTRABASS = Instrument("contrabass", -Interval.OCTAVE, Pitch(Step.E, 2), Pitch(Step.D, 4), clef = Clef.BASS)
    val VOICE_SOPRANO = Instrument("voice_soprano", Interval.UNISON, Pitch(Step.C, 4), Pitch(Step.C, 6))
    val VOICE_ALTO = Instrument("voice_alto", Interval.UNISON, Pitch(Step.G, 3), Pitch(Step.D, 5))
    val VOICE_TENOR = Instrument("voice_tenor", Interval.UNISON, Pitch(Step.C, 3), Pitch(Step.C, 5), clef = Clef.TREBLE_8VB)
    val VOICE_BASS = Instrument("voice_bass", Interval.UNISON, Pitch(Step.E, 2), Pitch(Step.E, 4), clef = Clef.BASS)

    val ALL: List<Instrument> = listOf(
        FLUTE, OBOE, CLARINET_BB, SAX_SOPRANO, SAX_ALTO, SAX_TENOR, SAX_BARITONE,
        TRUMPET_BB, HORN_F, TROMBONE, VIOLIN, HARP_CELTIC, GUITAR, BASS_ELECTRIC, CONTRABASS,
        VOICE_SOPRANO, VOICE_ALTO, VOICE_TENOR, VOICE_BASS,
    )

    fun byId(id: String): Instrument? = ALL.firstOrNull { it.id == id }
}
