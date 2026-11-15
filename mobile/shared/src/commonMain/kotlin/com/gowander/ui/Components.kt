package com.gowander.ui

import com.gowander.Prefs
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.gowander.nav.Tab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.resources.Res
import com.gowander.resources.bevietnampro_bold
import com.gowander.resources.bevietnampro_medium
import com.gowander.resources.bevietnampro_regular
import com.gowander.resources.bevietnampro_semibold
import org.jetbrains.compose.resources.Font

/** Light or dark look (owner, 15-11-2026), kept on the device. Every [Palette] colour reads it, so a change redraws the app. */
object Theme {
    private const val KEY = "theme.dark"
    var dark by mutableStateOf(Prefs.getBool(KEY))
        private set

    fun set(value: Boolean) {
        dark = value
        Prefs.setBool(KEY, value)
    }
}

/**
 * "ui" theme of GoWander-Design-Kit-v4 (ADR-030), with a dark counterpart for each colour. Colours that carry text
 * flip so that text stays readable (ContrastTest checks both looks).
 */
object Palette {
    private fun pick(light: Long, dark: Long) = Color(if (Theme.dark) dark else light)
    // Dark look: slate-navy surfaces in the family of the dark map style, lighter lines so cards stand off the page.
    val Bg get() = pick(0xFFFFFFFF, 0xFF121A26)
    val Subtle get() = pick(0xFFF6FAFA, 0xFF1B2534)
    val Raised get() = pick(0xFFFFFFFF, 0xFF2C394C) // the chosen item inside a Subtle track: lighter than the track in both looks
    val Line get() = pick(0xFFE5F2F1, 0xFF344257)
    val Ink get() = pick(0xFF123C48, 0xFFE8EEF5)
    val Muted get() = pick(0xFF52717A, 0xFFA6B3C4)
    val Primary get() = pick(0xFF047A7D, 0xFF45BDB9)
    val OnPrimary get() = pick(0xFFFFFFFF, 0xFF0C1620)
    val Soft get() = pick(0xFFDDF7F0, 0xFF15343B)
    val Warn get() = pick(0xFFFFF4D6, 0xFF3B3420)
    val Accent get() = pick(0xFFC8234F, 0xFFFF7A99)
    val AccentSoft get() = pick(0xFFFDE7EC, 0xFF3E2331)
    val Metro get() = pick(0xFF0079B8, 0xFF6BBEF2)
    val Bus get() = pick(0xFF08805F, 0xFF5AD3A6)
    val Surface get() = Bg
    val Pin get() = Accent
    // UX-2 canvas colours (screens 1-17): star rating, guide sections, profile theme.
    val Amber get() = pick(0xFF7A4E0E, 0xFFF2B95C)
    val Sky get() = pick(0xFFDCEFF3, 0xFF1F3147)
    val Body get() = pick(0xFF3D4F57, 0xFFC6D0DC)
    val Violet get() = pick(0xFF6B4FBB, 0xFFA18BE6)
    val VioletDark get() = pick(0xFF5A3FA8, 0xFFBBA8F5) // violet as text and icons
    val VioletSoft get() = pick(0xFFF1ECFB, 0xFF2C2947)
    val Green get() = pick(0xFFD6F0DF, 0xFF1F3A30)
    // Backgrounds that carry white text in both looks.
    val Deep get() = pick(0xFF123C48, 0xFF1A2B3F)
    val Night get() = pick(0xFF2A5563, 0xFF26384F)
    val VioletFill = Color(0xFF5A3FA8)
    val Gold = Color(0xFFFFD167)
}

private val LocalKitFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** Provides Be Vietnam Pro to every [Text] below (ADR-030). */
@Composable
fun KitTheme(content: @Composable () -> Unit) {
    val family = FontFamily(
        Font(Res.font.bevietnampro_regular, FontWeight.Normal),
        Font(Res.font.bevietnampro_medium, FontWeight.Medium),
        Font(Res.font.bevietnampro_semibold, FontWeight.SemiBold),
        Font(Res.font.bevietnampro_bold, FontWeight.Bold),
    )
    CompositionLocalProvider(LocalKitFont provides family, content = content)
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = Palette.Ink,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    bold: Boolean = false,
) = BasicText(
    text,
    modifier,
    style = TextStyle(fontFamily = LocalKitFont.current, fontSize = size, color = color, fontWeight = if (bold) FontWeight.SemiBold else weight),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

/** Screen title (28 Bold in the kit), announced as a heading. */
@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: TextUnit = 28.sp) =
    Text(text, modifier.semantics { heading() }, size = size, weight = FontWeight.Bold)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.semantics { heading() }, size = 18.sp, weight = FontWeight.SemiBold)

@Composable
fun Note(text: String, modifier: Modifier = Modifier) = Text(text, modifier, size = 13.sp, color = Palette.Muted)

@Composable
fun Icon(icon: ImageVector, size: Dp = 24.dp, tint: Color = Palette.Ink, modifier: Modifier = Modifier) =
    Image(rememberVectorPainter(icon), contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))

/** Kit shadow: y 6, blur 20, ink at 14 %. */
fun Modifier.kitShadow(shape: Shape): Modifier =
    shadow(10.dp, shape, ambientColor = Palette.Ink.copy(alpha = 0.14f), spotColor = Palette.Ink.copy(alpha = 0.14f))

enum class ButtonKind { Primary, Secondary, Soft, Accent, Outline, Violet, VioletOutline }

/** Kit button: radius 14, padding 15/18, label 15 SemiBold, optional leading icon; 48 dp minimum touch target. */
@Composable
fun Button(label: String, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.Primary, icon: ImageVector? = null, compact: Boolean = false, pill: Boolean = false, onClick: () -> Unit) {
    val (bg, fg, border) = when (kind) {
        ButtonKind.Primary -> Triple(Palette.Primary, Palette.OnPrimary, null)
        ButtonKind.Secondary -> Triple(Palette.Bg, Palette.Ink, Palette.Line)
        ButtonKind.Soft -> Triple(Palette.Soft, Palette.Primary, null)
        ButtonKind.Accent -> Triple(Palette.Accent, Palette.OnPrimary, null)
        ButtonKind.Outline -> Triple(Palette.Bg, Palette.Primary, Palette.Primary)
        ButtonKind.Violet -> Triple(Palette.VioletFill, Color.White, null) // profile screens (canvas 11, 12, 17)
        ButtonKind.VioletOutline -> Triple(Palette.Bg, Palette.Ink, Palette.Violet)
    }
    // pill: the round-ended buttons of the UX-2 route screens (design screens 5-7).
    val shape = RoundedCornerShape(if (pill) 999.dp else 14.dp)
    Row(
        modifier.sizeIn(minHeight = 48.dp).clip(shape).background(bg, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = if (compact) 10.dp else 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, if (compact) 16.dp else 18.dp, fg)
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        }
        // One line always: a label broken over two lines looked broken on iPhone (owner, 14-11-2026); compact buttons
        // use a smaller size so three fit side by side on a 375 pt screen (UxComponentTest checks it).
        Text(label, size = if (compact) 14.sp else 15.sp, color = fg, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Kit chip: pill, 14 Medium; selected = primary fill. A check mark also marks selection (not colour only, UX 12.3). */
@Composable
fun Chip(label: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.sizeIn(minHeight = 48.dp).clip(shape)
            .background(if (selected) Palette.Primary else Palette.Bg, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, Palette.Line, shape))
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(KitIcons.check, 14.dp, Palette.OnPrimary)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, size = 14.sp, color = if (selected) Palette.OnPrimary else Palette.Ink, weight = FontWeight.Medium)
    }
}

/**
 * Segmented mode tabs (as in Google Maps, Naver Map, KakaoMap): equal-width icon + label, one selected.
 * Each tab is a 56 dp tall touch target and reports its selected state to screen readers.
 */
@Composable
fun ModeTabs(items: List<Pair<ImageVector, String>>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Palette.Subtle, shape).padding(4.dp)) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(
                Modifier.weight(1f).sizeIn(minHeight = 56.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.Raised else Palette.Subtle)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, 22.dp, if (on) Palette.Primary else Palette.Muted)
                Text(label, size = 12.sp, color = if (on) Palette.Primary else Palette.Muted, weight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Round 48 dp icon button with a spoken label (the icon alone is not announced). */
@Composable
fun IconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Palette.Primary, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 20.dp, tint) }
}

/** Kit card: radius 20, padding 16, line border, or shadow when floating. */
@Composable
fun Card(modifier: Modifier = Modifier, shadow: Boolean = false, bg: Color = Palette.Bg, gap: Dp = 12.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.then(if (shadow) Modifier.kitShadow(shape) else Modifier).clip(shape).background(bg, shape)
            .then(if (shadow) Modifier else Modifier.border(1.dp, Palette.Line, shape))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Round icon badge (kit `badge`): 40 dp by default, icon at half size. */
@Composable
fun Badge(icon: ImageVector, bg: Color, fg: Color, size: Dp = 40.dp) =
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, size / 2, fg) }

enum class ModeKind { Walk, Metro, Bus, Bicycle, Car }

/** Kit `mode` pill: icon + short label, radius 8; walking is light, metro/bus filled with their colour. */
@Composable
fun ModePill(kind: ModeKind, label: String) {
    val (bg, fg, icon) = when (kind) {
        ModeKind.Walk -> Triple(Palette.Subtle, Palette.Ink, KitIcons.walk)
        ModeKind.Metro -> Triple(Palette.Metro, Palette.OnPrimary, KitIcons.metro)
        ModeKind.Bus -> Triple(Palette.Bus, Palette.OnPrimary, KitIcons.bus)
        ModeKind.Bicycle -> Triple(Palette.Subtle, Palette.Ink, ExtraIcons.bike)
        ModeKind.Car -> Triple(Palette.Subtle, Palette.Ink, ExtraIcons.car)
    }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 14.dp, fg)
        Spacer(Modifier.width(4.dp))
        Text(label, size = 13.sp, color = fg, weight = FontWeight.SemiBold)
    }
}

/** Kit `row`: optional lead, title 15 SemiBold + subtitle 13 muted, chevron when clickable. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    lead: (@Composable () -> Unit)? = null,
    trail: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) {
            lead()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2)
            if (subtitle != null) Text(subtitle, size = 13.sp, color = Palette.Muted, maxLines = 2)
        }
        when {
            trail != null -> { Spacer(Modifier.width(12.dp)); trail() }
            onClick != null -> { Spacer(Modifier.width(12.dp)); Icon(KitIcons.chevron, 16.dp, Palette.Muted) }
        }
    }
}

/** Kit `header`: round back button (kit 44 dp, raised to the 48 dp touch target) + 20 SemiBold title. */
@Composable
fun Header(title: String, modifier: Modifier = Modifier, floating: Boolean = false, onBack: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).then(if (floating) Modifier.kitShadow(CircleShape) else Modifier).clip(CircleShape).background(Palette.Bg)
                .then(if (floating) Modifier else Modifier.border(1.dp, Palette.Line, CircleShape))
                .semantics { contentDescription = Strings.BACK }
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(KitIcons.back, 18.dp) }
        Spacer(Modifier.width(10.dp))
        if (floating && title.isNotEmpty()) {
            // Over a map the title needs its own background, or it mixes with street labels.
            Text(
                title, Modifier.kitShadow(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Palette.Bg)
                    .padding(horizontal = 14.dp, vertical = 10.dp).semantics { heading() },
                size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
        } else {
            Text(title, Modifier.weight(1f).semantics { heading() }, size = 20.sp, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Tappable search field look (kit `search`): icon + placeholder, radius 16. */
@Composable
fun SearchBar(placeholder: String, modifier: Modifier = Modifier, floating: Boolean = false, bg: Color = Palette.Bg, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.sizeIn(minHeight = 52.dp).then(if (floating) Modifier.kitShadow(shape) else Modifier).clip(shape).background(bg, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KitIcons.search, 18.dp, Palette.Muted)
        Spacer(Modifier.width(10.dp))
        Text(placeholder, size = 15.sp, color = Palette.Muted, maxLines = 1)
    }
}

/** Status banner (kit `weather` / `offline` shape): icon + bold head + muted detail. */
@Composable
fun Banner(head: String, detail: String? = null, icon: ImageVector = KitIcons.flag, bg: Color = Palette.Warn, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 18.dp, if (bg == Palette.AccentSoft) Palette.Accent else Palette.Ink)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(head, size = 14.sp, weight = FontWeight.SemiBold)
            if (detail != null) Text(detail, size = 12.sp, color = Palette.Muted)
        }
    }
}

/** The data has no photos yet: the kit's wireframe tile (soft fill + icon) stands in. */
@Composable
fun PhotoTile(icon: ImageVector = KitIcons.landmark, size: Dp = 56.dp, radius: Dp = 14.dp) =
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(Palette.Soft), contentAlignment = Alignment.Center) {
        Icon(icon, (size / 3).coerceIn(14.dp, 28.dp), Palette.Primary)
    }

/**
 * Kit bottom navigation, UX-2 (ADR-034): Bản đồ · Khám phá · raised "Đi đâu?" · Hành trình · Hồ sơ.
 * The centre is an action (opens the destination search), not a tab, so it has the button role and no selected state.
 * Five equal slots: fits a 320 dp screen without cutting labels (iOS UI test).
 */
@Composable
fun BottomNav(active: Tab, modifier: Modifier = Modifier, onGo: () -> Unit, onSelect: (Tab) -> Unit) {
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 32.dp).background(Palette.Bg).border(1.dp, Palette.Line).padding(bottom = 8.dp),
        ) {
            NavItem(Tab.Map, KitIcons.map, Strings.TAB_MAP, active, onSelect)
            NavItem(Tab.Discover, KitIcons.compass, Strings.TAB_DISCOVER, active, onSelect)
            Spacer(Modifier.weight(1f))
            NavItem(Tab.Journey, KitIcons.book, Strings.TAB_JOURNEY, active, onSelect)
            NavItem(Tab.Profile, KitIcons.user, Strings.TAB_PROFILE, active, onSelect)
        }
        // Centre action: 64 dp primary circle with a 6 dp white ring, raised above the bar like the kit.
        // The whole column (circle + label) is one tap target: tapping the label must work too (found in E2E).
        Column(
            Modifier.align(Alignment.TopCenter).semantics(mergeDescendants = true) {}.clickable(role = Role.Button, onClick = onGo),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Palette.Bg).padding(6.dp).clip(CircleShape).background(Palette.Primary),
                contentAlignment = Alignment.Center,
            ) { Icon(KitIcons.route, 28.dp, Palette.OnPrimary) }
            Text(Strings.TAB_GO, Modifier.offset(y = (-2).dp), size = 12.sp, color = Palette.Ink, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, icon: ImageVector, label: String, active: Tab, onSelect: (Tab) -> Unit) {
    val on = tab == active
    Column(
        Modifier.weight(1f).sizeIn(minHeight = 48.dp).semantics { selected = on }
            .clickable(role = Role.Tab) { onSelect(tab) }.padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Active indicator plus colour: two cues, not colour alone. It sits on the bar's top edge and is half the tab
        // wide, so it scales with the screen (owner, 15-11-2026).
        Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).background(if (on) Palette.Primary else Color.Transparent))
        Spacer(Modifier.size(18.dp))
        Icon(icon, 24.dp, if (on) Palette.Primary else Palette.Muted)
        Text(label, size = 12.sp, color = if (on) Palette.Primary else Palette.Muted, weight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** M06 call sites: primary action. */
@Composable
fun Action(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) = Button(text, modifier, onClick = onClick)

/** M06 call sites: plain label bubble. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, background: Color = Palette.Bg) = Text(
    text,
    modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 12.dp, vertical = 8.dp),
    size = 14.sp,
)
