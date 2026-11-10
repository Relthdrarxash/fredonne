package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyTest {

    private fun major(step: Step, alter: Int = 0) = Key(step, alter, Mode.MAJOR)
    private fun minor(step: Step, alter: Int = 0) = Key(step, alter, Mode.MINOR)

    @Test
    fun `armures majeures`() {
        assertEquals(0, major(Step.C).fifths)
        assertEquals(1, major(Step.G).fifths)
        assertEquals(-1, major(Step.F).fifths)
        assertEquals(-2, major(Step.B, -1).fifths)
        assertEquals(3, major(Step.A).fifths)
        assertEquals(7, major(Step.C, 1).fifths)
        assertEquals(-7, major(Step.C, -1).fifths)
    }

    @Test
    fun `armures mineures`() {
        assertEquals(0, minor(Step.A).fifths)
        assertEquals(1, minor(Step.E).fifths)
        assertEquals(-3, minor(Step.C).fifths)
        assertEquals(-1, minor(Step.D).fifths)
    }

    @Test
    fun `construction depuis le nombre d'altérations`() {
        assertEquals(major(Step.C), Key.fromFifths(0, Mode.MAJOR))
        assertEquals(major(Step.B, -1), Key.fromFifths(-2, Mode.MAJOR))
        assertEquals(major(Step.C, 1), Key.fromFifths(7, Mode.MAJOR))
        assertEquals(minor(Step.A), Key.fromFifths(0, Mode.MINOR))
        assertEquals(minor(Step.C), Key.fromFifths(-3, Mode.MINOR))
        for (f in -7..7) {
            assertEquals(f, Key.fromFifths(f, Mode.MAJOR).fifths)
            assertEquals(f, Key.fromFifths(f, Mode.MINOR).fifths)
        }
    }

    @Test
    fun `do majeur transposé d'une seconde majeure donne ré majeur`() {
        assertEquals(major(Step.D), major(Step.C).transposed(Interval.MAJOR_SECOND))
    }

    @Test
    fun `si bémol majeur de concert s'écrit do majeur pour la clarinette`() {
        val written = major(Step.B, -1).transposed(-Instruments.CLARINET_BB.transposition)
        assertEquals(major(Step.C), written)
        assertEquals(0, written.fifths)
    }

    @Test
    fun `si bémol majeur de concert s'écrit sol majeur pour le saxophone alto`() {
        val written = major(Step.B, -1).transposed(-Instruments.SAX_ALTO.transposition)
        assertEquals(major(Step.G), written)
    }

    @Test
    fun `une armure de plus de sept altérations est signalée`() {
        val impractical = major(Step.C, 1).transposed(Interval.MAJOR_SECOND)
        assertEquals(9, impractical.fifths)
        assertFalse(impractical.isPractical)
        assertTrue(major(Step.B).transposed(Interval.MAJOR_SECOND).isPractical)
    }

    @Test
    fun `intervalle entre tonalités dans le sens demandé`() {
        val c = major(Step.C)
        assertEquals(Interval.MAJOR_SECOND, Key.intervalBetweenTonics(c, Step.D, 0, Direction.UP))
        assertEquals(Interval(-6, -10), Key.intervalBetweenTonics(c, Step.D, 0, Direction.DOWN))
    }

    @Test
    fun `sens le plus court`() {
        val c = major(Step.C)
        // do vers la : une tierce mineure vers le grave plutôt qu'une sixte majeure vers l'aigu
        assertEquals(Interval(-2, -3), Key.intervalBetweenTonics(c, Step.A, 0))
        // do vers ré : seconde majeure vers l'aigu
        assertEquals(Interval.MAJOR_SECOND, Key.intervalBetweenTonics(c, Step.D, 0))
        // même tonalité : unisson
        assertEquals(Interval.UNISON, Key.intervalBetweenTonics(c, Step.C, 0))
    }

    @Test
    fun `l'intervalle calculé mène bien à la tonalité cible`() {
        val from = major(Step.E, -1)
        val interval = Key.intervalBetweenTonics(from, Step.G, 0)
        assertEquals(major(Step.G), from.transposed(interval))
    }
}
