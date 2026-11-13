package com.gowander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "ui" palette of GoWander-Design-Kit-v4 (transit/metro, brand/teal-strong, neutral/ink, tint/sun, accent/coral-strong).
object Palette {
    val Metro = Color(0xFF0079B8)
    val Primary = Color(0xFF047A7D)
    val Ink = Color(0xFF123C48)
    val Muted = Color(0xFF5B7480)
    val Warn = Color(0xFFFFF4D6)
    val Pin = Color(0xFFC8234F)
    val Surface = Color.White
    val Line = Color(0xFFD5E0E4)
}

@Composable
fun Text(text: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp, color: Color = Palette.Ink, bold: Boolean = false) =
    BasicText(text, modifier, style = TextStyle(fontSize = size, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal))

@Composable
fun Label(text: String, modifier: Modifier = Modifier, background: Color = Palette.Surface) = Text(
    text,
    modifier.padding(bottom = 6.dp).background(background, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
)

/** Primary button: 48 dp touch target, announced as a button. */
@Composable
fun Action(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) = BasicText(
    text,
    style = TextStyle(fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
    modifier = modifier
        .sizeIn(minHeight = 48.dp)
        .background(Palette.Primary, RoundedCornerShape(999.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
)

/** Secondary button / selectable chip. Selection is also stated in text, not only by colour. */
@Composable
fun Chip(text: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) = BasicText(
    if (selected) "✓ $text" else text,
    style = TextStyle(fontSize = 14.sp, color = if (selected) Color.White else Palette.Ink),
    modifier = modifier
        .sizeIn(minHeight = 48.dp)
        .background(if (selected) Palette.Ink else Palette.Surface, RoundedCornerShape(999.dp))
        .border(1.dp, Palette.Line, RoundedCornerShape(999.dp))
        .semantics { this.selected = selected }
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 14.dp),
)
