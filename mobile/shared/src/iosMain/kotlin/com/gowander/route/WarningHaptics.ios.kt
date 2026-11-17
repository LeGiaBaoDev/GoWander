package com.gowander.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate

@Composable
actual fun rememberWarningHaptics(): WarningHaptics = remember { IosWarningHaptics() }

/** One strong vibration; the spoken warning carries the repetition, and the overlay stays until dismissed. */
private class IosWarningHaptics : WarningHaptics {
    override fun start() {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }

    override fun stop() = Unit
}
