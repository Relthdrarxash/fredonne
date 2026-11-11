package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FigureTest {

    @Test
    fun `figures exactes, avec ou sans point`() {
        assertEquals(NoteFigure(4), NoteFigure.of(Durations.QUARTER))
        assertEquals(NoteFigure(2, 1), NoteFigure.of(Durations.dotted(Durations.HALF)))
        assertEquals(NoteFigure(8, 2), NoteFigure.of(Fraction.of(7, 32)))
        assertNull(NoteFigure.of(Fraction.of(5, 16)))
        assertEquals(Fraction.of(3, 8), NoteFigure(4, 1).value)
    }

    @Test
    fun `figure la plus proche par defaut`() {
        assertEquals(NoteFigure(4), NoteFigure.closest(Fraction.of(5, 16)))
        assertEquals(NoteFigure(1), NoteFigure.closest(Fraction.of(2)))
        assertEquals(NoteFigure(64), NoteFigure.closest(Fraction.of(1, 128)))
    }

    @Test
    fun `crochets`() {
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 4), listOf(1, 2, 4, 8, 16, 32, 64).map { NoteFigure(it).flags })
    }

    @Test
    fun `alterations imposees par l armure`() {
        val g = Key(Step.G, 0, Mode.MAJOR)
        assertEquals(1, g.alterOf(Step.F))
        assertEquals(0, g.alterOf(Step.C))

        val eFlat = Key(Step.E, -1, Mode.MAJOR)
        assertEquals(listOf(-1, -1, -1, 0), listOf(Step.B, Step.E, Step.A, Step.D).map { eFlat.alterOf(it) })

        val c = Key(Step.C, 0, Mode.MAJOR)
        assertEquals(List(7) { 0 }, Step.entries.map { c.alterOf(it) })

        // Do dièse majeur : sept dièses, tout est diésé.
        val cSharp = Key(Step.C, 1, Mode.MAJOR)
        assertEquals(List(7) { 1 }, Step.entries.map { cSharp.alterOf(it) })

        // La mineur : comme do majeur. Mi mineur : un dièse.
        assertEquals(0, Key(Step.A, 0, Mode.MINOR).alterOf(Step.F))
        assertEquals(1, Key(Step.E, 0, Mode.MINOR).alterOf(Step.F))

        // Armure théorique de sol dièse majeur : huit dièses, le fa devient double dièse.
        assertEquals(2, Key(Step.G, 1, Mode.MAJOR).alterOf(Step.F))
    }
}
