package com.gowander

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.gowander.ui.Theme
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

fun MainViewController() = ComposeUIViewController {
    // The status bar text follows the app's light / dark look, not the phone's.
    val dark = Theme.dark
    LaunchedEffect(dark) {
        val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        UIApplication.sharedApplication.windows.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
    }
    App()
}
