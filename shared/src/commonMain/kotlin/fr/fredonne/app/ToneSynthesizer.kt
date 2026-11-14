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
 * Familles de timbres acoustiques synthétisés hors-ligne :
 * attaque physique (feutre de marteau, souffle d'embouchure, grain d'archet) +
 * spectre harmonique stable sans vibrato LFO artificiel ni filtres en peigne métalliques.
 */
enum class InstrumentTimbre {
    WARM_KEYS,
    FLUTE,
    REED,
    BRASS,
    STRING,
    BASS,
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
        "violin", "guitar", "harp_celtic" -> InstrumentTimbre.STRING
        "bass_electric", "contrabass" -> InstrumentTimbre.BASS
        else -> InstrumentTimbre.WARM_KEYS
    }

    /**
     * Pondération psychoacoustique inspirée des courbes isosoniques ISO 226:2023 :
     * soutient légèrement le registre grave (< 240 Hz) et adoucit la zone de résonance
     * du conduit auditif autour de 3 kHz.
     */
    fun iso226Gain(freqHz: Double): Double {
        val f = freqHz.coerceIn(55.0, 4200.0)
        val lowBoost = if (f < 240.0) {
            1.0 + 0.22 * ((240.0 - f) / 185.0).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val logRatio = ln(f / 3000.0)
        val earCanalNotch = 1.0 - 0.28 * exp(-(logRatio * logRatio) / 0.32)
        return (lowBoost * earCanalNotch).coerceIn(0.65, 1.25)
    }

    /**
     * Résonance de caisse (formants acoustiques fixes) pour donner du corps aux cordes et vents
     * sans aucune oscillation temporelle.
     */
    private fun bodyFormantGain(freqHz: Double, timbre: InstrumentTimbre): Double = when (timbre) {
        InstrumentTimbre.STRING -> {
            val f1 = exp(-((ln(freqHz / 290.0)).let { it * it }) / 0.25) * 0.25
            val f2 = exp(-((ln(freqHz / 540.0)).let { it * it }) / 0.22) * 0.20
            1.0 + f1 + f2
        }
        InstrumentTimbre.REED -> {
            val f1 = exp(-((ln(freqHz / 450.0)).let { it * it }) / 0.30) * 0.20
            val f2 = exp(-((ln(freqHz / 1150.0)).let { it * it }) / 0.25) * 0.15
            1.0 + f1 + f2
        }
        InstrumentTimbre.BRASS -> {
            val f1 = exp(-((ln(freqHz / 680.0)).let { it * it }) / 0.28) * 0.24
            1.0 + f1
        }
        else -> 1.0
    }

    fun synthesizeTone(
        freqHz: Double,
        durationMs: Int,
        tailMs: Int = 240,
        timbre: InstrumentTimbre = InstrumentTimbre.WARM_KEYS,
    ): ShortArray {
        val dry = synthesizeNoteFloat(freqHz, durationMs, tailMs, timbre)
        val wet = RoomReverb(SAMPLE_RATE).process(dry)
        return toPcm16(wet)
    }

    /**
     * Synthétise une note stable à phase exacte (zéro vibrato artificiel, zéro battement d'oscillateurs)
     * avec transitoire d'attaque physique (feutre de marteau ou léger souffle d'embouchure).
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
        val brightness = (440.0 / freqHz.coerceAtLeast(80.0)).pow(0.48).coerceIn(0.32, 1.30)
        val b2 = brightness * brightness
        val isoGain = iso226Gain(freqHz)

        val attackSec = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 0.008
            InstrumentTimbre.FLUTE -> 0.028
            InstrumentTimbre.REED -> 0.018
            InstrumentTimbre.BRASS -> 0.024
            InstrumentTimbre.STRING -> 0.030
            InstrumentTimbre.BASS -> 0.014
        }
        val attackSamples = (SAMPLE_RATE * attackSec).toInt().coerceAtLeast(1)

        val inharmonicity = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 0.00014
            InstrumentTimbre.STRING, InstrumentTimbre.BASS -> 0.00010
            else -> 0.0
        }

        // Profils spectraux mesurés sur échantillons acoustiques CC0 (VSCO 2 CE)
        val harmonicAmps = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> doubleArrayOf(
                1.00,
                0.38 * brightness,
                0.16 * brightness,
                0.075 * b2,
                0.035 * b2,
                0.015 * b2,
                0.006 * b2,
            )
            InstrumentTimbre.FLUTE -> doubleArrayOf(
                1.00,
                0.24 * brightness,
                0.07 * brightness,
                0.02 * brightness,
                0.005 * b2,
                0.0,
                0.0,
            )
            InstrumentTimbre.REED -> doubleArrayOf(
                1.00,
                0.14 * brightness,
                0.48 * brightness,
                0.10 * b2,
                0.22 * b2,
                0.05 * b2,
                0.08 * b2,
            )
            InstrumentTimbre.BRASS -> doubleArrayOf(
                0.92,
                0.58 * brightness,
                0.38 * brightness,
                0.22 * b2,
                0.12 * b2,
                0.06 * b2,
                0.03 * b2,
            )
            InstrumentTimbre.STRING -> doubleArrayOf(
                1.00,
                0.46 * brightness,
                0.28 * brightness,
                0.16 * b2,
                0.09 * b2,
                0.05 * b2,
                0.025 * b2,
            )
            InstrumentTimbre.BASS -> doubleArrayOf(
                1.00,
                0.62 * brightness,
                0.36 * brightness,
                0.18 * b2,
                0.08 * b2,
                0.03 * b2,
                0.01 * b2,
            )
        }

        // Décroissance naturelle : les partiels aigus s'éteignent plus vite que la fondamentale
        val harmonicDecays = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> doubleArrayOf(1.35, 2.6, 4.2, 6.2, 8.8, 11.5, 14.5)
            InstrumentTimbre.FLUTE -> doubleArrayOf(0.35, 0.9, 1.6, 2.4, 3.4, 4.5, 5.5)
            InstrumentTimbre.REED -> doubleArrayOf(0.42, 1.1, 0.9, 2.0, 1.6, 3.2, 2.8)
            InstrumentTimbre.BRASS -> doubleArrayOf(0.50, 0.95, 1.4, 2.0, 2.9, 4.0, 5.2)
            InstrumentTimbre.STRING -> doubleArrayOf(0.65, 1.25, 1.9, 2.8, 3.9, 5.2, 6.8)
            InstrumentTimbre.BASS -> doubleArrayOf(1.10, 2.1, 3.4, 5.0, 7.2, 9.8, 12.5)
        }

        val partialOmega = DoubleArray(harmonicAmps.size)
        val partialGains = DoubleArray(harmonicAmps.size)
        for (h in harmonicAmps.indices) {
            val n = h + 1
            val pf = freqHz * n * sqrt(1.0 + inharmonicity * n * n)
            partialOmega[h] = 2.0 * PI * pf / SAMPLE_RATE
            partialGains[h] = if (pf < SAMPLE_RATE * 0.42) {
                harmonicAmps[h] * iso226Gain(pf) * bodyFormantGain(pf, timbre)
            } else {
                0.0
            }
        }

        // Phases initiales décorrélées pour éviter un pic de crête artificiel à t=0
        val initialPhases = doubleArrayOf(0.0, 0.72, 1.94, 3.11, 0.45, 2.38, 4.12)

        // Transitoire d'attaque (feutre de marteau ou souffle léger filtré passe-bas)
        var noiseSeed = 0x5DEECE66DL xor freqHz.toLong()
        var noiseLp = 0.0
        val transientDecay = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 115.0 // ~14 ms de feutre de marteau
            InstrumentTimbre.FLUTE -> 65.0      // ~22 ms de souffle doux
            InstrumentTimbre.REED, InstrumentTimbre.BRASS -> 85.0
            InstrumentTimbre.STRING, InstrumentTimbre.BASS -> 75.0
        }
        val transientAmp = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 0.045
            InstrumentTimbre.FLUTE -> 0.028
            InstrumentTimbre.REED -> 0.020
            InstrumentTimbre.BRASS -> 0.022
            InstrumentTimbre.STRING, InstrumentTimbre.BASS -> 0.020
        }

        // Niveau de maintien doux (sustain) pour éviter une coupure sèche sur les notes longues
        val sustainFloor = when (timbre) {
            InstrumentTimbre.WARM_KEYS -> 0.08
            else -> 0.38
        }

        var lpOut = 0.0
        val lpAlpha = (2.0 * PI * (freqHz * 7.5).coerceIn(1400.0, 9500.0) / SAMPLE_RATE).coerceIn(0.08, 0.85)

        // Échantillon acoustique réel CC0 (VSCO 2 CE) interpolé par spline cubique d'Hermite
        val refSample = AcousticSampleBank.get(timbre, freqHz)
        val refRootFreq = midiToFreq(refSample.rootMidi.toInt())
        val pitchRatio = (freqHz / refRootFreq).coerceIn(0.35, 2.85)
        val sampleStep = pitchRatio * (AcousticSampleBank.BANK_SAMPLE_RATE.toDouble() / SAMPLE_RATE)
        val refPcm = refSample.pcm
        val refMaxPos = (refPcm.size - 4).coerceAtLeast(1)

        for (i in 0 until totalCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val brassBloom = if (timbre == InstrumentTimbre.BRASS) {
                (t / 0.038).coerceIn(0.30, 1.0)
            } else {
                1.0
            }

            // Fondamentale pure et stable (aucun battement ni dérive de phase)
            val env0 = (1.0 - sustainFloor) * exp(-harmonicDecays[0] * t) + sustainFloor * exp(-0.25 * t)
            var sum = partialGains[0] * sin(partialOmega[0] * i + initialPhases[0]) * env0

            for (h in 1 until harmonicAmps.size) {
                val pg = partialGains[h]
                if (pg > 0.0) {
                    val envH = exp(-harmonicDecays[h] * t)
                    sum += pg * brassBloom * sin(partialOmega[h] * i + initialPhases[h]) * envH
                }
            }

            // Bruit d'attaque acoustique filtré (marteau / souffle) durant les premières millisecondes
            if (t < 0.045) {
                noiseSeed = (noiseSeed * 25214903917L + 11L) and ((1L shl 48) - 1)
                val white = ((noiseSeed ushr 24).toInt() / 8388608.0) - 1.0
                noiseLp += 0.18 * (white - noiseLp)
                sum += noiseLp * transientAmp * exp(-transientDecay * t)
            }

            val attackEnv = if (i < attackSamples) {
                val s = sin((i.toDouble() / attackSamples) * (PI / 2.0))
                s * s
            } else {
                1.0
            }
            val releaseEnv = if (i >= activeCount && tailCount > 0) {
                val p = (1.0 - (i - activeCount).toDouble() / tailCount).coerceIn(0.0, 1.0)
                p * p
            } else {
                1.0
            }

            // Lecture interpolée de l'échantillon réel CC0 et fondu croisé avec le modèle harmonique stable
            val samplePos = i * sampleStep
            val realSamplePart = if (samplePos < refMaxPos) {
                val fadeWindow = (1.0 - (samplePos / refMaxPos).pow(2.0)).coerceIn(0.0, 1.0)
                sampleHermite(refPcm, samplePos) * fadeWindow * 0.48 * isoGain * attackEnv * releaseEnv
            } else {
                0.0
            }

            val synthPart = sum * 0.24 * isoGain * attackEnv * releaseEnv
            val raw = realSamplePart + synthPart
            lpOut += lpAlpha * (raw - lpOut)
            out[i] = lpOut.toFloat()
        }
        return out
    }

    /** Interpolation cubique d'Hermite à 4 points pour le rééchantillonnage de hauteur sans crénelage. */
    private fun sampleHermite(buf: FloatArray, pos: Double): Double {
        val idx = pos.toInt().coerceIn(1, buf.size - 3)
        val frac = pos - idx
        val y0 = buf[idx - 1].toDouble()
        val y1 = buf[idx].toDouble()
        val y2 = buf[idx + 1].toDouble()
        val y3 = buf[idx + 2].toDouble()
        val c0 = y1
        val c1 = 0.5 * (y2 - y0)
        val c2 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3
        val c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2)
        return ((c3 * frac + c2) * frac + c1) * frac + c0
    }

    fun toPcm16(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val shaped = tanh(samples[i].toDouble() * 1.12)
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
        val releaseTailMs = 190

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

        val endPad = (SAMPLE_RATE * 240) / 1000
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
     * Diffuseur d'ambiance acoustique doux (2 étages passe-tout sans boucles en peigne résonantes)
     * pour donner une légère aération de pièce sans aucune coloration ni battement métallique.
     */
    internal class RoomReverb(sampleRate: Int) {
        private val allPassDelays = intArrayOf(
            (sampleRate * 0.0113).toInt().coerceAtLeast(1),
            (sampleRate * 0.0037).toInt().coerceAtLeast(1),
        )
        private val allPassBuffers = Array(allPassDelays.size) { FloatArray(allPassDelays[it]) }
        private val allPassIndices = IntArray(allPassDelays.size)
        private val allPassGain = 0.35f
        private var lpState = 0f
        private val wetMix = 0.08f

        fun process(input: FloatArray): FloatArray {
            val out = FloatArray(input.size)
            for (i in input.indices) {
                val dry = input[i]
                var diffused = dry
                for (a in allPassBuffers.indices) {
                    val apBuf = allPassBuffers[a]
                    val apIdx = allPassIndices[a]
                    val bufOut = apBuf[apIdx]
                    val inputPlusFb = diffused + bufOut * allPassGain
                    apBuf[apIdx] = inputPlusFb
                    allPassIndices[a] = (apIdx + 1) % apBuf.size
                    diffused = bufOut - inputPlusFb * allPassGain
                }
                lpState += 0.35f * (diffused - lpState)
                out[i] = dry * (1f - wetMix * 0.3f) + lpState * wetMix
            }
            return out
        }
    }
}
