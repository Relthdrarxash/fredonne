package fr.fredonne.app

import fr.fredonne.core.music.Instrument
import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Note
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Familles de timbres acoustiques synthétisés hors-ligne par modèles spectraux et guides d'ondes simplifiés.
 */
enum class InstrumentTimbre {
    WARM_KEYS,
    FLUTE,
    REED,
    BRASS,
    STRING,
}

data class MelodyEventTiming(
    val measureIndex: Int,
    val eventIndex: Int,
    val startMs: Long,
)

object ToneSynthesizer {
    const val SAMPLE_RATE = 44_100

    fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun timbreForInstrument(instrument: Instrument?): InstrumentTimbre = when (instrument?.id) {
        "flute" -> InstrumentTimbre.FLUTE
        "oboe", "clarinet_bb", "sax_soprano", "sax_alto", "sax_tenor", "sax_baritone" -> InstrumentTimbre.REED
        "trumpet_bb", "horn_f", "trombone" -> InstrumentTimbre.BRASS
        "violin", "guitar" -> InstrumentTimbre.STRING
        else -> InstrumentTimbre.WARM_KEYS
    }

    /**
     * Pondération psychoacoustique inspirée des courbes isosoniques ISO 226:2023 :
     * soutient légèrement le registre grave (< 220 Hz) et adoucit le pic de sensibilité
     * du conduit auditif autour de 2,5–3,8 kHz pour éviter toute dureté.
     */
    fun iso226Gain(freqHz: Double): Double {
        val f = freqHz.coerceIn(55.0, 4200.0)
        val lowBoost = if (f < 240.0) {
            1.0 + 0.22 * ((240.0 - f) / 185.0).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        // Creux doux autour de 3000 Hz (résonance du canal auditif)
        val logRatio = ln(f / 3000.0)
        val earCanalNotch = 1.0 - 0.28 * exp(-(logRatio * logRatio) / 0.32)
        return (lowBoost * earCanalNotch).coerceIn(0.65, 1.25)
    }

    fun synthesizeTone(
        freqHz: Double,
        durationMs: Int,
        tailMs: Int = 360,
        timbre: InstrumentTimbre = InstrumentTimbre.WARM_KEYS,
    ): ShortArray {
        val dry = synthesizeNoteFloat(freqHz, durationMs, tailMs, timbre)
        val wet = RoomReverb(SAMPLE_RATE).process(dry)
        return toPcm16(wet)
    }

    /**
     * Synthétise une note harmonique selon la famille acoustique [timbre] :
     * - [InstrumentTimbre.WARM_KEYS] : piano feutré à double corde légèrement désaccordée (+0,12 %) et inharmonicité de raideur
     * - [InstrumentTimbre.FLUTE] : colonne d'air ouverte, fondamentale dominante, léger vibrato à 5,2 Hz
     * - [InstrumentTimbre.REED] : tuyau à anche avec prédominance des harmoniques impairs (1, 3, 5)
     * - [InstrumentTimbre.BRASS] : cuivre avec ouverture spectrale progressive à l'attaque (effet de brillance)
     * - [InstrumentTimbre.STRING] : corde frottée/pincée avec richesse harmonique et chorus naturel
     */
    fun synthesizeNoteFloat(
        freqHz: Double,
        durationMs: Int,
        tailMs: Int,
        timbre: InstrumentTimbre = InstrumentTimbre.WARM_KEYS,
    ): FloatArray {
        val activeCount = (SAMPLE_RATE * durationMs) / 1000
        val tailCount = (SAMPLE_RATE * tailMs) / 1000
        val totalCount = activeCount + tailCount
        if (totalCount <= 0) return FloatArray(0)

        val out = FloatArray(totalCount)
        val brightness = (440.0 / freqHz.coerceAtLeast(80.0)).pow(0.45).coerceIn(0.35, 1.35)
        val isoGain = iso226Gain(freqHz)

        val attackSec = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 0.012
            InstrumentTimbre.FLUTE -> 0.032
            InstrumentTimbre.REED -> 0.022
            InstrumentTimbre.BRASS -> 0.028
            InstrumentTimbre.STRING -> 0.025
        }
        val attackSamples = (SAMPLE_RATE * attackSec).toInt().coerceAtLeast(1)

        val inharmonicity = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 0.00018
            InstrumentTimbre.STRING -> 0.00022
            else -> 0.00003
        }

        val harmonicAmps = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> doubleArrayOf(
                1.00,
                0.42 * brightness,
                0.20 * brightness,
                0.10 * brightness * brightness,
                0.05 * brightness * brightness,
                0.025 * brightness * brightness,
            )
            InstrumentTimbre.FLUTE -> doubleArrayOf(
                1.00,
                0.22 * brightness,
                0.07 * brightness,
                0.02 * brightness,
                0.008 * brightness,
                0.0,
            )
            InstrumentTimbre.REED -> doubleArrayOf(
                1.00,
                0.18 * brightness,
                0.52 * brightness,
                0.12 * brightness * brightness,
                0.26 * brightness * brightness,
                0.06 * brightness * brightness,
            )
            InstrumentTimbre.BRASS -> doubleArrayOf(
                0.90,
                0.65 * brightness,
                0.45 * brightness,
                0.28 * brightness * brightness,
                0.16 * brightness * brightness,
                0.09 * brightness * brightness,
            )
            InstrumentTimbre.STRING -> doubleArrayOf(
                1.00,
                0.50 * brightness,
                0.33 * brightness,
                0.22 * brightness * brightness,
                0.14 * brightness * brightness,
                0.08 * brightness * brightness,
            )
        }

        val harmonicDecays = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> doubleArrayOf(1.6, 2.8, 4.4, 6.5, 9.0, 12.0)
            InstrumentTimbre.FLUTE -> doubleArrayOf(0.55, 1.1, 1.8, 2.6, 3.5, 4.5)
            InstrumentTimbre.REED -> doubleArrayOf(0.65, 1.3, 1.1, 2.4, 1.9, 3.8)
            InstrumentTimbre.BRASS -> doubleArrayOf(0.75, 1.1, 1.5, 2.1, 3.0, 4.2)
            InstrumentTimbre.STRING -> doubleArrayOf(0.95, 1.6, 2.3, 3.2, 4.5, 6.0)
        }

        val hasVibrato = timbre == InstrumentTimbre.FLUTE || timbre == InstrumentTimbre.STRING || timbre == InstrumentTimbre.REED

        for (i in 0 until totalCount) {
            val t = i.toDouble() / SAMPLE_RATE
            // Vibrato progressif léger (5,2 Hz) après 120 ms pour les instruments à vent/archet
            val vibratoOffset = if (hasVibrato && t > 0.08) {
                val vibDepth = ((t - 0.08) / 0.18).coerceIn(0.0, 1.0) * 0.0022
                vibDepth * sin(2.0 * PI * 5.2 * t)
            } else {
                0.0
            }
            val effFreq = freqHz * (1.0 + vibratoOffset)
            val twoPiT = 2.0 * PI * t

            var sum = 0.68 * sin(twoPiT * effFreq) * exp(-harmonicDecays[0] * t) +
                0.32 * sin(twoPiT * effFreq * 1.0012) * exp(-harmonicDecays[0] * t)

            // Pour les cuivres, les partiels supérieurs s'ouvrent légèrement après la fondamentale (spectral bloom)
            val brassBloom = if (timbre == InstrumentTimbre.BRASS) {
                (t / 0.045).coerceIn(0.25, 1.0)
            } else {
                1.0
            }

            for (h in 1 until harmonicAmps.size) {
                val n = h + 1
                val partialFreq = effFreq * n * sqrt(1.0 + inharmonicity * n * n)
                if (partialFreq < SAMPLE_RATE * 0.42) {
                    val partialGain = harmonicAmps[h] * iso226Gain(partialFreq) * brassBloom
                    sum += partialGain * sin(twoPiT * partialFreq) * exp(-harmonicDecays[h] * t)
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

            out[i] = (sum * 0.34 * isoGain * attackEnv * releaseEnv).toFloat()
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

    fun synthesizeMelody(
        melody: Melody,
        bpm: Int,
        timbre: InstrumentTimbre = InstrumentTimbre.WARM_KEYS,
    ): Pair<ShortArray, List<MelodyEventTiming>> {
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
                    is Note -> synthesizeNoteFloat(
                        freqHz = midiToFreq(event.pitch.midi),
                        durationMs = eventMs,
                        tailMs = releaseTailMs,
                        timbre = timbre,
                    )
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

    /**
     * Réverbérateur de Schroeder (1962) / Moorer (1979) :
     * 4 filtres en peigne passe-bas en parallèle suivis de 2 filtres passe-tout (all-pass) en série
     * pour diffuser les réflexions sans écho métallique.
     */
    internal class RoomReverb(sampleRate: Int) {
        private val combDelays = intArrayOf(
            (sampleRate * 0.023).toInt().coerceAtLeast(1),
            (sampleRate * 0.031).toInt().coerceAtLeast(1),
            (sampleRate * 0.041).toInt().coerceAtLeast(1),
            (sampleRate * 0.053).toInt().coerceAtLeast(1),
        )
        private val combBuffers = Array(combDelays.size) { FloatArray(combDelays[it]) }
        private val combIndices = IntArray(combDelays.size)
        private val lpState = FloatArray(combDelays.size)
        private val feedback = 0.38f
        private val damping = 0.45f

        private val allPassDelays = intArrayOf(
            (sampleRate * 0.0050).toInt().coerceAtLeast(1),
            (sampleRate * 0.0017).toInt().coerceAtLeast(1),
        )
        private val allPassBuffers = Array(allPassDelays.size) { FloatArray(allPassDelays[it]) }
        private val allPassIndices = IntArray(allPassDelays.size)
        private val allPassGain = 0.5f

        private val wetMix = 0.20f

        fun process(input: FloatArray): FloatArray {
            val out = FloatArray(input.size)
            for (i in input.indices) {
                val dry = input[i]
                var wet = 0f
                for (c in combBuffers.indices) {
                    val buf = combBuffers[c]
                    val idx = combIndices[c]
                    val delayed = buf[idx]
                    val filtered = delayed * (1f - damping) + lpState[c] * damping
                    lpState[c] = filtered
                    buf[idx] = dry + filtered * feedback
                    combIndices[c] = (idx + 1) % buf.size
                    wet += delayed
                }
                var diffused = wet * 0.25f
                for (a in allPassBuffers.indices) {
                    val apBuf = allPassBuffers[a]
                    val apIdx = allPassIndices[a]
                    val bufOut = apBuf[apIdx]
                    val inputPlusFb = diffused + bufOut * allPassGain
                    apBuf[apIdx] = inputPlusFb
                    allPassIndices[a] = (apIdx + 1) % apBuf.size
                    diffused = bufOut - inputPlusFb * allPassGain
                }
                out[i] = dry * (1f - wetMix * 0.4f) + diffused * wetMix
            }
            return out
        }
    }
}
