// Icons the Design Kit v4 does not have, drawn in the kit's style (24x24, 2 px round stroke, no fill).
// Paths from Lucide (https://lucide.dev, ISC licence): "car", "bike", "arrow-up-down", "layers", "box", "x".
package com.gowander.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { addPath(pathData = addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
    }.build()

object ExtraIcons {
    val car: ImageVector by lazy {
        strokeIcon(
            "car",
            "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
            "M5,17a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M9 17h6", "M15,17a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        )
    }
    val bike: ImageVector by lazy {
        strokeIcon(
            "bike",
            "M15,17.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0", "M2,17.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0",
            "M14,5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0", "M12 17.5V14l-3-3 4-3 2 3h2",
        )
    }
    val sun: ImageVector by lazy {
        strokeIcon(
            "sun",
            "M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0", "M12 2v2", "M12 20v2", "m4.9 4.9 1.4 1.4", "m17.7 17.7 1.4 1.4",
            "M2 12h2", "M20 12h2", "m6.3 17.7-1.4 1.4", "m19.1 4.9-1.4 1.4",
        )
    }
    val home: ImageVector by lazy {
        strokeIcon("house", "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10")
    }
    val layers: ImageVector by lazy {
        strokeIcon(
            "layers",
            "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z",
            "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65", "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65",
        )
    }
    val cube: ImageVector by lazy {
        strokeIcon("box", "M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z", "m3.3 7 8.7 5 8.7-5", "M12 22V12")
    }
    val chat: ImageVector by lazy { strokeIcon("message-circle", "M7.9 20A9 9 0 1 0 4 16.1L2 22z", "M8 12h.01M12 12h.01M16 12h.01") }
    val close: ImageVector by lazy { strokeIcon("x", "M18 6 6 18", "m6 6 12 12") }
    val fastForward: ImageVector by lazy { strokeIcon("fast-forward", "M13 19l9-7-9-7z", "M2 19l9-7-9-7z") }
    val pause: ImageVector by lazy { strokeIcon("pause", "M6 4h4v16H6z", "M14 4h4v16h-4z") }
    val list: ImageVector by lazy { strokeIcon("list", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01") }
    val send: ImageVector by lazy { strokeIcon("navigation", "M3 11 22 2l-9 19-2-8-8-2z") }
    val trend: ImageVector by lazy { strokeIcon("trend", "M3 17l6-6 4 4 8-8", "M15 7h6v6") }
    val share: ImageVector by lazy { strokeIcon("share", "M15,5a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "M3,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "M15,19a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "m8.6 13.5 6.8 4", "M15.4 6.5l-6.8 4") }
    val play: ImageVector by lazy { strokeIcon("play", "M6 4v16l14-8z") }
    val volume: ImageVector by lazy { strokeIcon("volume-2", "M11 5 6 9H2v6h4l5 4z", "M15.5 8.5a5 5 0 0 1 0 7", "M19 5a10 10 0 0 1 0 14") }
    val volumeOff: ImageVector by lazy { strokeIcon("volume-x", "M11 5 6 9H2v6h4l5 4z", "m22 9-6 6", "m16 9 6 6") }
    val swap: ImageVector by lazy { strokeIcon("arrow-up-down", "m21 16-4 4-4-4", "M17 20V4", "m3 8 4-4 4 4", "M7 4v16") }

    // Account screens (AUTH-2): the kit has no mail, phone, at-sign, eye or arrow-right.
    val mail: ImageVector by lazy {
        strokeIcon("mail", "M2 6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2Z", "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7")
    }
    val phone: ImageVector by lazy {
        strokeIcon(
            "phone",
            "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z",
        )
    }
    val at: ImageVector by lazy {
        strokeIcon("at-sign", "M16,12a4,4 0 1,0 -8,0a4,4 0 1,0 8,0", "M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8")
    }
    val eye: ImageVector by lazy {
        strokeIcon("eye", "M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0", "M12,9a3,3 0 1,0 0,6a3,3 0 1,0 0,-6")
    }
    val eyeOff: ImageVector by lazy {
        strokeIcon(
            "eye-off",
            "m2 2 20 20",
            "M6.71 6.71A10.75 10.75 0 0 0 2.06 11.65a1 1 0 0 0 0 .7 10.75 10.75 0 0 0 14.6 4.19",
            "M9.88 5.09A10.75 10.75 0 0 1 21.94 11.65a1 1 0 0 1 0 .7 10.75 10.75 0 0 1-2.35 3.35",
            "M14.12 14.12a3 3 0 1 1-4.24-4.24",
        )
    }
    val arrowRight: ImageVector by lazy { strokeIcon("arrow-right", "M5 12h13", "m13 6 6 6-6 6") }
    val logout: ImageVector by lazy { strokeIcon("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9") }
    val device: ImageVector by lazy { strokeIcon("smartphone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z", "M11 18h2") }
}
