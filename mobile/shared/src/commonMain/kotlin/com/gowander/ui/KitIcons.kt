// GENERATED from GoWander-Design-Kit-v4/src/main.js (ICONS) — do not edit by hand (ADR-030).
// 24x24, 2 px round stroke, no fill: same as the Figma kit.
package com.gowander.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun kitIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { addPath(pathData = addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
    }.build()

object KitIcons {
    val map: ImageVector by lazy { kitIcon("map", "M9 3 3 6v15l6-3 6 3 6-3V3l-6 3-6-3z", "M9 3v15M15 6v15") }
    val compass: ImageVector by lazy { kitIcon("compass", "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0", "m16.2 7.8-2 6.4-6.4 2 2-6.4z") }
    val user: ImageVector by lazy { kitIcon("user", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0") }
    val bookmark: ImageVector by lazy { kitIcon("bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z") }
    val search: ImageVector by lazy { kitIcon("search", "M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0", "m21 21-4.3-4.3") }
    val back: ImageVector by lazy { kitIcon("back", "m12 19-7-7 7-7", "M19 12H5") }
    val chevron: ImageVector by lazy { kitIcon("chevron", "m9 18 6-6-6-6") }
    val pin: ImageVector by lazy { kitIcon("pin", "M20 10c0 5-8 12-8 12s-8-7-8-12a8 8 0 0 1 16 0z", "M9,10a3,3 0 1,0 6,0a3,3 0 1,0 -6,0") }
    val locate: ImageVector by lazy { kitIcon("locate", "M3 11 22 2l-9 19-2-8-8-2z") }
    val rain: ImageVector by lazy { kitIcon("rain", "M4 15a7 7 0 1 1 11.7-7H17a4.5 4.5 0 0 1 2.5 8.2", "M16 14v6M8 14v6M12 16v6") }
    val route: ImageVector by lazy { kitIcon("route", "M3,19a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", "M15,5a3,3 0 1,0 6,0a3,3 0 1,0 -6,0") }
    val check: ImageVector by lazy { kitIcon("check", "M20 6 9 17l-5-5") }
    val plus: ImageVector by lazy { kitIcon("plus", "M12 5v14M5 12h14") }
    val clock: ImageVector by lazy { kitIcon("clock", "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0", "M12 6v6l4 2") }
    val star: ImageVector by lazy { kitIcon("star", "m12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.9-6.2-3.3-6.2 3.3L7 14.2 2 9.3l6.9-1z") }
    val camera: ImageVector by lazy { kitIcon("camera", "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z", "M8,13a4,4 0 1,0 8,0a4,4 0 1,0 -8,0") }
    val bell: ImageVector by lazy { kitIcon("bell", "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9", "M13.7 21a2 2 0 0 1-3.4 0") }
    val lock: ImageVector by lazy { kitIcon("lock", "M5,11h14a2,2 0 0,1 2,2v7a2,2 0 0,1 -2,2h-14a2,2 0 0,1 -2,-2v-7a2,2 0 0,1 2,-2z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val book: ImageVector by lazy { kitIcon("book", "M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z", "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z") }
    val pen: ImageVector by lazy { kitIcon("pen", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z") }
    val gear: ImageVector by lazy { kitIcon("gear", "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0", "M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0") }
    val metro: ImageVector by lazy { kitIcon("metro", "M8,3h8a3,3 0 0,1 3,3v8a3,3 0 0,1 -3,3h-8a3,3 0 0,1 -3,-3v-8a3,3 0 0,1 3,-3z", "M5 11h14M8 21l2-4M16 21l-2-4") }
    val bus: ImageVector by lazy { kitIcon("bus", "M6,4h12a2,2 0 0,1 2,2v9a2,2 0 0,1 -2,2h-12a2,2 0 0,1 -2,-2v-9a2,2 0 0,1 2,-2z", "M4 10h16", "M6.5,19a1.5,1.5 0 1,0 3,0a1.5,1.5 0 1,0 -3,0", "M14.5,19a1.5,1.5 0 1,0 3,0a1.5,1.5 0 1,0 -3,0") }
    val walk: ImageVector by lazy { kitIcon("walk", "M11,4a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "m13 7-3 5 3 3v6M10 12l-3 1M13 9l3 3 3 1") }
    val landmark: ImageVector by lazy { kitIcon("landmark", "M3 22h18M6 18v-7M10 18v-7M14 18v-7M18 18v-7M12 2l9 6H3z") }
    val flag: ImageVector by lazy { kitIcon("flag", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7") }
    val globe: ImageVector by lazy { kitIcon("globe", "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0", "M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20") }
}
