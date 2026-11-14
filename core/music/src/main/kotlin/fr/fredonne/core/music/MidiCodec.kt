package fr.fredonne.core.music

import kotlin.math.abs

/**
 * Lecture et écriture de fichiers Standard MIDI File (SMF Format 0, `.mid`).
 *
 * - À l'export : génère un fichier binaire `.mid` conforme à la spécification MIDI 1.0
 *   (en-tête `MThd` à 480 ticks par noire, piste `MTrk` avec méta-événements de tempo,
 *   chiffrage, armure, changement de programme General MIDI selon l'instrument, et événements
 *   `NoteOn` / `NoteOff` en sons réels).
 * - À l'import : décode les pistes MIDI, extrait le tempo, l'armure et le chiffrage, quantifie
 *   les durées en valeurs rythmiques exactes ([Fraction]) et orthographie les hauteurs MIDI
 *   selon la tonalité et le contexte mélodique ([MusicTheory.spellPitch]).
 */
object MidiCodec {

    private const val TICKS_PER_QUARTER = 480

    fun export(
        melody: Melody,
        bpm: Int = 96,
        instrument: Instrument? = null,
    ): ByteArray {
        val track = mutableListOf<Byte>()

        fun writeVarLen(value: Int) {
            var v = value.coerceAtLeast(0)
            val bytes = IntArray(5)
            var count = 0
            bytes[count++] = v and 0x7F
            v = v ushr 7
            while (v > 0) {
                bytes[count++] = (v and 0x7F) or 0x80
                v = v ushr 7
            }
            for (i in (count - 1) downTo 0) {
                track += bytes[i].toByte()
            }
        }

        // 1. Méta-événement Tempo (FF 51 03 tt tt tt)
        val microsPerQuarter = (60_000_000 / bpm.coerceIn(20, 300))
        writeVarLen(0)
        track += 0xFF.toByte()
        track += 0x51.toByte()
        track += 0x03.toByte()
        track += ((microsPerQuarter ushr 16) and 0xFF).toByte()
        track += ((microsPerQuarter ushr 8) and 0xFF).toByte()
        track += (microsPerQuarter and 0xFF).toByte()

        // 2. Méta-événement Chiffrage de mesure (FF 58 04 nn dd cc bb)
        val dd = when (melody.time.unit) {
            1 -> 0
            2 -> 1
            4 -> 2
            8 -> 3
            16 -> 4
            32 -> 5
            64 -> 6
            else -> 2
        }
        writeVarLen(0)
        track += 0xFF.toByte()
        track += 0x58.toByte()
        track += 0x04.toByte()
        track += melody.time.beats.toByte()
        track += dd.toByte()
        track += 24.toByte()
        track += 8.toByte()

        // 3. Méta-événement Armure (FF 59 02 sf mi)
        val practicalKey = MusicTheory.practicalEnharmonicKey(melody.key)
        writeVarLen(0)
        track += 0xFF.toByte()
        track += 0x59.toByte()
        track += 0x02.toByte()
        track += practicalKey.fifths.coerceIn(-7, 7).toByte()
        track += (if (practicalKey.mode == Mode.MINOR) 1 else 0).toByte()

        // 4. Changement de programme General MIDI (C0 pp)
        writeVarLen(0)
        track += 0xC0.toByte()
        track += generalMidiProgram(instrument).toByte()

        // 5. Événements de notes et silences (1 ronde = 4 * TICKS_PER_QUARTER)
        val wholeTicks = 4L * TICKS_PER_QUARTER
        var pendingRestTicks = 0

        for (measure in melody.measures) {
            for (event in measure.events) {
                val eventTicks = ((event.length.numerator * wholeTicks) / event.length.denominator)
                    .toInt()
                    .coerceAtLeast(1)
                when (event) {
                    is Note -> {
                        val midi = event.pitch.midi.coerceIn(0, 127)
                        writeVarLen(pendingRestTicks)
                        pendingRestTicks = 0
                        track += 0x90.toByte()
                        track += midi.toByte()
                        track += 88.toByte() // Vélocité naturelle

                        writeVarLen(eventTicks)
                        track += 0x80.toByte()
                        track += midi.toByte()
                        track += 0.toByte()
                    }
                    is Rest -> {
                        pendingRestTicks += eventTicks
                    }
                }
            }
        }

        // 6. Fin de piste (FF 2F 00)
        writeVarLen(pendingRestTicks)
        track += 0xFF.toByte()
        track += 0x2F.toByte()
        track += 0x00.toByte()

        // Construction du fichier complet : MThd (14 octets) + MTrk (8 + track.size octets)
        val out = ByteArray(14 + 8 + track.size)
        var pos = 0

        fun writeAscii(s: String) {
            for (ch in s) out[pos++] = ch.code.toByte()
        }

        fun writeInt16BE(v: Int) {
            out[pos++] = ((v ushr 8) and 0xFF).toByte()
            out[pos++] = (v and 0xFF).toByte()
        }

        fun writeInt32BE(v: Int) {
            out[pos++] = ((v ushr 24) and 0xFF).toByte()
            out[pos++] = ((v ushr 16) and 0xFF).toByte()
            out[pos++] = ((v ushr 8) and 0xFF).toByte()
            out[pos++] = (v and 0xFF).toByte()
        }

        writeAscii("MThd")
        writeInt32BE(6)
        writeInt16BE(0) // Format 0 (une piste)
        writeInt16BE(1) // 1 piste
        writeInt16BE(TICKS_PER_QUARTER)

        writeAscii("MTrk")
        writeInt32BE(track.size)
        for (b in track) {
            out[pos++] = b
        }

        return out
    }

    /**
     * Lit un fichier Standard MIDI File (`.mid`) et renvoie la [Melody] reconstruite
     * ainsi que le tempo en BPM.
     */
    fun import(bytes: ByteArray): Pair<Melody, Int> {
        require(bytes.size >= 14 &&
            bytes[0] == 'M'.code.toByte() &&
            bytes[1] == 'T'.code.toByte() &&
            bytes[2] == 'h'.code.toByte() &&
            bytes[3] == 'd'.code.toByte()
        ) {
            "En-tête MIDI MThd invalide"
        }

        fun readUInt16BE(offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

        fun readInt32BE(offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

        val headerLen = readInt32BE(4)
        val division = readUInt16BE(12).coerceAtLeast(1)

        var bpm = 96
        var beats = 4
        var unit = 4
        var explicitKey: Key? = null

        data class RawNote(val startTick: Long, val endTick: Long, val midi: Int)
        val rawNotes = mutableListOf<RawNote>()

        var offset = 8 + headerLen
        while (offset + 8 <= bytes.size) {
            val isMTrk = bytes[offset] == 'M'.code.toByte() &&
                bytes[offset + 1] == 'T'.code.toByte() &&
                bytes[offset + 2] == 'r'.code.toByte() &&
                bytes[offset + 3] == 'k'.code.toByte()
            val chunkLen = readInt32BE(offset + 4).coerceAtLeast(0)
            val chunkEnd = (offset + 8 + chunkLen).coerceAtMost(bytes.size)

            if (isMTrk) {
                var p = offset + 8
                var currentTick = 0L
                var runningStatus = 0
                val activeNoteStart = HashMap<Int, Long>()

                fun readVarLen(): Long {
                    var result = 0L
                    while (p < chunkEnd) {
                        val b = bytes[p++].toInt() and 0xFF
                        result = (result shl 7) or (b and 0x7F).toLong()
                        if ((b and 0x80) == 0) break
                    }
                    return result
                }

                while (p < chunkEnd) {
                    currentTick += readVarLen()
                    if (p >= chunkEnd) break

                    val firstByte = bytes[p].toInt() and 0xFF
                    val status: Int
                    if (firstByte >= 0x80) {
                        status = firstByte
                        p++
                        if (status < 0xF0) runningStatus = status
                    } else {
                        status = runningStatus
                    }

                    when {
                        status == 0xFF -> {
                            if (p >= chunkEnd) break
                            val metaType = bytes[p++].toInt() and 0xFF
                            val metaLen = readVarLen().toInt()
                            val metaEnd = (p + metaLen).coerceAtMost(chunkEnd)
                            if (metaType == 0x51 && metaLen == 3 && p + 3 <= chunkEnd) {
                                val micros = ((bytes[p].toInt() and 0xFF) shl 16) or
                                    ((bytes[p + 1].toInt() and 0xFF) shl 8) or
                                    (bytes[p + 2].toInt() and 0xFF)
                                if (micros > 0) bpm = (60_000_000.0 / micros + 0.5).toInt().coerceIn(30, 240)
                            } else if (metaType == 0x58 && metaLen >= 2 && p + 2 <= chunkEnd) {
                                beats = (bytes[p].toInt() and 0xFF).coerceAtLeast(1)
                                val dd = (bytes[p + 1].toInt() and 0xFF).coerceIn(0, 6)
                                unit = 1 shl dd
                            } else if (metaType == 0x59 && metaLen >= 2 && p + 2 <= chunkEnd) {
                                val sf = bytes[p].toInt() // signé
                                val mi = bytes[p + 1].toInt() and 0xFF
                                explicitKey = Key.fromFifths(sf.coerceIn(-7, 7), if (mi == 1) Mode.MINOR else Mode.MAJOR)
                            }
                            p = metaEnd
                        }
                        status == 0xF0 || status == 0xF7 -> {
                            val sysexLen = readVarLen().toInt()
                            p = (p + sysexLen).coerceAtMost(chunkEnd)
                        }
                        (status and 0xF0) == 0x90 -> {
                            if (p + 2 > chunkEnd) break
                            val note = bytes[p++].toInt() and 0x7F
                            val vel = bytes[p++].toInt() and 0x7F
                            if (vel > 0) {
                                activeNoteStart[note] = currentTick
                            } else {
                                val start = activeNoteStart.remove(note)
                                if (start != null && currentTick > start) {
                                    rawNotes += RawNote(start, currentTick, note)
                                }
                            }
                        }
                        (status and 0xF0) == 0x80 -> {
                            if (p + 2 > chunkEnd) break
                            val note = bytes[p++].toInt() and 0x7F
                            p++ // velocity
                            val start = activeNoteStart.remove(note)
                            if (start != null && currentTick > start) {
                                rawNotes += RawNote(start, currentTick, note)
                            }
                        }
                        (status and 0xF0) in listOf(0xA0, 0xB0, 0xE0) -> {
                            p = (p + 2).coerceAtMost(chunkEnd)
                        }
                        (status and 0xF0) in listOf(0xC0, 0xD0) -> {
                            p = (p + 1).coerceAtMost(chunkEnd)
                        }
                        else -> {
                            p++
                        }
                    }
                }
            }
            offset = chunkEnd
        }

        val sortedNotes = rawNotes.sortedBy { it.startTick }
        val wholeTicks = division.toDouble() * 4.0

        // Reconstruction de la séquence d'événements monophoniques (notes + silences intercalaires)
        data class UnspelledEvent(val midi: Int?, val written: Fraction, val tuplet: Tuplet?)
        val unspelled = mutableListOf<UnspelledEvent>()
        var cursorTick = 0L

        for (rn in sortedNotes) {
            if (rn.startTick > cursorTick) {
                val gapRatio = (rn.startTick - cursorTick).toDouble() / wholeTicks
                if (gapRatio >= 1.0 / 48.0) {
                    val (w, tup) = quantizeDuration(gapRatio)
                    unspelled += UnspelledEvent(null, w, tup)
                }
            }
            val start = maxOf(rn.startTick, cursorTick)
            val durTicks = (rn.endTick - start).coerceAtLeast(division / 8L)
            val durRatio = durTicks.toDouble() / wholeTicks
            val (w, tup) = quantizeDuration(durRatio)
            unspelled += UnspelledEvent(rn.midi, w, tup)
            cursorTick = maxOf(cursorTick, rn.endTick)
        }

        // Estimation de la tonalité si aucun méta-événement d'armure n'était présent
        val tempKey = explicitKey ?: run {
            val roughNotes = unspelled.mapNotNull { u ->
                u.midi?.let { Note(MusicTheory.spellPitch(it, Key(Step.C, 0, Mode.MAJOR)), u.written, u.tuplet) }
            }
            MusicTheory.detectKey(Melody(Key(Step.C, 0, Mode.MAJOR), TimeSignature(beats, unit), listOf(Measure(roughNotes))))
        }

        // Orthographe enharmonique avec contexte PS13
        val midiIndices = unspelled.mapIndexedNotNull { idx, u -> if (u.midi != null) idx else null }
        val spelledPitches = HashMap<Int, Pitch>()
        for (k in midiIndices.indices) {
            val idx = midiIndices[k]
            val midi = unspelled[idx].midi!!
            val prev = if (k > 0) unspelled[midiIndices[k - 1]].midi else null
            val next = if (k + 1 < midiIndices.size) unspelled[midiIndices[k + 1]].midi else null
            spelledPitches[idx] = MusicTheory.spellPitch(midi, tempKey, prev, next)
        }

        val allEvents = unspelled.mapIndexed { idx, u ->
            if (u.midi != null) {
                Note(spelledPitches[idx]!!, u.written, u.tuplet)
            } else {
                Rest(u.written, u.tuplet)
            }
        }

        // Découpage en mesures selon le chiffrage
        val timeSig = TimeSignature(beats, unit)
        val measureMax = timeSig.measureLength
        val measures = mutableListOf<Measure>()
        var currentMeasureEvents = mutableListOf<Event>()
        var currentMeasureSum = Fraction.ZERO

        for (ev in allEvents) {
            if (currentMeasureSum + ev.length > measureMax && currentMeasureEvents.isNotEmpty()) {
                measures += Measure(currentMeasureEvents)
                currentMeasureEvents = mutableListOf(ev)
                currentMeasureSum = ev.length
            } else {
                currentMeasureEvents += ev
                currentMeasureSum += ev.length
                if (currentMeasureSum >= measureMax) {
                    measures += Measure(currentMeasureEvents)
                    currentMeasureEvents = mutableListOf()
                    currentMeasureSum = Fraction.ZERO
                }
            }
        }
        if (currentMeasureEvents.isNotEmpty() || measures.isEmpty()) {
            measures += Measure(currentMeasureEvents)
        }

        return Melody(tempKey, timeSig, measures) to bpm
    }

    private fun quantizeDuration(wholeRatio: Double): Pair<Fraction, Tuplet?> {
        val triplet = Tuplet(3, 2)
        val candidates = listOf<Pair<Fraction, Tuplet?>>(
            Durations.WHOLE to null,
            Durations.dotted(Durations.HALF) to null,
            Durations.HALF to null,
            Durations.dotted(Durations.QUARTER) to null,
            Durations.QUARTER to null,
            Durations.dotted(Durations.EIGHTH) to null,
            Durations.EIGHTH to null,
            Durations.EIGHTH to triplet,
            Durations.dotted(Durations.SIXTEENTH) to null,
            Durations.SIXTEENTH to null,
            Durations.SIXTEENTH to triplet,
            Durations.THIRTY_SECOND to null,
        )
        return candidates.minByOrNull { (w, tup) ->
            val realLen = tup?.let { (w * it.factor).toDouble() } ?: w.toDouble()
            abs(realLen - wholeRatio)
        } ?: (Durations.QUARTER to null)
    }

    private fun generalMidiProgram(instrument: Instrument?): Int = when (instrument?.id) {
        "flute" -> 73
        "oboe" -> 68
        "clarinet_bb" -> 71
        "sax_soprano" -> 64
        "sax_alto" -> 65
        "sax_tenor" -> 66
        "sax_baritone" -> 67
        "trumpet_bb" -> 56
        "horn_f" -> 60
        "trombone" -> 57
        "violin" -> 40
        "guitar" -> 24
        "voice_soprano", "voice_alto", "voice_tenor", "voice_bass" -> 52
        else -> 0 // Acoustic Grand Piano
    }
}
