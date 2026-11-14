@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package fr.fredonne.app

import fr.fredonne.core.music.Melody
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSTimer
import platform.Foundation.create

actual class TonePlayer {

    private var singlePlayer: AVAudioPlayer? = null
    private var melodyPlayer: AVAudioPlayer? = null
    private var progressTimer: NSTimer? = null
    private var playingFlag: Boolean = false

    actual fun isPlaying(): Boolean = playingFlag

    actual fun stop() {
        playingFlag = false
        progressTimer?.invalidate()
        progressTimer = null
        melodyPlayer?.stop()
        melodyPlayer = null
    }

    actual fun playSingleNote(midi: Int, durationMs: Int) {
        ensureAudioSession()
        val samples = ToneSynthesizer.synthesizeTone(ToneSynthesizer.midiToFreq(midi), durationMs)
        if (samples.isEmpty()) return
        val wav = ToneSynthesizer.pcm16ToWav(samples)
        val data = wav.toNSData() ?: return
        val player = AVAudioPlayer(data = data, error = null) ?: return
        singlePlayer = player
        player.prepareToPlay()
        player.play()
    }

    actual fun playMelody(
        melody: Melody,
        bpm: Int,
        onEventPlaying: (measureIndex: Int, eventIndex: Int) -> Unit,
        onFinished: () -> Unit,
    ) {
        stop()
        ensureAudioSession()
        val (pcm, timings) = ToneSynthesizer.synthesizeMelody(melody, bpm)
        if (pcm.isEmpty() || timings.isEmpty()) {
            onFinished()
            return
        }
        val wav = ToneSynthesizer.pcm16ToWav(pcm)
        val data = wav.toNSData() ?: run {
            onFinished()
            return
        }
        val player = AVAudioPlayer(data = data, error = null) ?: run {
            onFinished()
            return
        }

        playingFlag = true
        melodyPlayer = player
        player.prepareToPlay()
        var lastReportedIdx = -1
        player.play()

        progressTimer = NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.025,
            repeats = true,
        ) { timer ->
            if (!playingFlag) {
                timer?.invalidate()
                return@scheduledTimerWithTimeInterval
            }
            val currentMs = (player.currentTime * 1000.0).toLong()
            var activeIdx = 0
            for (i in timings.indices) {
                if (timings[i].startMs <= currentMs) activeIdx = i else break
            }
            if (activeIdx != lastReportedIdx) {
                lastReportedIdx = activeIdx
                val timing = timings[activeIdx]
                onEventPlaying(timing.measureIndex, timing.eventIndex)
            }
            if (!player.isPlaying()) {
                timer?.invalidate()
                progressTimer = null
                melodyPlayer = null
                playingFlag = false
                onFinished()
            }
        }
    }

    private fun ensureAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
    }

    private fun ByteArray.toNSData(): NSData? {
        if (isEmpty()) return null
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}
