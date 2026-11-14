package fr.fredonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

class IosFredonnePrefs : FredonnePrefs {

    private val defaults = NSUserDefaults.standardUserDefaults

    override var onboarded: Boolean
        get() = defaults.boolForKey(KEY_ONBOARDED)
        set(value) {
            defaults.setBool(value, forKey = KEY_ONBOARDED)
        }

    override var preferredInstrumentId: String?
        get() = defaults.stringForKey(KEY_INSTRUMENT)
        set(value) {
            if (value == null) {
                defaults.removeObjectForKey(KEY_INSTRUMENT)
            } else {
                defaults.setObject(value, forKey = KEY_INSTRUMENT)
            }
        }

    private companion object {
        const val KEY_ONBOARDED = "fredonne_onboarded"
        const val KEY_INSTRUMENT = "fredonne_preferred_instrument"
    }
}

@Composable
actual fun rememberFredonnePrefs(): FredonnePrefs = remember { IosFredonnePrefs() }
