package com.gowander

/** Tiny persistent flags (first-run done, ...). Android: SharedPreferences; iOS: NSUserDefaults. No library needed. */
expect object Prefs {
    fun getBool(key: String): Boolean
    fun setBool(key: String, value: Boolean)
}
