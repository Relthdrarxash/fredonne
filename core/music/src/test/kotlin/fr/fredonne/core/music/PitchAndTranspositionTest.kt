package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PitchAndTranspositionTest {

    private val c4 = Pitch(Step.C, 4)

    @Test
    fun `numéros MIDI de référence`() {
        assertEquals(60, c4.midi)
        assertEquals(69, Pitch(Step.A, 4).midi)
        assertEquals(58, Pitch(Step.B, 3, -1).midi)
        assertEquals(66, Pitch(Step.F, 4, 1).midi)
    }

    @Test
    fun `fa dièse et sol bémol ont le même MIDI mais ne sont pas égaux`() {
        val fSharp = Pitch(Step.F, 4, 1)
        val gFlat = Pitch(Step.G, 4, -1)
        assertEquals(fSharp.midi, gFlat.midi)
        assertFalse(fSharp == gFlat)
    }

    @Test
    fun `seconde majeure au-dessus de do`() {
        assertEquals(Pitch(Step.D, 4), c4.transpose(Interval.MAJOR_SECOND))
    }

    @Test
    fun `seconde majeure au-dessus de si donne do dièse`() {
        assertEquals(Pitch(Step.C, 4, 1), Pitch(Step.B, 3).transpose(Interval.MAJOR_SECOND))
    }

    @Test
    fun `seconde mineure au-dessus de fa dièse donne sol`() {
        assertEquals(Pitch(Step.G, 4), Pitch(Step.F, 4, 1).transpose(Interval.MINOR_SECOND))
    }

    @Test
    fun `transposer vers le grave`() {
        assertEquals(Pitch(Step.E, 4, -1), Pitch(Step.C, 5).transpose(-Interval.MAJOR_SIXTH))
    }

    @Test
    fun `transposer puis revenir redonne la hauteur d'origine`() {
        val pitches = listOf(
            Pitch(Step.C, 4), Pitch(Step.F, 4, 1), Pitch(Step.B, 3, -1), Pitch(Step.E, 5),
        )
        val intervals = listOf(
            Interval.MAJOR_SECOND, Interval.MINOR_THIRD, Interval.PERFECT_FIFTH,
            Interval.MAJOR_SEVENTH, Interval(-8, -14),
        )
        for (p in pitches) for (i in intervals) {
            assertEquals(p, p.transpose(i).transpose(-i))
        }
    }

    @Test
    fun `intervalle entre deux hauteurs`() {
        assertEquals(Interval.MAJOR_SECOND, Interval.between(c4, Pitch(Step.D, 4)))
        assertEquals(Interval(-5, -9), Interval.between(Pitch(Step.C, 5), Pitch(Step.E, 4, -1)))
    }

    @Test
    fun `altération au-delà du double dièse est signalée`() {
        assertTrue(Pitch(Step.C, 4, 2).isPractical)
        assertFalse(Pitch(Step.C, 4, 3).isPractical)
    }

    // Instruments transpositeurs

    @Test
    fun `clarinette en si bémol écrit do et sonne si bémol`() {
        assertEquals(Pitch(Step.B, 4, -1), Instruments.CLARINET_BB.toSounding(Pitch(Step.C, 5)))
        assertEquals(Pitch(Step.C, 5), Instruments.CLARINET_BB.toWritten(Pitch(Step.B, 4, -1)))
    }

    @Test
    fun `saxophone alto écrit do et sonne mi bémol plus bas`() {
        assertEquals(Pitch(Step.E, 4, -1), Instruments.SAX_ALTO.toSounding(Pitch(Step.C, 5)))
    }

    @Test
    fun `saxophone ténor sonne une neuvième plus bas`() {
        assertEquals(Pitch(Step.B, 3, -1), Instruments.SAX_TENOR.toSounding(Pitch(Step.C, 5)))
    }

    @Test
    fun `saxophone baryton sonne une octave et une sixte plus bas`() {
        assertEquals(Pitch(Step.E, 3, -1), Instruments.SAX_BARITONE.toSounding(Pitch(Step.C, 5)))
    }

    @Test
    fun `cor en fa sonne une quinte plus bas`() {
        assertEquals(Pitch(Step.F, 4), Instruments.HORN_F.toSounding(Pitch(Step.C, 5)))
    }

    @Test
    fun `guitare sonne une octave plus bas que ce qui est écrit`() {
        assertEquals(Pitch(Step.E, 2), Instruments.GUITAR.toSounding(Pitch(Step.E, 3)))
    }

    @Test
    fun `tessiture réelle de la clarinette en si bémol`() {
        assertEquals(Pitch(Step.D, 3), Instruments.CLARINET_BB.soundingLow)
        assertEquals(Pitch(Step.B, 6, -1), Instruments.CLARINET_BB.soundingHigh)
    }

    @Test
    fun `le catalogue a des identifiants uniques et des tessitures cohérentes`() {
        val ids = Instruments.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (inst in Instruments.ALL) {
            assertTrue(inst.writtenLow.midi < inst.writtenHigh.midi, inst.id)
            assertTrue(inst.soundingLow.midi < inst.soundingHigh.midi, inst.id)
            assertEquals(inst, Instruments.byId(inst.id))
        }
    }
}
