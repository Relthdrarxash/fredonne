package fr.fredonne.app

import fr.fredonne.core.music.Melody

/**
 * Synthétiseur hors-ligne léger (PCM 16 bits, 44 100 Hz).
 * Aucun fichier SoundFont requis, aucune permission réseau.
 */
expect class TonePlayer() {
    fun isPlaying(): Boolean
    fun stop()
    fun playSingleNote(midi: Int, durationMs: Int = 520)
    fun playMelody(
        melody: Melody,
        bpm: Int,
        onEventPlaying: (measureIndex: Int, eventIndex: Int) -> Unit,
        onFinished: () -> Unit,
    )
}
