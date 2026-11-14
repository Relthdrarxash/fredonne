package fr.fredonne.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Note
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

actual class TonePlayer {

    private val sampleRate = ToneSynthesizer.SAMPLE_RATE
    private val playing = AtomicBoolean(false)

    actual fun isPlaying(): Boolean = playing.get()

    actual fun stop() {
        playing.set(false)
    }

    actual fun playSingleNote(midi: Int, durationMs: Int, timbre: InstrumentTimbre) {
        thread(isDaemon = true) {
            val samples = ToneSynthesizer.synthesizeTone(
                freqHz = ToneSynthesizer.midiToFreq(midi),
                durationMs = durationMs,
                timbre = timbre,
            )
            playPcm(samples)
        }
    }

    actual fun playMelody(
        melody: Melody,
        bpm: Int,
        onEventPlaying: (measureIndex: Int, eventIndex: Int) -> Unit,
        onFinished: () -> Unit,
        timbre: InstrumentTimbre,
    ) {
        stop()
        playing.set(true)
        thread(isDaemon = true) {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)
            val track = runCatching {
                AudioTrack.Builder()
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
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            try {
                track?.play()
                val wholeMs = (4.0 * 60_000.0) / bpm.coerceAtLeast(30)
                val reverb = ToneSynthesizer.RoomReverb(sampleRate)
                var overlapTail = FloatArray(0)
                val releaseTailMs = 260

                melody.measures.forEachIndexed { mIdx, measure ->
                    measure.events.forEachIndexed { eIdx, event ->
                        if (!playing.get()) return@thread
                        onEventPlaying(mIdx, eIdx)
                        val eventMs = (event.length.toDouble() * wholeMs).toInt().coerceAtLeast(60)
                        val eventCount = (sampleRate * eventMs) / 1000
                        val generated = when (event) {
                            is Note -> ToneSynthesizer.synthesizeNoteFloat(
                                freqHz = ToneSynthesizer.midiToFreq(event.pitch.midi),
                                durationMs = eventMs,
                                tailMs = releaseTailMs,
                                timbre = timbre,
                            )
                            else -> FloatArray(eventCount)
                        }
                        val combined = FloatArray(maxOf(generated.size, overlapTail.size))
                        for (i in generated.indices) combined[i] += generated[i]
                        for (i in overlapTail.indices) combined[i] += overlapTail[i]

                        val currentSlice = combined.copyOfRange(0, eventCount)
                        overlapTail = if (combined.size > eventCount) {
                            combined.copyOfRange(eventCount, combined.size)
                        } else {
                            FloatArray(0)
                        }
                        val pcm = ToneSynthesizer.toPcm16(reverb.process(currentSlice))
                        if (track != null) {
                            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                        } else {
                            Thread.sleep(eventMs.toLong())
                        }
                    }
                }
                if (playing.get()) {
                    val endPad = (sampleRate * 320) / 1000
                    val endMix = FloatArray(maxOf(overlapTail.size, endPad))
                    for (i in overlapTail.indices) endMix[i] = overlapTail[i]
                    val pcmEnd = ToneSynthesizer.toPcm16(reverb.process(endMix))
                    if (track != null) {
                        track.write(pcmEnd, 0, pcmEnd.size, AudioTrack.WRITE_BLOCKING)
                    }
                }
                track?.stop()
            } catch (_: Throwable) {
                // Ignorer proprement sur périphérique audio indisponible
            } finally {
                runCatching { track?.release() }
                playing.set(false)
                onFinished()
            }
        }
    }

    private fun playPcm(samples: ShortArray) {
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
            Thread.sleep(totalMs)
            track.stop()
        } catch (_: Throwable) {
            // Ignorer proprement sur périphérique audio indisponible
        } finally {
            track.release()
        }
    }
}
