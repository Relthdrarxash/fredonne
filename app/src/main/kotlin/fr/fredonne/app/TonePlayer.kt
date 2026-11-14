package fr.fredonne.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Note
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Synthétiseur hors-ligne léger via [AudioTrack] (PCM 16 bits, 44 100 Hz).
 * Aucun fichier SoundFont requis, aucune permission réseau.
 */
class TonePlayer {

    private val sampleRate = 44_100
    private val playing = AtomicBoolean(false)

    fun isPlaying(): Boolean = playing.get()

    fun stop() {
        playing.set(false)
    }

    fun playSingleNote(midi: Int, durationMs: Int = 260) {
        thread(isDaemon = true) {
            val samples = synthesizeTone(midiToFreq(midi), durationMs)
            playPcm(samples)
        }
    }

    fun playMelody(
        melody: Melody,
        bpm: Int,
        onEventPlaying: (measureIndex: Int, eventIndex: Int) -> Unit,
        onFinished: () -> Unit,
    ) {
        stop()
        playing.set(true)
        thread(isDaemon = true) {
            try {
                // La noire (1/4 de ronde) dure 60_000 / bpm ms, donc la ronde dure 4 * 60_000 / bpm ms.
                val wholeMs = (4.0 * 60_000.0) / bpm.coerceAtLeast(30)
                melody.measures.forEachIndexed { mIdx, measure ->
                    measure.events.forEachIndexed { eIdx, event ->
                        if (!playing.get()) return@thread
                        onEventPlaying(mIdx, eIdx)
                        val eventMs = (event.length.toDouble() * wholeMs).toInt().coerceAtLeast(60)
                        val pcm = when (event) {
                            is Note -> synthesizeTone(midiToFreq(event.pitch.midi), eventMs)
                            else -> ShortArray((sampleRate * eventMs) / 1000)
                        }
                        playPcm(pcm, checkStop = true)
                    }
                }
            } finally {
                playing.set(false)
                onFinished()
            }
        }
    }

    private fun playPcm(samples: ShortArray, checkStop: Boolean = false) {
        if (samples.isEmpty()) return
        val bufferBytes = samples.size * 2
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferBytes, minBuf))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(samples, 0, samples.size)
            track.play()
            val totalMs = (samples.size * 1000L) / sampleRate
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < totalMs) {
                if (checkStop && !playing.get()) break
                Thread.sleep(20)
            }
            track.stop()
        } catch (_: Throwable) {
            // Ignorer proprement sur périphérique audio indisponible
        } finally {
            track.release()
        }
    }

    private fun synthesizeTone(freqHz: Double, durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val attackSamples = (sampleRate * 0.012).toInt().coerceAtLeast(1)
        val releaseSamples = (sampleRate * 0.045).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val fundamental = sin(2.0 * PI * freqHz * t)
            val harmonic2 = 0.28 * sin(2.0 * PI * freqHz * 2.0 * t)
            val harmonic3 = 0.10 * sin(2.0 * PI * freqHz * 3.0 * t)
            val decay = exp(-2.2 * t)

            val attackEnv = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val releaseEnv = if (i > count - releaseSamples) {
                (count - i).toDouble().coerceAtLeast(0.0) / releaseSamples
            } else {
                1.0
            }

            val sample = (fundamental + harmonic2 + harmonic3) * 0.36 * decay * attackEnv * releaseEnv
            out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
}
