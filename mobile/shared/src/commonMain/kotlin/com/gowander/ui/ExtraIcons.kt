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
    val close: ImageVector by lazy { strokeIcon("x", "M18 6 6 18", "m6 6 12 12") }
    val fastForward: ImageVector by lazy { strokeIcon("fast-forward", "M13 19l9-7-9-7z", "M2 19l9-7-9-7z") }
    val pause: ImageVector by lazy { strokeIcon("pause", "M6 4h4v16H6z", "M14 4h4v16h-4z") }
    val swap: ImageVector by lazy { strokeIcon("arrow-up-down", "m21 16-4 4-4-4", "M17 20V4", "m3 8 4-4 4 4", "M7 4v16") }
}
