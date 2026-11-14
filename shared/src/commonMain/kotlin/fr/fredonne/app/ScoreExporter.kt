package fr.fredonne.app

import androidx.compose.runtime.Composable

/**
 * Exporteur hors-ligne de partitions (MusicXML, MIDI, PDF) sans aucune permission de stockage.
 */
interface ScoreExporter {
    fun exportBytes(fileName: String, mimeType: String, bytes: ByteArray): String
}

@Composable
expect fun rememberScoreExporter(): ScoreExporter
