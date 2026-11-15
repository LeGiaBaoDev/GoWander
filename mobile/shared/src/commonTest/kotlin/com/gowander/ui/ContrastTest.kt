package com.gowander.ui

import androidx.compose.ui.graphics.Color
import com.gowander.route.NavBanner
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.2 contrast (SC 1.4.3: text 4.5:1) for every text-on-background pair the app draws. A palette change that
 * makes text hard to read fails here instead of on a user's phone.
 */
class ContrastTest {
    private fun channel(c: Float) = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    // (what, text colour to background, minimum ratio)
    private fun pairs() = listOf(
        Triple("body text", Palette.Ink to Palette.Bg, 4.5),
        Triple("notes / secondary text", Palette.Muted to Palette.Bg, 4.5),
        Triple("notes on subtle cards", Palette.Muted to Palette.Subtle, 4.5),
        Triple("links / selected labels", Palette.Primary to Palette.Bg, 4.5),
        Triple("selected tile label on soft", Palette.Primary to Palette.Soft, 4.5),
        Triple("selected tab label", Palette.Primary to Palette.Raised, 4.5),
        Triple("selected profile tab label", Palette.VioletDark to Palette.Raised, 4.5),
        Triple("primary button label", Palette.OnPrimary to Palette.Primary, 4.5),
        Triple("Thoát (accent button)", Palette.OnPrimary to Palette.Accent, 4.5),
        Triple("navigation banner", Color.White to NavBanner, 4.5),
        Triple("text on warning banner", Palette.Ink to Palette.Warn, 4.5),
        Triple("body text on cards", Palette.Body to Palette.Bg, 4.5),
        Triple("star rating", Palette.Amber to Palette.Bg, 4.5),
        Triple("bus / walk leg label", Palette.Bus to Palette.Bg, 4.5),
        Triple("metro leg label", Palette.Metro to Palette.Bg, 4.5),
        Triple("warning text", Palette.Accent to Palette.Bg, 4.5),
        Triple("profile heading", Palette.VioletDark to Palette.Bg, 4.5),
        Triple("profile name on soft violet", Palette.VioletDark to Palette.VioletSoft, 4.5),
        Triple("violet button label and profile numbers", Color.White to Palette.VioletFill, 4.5),
        Triple("white on placeholder tiles", Color.White to Palette.Night, 4.5),
        Triple("white on always-dark sections", Color.White to Palette.Deep, 4.5),
        Triple("label on gold chip", Palette.Deep to Palette.Gold, 4.5),
    )

    @Test
    fun everyTextPairMeetsWcagAa() = try {
        for (dark in listOf(false, true)) {
            Theme.set(dark)
            val failures = pairs().map { (what, colors, min) -> Triple(what, ratio(colors.first, colors.second), min) }.filter { it.second < it.third }
            assertTrue(failures.isEmpty(), (if (dark) "dark: " else "light: ") + failures.joinToString { "${it.first}: ${(it.second * 100).toInt() / 100.0}:1 < ${it.third}:1" })
        }
    } finally {
        Theme.set(false)
    }

    @Test
    fun ratioFormulaMatchesKnownValues() {
        assertTrue(ratio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(ratio(Color.White, Color.White) in 0.99..1.01)
    }
}
