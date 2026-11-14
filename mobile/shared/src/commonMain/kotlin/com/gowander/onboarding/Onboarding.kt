package com.gowander.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.resources.Res
import com.gowander.resources.logo_full
import com.gowander.resources.logo_icon
import com.gowander.ui.Badge
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Chip
import com.gowander.ui.KitIcons
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.SectionTitle
import com.gowander.ui.Strings
import com.gowander.ui.Text
import org.jetbrains.compose.resources.painterResource

/** Kit S0 "Màn hình chào": full logo with the GoWander wordmark, the tagline, one "Bắt đầu" button. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(Res.drawable.logo_full), contentDescription = Strings.APP_NAME, Modifier.width(250.dp))
        Spacer(Modifier.height(16.dp))
        Text(Strings.TAGLINE, size = 16.sp, color = Palette.Muted, weight = FontWeight.Medium)
        Spacer(Modifier.weight(1.2f))
        Button(Strings.START, Modifier.fillMaxWidth(), onClick = onStart)
    }
}

/** Kit S1 "Lần đầu": language, then why location helps; both buttons lead into the app, nothing is forced. */
@Composable
fun FirstRunScreen(onAllowLocation: () -> Unit, onLater: () -> Unit) {
    var englishNote by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(painterResource(Res.drawable.logo_icon), contentDescription = Strings.APP_NAME, Modifier.width(140.dp).align(Alignment.CenterHorizontally))
        SectionTitle(Strings.CHOOSE_LANGUAGE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Strings.VIETNAMESE, selected = true) { englishNote = false }
            Chip(Strings.ENGLISH) { englishNote = true }
        }
        Note(if (englishNote) Strings.ENGLISH_SOON else Strings.MORE_LANGUAGES_LATER)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Badge(KitIcons.locate, Palette.Soft, Palette.Primary, 80.dp) }
        Text(Strings.LOCATION_QUESTION, size = 20.sp, weight = FontWeight.SemiBold)
        Text(Strings.LOCATION_WHY, color = Palette.Muted)
        Spacer(Modifier.height(8.dp))
        Button(Strings.CONTINUE, Modifier.fillMaxWidth(), onClick = onAllowLocation)
        Button(Strings.LATER_TYPE_ORIGIN, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, onClick = onLater)
    }
}

/**
 * Branded loading: the GoWander mark breathing (scale + fade, not a bare spinner) over grey placeholder cards, so the
 * screen already has the shape of the results. Announced politely to screen readers.
 */
@Composable
fun LoadingView(message: String, modifier: Modifier = Modifier, cards: Int = 2) {
    val pulse = rememberInfiniteTransition()
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(Res.drawable.logo_icon), contentDescription = null, Modifier.width(96.dp).scale(0.92f + 0.08f * t).alpha(0.65f + 0.35f * t))
            Spacer(Modifier.height(8.dp))
            Text(message, color = Palette.Muted)
        }
        repeat(cards) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Subtle).padding(16.dp).alpha(0.55f + 0.45f * t),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(110.dp).height(22.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Line))
                Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Line))
                Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Line))
            }
        }
    }
}
