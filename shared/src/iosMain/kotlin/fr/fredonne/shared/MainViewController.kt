package fr.fredonne.shared

import androidx.compose.ui.window.ComposeUIViewController
import fr.fredonne.app.FredonneApp
import fr.fredonne.app.FredonneTheme
import platform.UIKit.UIViewController

/** Point d'entrée iOS de Fredonne (portée interactive, transposition et audio PCM/WAV hors-ligne). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    FredonneTheme {
        FredonneApp()
    }
}
