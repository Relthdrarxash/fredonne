package fr.fredonne.app

import androidx.compose.runtime.Composable

interface FredonnePrefs {
    var onboarded: Boolean
    var preferredInstrumentId: String?
}

@Composable
expect fun rememberFredonnePrefs(): FredonnePrefs
