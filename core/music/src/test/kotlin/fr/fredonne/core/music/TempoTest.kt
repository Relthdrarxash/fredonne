package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TempoTest {

    private val tripletEighth = Note(Pitch(Step.C, 4), Durations.EIGHTH, Tuplet(3, 2))
    private val eighth = Note(Pitch(Step.C, 4), Durations.EIGHTH)

    @Test
    fun `le triolet de croches devient la croche - noire 60 donne noire 90`() {
        val after = Tempo.quarter(60).modulated(MetricModulation.of(tripletEighth, eighth))
        assertEquals(Tempo.quarter(90), after)
    }

    @Test
    fun `le quintolet de doubles devient la double - noire 60 donne noire 75`() {
        val quintuplet = Note(Pitch(Step.C, 4), Durations.SIXTEENTH, Tuplet(5, 4))
        val sixteenth = Note(Pitch(Step.C, 4), Durations.SIXTEENTH)
        val after = Tempo.quarter(60).modulated(MetricModulation.of(quintuplet, sixteenth))
        assertEquals(Tempo.quarter(75), after)
    }

    @Test
    fun `la noire pointee devient la noire - le tempo ralentit aux deux tiers`() {
        val modulation = MetricModulation(Durations.dotted(Durations.QUARTER), Durations.QUARTER)
        assertEquals(Fraction.of(2, 3), modulation.ratio)
        assertEquals(Tempo.quarter(80), Tempo.quarter(120).modulated(modulation))
    }

    @Test
    fun `un tempo non entier reste exact`() {
        val modulation = MetricModulation(Fraction.of(1, 20), Durations.SIXTEENTH) // 5:4
        val after = Tempo.quarter(54).modulated(modulation)
        assertEquals(Fraction.of(135, 2), after.perMinute)
        // Revenir en arrière redonne exactement le tempo de départ.
        val back = after.modulated(MetricModulation(Durations.SIXTEENTH, Fraction.of(1, 20)))
        assertEquals(Tempo.quarter(54), back)
    }

    @Test
    fun `meme vitesse exprimee a la croche ou a la noire pointee`() {
        val tempo = Tempo.quarter(60)
        assertEquals(Tempo(Durations.EIGHTH, Fraction.of(120)), tempo.expressedIn(Durations.EIGHTH))
        assertEquals(Tempo(Durations.dotted(Durations.QUARTER), Fraction.of(40)), tempo.expressedIn(Durations.dotted(Durations.QUARTER)))
        assertEquals(tempo.wholesPerMinute, tempo.expressedIn(Durations.EIGHTH).wholesPerMinute)
    }

    @Test
    fun `duree reelle d une valeur`() {
        // À noire = 60, une ronde dure 4 secondes, soit 1/15 de minute.
        assertEquals(Fraction.of(1, 15), Tempo.quarter(60).minutesFor(Durations.WHOLE))
    }

    @Test
    fun `enchainer plusieurs modulations`() {
        val threeToTwo = MetricModulation.of(tripletEighth, eighth)
        val tempos = Tempo.quarter(60).through(listOf(threeToTwo, threeToTwo))
        assertEquals(listOf(Tempo.quarter(60), Tempo.quarter(90), Tempo.quarter(135)), tempos)
    }

    @Test
    fun `un tempo nul ou negatif est refuse`() {
        assertFailsWith<IllegalArgumentException> { Tempo(Durations.QUARTER, Fraction.ZERO) }
        assertFailsWith<IllegalArgumentException> { MetricModulation(Fraction.ZERO, Durations.QUARTER) }
    }
}
