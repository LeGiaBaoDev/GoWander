package com.gowander.route

import androidx.compose.runtime.Composable

/** Repeating vibration for the off-route warning; stopped as soon as the user is back on the route or dismisses it. */
interface WarningHaptics {
    fun start()
    fun stop()
}

@Composable
expect fun rememberWarningHaptics(): WarningHaptics
