package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MelodyAndVerifierTest {

    private val q = Durations.QUARTER
    private val fourFour = TimeSignature(4, 4)
    private val cMajor = Key(Step.C, 0, Mode.MAJOR)

    private fun note(step: Step, octave: Int, alter: Int = 0, d: Fraction = q, tuplet: Tuplet? = null) =
        Note(Pitch(step, octave, alter), d, tuplet)

    private fun fourQuarters() = Measure(
        listOf(note(Step.C, 4), note(Step.D, 4), note(Step.E, 4), note(Step.F, 4)),
    )

    private fun melody(vararg measures: Measure, time: TimeSignature = fourFour) =
        Melody(cMajor, time, measures.toList())

    // Durées

    @Test
    fun `mesure complète sans problème`() {
        assertTrue(Verifier.check(melody(fourQuarters())).isEmpty())
    }

    @Test
    fun `mesure trop courte`() {
        val m = Measure(listOf(note(Step.C, 4), note(Step.D, 4), note(Step.E, 4)))
        assertEquals(listOf(MeasureTooShort(0, q)), Verifier.check(melody(m)))
    }

    @Test
    fun `mesure trop longue`() {
        val m = Measure(List(5) { note(Step.C, 4) })
        assertEquals(listOf(MeasureTooLong(0, q)), Verifier.check(melody(m)))
    }

    @Test
    fun `mesure vide`() {
        assertEquals(
            listOf(MeasureTooShort(0, Fraction.ONE)),
            Verifier.check(melody(Measure(emptyList()))),
        )
    }

    @Test
    fun `le numéro de mesure signalé est le bon`() {
        val bad = Measure(listOf(note(Step.C, 4)))
        val issues = Verifier.check(melody(fourQuarters(), bad, fourQuarters()))
        assertEquals(1, issues.size)
        assertEquals(1, issues[0].measure)
    }

    @Test
    fun `chiffrage 6 8 et 5 8`() {
        val e = Durations.EIGHTH
        val sixEight = Measure(List(6) { note(Step.C, 4, d = e) })
        assertTrue(Verifier.check(melody(sixEight, time = TimeSignature(6, 8))).isEmpty())
        val fiveEight = Measure(List(5) { note(Step.C, 4, d = e) })
        assertTrue(Verifier.check(melody(fiveEight, time = TimeSignature(5, 8))).isEmpty())
        assertEquals(1, Verifier.check(melody(sixEight, time = TimeSignature(5, 8))).size)
    }

    @Test
    fun `la note pointée et le silence comptent`() {
        val m = Measure(
            listOf(
                note(Step.C, 4, d = Durations.dotted(Durations.HALF)),
                Rest(q),
            ),
        )
        assertTrue(Verifier.check(melody(m)).isEmpty())
    }

    @Test
    fun `triolet de croches dans un temps`() {
        val triplet = Tuplet(3, 2)
        val m = Measure(
            listOf(
                note(Step.C, 4, d = Durations.HALF),
                note(Step.D, 4, d = Durations.EIGHTH, tuplet = triplet),
                note(Step.E, 4, d = Durations.EIGHTH, tuplet = triplet),
                note(Step.F, 4, d = Durations.EIGHTH, tuplet = triplet),
                Rest(q),
            ),
        )
        assertTrue(Verifier.check(melody(m)).isEmpty())
    }

    @Test
    fun `quintolet de doubles croches dans un temps`() {
        val quintuplet = Tuplet(5, 4)
        val m = Measure(
            List(5) { note(Step.C, 4, d = Durations.SIXTEENTH, tuplet = quintuplet) } +
                listOf(Rest(Durations.HALF), Rest(q)),
        )
        assertTrue(Verifier.check(melody(m)).isEmpty())
    }

    @Test
    fun `anacrouse autorisée seulement si demandée`() {
        val pickup = Measure(listOf(note(Step.G, 3)))
        val m = melody(pickup, fourQuarters())
        assertTrue(Verifier.check(m, pickup = true).isEmpty())
        assertEquals(listOf(MeasureTooShort(0, Fraction.of(3, 4))), Verifier.check(m))
    }

    @Test
    fun `une anacrouse ne peut pas être trop longue ni vide`() {
        val tooLong = Measure(List(5) { note(Step.C, 4) })
        assertEquals(1, Verifier.check(melody(tooLong), pickup = true).size)
        assertEquals(1, Verifier.check(melody(Measure(emptyList())), pickup = true).size)
    }

    // Tessiture

    @Test
    fun `note dans la tessiture`() {
        assertTrue(Verifier.check(melody(fourQuarters()), Instruments.FLUTE).isEmpty())
    }

    @Test
    fun `note trop grave pour la clarinette (sons réels)`() {
        // Re 3 réel est le plus bas de la clarinette en si bémol : do dièse 3 est trop grave.
        val low = Measure(
            listOf(note(Step.D, 3), note(Step.C, 3, 1), note(Step.D, 3), note(Step.D, 3)),
        )
        val issues = Verifier.check(melody(low), Instruments.CLARINET_BB)
        assertEquals(listOf(OutOfRange(0, 1, Pitch(Step.C, 3, 1), tooLow = true)), issues)
    }

    @Test
    fun `note trop aiguë`() {
        val high = Measure(
            listOf(note(Step.C, 6), note(Step.C, 4), note(Step.C, 4), note(Step.C, 4)),
        )
        val issues = Verifier.check(melody(high), Instruments.VOICE_BASS)
        assertEquals(listOf(OutOfRange(0, 0, Pitch(Step.C, 6), tooLow = false)), issues)
    }

    @Test
    fun `les silences ne sont jamais hors tessiture`() {
        val m = Measure(listOf(Rest(Durations.WHOLE)))
        assertTrue(Verifier.check(melody(m), Instruments.VOICE_BASS).isEmpty())
    }

    // Transposition de mélodie

    @Test
    fun `transposer une mélodie transpose les notes et l'armure`() {
        val m = Melody(
            cMajor, fourFour,
            listOf(Measure(listOf(note(Step.C, 4), note(Step.D, 4), note(Step.E, 4), Rest(q)))),
        )
        val up = m.transposed(Interval.MAJOR_SECOND)
        assertEquals(Key(Step.D, 0, Mode.MAJOR), up.key)
        assertEquals(
            listOf(
                note(Step.D, 4), note(Step.E, 4), note(Step.F, 4, 1), Rest(q),
            ),
            up.measures[0].events,
        )
        assertEquals(m, up.transposed(-Interval.MAJOR_SECOND))
    }

    @Test
    fun `la transposition conserve les durées et les tuplets`() {
        val triplet = Tuplet(3, 2)
        val m = Melody(
            cMajor, fourFour,
            listOf(Measure(listOf(note(Step.C, 4, d = Durations.EIGHTH, tuplet = triplet)))),
        )
        val up = m.transposed(Interval.PERFECT_FIFTH)
        val moved = up.measures[0].events[0]
        assertEquals(Durations.EIGHTH, moved.written)
        assertEquals(triplet, moved.tuplet)
    }

    @Test
    fun `harpe celtique signale les changements de leviers hors armure et adapte la clé`() {
        val m = Melody(
            cMajor, fourFour,
            listOf(Measure(listOf(note(Step.C, 4), note(Step.F, 4, 1), note(Step.G, 4), note(Step.C, 4)))),
        )
        val issues = Verifier.check(m, Instruments.HARP_CELTIC)
        assertEquals(listOf(LeverChangeRequired(0, 1, Pitch(Step.F, 4, 1))), issues)
        assertEquals(Clef.TREBLE, Clef.resolveFor(m, Instruments.HARP_CELTIC))

        val lowHarp = Melody(
            cMajor, fourFour,
            listOf(Measure(listOf(note(Step.C, 2), note(Step.G, 2), note(Step.C, 3), note(Step.E, 3)))),
        )
        assertEquals(Clef.BASS, Clef.resolveFor(lowHarp, Instruments.HARP_CELTIC))
    }

    @Test
    fun `harpe à pédales suit l'état des 7 pédales sur toutes les octaves`() {
        // En Do majeur, un Fa#4 déplace la pédale Fa sur Fa# ; le Fa#5 suivant n'a pas besoin
        // d'un nouveau changement, mais le retour à Fa♮4 exige de remettre la pédale sur Fa♮.
        val m = Melody(
            cMajor, fourFour,
            listOf(Measure(listOf(note(Step.F, 4, 1), note(Step.F, 5, 1), note(Step.F, 4, 0), note(Step.C, 4)))),
        )
        val issues = Verifier.check(m, Instruments.HARP_PEDAL)
        assertEquals(
            listOf(
                PedalChangeRequired(0, 0, Pitch(Step.F, 4, 1), "Fa#"),
                PedalChangeRequired(0, 2, Pitch(Step.F, 4, 0), "Fa♮"),
            ),
            issues,
        )
        assertEquals("Ré♮ Do♮ Si♮ | Mi♮ Fa# Sol♮ La♮", HarpPedals.initialDiagram(Key(Step.G, 0, Mode.MAJOR)))
    }

    @Test
    fun `basse électrique et voix graves utilisent la clé de Fa ou Sol 8vb`() {
        val bassLine = Melody(
            cMajor, fourFour,
            listOf(Measure(listOf(note(Step.E, 1), note(Step.A, 1), note(Step.D, 2), note(Step.G, 2)))),
        )
        assertTrue(Verifier.check(bassLine, Instruments.BASS_ELECTRIC).isEmpty())
        assertEquals(Pitch(Step.E, 2), Instruments.BASS_ELECTRIC.toWritten(Pitch(Step.E, 1)))
        assertEquals(Clef.BASS, Clef.resolveFor(bassLine, Instruments.BASS_ELECTRIC))
        assertEquals(Clef.BASS, Clef.resolveFor(bassLine, Instruments.VOICE_BASS))
        assertEquals(Clef.TREBLE_8VB, Clef.resolveFor(bassLine, Instruments.VOICE_TENOR))
    }
}
