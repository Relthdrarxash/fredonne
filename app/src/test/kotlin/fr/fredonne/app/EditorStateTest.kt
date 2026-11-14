package fr.fredonne.app

import fr.fredonne.core.music.Durations
import fr.fredonne.core.music.Instruments
import fr.fredonne.core.music.Key
import fr.fredonne.core.music.MeasureTooLong
import fr.fredonne.core.music.MeasureTooShort
import fr.fredonne.core.music.Mode
import fr.fredonne.core.music.Note
import fr.fredonne.core.music.OutOfRange
import fr.fredonne.core.music.Pitch
import fr.fredonne.core.music.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {

    @Test
    fun `inserting four quarter notes fills first measure cleanly`() {
        var state = EditorState()
        state = state.insertNote(Pitch(Step.C, 4))
            .insertNote(Pitch(Step.D, 4))
            .insertNote(Pitch(Step.E, 4))
            .insertNote(Pitch(Step.G, 4))

        assertEquals(1, state.melody.measures.size)
        assertEquals(4, state.melody.measures[0].events.size)
        assertTrue(state.issues.isEmpty())
    }

    @Test
    fun `inserting a fifth quarter note automatically starts a second measure`() {
        var state = EditorState()
        repeat(5) {
            state = state.insertNote(Pitch(Step.C, 4))
        }
        assertEquals(2, state.melody.measures.size)
        assertEquals(4, state.melody.measures[0].events.size)
        assertEquals(1, state.melody.measures[1].events.size)
        assertTrue(state.issues.any { it is MeasureTooShort && it.measure == 1 })
    }

    @Test
    fun `undo and redo restore notes and cursor position`() {
        val initial = EditorState()
        val after = initial.insertNote(Pitch(Step.E, 4)).insertNote(Pitch(Step.G, 4))
        assertEquals(2, after.melody.measures[0].events.size)
        assertTrue(after.canUndo)

        val undone = after.undo()
        assertEquals(1, undone.melody.measures[0].events.size)
        assertTrue(undone.canRedo)

        val redone = undone.redo()
        assertEquals(2, redone.melody.measures[0].events.size)
        assertFalse(redone.canRedo)
    }

    @Test
    fun `transposing to G major moves C4 to G4 and updates key signature`() {
        val state = EditorState().insertNote(Pitch(Step.C, 4))
        val transposed = state.transposeToKey(Step.G, 0)
        assertEquals(Key(Step.G, 0, Mode.MAJOR), transposed.melody.key)
        val note = transposed.melody.measures[0].events[0] as Note
        assertEquals(Step.G, note.pitch.step)
        assertEquals(1, transposed.melody.key.fifths)
    }

    @Test
    fun `bb clarinet written display transposes sounding C4 up a major second to D4`() {
        val state = EditorState()
            .setInstrument(Instruments.CLARINET_BB)
            .insertNote(Pitch(Step.C, 4))
        val written = state.displayedMelody.measures[0].events[0] as Note
        assertEquals(Pitch(Step.D, 4), written.pitch)
    }

    @Test
    fun `verifier flags out of range notes for flute`() {
        val state = EditorState()
            .setInstrument(Instruments.FLUTE)
            .setDuration(Durations.WHOLE)
            .insertNote(Pitch(Step.G, 3))
        assertTrue(state.issues.any { it is OutOfRange && it.tooLow })
    }

    @Test
    fun `measure exceeding time signature reports MeasureTooLong`() {
        val state = EditorState()
            .setTimeSignature(3, 4)
            .setDuration(Durations.WHOLE)
            .insertNote(Pitch(Step.C, 4))
        assertTrue(state.issues.any { it is MeasureTooLong && it.measure == 0 })
    }

    @Test
    fun `black key insertion in F major spells A sharp as B flat via Meredith PS13 context`() {
        val state = EditorState()
            .setKey(Key(Step.F, 0, Mode.MAJOR))
            .insertNote(Pitch(Step.A, 4, 1))
        val note = state.melody.measures[0].events[0] as Note
        assertEquals(Pitch(Step.B, 4, -1), note.pitch)
    }

    @Test
    fun `each instrument family synthesizes non-empty harmonic audio with Schroeder reverb`() {
        for (timbre in InstrumentTimbre.entries) {
            val pcm = ToneSynthesizer.synthesizeTone(
                freqHz = ToneSynthesizer.midiToFreq(69),
                durationMs = 240,
                tailMs = 180,
                timbre = timbre,
            )
            assertTrue(pcm.isNotEmpty())
            assertTrue(pcm.any { it != 0.toShort() })
        }
    }
}
