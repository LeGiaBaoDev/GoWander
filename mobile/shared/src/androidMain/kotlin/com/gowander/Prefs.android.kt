package com.gowander

import android.content.Context
import android.content.SharedPreferences

actual object Prefs {
    private var prefs: SharedPreferences? = null

    /** Called once from MainActivity before setContent. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("gowander", Context.MODE_PRIVATE)
    }

    // Before init (previews/tests) flags read as false and writes are dropped: the app still works, it just shows onboarding.
    actual fun getBool(key: String): Boolean = prefs?.getBoolean(key, false) ?: false
    actual fun setBool(key: String, value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply() }
    actual fun getString(key: String): String? = prefs?.getString(key, null)
    actual fun setString(key: String, value: String) { prefs?.edit()?.putString(key, value)?.apply() }
}

actual fun systemLanguageCode(): String = java.util.Locale.getDefault().language
