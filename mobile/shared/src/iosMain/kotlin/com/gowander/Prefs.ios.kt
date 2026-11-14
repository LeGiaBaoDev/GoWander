package com.gowander

import platform.Foundation.NSUserDefaults

actual object Prefs {
    actual fun getBool(key: String): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(key)
    actual fun setBool(key: String, value: Boolean) = NSUserDefaults.standardUserDefaults.setBool(value, forKey = key)
}
