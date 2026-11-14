package fr.fredonne.app

import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Note
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

internal data class MelodyEventTiming(
    val measureIndex: Int,
    val eventIndex: Int,
    val startMs: Long,
)

internal object ToneSynthesizer {
    const val SAMPLE_RATE = 44_100

    fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun synthesizeTone(freqHz: Double, durationMs: Int, tailMs: Int = 360): ShortArray {
        val dry = synthesizeNoteFloat(freqHz, durationMs, tailMs)
        val wet = RoomReverb(SAMPLE_RATE).process(dry)
        return toPcm16(wet)
    }

    /**
     * Synthétise une note harmonique chaude (corde feutrée avec légère inharmonicité et double corde)
     * sur [durationMs] + [tailMs] de résonance naturelle.
     */
    fun synthesizeNoteFloat(freqHz: Double, durationMs: Int, tailMs: Int): FloatArray {
        val activeCount = (SAMPLE_RATE * durationMs) / 1000
        val tailCount = (SAMPLE_RATE * tailMs) / 1000
        val totalCount = activeCount + tailCount
        if (totalCount <= 0) return FloatArray(0)

        val out = FloatArray(totalCount)
        val attackSamples = (SAMPLE_RATE * 0.012).toInt().coerceAtLeast(1)
        val brightness = (440.0 / freqHz.coerceAtLeast(80.0)).pow(0.45).coerceIn(0.35, 1.35)
        val inharmonicity = 0.00018

        val harmonicAmps = doubleArrayOf(
            1.00,
            0.42 * brightness,
            0.20 * brightness,
            0.10 * brightness * brightness,
            0.05 * brightness * brightness,
            0.025 * brightness * brightness,
        )
        val harmonicDecays = doubleArrayOf(1.6, 2.8, 4.4, 6.5, 9.0, 12.0)

        for (i in 0 until totalCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val twoPiT = 2.0 * PI * t

            var sum = 0.68 * sin(twoPiT * freqHz) * exp(-harmonicDecays[0] * t) +
                0.32 * sin(twoPiT * freqHz * 1.0012) * exp(-harmonicDecays[0] * t)

            for (h in 1 until harmonicAmps.size) {
                val n = h + 1
                val partialFreq = freqHz * n * sqrt(1.0 + inharmonicity * n * n)
                if (partialFreq < SAMPLE_RATE * 0.42) {
                    sum += harmonicAmps[h] * sin(twoPiT * partialFreq) * exp(-harmonicDecays[h] * t)
                }
            }

            val attackEnv = if (i < attackSamples) {
                val s = sin((i.toDouble() / attackSamples) * (PI / 2.0))
                s * s
            } else {
                1.0
            }
            val releaseEnv = if (i >= activeCount && tailCount > 0) {
                val p = 1.0 - (i - activeCount).toDouble() / tailCount
                val c = p.coerceIn(0.0, 1.0)
                c * c
            } else {
                1.0
            }

            out[i] = (sum * 0.36 * attackEnv * releaseEnv).toFloat()
        }
        return out
    }

    fun toPcm16(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val shaped = tanh(samples[i].toDouble() * 1.15)
            out[i] = (shaped * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    fun synthesizeMelody(melody: Melody, bpm: Int): Pair<ShortArray, List<MelodyEventTiming>> {
        val wholeMs = (4.0 * 60_000.0) / bpm.coerceAtLeast(30)
        val timings = mutableListOf<MelodyEventTiming>()
        val reverb = RoomReverb(SAMPLE_RATE)
        val slices = mutableListOf<FloatArray>()
        var overlapTail = FloatArray(0)
        var elapsedSamples = 0L
        val releaseTailMs = 260

        melody.measures.forEachIndexed { mIdx, measure ->
            measure.events.forEachIndexed { eIdx, event ->
                val eventMs = (event.length.toDouble() * wholeMs).toInt().coerceAtLeast(60)
                val eventCount = (SAMPLE_RATE * eventMs) / 1000
                val startMs = (elapsedSamples * 1000L) / SAMPLE_RATE
                timings += MelodyEventTiming(mIdx, eIdx, startMs)

                val generated = when (event) {
                    is Note -> synthesizeNoteFloat(midiToFreq(event.pitch.midi), eventMs, tailMs = releaseTailMs)
                    else -> FloatArray(eventCount)
                }

                val combined = FloatArray(maxOf(generated.size, overlapTail.size))
                for (i in generated.indices) combined[i] += generated[i]
                for (i in overlapTail.indices) combined[i] += overlapTail[i]

                slices += reverb.process(combined.copyOfRange(0, eventCount))
                overlapTail = if (combined.size > eventCount) {
                    combined.copyOfRange(eventCount, combined.size)
                } else {
                    FloatArray(0)
                }
                elapsedSamples += eventCount
            }
        }

        val endPad = (SAMPLE_RATE * 320) / 1000
        val endMix = FloatArray(maxOf(overlapTail.size, endPad))
        for (i in overlapTail.indices) endMix[i] = overlapTail[i]
        slices += reverb.process(endMix)

        val totalSize = slices.sumOf { it.size }
        val total = FloatArray(totalSize)
        var offset = 0
        for (slice in slices) {
            slice.copyInto(total, destinationOffset = offset)
            offset += slice.size
        }
        return toPcm16(total) to timings
    }

    fun pcm16ToWav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val wav = ByteArray(44 + dataSize)

        fun writeAscii(pos: Int, text: String) {
            for (i in text.indices) wav[pos + i] = text[i].code.toByte()
        }

        fun writeIntLE(pos: Int, value: Int) {
            wav[pos] = (value and 0xFF).toByte()
            wav[pos + 1] = ((value ushr 8) and 0xFF).toByte()
            wav[pos + 2] = ((value ushr 16) and 0xFF).toByte()
            wav[pos + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        fun writeShortLE(pos: Int, value: Int) {
            wav[pos] = (value and 0xFF).toByte()
            wav[pos + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, 36 + dataSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1) // PCM
        writeShortLE(22, 1) // Mono
        writeIntLE(24, sampleRate)
        writeIntLE(28, sampleRate * 2)
        writeShortLE(32, 2)
        writeShortLE(34, 16)
        writeAscii(36, "data")
        writeIntLE(40, dataSize)

        var idx = 44
        for (s in samples) {
            val v = s.toInt()
            wav[idx++] = (v and 0xFF).toByte()
            wav[idx++] = ((v ushr 8) and 0xFF).toByte()
        }
        return wav
    }

    /** Réverbération de salle légère (4 peignes passe-bas en parallèle). */
    internal class RoomReverb(sampleRate: Int) {
        private val delays = intArrayOf(
            (sampleRate * 0.023).toInt(),
            (sampleRate * 0.031).toInt(),
            (sampleRate * 0.041).toInt(),
            (sampleRate * 0.053).toInt(),
        )
        private val buffers = Array(delays.size) { FloatArray(delays[it]) }
        private val indices = IntArray(delays.size)
        private val lpState = FloatArray(delays.size)
        private val feedback = 0.36f
        private val damping = 0.45f
        private val wetMix = 0.18f

        fun process(input: FloatArray): FloatArray {
            val out = FloatArray(input.size)
            for (i in input.indices) {
                val dry = input[i]
                var wet = 0f
                for (c in buffers.indices) {
                    val buf = buffers[c]
                    val idx = indices[c]
                    val delayed = buf[idx]
                    val filtered = delayed * (1f - damping) + lpState[c] * damping
                    lpState[c] = filtered
                    buf[idx] = dry + filtered * feedback
                    indices[c] = (idx + 1) % buf.size
                    wet += delayed
                }
                out[i] = dry * (1f - wetMix * 0.4f) + (wet * 0.25f) * wetMix
            }
            return out
        }
    }
}
