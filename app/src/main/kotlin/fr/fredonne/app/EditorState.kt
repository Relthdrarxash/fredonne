package fr.fredonne.app

import fr.fredonne.core.music.Direction
import fr.fredonne.core.music.Durations
import fr.fredonne.core.music.Event
import fr.fredonne.core.music.Fraction
import fr.fredonne.core.music.Instrument
import fr.fredonne.core.music.Interval
import fr.fredonne.core.music.Issue
import fr.fredonne.core.music.Key
import fr.fredonne.core.music.Measure
import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Mode
import fr.fredonne.core.music.Note
import fr.fredonne.core.music.Pitch
import fr.fredonne.core.music.Rest
import fr.fredonne.core.music.Step
import fr.fredonne.core.music.TimeSignature
import fr.fredonne.core.music.Verifier
import fr.fredonne.core.music.transposed

data class EditorSnapshot(
    val melody: Melody,
    val cursorMeasure: Int,
    val cursorEventIndex: Int,
)

data class EditorState(
    val melody: Melody = defaultMelody(),
    val instrument: Instrument? = null,
    val selectedDuration: Fraction = Durations.QUARTER,
    val dotted: Boolean = false,
    val cursorMeasure: Int = 0,
    val cursorEventIndex: Int = 0,
    val tempoBpm: Int = 96,
    val undoStack: List<EditorSnapshot> = emptyList(),
    val redoStack: List<EditorSnapshot> = emptyList(),
) {
    val effectiveDuration: Fraction
        get() = if (dotted) Durations.dotted(selectedDuration) else selectedDuration

    val issues: List<Issue>
        get() = Verifier.check(melody, instrument = instrument)

    /** Mélodie écrite pour l'instrument sélectionné (transposée si instrument transpositeur). */
    val displayedMelody: Melody
        get() = if (instrument == null || instrument.transposition == Interval.UNISON) {
            melody
        } else {
            melody.transposed(-instrument.transposition)
        }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun pushUndo(): EditorState {
        val snapshot = EditorSnapshot(melody, cursorMeasure, cursorEventIndex)
        return copy(
            undoStack = (undoStack + snapshot).takeLast(50),
            redoStack = emptyList(),
        )
    }

    fun insertNote(soundingPitch: Pitch): EditorState = insertEvent(Note(soundingPitch, effectiveDuration))

    fun insertRest(): EditorState = insertEvent(Rest(effectiveDuration))

    private fun insertEvent(event: Event): EditorState {
        val base = pushUndo()
        val measures = base.melody.measures.toMutableList()
        var targetMeasure = base.cursorMeasure.coerceIn(0, (measures.size - 1).coerceAtLeast(0))
        if (measures.isEmpty()) {
            measures += Measure(emptyList())
            targetMeasure = 0
        }

        val currentMeasure = measures[targetMeasure]
        val currentTotal = currentMeasure.events.fold(Fraction.ZERO) { acc, e -> acc + e.length }
        val measureFull = base.melody.time.measureLength
        val insertIndex = base.cursorEventIndex.coerceIn(0, currentMeasure.events.size)

        // Si on insère en fin de mesure déjà pleine, on passe automatiquement à la mesure suivante.
        return if (insertIndex == currentMeasure.events.size &&
            currentTotal + event.length > measureFull &&
            currentTotal >= measureFull
        ) {
            val nextIndex = targetMeasure + 1
            if (nextIndex >= measures.size) {
                measures += Measure(listOf(event))
                base.copy(
                    melody = base.melody.copy(measures = measures),
                    cursorMeasure = nextIndex,
                    cursorEventIndex = 1,
                )
            } else {
                val nextMeasure = measures[nextIndex]
                measures[nextIndex] = Measure(listOf(event) + nextMeasure.events)
                base.copy(
                    melody = base.melody.copy(measures = measures),
                    cursorMeasure = nextIndex,
                    cursorEventIndex = 1,
                )
            }
        } else {
            val updatedEvents = currentMeasure.events.toMutableList().apply {
                add(insertIndex, event)
            }
            measures[targetMeasure] = Measure(updatedEvents)
            base.copy(
                melody = base.melody.copy(measures = measures),
                cursorMeasure = targetMeasure,
                cursorEventIndex = insertIndex + 1,
            )
        }
    }

    fun deleteAtCursor(): EditorState {
        if (melody.measures.isEmpty()) return this
        val mIdx = cursorMeasure.coerceIn(0, melody.measures.lastIndex)
        val events = melody.measures[mIdx].events
        return when {
            cursorEventIndex > 0 && events.isNotEmpty() -> {
                val base = pushUndo()
                val removeIdx = (cursorEventIndex - 1).coerceIn(0, events.lastIndex)
                val updated = events.toMutableList().apply { removeAt(removeIdx) }
                val measures = base.melody.measures.toMutableList().apply {
                    this[mIdx] = Measure(updated)
                }
                base.copy(
                    melody = base.melody.copy(measures = measures),
                    cursorMeasure = mIdx,
                    cursorEventIndex = removeIdx,
                )
            }
            mIdx > 0 -> {
                val base = pushUndo()
                val measures = base.melody.measures.toMutableList()
                if (events.isEmpty() && measures.size > 1) {
                    measures.removeAt(mIdx)
                }
                val prevIdx = mIdx - 1
                base.copy(
                    melody = base.melody.copy(measures = measures),
                    cursorMeasure = prevIdx,
                    cursorEventIndex = measures[prevIdx].events.size,
                )
            }
            else -> this
        }
    }

    fun addMeasure(): EditorState {
        val base = pushUndo()
        val measures = base.melody.measures + Measure(emptyList())
        return base.copy(
            melody = base.melody.copy(measures = measures),
            cursorMeasure = measures.lastIndex,
            cursorEventIndex = 0,
        )
    }

    fun setCursor(measureIndex: Int, eventIndex: Int): EditorState {
        if (melody.measures.isEmpty()) return this
        val m = measureIndex.coerceIn(0, melody.measures.lastIndex)
        val e = eventIndex.coerceIn(0, melody.measures[m].events.size)
        return copy(cursorMeasure = m, cursorEventIndex = e)
    }

    fun moveCursorLeft(): EditorState {
        if (melody.measures.isEmpty()) return this
        return if (cursorEventIndex > 0) {
            copy(cursorEventIndex = cursorEventIndex - 1)
        } else if (cursorMeasure > 0) {
            val prev = cursorMeasure - 1
            copy(cursorMeasure = prev, cursorEventIndex = melody.measures[prev].events.size)
        } else {
            this
        }
    }

    fun moveCursorRight(): EditorState {
        if (melody.measures.isEmpty()) return this
        val currentSize = melody.measures[cursorMeasure].events.size
        return if (cursorEventIndex < currentSize) {
            copy(cursorEventIndex = cursorEventIndex + 1)
        } else if (cursorMeasure < melody.measures.lastIndex) {
            copy(cursorMeasure = cursorMeasure + 1, cursorEventIndex = 0)
        } else {
            this
        }
    }

    fun setDuration(duration: Fraction): EditorState = copy(selectedDuration = duration)

    fun toggleDotted(): EditorState = copy(dotted = !dotted)

    fun setTimeSignature(beats: Int, unit: Int): EditorState {
        val base = pushUndo()
        return base.copy(melody = base.melody.copy(time = TimeSignature(beats, unit)))
    }

    fun setKey(key: Key): EditorState {
        val base = pushUndo()
        return base.copy(melody = base.melody.copy(key = key))
    }

    fun transposeToKey(targetStep: Step, targetAlter: Int, direction: Direction = Direction.NEAREST): EditorState {
        val interval = Key.intervalBetweenTonics(melody.key, targetStep, targetAlter, direction)
        if (interval == Interval.UNISON) return this
        val base = pushUndo()
        return base.copy(melody = base.melody.transposed(interval))
    }

    fun transposeOctave(up: Boolean): EditorState {
        val base = pushUndo()
        val interval = if (up) Interval.OCTAVE else -Interval.OCTAVE
        return base.copy(melody = base.melody.transposed(interval))
    }

    fun setInstrument(instrument: Instrument?): EditorState = copy(instrument = instrument)

    fun setTempo(bpm: Int): EditorState = copy(tempoBpm = bpm.coerceIn(40, 200))

    fun undo(): EditorState {
        if (undoStack.isEmpty()) return this
        val previous = undoStack.last()
        val currentSnapshot = EditorSnapshot(melody, cursorMeasure, cursorEventIndex)
        return copy(
            melody = previous.melody,
            cursorMeasure = previous.cursorMeasure,
            cursorEventIndex = previous.cursorEventIndex,
            undoStack = undoStack.dropLast(1),
            redoStack = (redoStack + currentSnapshot).takeLast(50),
        )
    }

    fun redo(): EditorState {
        if (redoStack.isEmpty()) return this
        val next = redoStack.last()
        val currentSnapshot = EditorSnapshot(melody, cursorMeasure, cursorEventIndex)
        return copy(
            melody = next.melody,
            cursorMeasure = next.cursorMeasure,
            cursorEventIndex = next.cursorEventIndex,
            undoStack = (undoStack + currentSnapshot).takeLast(50),
            redoStack = redoStack.dropLast(1),
        )
    }

    fun clearAll(): EditorState {
        val base = pushUndo()
        return base.copy(
            melody = Melody(melody.key, melody.time, listOf(Measure(emptyList()))),
            cursorMeasure = 0,
            cursorEventIndex = 0,
        )
    }

    fun loadSampleMelody(): EditorState {
        val base = pushUndo()
        return base.copy(
            melody = sampleMelody(),
            cursorMeasure = 1,
            cursorEventIndex = 3,
        )
    }

    companion object {
        fun defaultMelody(): Melody = Melody(
            key = Key(Step.C, 0, Mode.MAJOR),
            time = TimeSignature(4, 4),
            measures = listOf(Measure(emptyList())),
        )

        fun sampleMelody(): Melody = Melody(
            key = Key(Step.C, 0, Mode.MAJOR),
            time = TimeSignature(4, 4),
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.C, 4), Durations.QUARTER),
                        Note(Pitch(Step.D, 4), Durations.QUARTER),
                        Note(Pitch(Step.E, 4), Durations.QUARTER),
                        Note(Pitch(Step.C, 4), Durations.QUARTER),
                    )
                ),
                Measure(
                    listOf(
                        Note(Pitch(Step.E, 4), Durations.QUARTER),
                        Note(Pitch(Step.F, 4), Durations.QUARTER),
                        Note(Pitch(Step.G, 4), Durations.HALF),
                    )
                ),
            ),
        )
    }
}
