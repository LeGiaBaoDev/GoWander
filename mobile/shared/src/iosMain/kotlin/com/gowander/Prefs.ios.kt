package com.gowander

import platform.Foundation.NSUserDefaults

actual object Prefs {
    actual fun getBool(key: String): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(key)
    actual fun setBool(key: String, value: Boolean) = NSUserDefaults.standardUserDefaults.setBool(value, forKey = key)
    actual fun getString(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    actual fun setString(key: String, value: String) = NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
}
