package fr.fredonne.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import fr.fredonne.core.music.Instruments
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class ScreenshotsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(locale = "fr"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    init {
        Locale.setDefault(Locale.FRENCH)
    }

    @Test
    fun bienvenue() {
        paparazzi.snapshot {
            FredonneApp(initialOnboarded = false)
        }
    }

    @Test
    fun editeur() {
        paparazzi.snapshot {
            FredonneApp(
                initialOnboarded = true,
                initialEditorState = EditorState(
                    melody = EditorState.sampleMelody(),
                    cursorMeasure = 1,
                    cursorEventIndex = 2,
                ),
            )
        }
    }

    @Test
    fun transpositeur() {
        paparazzi.snapshot {
            FredonneApp(
                initialOnboarded = true,
                initialEditorState = EditorState(
                    melody = EditorState.sampleMelody(),
                    instrument = Instruments.CLARINET_BB,
                    cursorMeasure = 0,
                    cursorEventIndex = 1,
                ),
            )
        }
    }
}
