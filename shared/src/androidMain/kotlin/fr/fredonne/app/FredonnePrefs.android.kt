package fr.fredonne.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class AndroidFredonnePrefs(context: Context) : FredonnePrefs {

    private val prefs = context.getSharedPreferences("fredonne_prefs", Context.MODE_PRIVATE)

    override var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    override var preferredInstrumentId: String?
        get() = prefs.getString(KEY_INSTRUMENT, null)
        set(value) = prefs.edit().putString(KEY_INSTRUMENT, value).apply()

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_INSTRUMENT = "preferred_instrument"
    }
}

@Composable
actual fun rememberFredonnePrefs(): FredonnePrefs {
    val context = LocalContext.current
    return remember { AndroidFredonnePrefs(context) }
}
