package fr.fredonne.core.engraving

import fr.fredonne.core.music.Durations
import fr.fredonne.core.music.Fraction
import fr.fredonne.core.music.Key
import fr.fredonne.core.music.Measure
import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Mode
import fr.fredonne.core.music.Note
import fr.fredonne.core.music.Pitch
import fr.fredonne.core.music.Rest
import fr.fredonne.core.music.Step
import fr.fredonne.core.music.TimeSignature
import fr.fredonne.core.music.Tuplet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngraverTest {

    private val cMajor = Key(Step.C, 0, Mode.MAJOR)
    private val gMajor = Key(Step.G, 0, Mode.MAJOR)
    private val fourFour = TimeSignature(4, 4)
    private val q = Durations.QUARTER

    private fun n(step: Step, octave: Int, d: Fraction = q, alter: Int = 0, tuplet: Tuplet? = null, tied: Boolean = false) =
        Note(Pitch(step, octave, alter), d, tuplet, tied)

    private fun melody(vararg measures: List<fr.fredonne.core.music.Event>, key: Key = cMajor, time: TimeSignature = fourFour) =
        Melody(key, time, measures.map { Measure(it) })

    private fun notes(layout: ScoreLayout): List<NoteSymbol> =
        layout.systems.flatMap { it.measures }.flatMap { it.symbols }.filterIsInstance<NoteSymbol>()

    // Positions sur la portée

    @Test
    fun `positions en clef de sol et en clef de fa`() {
        assertEquals(0, Clef.TREBLE.positionOf(Pitch(Step.E, 4)))
        assertEquals(4, Clef.TREBLE.positionOf(Pitch(Step.B, 4)))
        assertEquals(8, Clef.TREBLE.positionOf(Pitch(Step.F, 5)))
        assertEquals(-2, Clef.TREBLE.positionOf(Pitch(Step.C, 4)))
        // Le dièse ne change pas la position.
        assertEquals(1, Clef.TREBLE.positionOf(Pitch(Step.F, 4, 1)))
        assertEquals(0, Clef.BASS.positionOf(Pitch(Step.G, 2)))
        assertEquals(10, Clef.BASS.positionOf(Pitch(Step.C, 4)))
    }

    @Test
    fun `aller-retour position et hauteur, pour la saisie au doigt`() {
        for (position in -6..14) {
            assertEquals(position, Clef.TREBLE.positionOf(Clef.TREBLE.naturalAt(position)))
            assertEquals(position, positionAt(yOf(position)))
        }
        assertEquals(Pitch(Step.G, 4), Clef.TREBLE.naturalAt(2))
        assertEquals(0f, yOf(8))
        assertEquals(4f, yOf(0))
        // Un appui entre une ligne et un interligne s'aimante sur le plus proche.
        assertEquals(7, positionAt(0.4f))
    }

    @Test
    fun `lignes supplementaires`() {
        assertEquals(emptyList(), Engraver.ledgerLines(-1))
        assertEquals(listOf(-2), Engraver.ledgerLines(-2))
        assertEquals(listOf(-2), Engraver.ledgerLines(-3))
        assertEquals(listOf(-2, -4), Engraver.ledgerLines(-4))
        assertEquals(emptyList(), Engraver.ledgerLines(9))
        assertEquals(listOf(10), Engraver.ledgerLines(10))
        assertEquals(listOf(10, 12), Engraver.ledgerLines(13))
    }

    // Têtes, hampes, crochets

    @Test
    fun `tetes, hampes et crochets selon la valeur`() {
        val layout = Engraver.layout(
            melody(listOf(n(Step.G, 4, Durations.WHOLE)), listOf(n(Step.G, 4, Durations.HALF), n(Step.G, 4), n(Step.G, 4, Durations.EIGHTH), n(Step.G, 4, Durations.SIXTEENTH))),
            width = 200f,
        )
        val (whole, half, quarter, eighth, sixteenth) = notes(layout)
        assertEquals(Head.WHOLE, whole.head)
        assertNull(whole.stemUp)
        assertEquals(Head.HALF, half.head)
        assertEquals(Head.FILLED, quarter.head)
        assertEquals(listOf(0, 0, 0, 1, 2), listOf(whole, half, quarter, eighth, sixteenth).map { it.flags })
    }

    @Test
    fun `la hampe monte sous la ligne du milieu et descend a partir d elle`() {
        val layout = Engraver.layout(melody(listOf(n(Step.A, 4), n(Step.B, 4), n(Step.C, 5), n(Step.C, 4))))
        assertEquals(listOf(true, false, false, true), notes(layout).map { it.stemUp })
    }

    @Test
    fun `une note tres loin de la portee a une hampe qui rejoint la ligne du milieu`() {
        val layout = Engraver.layout(melody(listOf(n(Step.G, 4), n(Step.E, 6), n(Step.F, 3))))
        val (near, high, low) = notes(layout)
        assertEquals(3.5f, near.stemLength)
        // Mi 6 : position 14, soit 5 interlignes au-dessus de la ligne du milieu.
        assertEquals(5f, high.stemLength)
        assertEquals(false, high.stemUp)
        assertEquals(listOf(10, 12, 14), high.ledgerLines)
        // Fa 3 : position -6, 5 interlignes en dessous.
        assertEquals(5f, low.stemLength)
        assertEquals(true, low.stemUp)
    }

    @Test
    fun `valeur pointee et valeur inhabituelle`() {
        val layout = Engraver.layout(melody(listOf(n(Step.G, 4, Durations.dotted(q)), n(Step.G, 4, Fraction.of(5, 16)))))
        val (dotted, odd) = notes(layout)
        assertEquals(1, dotted.dots)
        assertEquals(Head.FILLED, dotted.head)
        // 5/16 ne s'écrit pas d'un seul signe : on montre la figure la plus proche par défaut (noire).
        assertEquals(Head.FILLED, odd.head)
        assertEquals(0, odd.flags)
        assertEquals(0, odd.dots)
    }

    // Altérations

    @Test
    fun `en sol majeur le fa diese n a pas d alteration, le fa naturel a un becarre`() {
        val layout = Engraver.layout(
            melody(
                listOf(n(Step.F, 4, alter = 1), n(Step.F, 4), n(Step.F, 4), n(Step.F, 4, alter = 1)),
                listOf(n(Step.F, 4, alter = 1), n(Step.F, 4), n(Step.F, 5), n(Step.G, 4)),
                key = gMajor,
            ),
            width = 200f,
        )
        assertEquals(
            listOf(
                null, // fa dièse : donné par l'armure
                Accidental.NATURAL, // bécarre
                null, // le bécarre vaut pour le reste de la mesure
                Accidental.SHARP, // retour au dièse : il faut le redire
                null, // nouvelle mesure : l'armure reprend ses droits
                Accidental.NATURAL,
                Accidental.NATURAL, // autre octave : l'altération de la mesure ne s'y applique pas
                null,
            ),
            notes(layout).map { it.accidental },
        )
    }

    @Test
    fun `une note liee ne repete pas son alteration apres la barre`() {
        val layout = Engraver.layout(
            melody(
                listOf(n(Step.C, 5, Durations.WHOLE, alter = 1, tied = true)),
                listOf(n(Step.C, 5, Durations.HALF, alter = 1), n(Step.C, 5, Durations.HALF, alter = 1)),
            ),
            width = 200f,
        )
        val (first, tied, after) = notes(layout)
        assertEquals(Accidental.SHARP, first.accidental)
        assertNull(tied.accidental)
        assertNull(after.accidental)
        assertTrue(first.tiedToNext)
    }

    @Test
    fun `doubles alterations`() {
        val layout = Engraver.layout(melody(listOf(n(Step.F, 4, alter = 2), n(Step.B, 4, alter = -2), n(Step.B, 4, alter = -1), n(Step.E, 4))))
        assertEquals(listOf(Accidental.DOUBLE_SHARP, Accidental.DOUBLE_FLAT, Accidental.FLAT, null), notes(layout).map { it.accidental })
    }

    // Armure

    @Test
    fun `armures en clef de sol et en clef de fa`() {
        val d = Engraver.keySignature(Key(Step.D, 0, Mode.MAJOR), Clef.TREBLE)
        assertEquals(listOf(8, 5), d.map { it.position }) // fa dièse sur la ligne du haut, do dièse
        assertTrue(d.all { it.accidental == Accidental.SHARP })
        assertTrue(d[0].x < d[1].x)

        val eFlat = Engraver.keySignature(Key(Step.E, -1, Mode.MAJOR), Clef.TREBLE)
        assertEquals(listOf(4, 7, 3), eFlat.map { it.position }) // si, mi, la
        assertTrue(eFlat.all { it.accidental == Accidental.FLAT })

        assertEquals(listOf(6, 3), Engraver.keySignature(Key(Step.D, 0, Mode.MAJOR), Clef.BASS).map { it.position })
        assertEquals(emptyList(), Engraver.keySignature(cMajor, Clef.TREBLE))
        assertEquals(7, Engraver.keySignature(Key(Step.C, 1, Mode.MAJOR), Clef.TREBLE).size)
    }

    // Espacement et lignes

    @Test
    fun `les notes se suivent de gauche a droite et les longues ont plus de place`() {
        val layout = Engraver.layout(melody(listOf(n(Step.G, 4, Durations.EIGHTH), n(Step.G, 4, Durations.EIGHTH), n(Step.G, 4), n(Step.G, 4, Durations.HALF))), width = 200f)
        val xs = notes(layout).map { it.x }
        assertEquals(xs.sorted(), xs)
        val gaps = xs.zipWithNext { a, b -> b - a }
        // L'écart après une note dépend de sa propre durée : croche, croche, puis noire.
        assertEquals(gaps[0], gaps[1])
        assertTrue(gaps[2] > gaps[1], "la noire a plus de place que la croche")
        assertTrue(gaps[2] < 2 * gaps[1], "mais pas le double")
        assertTrue(Engraver.spaceFor(n(Step.G, 4, Durations.WHOLE)) > Engraver.spaceFor(n(Step.G, 4, Durations.HALF)))
        assertEquals(1.8f, Engraver.spaceFor(n(Step.G, 4, Durations.THIRTY_SECOND)))
    }

    @Test
    fun `une alteration decale la tete de note`() {
        val plain = Engraver.layout(melody(listOf(n(Step.G, 4), n(Step.A, 4))), width = 200f)
        val sharp = Engraver.layout(melody(listOf(n(Step.G, 4), n(Step.A, 4, alter = 1))), width = 200f)
        assertEquals(notes(plain)[1].x + 1f, notes(sharp)[1].x)
    }

    private val bar = listOf(n(Step.C, 4), n(Step.D, 4), n(Step.E, 4), n(Step.F, 4))

    @Test
    fun `les mesures passent a la ligne et remplissent la largeur`() {
        val layout = Engraver.layout(melody(bar, bar, bar, bar, bar), width = 40f)
        assertTrue(layout.systems.size > 1)
        assertEquals((0..4).toList(), layout.systems.flatMap { it.measures }.map { it.index })
        // Chiffrage sur la première ligne seulement.
        assertEquals(fourFour, layout.systems.first().time)
        assertTrue(layout.systems.drop(1).all { it.time == null })
        // Toutes les lignes sauf la dernière vont jusqu'au bord droit.
        for (system in layout.systems.dropLast(1)) {
            assertEquals(40f, system.width, 0.01f)
            assertEquals(40f, system.measures.last().x1, 0.01f)
        }
        assertTrue(layout.systems.last().width <= 40f)
        // Les mesures d'une ligne se touchent sans se chevaucher.
        for (system in layout.systems) {
            system.measures.zipWithNext { a, b -> assertEquals(a.x1, b.x0) }
            for (measure in system.measures) {
                assertTrue(measure.symbols.all { it.x >= measure.x0 && it.x < measure.x1 })
            }
        }
    }

    @Test
    fun `une mesure trop large pour la ligne est resserree, jamais coupee`() {
        val crowded = List(16) { n(Step.G, 4, Durations.SIXTEENTH) }
        val layout = Engraver.layout(melody(crowded), width = 20f)
        assertEquals(1, layout.systems.size)
        val measure = layout.systems.single().measures.single()
        assertEquals(16, measure.symbols.size)
        assertEquals(20f, measure.x1, 0.01f)
    }

    @Test
    fun `une melodie vide garde une mesure ou poser le curseur`() {
        val layout = Engraver.layout(Melody(cMajor, fourFour, emptyList()))
        val measure = layout.systems.single().measures.single()
        assertEquals(0, measure.index)
        assertTrue(measure.symbols.isEmpty())
        assertEquals(1, measure.cursorSlots.size)
        assertTrue(measure.x1 > measure.x0)
    }

    // Curseur et repérage

    @Test
    fun `positions du curseur - une avant chaque note et une a la fin`() {
        val layout = Engraver.layout(melody(bar, listOf(n(Step.G, 4), Rest(q))), width = 200f)
        val (first, second) = layout.systems.single().measures
        assertEquals(5, first.cursorSlots.size)
        assertEquals(3, second.cursorSlots.size)
        assertEquals(first.cursorSlots.sorted(), first.cursorSlots)
        assertTrue(first.cursorSlots.last() <= first.x1)

        // Un appui près de la troisième note de la première mesure.
        val third = first.symbols[2]
        assertEquals(CursorPosition(0, 2), layout.cursorAt(0, third.x - 0.1f))
        // Tout à droite : fin de la dernière mesure.
        assertEquals(CursorPosition(1, 2), layout.cursorAt(0, 10_000f))
        assertNull(layout.cursorAt(3, 0f))
    }

    @Test
    fun `retrouver le symbole d un evenement`() {
        val layout = Engraver.layout(melody(bar, bar, bar, bar, bar), width = 40f)
        val found = assertNotNull(layout.find(EventRef(4, 1)))
        assertEquals(layout.systems.last().index, found.first.index)
        assertEquals(Clef.TREBLE.positionOf(Pitch(Step.D, 4)), (found.second as NoteSymbol).position)
        assertNull(layout.find(EventRef(9, 0)))
        assertNull(layout.find(EventRef(0, 7)))
    }

    @Test
    fun `silences`() {
        val layout = Engraver.layout(melody(listOf(Rest(Durations.HALF), Rest(Durations.dotted(q)), Rest(Durations.EIGHTH))), width = 200f)
        val rests = layout.systems.single().measures.single().symbols.filterIsInstance<RestSymbol>()
        assertEquals(listOf(2, 4, 8), rests.map { it.denominator })
        assertEquals(listOf(0, 1, 0), rests.map { it.dots })
    }

    // Groupes irréguliers

    @Test
    fun `un crochet par triolet`() {
        val t = Tuplet(3, 2)
        val triplet = List(3) { n(Step.G, 4, Durations.EIGHTH, tuplet = t) }
        val layout = Engraver.layout(melody(triplet + triplet + listOf(n(Step.G, 4, Durations.HALF))), width = 200f)
        val measure = layout.systems.single().measures.single()
        assertEquals(2, measure.brackets.size)
        assertTrue(measure.brackets.all { it.number == 3 })
        val (a, b) = measure.brackets
        assertTrue(a.x0 < a.x1 && a.x1 <= b.x0 + 0.6f && b.x0 < b.x1)
        // Hampes vers le haut (sol 4) : crochet au-dessus.
        assertTrue(a.above)
        assertTrue(a.x0 < measure.symbols[0].x && a.x1 > measure.symbols[2].x)
    }

    @Test
    fun `clef de fa - memes regles, autres positions`() {
        val layout = Engraver.layout(melody(listOf(n(Step.C, 3), n(Step.C, 4), n(Step.E, 2))), clef = Clef.BASS)
        assertEquals(listOf(3, 10, -2), notes(layout).map { it.position })
        assertEquals(listOf(emptyList(), listOf(10), listOf(-2)), notes(layout).map { it.ledgerLines })
    }
}
