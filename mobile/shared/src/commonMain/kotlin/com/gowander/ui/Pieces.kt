package com.gowander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Canvas tabs (Khám phá, Hành trình, Hồ sơ): grey track, the chosen one white and coloured; 48 dp targets. */
@Composable
fun Segmented(items: List<String>, selected: Int, modifier: Modifier = Modifier, color: Color = Palette.Primary, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Subtle).padding(4.dp)) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.Raised else Palette.Subtle)
                    .semantics { this.selected = on }.clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, size = 14.sp, color = if (on) color else Palette.Muted, weight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1) }
        }
    }
}

/** Canvas filter pill: 36 dp to look at, 48 dp to touch; filled when chosen. */
@Composable
fun Pill(label: String, selected: Boolean = false, modifier: Modifier = Modifier, color: Color = Palette.Primary, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(modifier.sizeIn(minHeight = 48.dp).semantics { this.selected = selected }.clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.height(36.dp).clip(shape).background(if (selected) color else Palette.Bg)
                .then(if (selected) Modifier else Modifier.border(1.dp, Palette.Line, shape)).padding(horizontal = 14.dp, vertical = 8.dp),
            size = 13.sp, color = if (selected) Palette.OnPrimary else Palette.Ink, weight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
        )
    }
}

private val PHOTO_TINTS get() = listOf(Palette.Soft, Palette.Warn, Palette.Sky, Palette.Green, Palette.AccentSoft, Palette.VioletSoft)

/**
 * Stand-in for a photo the data does not have yet (PIPELINE §5c mock register: photos): a tinted tile with an icon,
 * the same tint for the same [seed]. Decorative: the text next to it says what it is.
 */
@Composable
fun MockPhoto(seed: String, modifier: Modifier, icon: ImageVector = KitIcons.camera, radius: Dp = 14.dp, iconSize: Dp = 24.dp) =
    Box(modifier.clip(RoundedCornerShape(radius)).background(PHOTO_TINTS[abs(seed.hashCode()) % PHOTO_TINTS.size]), contentAlignment = Alignment.Center) {
        Icon(icon, iconSize, Palette.Muted)
    }

/**
 * Body of a card pinned to a map (owner, 15-11-2026): a wide photo on top when there is one, below it the place-type
 * icon (no tile) and the caller's text. The photo is decorative: the text says what the place is. The caller's
 * [modifier] gives shape, border and click.
 */
@Composable
fun PinCard(photo: Painter?, icon: ImageVector, modifier: Modifier, width: Dp, photoHeight: Dp, iconSize: Dp = 20.dp, text: @Composable ColumnScope.() -> Unit) =
    Column(modifier.width(width)) {
        if (photo != null) Image(photo, contentDescription = null, Modifier.fillMaxWidth().height(photoHeight), contentScale = ContentScale.Crop)
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, iconSize, Palette.Primary)
            Spacer(Modifier.width(8.dp))
            Column(content = text)
        }
    }

/** "★ 4,5" in the canvas amber; the number uses the Vietnamese decimal comma. */
@Composable
fun Stars(rating: Double, modifier: Modifier = Modifier, size: Int = 12) =
    Text("★ ${rating.toString().take(3).replace('.', ',')}", modifier, size = size.sp, color = Palette.Amber, weight = FontWeight.Bold)
