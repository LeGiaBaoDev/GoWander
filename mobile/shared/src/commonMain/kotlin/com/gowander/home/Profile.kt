package com.gowander.home

import com.gowander.ui.Theme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.account.AccountStore
import com.gowander.account.InitialAvatar
import com.gowander.mock.MockPortfolio
import com.gowander.mock.Plan
import com.gowander.mock.PlanStatus
import com.gowander.mock.planEnd
import com.gowander.ui.Badge
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Card
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.IconButton
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.MockPhoto
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.SectionTitle
import com.gowander.ui.Segmented
import com.gowander.ui.Strings
import com.gowander.ui.Text
import com.gowander.ui.kitShadow
import com.gowander.route.categoryIcon

/** Profile theme of the canvas (screens 11, 12, 17): violet, so the user's own space looks different from the city. */
private val VIOLET_THEMES get() = listOf(Palette.Primary, Palette.Bus, Palette.Metro, Palette.Accent, Color(0xFFB7791F), Palette.Violet)

/**
 * UX-2 screen 11 + "Đã lưu": the Profile tab. Portfolio and its numbers are sample data (accounts are COM-1); saved
 * places are the user's own ([AppStore]). Plans live in the Journey tab (owner, 15-11-2026).
 */
@Composable
fun ProfileScreen(
    store: AppStore,
    account: AccountStore,
    onDesign: () -> Unit,
    onSettings: () -> Unit,
    onPlace: (String) -> Unit,
    onTrip: (String) -> Unit,
    onWelcome: () -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onVerifyEmail: () -> Unit,
    onAccountSettings: () -> Unit,
    onSearchHistory: () -> Unit,
    onCompleteProfile: () -> Unit,
) {
    val notice = LocalNotice.current
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val me = account.user
            if (tab == 0 && me != null) {
                InitialAvatar(me.name, 40)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(me.name, size = 17.sp, weight = FontWeight.Bold, maxLines = 1)
                    Text("@" + (me.username ?: ""), size = 12.sp, color = Palette.Muted, maxLines = 1)
                }
            } else if (tab == 0) Text(Strings.TAB_PROFILE, Modifier.weight(1f).semantics { heading() }, size = 15.sp, weight = FontWeight.Bold)
            else {
                Avatar(40)
                Text(MockPortfolio.NAME, Modifier.weight(1f).padding(start = 10.dp), size = 17.sp, weight = FontWeight.Bold)
            }
            if (tab == 0) {
                Button(
                    Strings.DESIGN, kind = ButtonKind.Violet, icon = KitIcons.pen, compact = true, pill = true,
                    // The portfolio belongs to an account: a guest is asked to sign in first (ADR-040 §6.3).
                    onClick = if (account.isSignedIn) onDesign else onWelcome,
                )
                IconButton(ExtraIcons.share, Strings.SHARE_PORTFOLIO, tint = Palette.Ink) { notice(Strings.SHARE_LATER) }
            }
            IconButton(KitIcons.gear, Strings.SETTINGS, tint = Palette.Ink, onClick = onSettings)
        }
        Segmented(listOf(Strings.PORTFOLIO, Strings.SAVED), tab, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Palette.VioletDark) { tab = it }
        if (tab == 0) AccountStrip(account, onWelcome, onSignIn, onRegister, onVerifyEmail, onAccountSettings, onCompleteProfile)
        if (tab == 0) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).sizeIn(minHeight = 52.dp).clickable(role = Role.Button, onClick = onSearchHistory),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KitIcons.clock, 20.dp, Palette.Muted)
            Text("Lịch sử tìm kiếm", Modifier.weight(1f).padding(start = 12.dp), size = 15.sp, weight = FontWeight.SemiBold)
            Icon(KitIcons.chevron, 16.dp, Palette.Muted)
        }
        when (tab) {
            0 -> Portfolio(onTrip, onDesign)
            else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (store.saved.isEmpty()) Note(Strings.SAVED_EMPTY)
                store.saved.forEach { p -> ListRow(p.name, p.category, lead = { MockPhoto(p.id, Modifier.size(48.dp), categoryIcon(null), radius = 12.dp) }, onClick = { onPlace(p.id) }) }
            }
        }
    }
}

@Composable
private fun Avatar(size: Int) = Box(
    Modifier.size(size.dp).clip(CircleShape).background(Palette.Violet).padding((size / 16).dp).clip(CircleShape).background(Palette.Soft),
    contentAlignment = Alignment.Center,
) { Text(MockPortfolio.INITIAL, size = (size * 0.38).sp, color = Palette.Primary, weight = FontWeight.Bold) }

@Composable
private fun Polaroid(seed: String, modifier: Modifier, angle: Float, side: Int = 112) =
    Column(modifier.rotate(angle).kitShadow(RoundedCornerShape(2.dp)).background(Color.White).padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 26.dp)) {
        MockPhoto(seed, Modifier.size(side.dp), KitIcons.camera, radius = 0.dp)
    }

@Composable
private fun SectionTitleViolet(text: String) = Text(text, Modifier.padding(horizontal = 4.dp).semantics { heading() }, size = 20.sp, weight = FontWeight.ExtraBold, color = Palette.VioletDark)

/** Screen 11: cover with polaroids, numbers, about, featured trip, discovery map, contributions, moments, privacy. */
@Composable
private fun Portfolio(onTrip: (String) -> Unit, onDesign: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Note(Strings.PORTFOLIO_SAMPLE)
        Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(24.dp)).background(Palette.VioletSoft)) {
            Column(Modifier.padding(start = 20.dp, top = 24.dp).width(190.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Strings.JOURNEY_OF, size = 24.sp, weight = FontWeight.Medium)
                Text(MockPortfolio.NAME, size = 34.sp, weight = FontWeight.ExtraBold, color = Palette.VioletDark)
                Text(MockPortfolio.TAGLINE, Modifier.padding(top = 8.dp), size = 13.sp, color = Palette.Body)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(KitIcons.pin, 14.dp, Palette.VioletDark)
                    Spacer(Modifier.width(6.dp))
                    Text(MockPortfolio.CITY, size = 12.sp, color = Palette.VioletDark, weight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.align(Alignment.BottomStart).padding(20.dp)) { Avatar(64) }
            Polaroid("cover1", Modifier.align(Alignment.TopEnd).offset(x = (-60).dp, y = 30.dp), -8f, 96)
            Polaroid("cover2", Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 92.dp), 7f, 96)
            Polaroid("cover3", Modifier.align(Alignment.TopEnd).offset(x = (-70).dp, y = 176.dp), -3f, 96)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.VioletFill).padding(vertical = 16.dp, horizontal = 8.dp)) {
            MockPortfolio.STATS.forEach { (n, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n, size = 24.sp, color = Color.White, weight = FontWeight.ExtraBold)
                    Text(label, size = 11.sp, color = Color.White, maxLines = 1)
                }
            }
        }
        Text(Strings.ABOUT_ME, Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() }, size = 24.sp, weight = FontWeight.Medium)
        Text(MockPortfolio.ABOUT, Modifier.padding(horizontal = 4.dp), size = 14.sp, color = Palette.Body)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { SectionTitleViolet(Strings.FEATURED_TRIPS) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Subtle).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp)) {
                MockPhoto("featured", Modifier.width(230.dp).height(190.dp), KitIcons.landmark, radius = 16.dp)
                Polaroid("featured2", Modifier.align(Alignment.TopEnd).offset(y = 18.dp), 6f, 92)
            }
            Text(MockPortfolio.FEATURED_TITLE, size = 18.sp, weight = FontWeight.Bold)
            Text(MockPortfolio.FEATURED_META, size = 12.sp, color = Palette.Muted)
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxSize().background(Palette.Violet))
                Text(MockPortfolio.FEATURED_QUOTE, Modifier.padding(start = 10.dp), size = 15.sp, weight = FontWeight.Medium)
            }
            Button(Strings.STORYBOOK, kind = ButtonKind.VioletOutline, compact = true, pill = true) { onTrip("kien-truc") }
        }
        Row(Modifier.padding(top = 12.dp)) { SectionTitleViolet(Strings.DISCOVERY_MAP) }
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Subtle)) {
            listOf(0.33f to 0.45f, 0.42f to 0.35f, 0.27f to 0.6f, 0.58f to 0.3f, 0.5f to 0.65f, 0.17f to 0.2f, 0.78f to 0.4f, 0.66f to 0.75f).forEach { (x, y) ->
                Box(Modifier.offset((x * 340).dp, (y * 190).dp).size(14.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(Palette.Violet))
            }
            Text(MockPortfolio.MAP_SUMMARY, Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Bg).padding(horizontal = 10.dp, vertical = 6.dp), size = 12.sp, weight = FontWeight.Bold)
        }
        Row(Modifier.padding(top = 12.dp)) { SectionTitleViolet(Strings.CONTRIBUTIONS) }
        MockPortfolio.CONTRIBUTIONS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (n, label) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Text(n, size = 26.sp, weight = FontWeight.ExtraBold)
                        Text(label, size = 13.sp, color = Palette.Body)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Subtle).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(MockPortfolio.RECENT_REVIEW_PLACE, size = 12.sp, color = Palette.Muted, weight = FontWeight.Bold)
            Text("★★★★★", size = 13.sp, color = Palette.Amber)
            Text(MockPortfolio.RECENT_REVIEW, size = 14.sp)
        }
        Note(Strings.CONTRIBUTIONS_RULE)
        Text(Strings.MOMENTS, Modifier.padding(top = 12.dp, start = 4.dp).semantics { heading() }, size = 26.sp, weight = FontWeight.Medium)
        repeat(MockPortfolio.MOMENTS / 3) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { c -> MockPhoto("moment$r$c", Modifier.weight(1f).height(112.dp), KitIcons.camera, radius = 12.dp) } }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Violet, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KitIcons.lock, 20.dp, Palette.VioletDark)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${Strings.WHO_SEES}: ${Strings.LINK_HOLDERS}", size = 13.sp, weight = FontWeight.SemiBold)
                Text(Strings.PER_SECTION_PRIVATE, size = 13.sp, color = Palette.Muted)
            }
            Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onDesign), contentAlignment = Alignment.CenterEnd) {
                Text(Strings.CHANGE, size = 13.sp, color = Palette.VioletDark, weight = FontWeight.Bold)
            }
        }
    }
}

/** Screen 17: upcoming plan with Sửa / Bắt đầu, drafts, done, and a new plan. */

/** UX-2 screen 12: layout, theme colour and per-section visibility (sample; saving needs accounts, COM-1). */
@Composable
fun PortfolioEditScreen(onBack: () -> Unit) {
    val notice = LocalNotice.current
    var layout by rememberSaveable { mutableStateOf(0) }
    var colour by rememberSaveable { mutableStateOf(VIOLET_THEMES.lastIndex) }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(KitIcons.back, Strings.BACK, tint = Palette.Ink, onClick = onBack)
            Text(Strings.DESIGN_PORTFOLIO, Modifier.weight(1f).semantics { heading() }, size = 18.sp, weight = FontWeight.Bold)
            Button(Strings.SAVE, kind = ButtonKind.Violet, compact = true, pill = true) { notice(Strings.PORTFOLIO_SAVE_LATER); onBack() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(Strings.LAYOUT, size = 14.sp, weight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Polaroid", "Tạp chí", "Tối giản").forEachIndexed { i, name ->
                    val on = i == layout
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Palette.VioletSoft else Palette.Bg)
                            .border(if (on) 2.dp else 1.dp, if (on) Palette.Violet else Palette.Line, RoundedCornerShape(14.dp))
                            .semantics { selected = on }.clickable(role = Role.RadioButton) { layout = i }.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MockPhoto(name, Modifier.fillMaxWidth().height(70.dp), if (i == 0) KitIcons.camera else KitIcons.book, radius = 8.dp)
                        Text(name, size = 12.sp, weight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                    }
                }
            }
            Text(Strings.THEME_COLOUR, size = 14.sp, weight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VIOLET_THEMES.forEachIndexed { i, c ->
                    val on = i == colour
                    Box(Modifier.size(48.dp).semantics { selected = on; contentDescription = "${Strings.THEME_COLOUR} ${i + 1}" }.clickable(role = Role.RadioButton) { colour = i }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c).then(if (on) Modifier.border(3.dp, Color.White, CircleShape) else Modifier))
                        if (on) Icon(KitIcons.check, 16.dp, Color.White)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.SECTIONS, Modifier.weight(1f), size = 14.sp, weight = FontWeight.Bold)
                Text(Strings.VISIBILITY, size = 12.sp, color = Palette.Muted)
            }
            Column(Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Palette.Line, RoundedCornerShape(16.dp))) {
                MockPortfolio.SECTIONS.forEachIndexed { i, sec ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
                    Row(Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sec.title, Modifier.weight(1f), size = 14.sp, weight = FontWeight.SemiBold)
                        Text(sec.visibility, size = 12.sp, color = if (sec.visibility == "Chỉ mình tôi") Palette.Accent else Palette.Muted, weight = FontWeight.SemiBold)
                    }
                }
            }
            Note(Strings.PRIVACY_NOTE, Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Subtle).padding(12.dp))
        }
    }
}

/** Gear of the Profile tab: guest mode and the data sources with their licences (OSM ODbL attribution must stay). */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(Strings.SETTINGS, onBack = onBack)
        Card(gap = 6.dp) {
            Text(Strings.GUEST, bold = true)
            Text(Strings.GUEST_NOTE, color = Palette.Muted)
        }
        SectionTitle(Strings.APPEARANCE)
        Segmented(listOf(Strings.LIGHT, Strings.DARK), if (Theme.dark) 1 else 0) { Theme.set(it == 1) }
        SectionTitle(Strings.DATA_SOURCES)
        Strings.SOURCES.forEach { (what, source) -> ListRow(what, source, lead = { Badge(KitIcons.globe, Palette.Subtle, Palette.Primary) }) }
    }
}

/** A1 (guest) and A7 (signed in): the account block at the top of the Profile tab. */
@Composable
private fun AccountStrip(
    account: AccountStore,
    onWelcome: () -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onVerifyEmail: () -> Unit,
    onAccountSettings: () -> Unit,
    onCompleteProfile: () -> Unit,
) {
    val me = account.user
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (me == null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Soft).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Bg), contentAlignment = Alignment.Center) {
                        Icon(KitIcons.user, 20.dp, Palette.Primary)
                    }
                    Text(
                        "Đăng nhập để lưu portfolio, kế hoạch và gửi feedback nhanh hơn",
                        Modifier.padding(start = 12.dp),
                        size = 16.sp,
                        weight = FontWeight.Bold,
                    )
                }
                Text(
                    "Chưa đăng nhập vẫn dùng bản đồ bình thường. Đăng nhập để GoWander tự điền tên và email khi bạn gửi feedback.",
                    size = 12.5.sp,
                    color = Palette.Body,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Đăng nhập", Modifier.weight(1f), compact = true, pill = true, onClick = onSignIn)
                    Button("Tạo tài khoản", Modifier.weight(1f), kind = ButtonKind.VioletOutline, compact = true, pill = true, onClick = onRegister)
                }
                Box(Modifier.fillMaxWidth().sizeIn(minHeight = 40.dp).clickable(role = Role.Button, onClick = onWelcome), contentAlignment = Alignment.Center) {
                    Text("Xem các cách đăng nhập", size = 13.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
                }
            }
        } else {
            // The whole row opens the account page (A8); the gear at the top stays for the app's settings.
            Row(
                Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).clickable(role = Role.Button, onClick = onAccountSettings),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialAvatar(me.name, 48)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(me.name, size = 16.sp, weight = FontWeight.Bold, maxLines = 1)
                    Text(me.email ?: me.phone.orEmpty(), size = 12.5.sp, color = Palette.Muted, maxLines = 1)
                }
                if (me.emailVerified) Badge(KitIcons.check, Palette.Soft, Palette.Primary, size = 32.dp)
                Icon(KitIcons.chevron, 16.dp, Palette.Muted)
            }
            if (!me.hasPassword) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Soft).padding(12.dp).sizeIn(minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = onCompleteProfile),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KitIcons.lock, 18.dp, Palette.Primary)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Đặt mật khẩu để đăng nhập nhanh hơn", size = 13.5.sp, weight = FontWeight.SemiBold)
                        Text("Bạn vẫn đăng nhập được bằng Google", size = 12.sp, color = Palette.Muted)
                    }
                    Icon(KitIcons.chevron, 16.dp, Palette.Muted)
                }
            }
            if (!me.emailVerified) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Warn).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KitIcons.bell, 18.dp, Palette.Ink)
                    Text("Email chưa xác minh", Modifier.weight(1f).padding(start = 10.dp), size = 13.sp, weight = FontWeight.SemiBold)
                    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onVerifyEmail), contentAlignment = Alignment.CenterEnd) {
                        Text("Xác minh", size = 13.sp, color = Palette.Primary, weight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
