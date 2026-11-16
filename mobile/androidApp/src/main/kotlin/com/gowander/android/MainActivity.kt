package com.gowander.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import com.gowander.ui.Theme
import com.gowander.App
import com.gowander.Prefs
import com.gowander.account.GoogleSignIn
import com.gowander.account.SecureToken

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        SecureToken.init(this) // app accounts (ADR-040): the session token is encrypted with a Keystore key
        GoogleSignIn.init(this, BuildConfig.GOOGLE_WEB_CLIENT_ID) // Web client ID from .env; empty turns Google off
        setContent {
            // Status and navigation bar icons follow the app's light / dark look, not the phone's.
            val dark = Theme.dark
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                // No grey scrim over the 3-button navigation bar: the app draws its own bar there.
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
            App()
        }
    }
}
