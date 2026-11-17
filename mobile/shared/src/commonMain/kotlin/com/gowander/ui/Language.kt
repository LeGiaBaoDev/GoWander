package com.gowander.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gowander.Prefs
import com.gowander.systemLanguageCode

/** UI language. ADR-022: Vietnamese + English first; Chinese/Korean later. */
enum class Lang(val code: String) {
    VI("vi"),
    EN("en"),
}

/**
 * The language chosen by the user, kept on the device. Reads as a Compose state so every [Strings] getter
 * recomposes when it changes. Default: the device language when it is Vietnamese or English, otherwise Vietnamese.
 */
object Language {
    private const val KEY = "language"

    var current by mutableStateOf(initial())
        private set

    fun set(value: Lang) {
        current = value
        Prefs.setString(KEY, value.code)
    }

    private fun initial(): Lang {
        val code = Prefs.getString(KEY) ?: systemLanguageCode()
        return if (code.equals("en", ignoreCase = true)) Lang.EN else Lang.VI
    }
}
