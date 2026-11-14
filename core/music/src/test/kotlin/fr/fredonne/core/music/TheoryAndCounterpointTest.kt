package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TheoryAndCounterpointTest {

    private val q = Durations.QUARTER
    private val h = Durations.HALF
    private val fourFour = TimeSignature(4, 4)

    @Test
    fun `detectKey identifie Sol majeur par profil de Krumhansl-Schmuckler`() {
        val gMajorMelody = Melody(
            key = Key(Step.C, 0, Mode.MAJOR),
            time = fourFour,
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4), h),
                        Note(Pitch(Step.B, 4), q),
                        Note(Pitch(Step.D, 5), q),
                    ),
                ),
                Measure(
                    listOf(
                        Note(Pitch(Step.C, 5), q),
                        Note(Pitch(Step.A, 4), q),
                        Note(Pitch(Step.F, 4, 1), q),
                        Note(Pitch(Step.G, 4), q),
                    ),
                ),
            ),
        )
        val detected = MusicTheory.detectKey(gMajorMelody)
        assertEquals(Key(Step.G, 0, Mode.MAJOR), detected)
    }

    @Test
    fun `spellPitch orthographie correctement Fa diese en Sol majeur et Sol bemol en Reb majeur`() {
        val gMajor = Key(Step.G, 0, Mode.MAJOR)
        val dbMajor = Key(Step.D, -1, Mode.MAJOR)

        val spelledInG = MusicTheory.spellPitch(66, gMajor, prevMidi = 64, nextMidi = 67)
        assertEquals(Pitch(Step.F, 4, 1), spelledInG)

        val spelledInDb = MusicTheory.spellPitch(66, dbMajor, prevMidi = 68, nextMidi = 65)
        assertEquals(Pitch(Step.G, 4, -1), spelledInDb)
    }

    @Test
    fun `respellPractical simplifie une tonalite theorique a 8 dieses en Lab majeur (4 bemols)`() {
        val gSharpMajor = Key(Step.G, 1, Mode.MAJOR) // 8 dièses
        val m = Melody(
            key = gSharpMajor,
            time = fourFour,
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4, 1), q),
                        Note(Pitch(Step.B, 4, 1), q),
                        Note(Pitch(Step.D, 5, 1), h),
                    ),
                ),
            ),
        )
        val practical = m.respellPractical()
        assertEquals(Key(Step.A, -1, Mode.MAJOR), practical.key)
        val notes = practical.measures[0].events.filterIsInstance<Note>()
        assertEquals(Pitch(Step.A, 4, -1), notes[0].pitch)
        assertEquals(Pitch(Step.C, 5, 0), notes[1].pitch)
        assertEquals(Pitch(Step.E, 5, -1), notes[2].pitch)
    }

    @Test
    fun `gourlaySpacingFactor est strictement croissant et vaut 1 pour la noire`() {
        val s16 = MusicTheory.gourlaySpacingFactor(Durations.SIXTEENTH)
        val s8 = MusicTheory.gourlaySpacingFactor(Durations.EIGHTH)
        val s4 = MusicTheory.gourlaySpacingFactor(Durations.QUARTER)
        val s2 = MusicTheory.gourlaySpacingFactor(Durations.HALF)
        val s1 = MusicTheory.gourlaySpacingFactor(Durations.WHOLE)

        assertEquals(1.0f, s4, 0.001f)
        assertTrue(s16 < s8 && s8 < s4 && s4 < s2 && s2 < s1)
        // Une ronde prend moins de 4 fois la place d'une noire (loi logarithmique de Gourlay)
        assertTrue(s1 < 2.5f)
    }

    @Test
    fun `sensoryDissonance classe la seconde mineure plus rugueuse que la quinte et l octave`() {
        val octaveRoughness = MusicTheory.sensoryDissonance(60, 72)
        val fifthRoughness = MusicTheory.sensoryDissonance(60, 67)
        val majorThirdRoughness = MusicTheory.sensoryDissonance(60, 64)
        val minorSecondRoughness = MusicTheory.sensoryDissonance(60, 61)

        assertTrue(minorSecondRoughness > majorThirdRoughness)
        assertTrue(majorThirdRoughness > fifthRoughness)
        assertTrue(fifthRoughness >= octaveRoughness)
    }

    @Test
    fun `checkTwoVoices detecte quintes paralleles, octaves paralleles et croisement de voix`() {
        val cMajor = Key(Step.C, 0, Mode.MAJOR)
        // Mesure : (Do4, Sol4) -> (Ré4, La4) [quintes parallèles] -> (Mi4, Mi5) -> (Fa4, Fa5) [octaves parallèles]
        val upper = Melody(
            key = cMajor,
            time = fourFour,
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4), q),
                        Note(Pitch(Step.A, 4), q),
                        Note(Pitch(Step.E, 5), q),
                        Note(Pitch(Step.F, 5), q),
                    ),
                ),
            ),
        )
        val lower = Melody(
            key = cMajor,
            time = fourFour,
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.C, 4), q),
                        Note(Pitch(Step.D, 4), q),
                        Note(Pitch(Step.E, 4), q),
                        Note(Pitch(Step.F, 4), q),
                    ),
                ),
            ),
        )

        val issues = Verifier.checkTwoVoices(upper, lower)
        assertTrue(issues.any { it is ParallelFifths })
        assertTrue(issues.any { it is ParallelOctaves })

        // Croisement de voix : lower passe au-dessus de upper
        val crossedLower = Melody(
            key = cMajor,
            time = fourFour,
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.B, 4), Durations.WHOLE),
                    ),
                ),
            ),
        )
        val crossedUpper = Melody(
            key = cMajor,
            time = fourFour,
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4), Durations.WHOLE),
                    ),
                ),
            ),
        )
        val crossIssues = Verifier.checkTwoVoices(crossedUpper, crossedLower)
        assertTrue(crossIssues.any { it is VoiceCrossing })
    }
}
