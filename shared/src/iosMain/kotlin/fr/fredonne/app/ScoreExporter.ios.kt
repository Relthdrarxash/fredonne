@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package fr.fredonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

class IosScoreExporter : ScoreExporter {

    override fun exportBytes(fileName: String, mimeType: String, bytes: ByteArray): String {
        val path = NSTemporaryDirectory() + fileName
        if (bytes.isNotEmpty()) {
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            data?.writeToFile(path, atomically = true)
        }
        val url = NSURL.fileURLWithPath(path)
        val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (rootVc != null) {
            val activityVc = UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null,
            )
            rootVc.presentViewController(activityVc, animated = true, completion = null)
        }
        return path
    }
}

@Composable
actual fun rememberScoreExporter(): ScoreExporter = remember { IosScoreExporter() }
