package fr.fredonne.app

import android.content.Context

class FredonnePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("fredonne_prefs", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var preferredInstrumentId: String?
        get() = prefs.getString(KEY_INSTRUMENT, null)
        set(value) = prefs.edit().putString(KEY_INSTRUMENT, value).apply()

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_INSTRUMENT = "preferred_instrument"
    }
}
