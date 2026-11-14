package fr.fredonne.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

class AndroidScoreExporter(private val context: Context) : ScoreExporter {

    override fun exportBytes(fileName: String, mimeType: String, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)

        // Si le contenu est textuel (MusicXML), on propose également le partage direct via Intent.ACTION_SEND
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, fileName)
                if (mimeType.contains("xml") || mimeType.startsWith("text/")) {
                    putExtra(Intent.EXTRA_TEXT, bytes.decodeToString())
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
        return file.absolutePath
    }
}

@Composable
actual fun rememberScoreExporter(): ScoreExporter {
    val context = LocalContext.current
    return remember(context) { AndroidScoreExporter(context) }
}
