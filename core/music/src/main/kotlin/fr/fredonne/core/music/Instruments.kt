package fr.fredonne.core.music

/**
 * Instrument avec sa transposition et sa tessiture courante.
 *
 * [transposition] mène de la hauteur écrite à la hauteur réelle : une clarinette en si bémol
 * sonne une seconde majeure plus bas que ce qui est écrit. Les tessitures sont données en
 * hauteurs écrites. Ce sont des étendues courantes, à valider avec des instrumentistes.
 */
data class Instrument(
    val id: String,
    val transposition: Interval,
    val writtenLow: Pitch,
    val writtenHigh: Pitch,
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
    val TROMBONE = Instrument("trombone", Interval.UNISON, Pitch(Step.E, 2), Pitch(Step.B, 4, -1))
    val VIOLIN = Instrument("violin", Interval.UNISON, Pitch(Step.G, 3), Pitch(Step.E, 7))
    val GUITAR = Instrument("guitar", -Interval.OCTAVE, Pitch(Step.E, 3), Pitch(Step.B, 6))
    val VOICE_SOPRANO = Instrument("voice_soprano", Interval.UNISON, Pitch(Step.C, 4), Pitch(Step.C, 6))
    val VOICE_ALTO = Instrument("voice_alto", Interval.UNISON, Pitch(Step.G, 3), Pitch(Step.D, 5))
    val VOICE_TENOR = Instrument("voice_tenor", Interval.UNISON, Pitch(Step.C, 3), Pitch(Step.C, 5))
    val VOICE_BASS = Instrument("voice_bass", Interval.UNISON, Pitch(Step.E, 2), Pitch(Step.E, 4))

    val ALL: List<Instrument> = listOf(
        FLUTE, OBOE, CLARINET_BB, SAX_SOPRANO, SAX_ALTO, SAX_TENOR, SAX_BARITONE,
        TRUMPET_BB, HORN_F, TROMBONE, VIOLIN, GUITAR,
        VOICE_SOPRANO, VOICE_ALTO, VOICE_TENOR, VOICE_BASS,
    )

    fun byId(id: String): Instrument? = ALL.firstOrNull { it.id == id }
}
