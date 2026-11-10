package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FractionTest {

    @Test
    fun `la fraction est toujours réduite`() {
        assertEquals(Fraction.of(1, 2), Fraction.of(2, 4))
        assertEquals(Fraction.of(3, 4), Fraction.of(-6, -8))
        assertEquals(Fraction.ZERO, Fraction.of(0, 5))
    }

    @Test
    fun `le dénominateur est toujours positif`() {
        val f = Fraction.of(1, -4)
        assertEquals(-1L, f.numerator)
        assertEquals(4L, f.denominator)
    }

    @Test
    fun `addition et soustraction exactes`() {
        assertEquals(Fraction.of(3, 8), Durations.QUARTER + Durations.EIGHTH)
        assertEquals(Durations.EIGHTH, Durations.QUARTER - Durations.EIGHTH)
        assertEquals(Fraction.ONE, Fraction.of(1, 3) + Fraction.of(2, 3))
    }

    @Test
    fun `multiplication et division`() {
        assertEquals(Fraction.of(1, 12), Durations.EIGHTH * Fraction.of(2, 3))
        assertEquals(Fraction.of(3, 2), Fraction.of(3, 4) / Fraction.of(1, 2))
        assertEquals(Fraction.of(3, 4), Durations.QUARTER * 3)
    }

    @Test
    fun `comparaison`() {
        assertTrue(Durations.QUARTER < Durations.HALF)
        assertTrue(Fraction.of(1, 3) > Fraction.of(1, 4))
        assertEquals(0, Fraction.of(2, 6).compareTo(Fraction.of(1, 3)))
    }

    @Test
    fun `la note pointée vaut une fois et demie la valeur`() {
        assertEquals(Fraction.of(3, 8), Durations.dotted(Durations.QUARTER))
    }

    @Test
    fun `dénominateur nul refusé`() {
        assertFailsWith<IllegalArgumentException> { Fraction.of(1, 0) }
        assertFailsWith<IllegalArgumentException> { Fraction.ONE / Fraction.ZERO }
    }

    @Test
    fun `affichage`() {
        assertEquals("3/4", Fraction.of(3, 4).toString())
        assertEquals("2", Fraction.of(4, 2).toString())
    }
}
