package com.gowander

/** Small on-device storage: flags (first-run done, ...) and the user's plans and saved places as one JSON string (ADR-036). Android: SharedPreferences; iOS: NSUserDefaults. No library needed. */
expect object Prefs {
    fun getBool(key: String): Boolean
    fun setBool(key: String, value: Boolean)
    fun getString(key: String): String?
    fun setString(key: String, value: String)
}
