package com.gowander.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Icon
import com.gowander.ui.Palette
import com.gowander.ui.Text

/** The official four-colour Google "G" (Google Sign-In branding guidelines); never redrawn by hand. */
val googleG: ImageVector by lazy {
    ImageVector.Builder(name = "google-g", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 48f, viewportHeight = 48f).apply {
        addPath(
            pathData = addPathNodes("M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"),
            fill = SolidColor(Color(0xFFEA4335)),
        )
        addPath(
            pathData = addPathNodes("M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        addPath(
            pathData = addPathNodes("M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        addPath(
            pathData = addPathNodes("M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"),
            fill = SolidColor(Color(0xFF34A853)),
        )
    }.build()
}

/**
 * One labelled field of the account screens (A3–A9): the label sits above the box, the box is white with the kit's
 * line colour, 52 dp tall, with a leading icon. Errors are a sentence under the field, never colour alone.
 */
@Composable
fun AccountField(
    label: String,
    value: String,
    hint: String = "",
    icon: ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, size = 13.sp, weight = FontWeight.SemiBold, color = Palette.Muted)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Bg)
                .border(1.dp, if (error != null) Palette.Accent else Palette.Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, 19.dp, if (error != null) Palette.Accent else Palette.Muted)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, size = 16.sp, color = Palette.Muted)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    textStyle = TextStyle(fontSize = 16.sp, color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Primary),
                )
            }
            trailing?.invoke()
        }
        if (error != null) AccountError(error)
    }
}

/** A password field with the eye that shows and hides what was typed (kit: the same eye as the Admin's forms). */
@Composable
fun PasswordField(
    label: String,
    value: String,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, size = 13.sp, weight = FontWeight.SemiBold, color = Palette.Muted)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Bg)
                .border(1.dp, if (error != null) Palette.Accent else Palette.Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(com.gowander.ui.KitIcons.lock, 19.dp, if (error != null) Palette.Accent else Palette.Muted)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("Mật khẩu", size = 16.sp, color = Palette.Muted)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(fontSize = 16.sp, color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Primary),
                )
            }
            Box(
                Modifier.size(40.dp).semantics { contentDescription = if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu" }
                    .clickable(role = Role.Button) { visible = !visible },
                contentAlignment = Alignment.Center,
            ) { Icon(if (visible) ExtraIcons.eyeOff else ExtraIcons.eye, 20.dp, Palette.Muted) }
        }
        if (error != null) AccountError(error)
    }
}

/** Three bars under a new password (A4, A6): weak, fair, strong. */
@Composable
fun StrengthBars(strength: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (i < strength) Palette.Bus else Palette.Line),
            )
        }
    }
}

/** The Google button of A2/A3/A4: white, light grey border, the G on the left and an arrow on the right. */
@Composable
fun GoogleSignInButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Bg)
            .border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drawn as an image, not through Icon(): the four colours must not be flattened by a tint.
        Image(rememberVectorPainter(googleG), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text("Tiếp tục với Google", Modifier.weight(1f), size = 15.5.sp, weight = FontWeight.SemiBold, maxLines = 1)
        Icon(ExtraIcons.arrowRight, 18.dp, Palette.Muted)
    }
}

/** A line with "hoặc" in the middle (A2, A3). */
@Composable
fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Line))
        Text("hoặc", Modifier.padding(horizontal = 12.dp), size = 12.sp, color = Palette.Muted)
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Line))
    }
}

/** An error sentence; a screen reader announces it as soon as it appears. */
@Composable
fun AccountError(text: String) {
    Text(
        text,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        size = 13.sp,
        color = Palette.Accent,
        weight = FontWeight.SemiBold,
    )
}

/** A quiet confirmation ("Tên này còn trống — bạn dùng được nhé"). */
@Composable
fun AccountHint(text: String, ok: Boolean = true) {
    Text(text, size = 12.5.sp, color = if (ok) Palette.Bus else Palette.Accent, weight = FontWeight.Medium)
}

/**
 * The main button of a form. It keeps the kit's teal while it is the obvious next step (a disabled-looking button made
 * the form look broken on the emulator, 16-11-2026) and only changes while a request runs.
 */
@Composable
fun BusyButton(label: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        if (busy) "Đang xử lý…" else label,
        Modifier.fillMaxWidth(),
        kind = if (busy) ButtonKind.Secondary else ButtonKind.Primary,
    ) { if (!busy && enabled) onClick() }
}

/** Six boxes for the code (A5): the real input is a transparent field on top, so the keyboard and paste still work. */
@Composable
fun CodeField(value: String, onValueChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(58.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            repeat(6) { i ->
                val char = value.getOrNull(i)?.toString().orEmpty()
                Box(
                    Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Bg)
                        .border(1.dp, if (char.isNotEmpty()) Palette.Primary else Palette.Line, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(char, size = 22.sp, weight = FontWeight.Bold) }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValueChange(typed.filter { it.isDigit() }.take(6)) },
            // Nearly transparent rather than gone: it must still take taps and focus for the keyboard to open.
            modifier = Modifier.matchParentSize().alpha(0.02f).semantics { contentDescription = "Mã 6 số" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(fontSize = 22.sp, color = Palette.Ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Color.Transparent),
            singleLine = true,
        )
    }
}

/** The line under the buttons of A2–A4 (required by the owner: terms and privacy are linked). */
@Composable
fun LegalLine() {
    Text(
        "Tiếp tục nghĩa là bạn đồng ý với Điều khoản và Chính sách quyền riêng tư của GoWander.",
        size = 11.5.sp,
        color = Palette.Muted,
        maxLines = 3,
    )
}

/** A round avatar with the first letter of the name (A7, A8, A10). */
@Composable
fun InitialAvatar(name: String, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Palette.Soft),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, size = (size * 0.4f).sp, color = Palette.Primary, weight = FontWeight.Bold)
    }
}
