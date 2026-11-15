package com.gowander.discover

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.home.LocalNotice
import com.gowander.mock.Dish
import com.gowander.mock.MOCK_DISHES
import com.gowander.mock.MOCK_GUIDE_INTRO
import com.gowander.mock.MOCK_OTHER_REGION_INTRO
import com.gowander.mock.MOCK_REGIONS
import com.gowander.mock.MOCK_STORY_META
import com.gowander.mock.MOCK_STORY_TITLE
import com.gowander.mock.MOCK_TRIPS
import com.gowander.mock.Region
import com.gowander.mock.CardSide
import com.gowander.route.RouteMap
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.spatialk.geojson.Position
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.IconButton
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.MockPhoto
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.Stars
import com.gowander.ui.Strings
import com.gowander.ui.Text
import com.gowander.ui.kitShadow
import kotlinx.coroutines.launch

// Whole country in view, islands included (plan §9.2).
private val VIETNAM = CameraUpdate(center = Position(latitude = 15.9, longitude = 109.5), zoom = 4.05, pitch = 0.0, bearing = 0.0)
private val HOANG_SA = Position(latitude = 16.5, longitude = 112.0)
private val TRUONG_SA = Position(latitude = 10.0, longitude = 114.2)

/**
 * UX-2 screen 13: the real map (OpenStreetMap data) showing all of Vietnam, one card per region pinned to its real
 * coordinates with a stem to the point; TP.HCM is highlighted because routing exists there. Hoàng Sa and Trường Sa are
 * labelled. "Danh sách" shows the same regions as a list (map alternative). Which regions exist is still sample data.
 */
@Composable
fun RegionsMap(modifier: Modifier, onRegion: (String) -> Unit) {
    var asList by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().background(Palette.Sky)) {
        if (asList) Column(Modifier.fillMaxSize().background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            MOCK_REGIONS.forEach { r -> ListRow(r.name, r.area, lead = { MockPhoto(r.id, Modifier.size(48.dp), KitIcons.landmark, radius = 10.dp) }, onClick = { onRegion(r.id) }) }
        } else RouteMap(null, null, null, null, Modifier.fillMaxSize().semantics { contentDescription = Strings.VIETNAM_MAP }, focus = VIETNAM) {
            listOf(HOANG_SA to Strings.ISLANDS_NORTH, TRUONG_SA to Strings.ISLANDS_SOUTH).forEach { (at, label) ->
                Text(label, Modifier.placedAt(at).clip(RoundedCornerShape(6.dp)).background(Palette.Bg.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 1.dp), size = 10.sp, color = Palette.Body)
            }
            MOCK_REGIONS.forEach { r ->
                val at = Position(latitude = r.lat, longitude = r.lon)
                val align = when (r.side) {
                    CardSide.Left -> Alignment.CenterEnd; CardSide.Right -> Alignment.CenterStart
                    CardSide.Above -> Alignment.BottomCenter; CardSide.Below -> Alignment.TopCenter
                    CardSide.AboveRight -> Alignment.BottomStart; CardSide.BelowRight -> Alignment.TopStart
                }
                RegionPin(r, Modifier.placedAt(at, align)) { onRegion(r.id) }
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp).sizeIn(minHeight = 48.dp)
                .kitShadow(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Palette.Bg)
                .clickable(role = Role.Button) { asList = !asList }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (asList) KitIcons.map else ExtraIcons.list, 18.dp, Palette.Primary)
            Spacer(Modifier.width(8.dp))
            Text(if (asList) Strings.TAB_MAP else Strings.LIST, size = 13.sp, weight = FontWeight.SemiBold)
        }
    }
}

/** Card + stem + dot; the dot sits on the region's point, the card on the side its [Region.side] says. */
@Composable
private fun RegionPin(r: Region, modifier: Modifier, onClick: () -> Unit) {
    val dot: @Composable () -> Unit = {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.Bg).padding(2.dp).clip(CircleShape).background(if (r.routing) Palette.Primary else Palette.Accent))
    }
    val stemH: @Composable () -> Unit = { Box(Modifier.size(12.dp, 2.dp).background(Palette.Primary)) }
    val stemV: @Composable () -> Unit = { Box(Modifier.size(2.dp, 12.dp).background(Palette.Primary)) }
    val card: @Composable () -> Unit = { RegionCard(r, Modifier, onClick) }
    when (r.side) {
        CardSide.Left -> Row(modifier, verticalAlignment = Alignment.CenterVertically) { card(); stemH(); dot() }
        CardSide.Right -> Row(modifier, verticalAlignment = Alignment.CenterVertically) { dot(); stemH(); card() }
        CardSide.Above -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { card(); stemV(); dot() }
        CardSide.Below -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { dot(); stemV(); card() }
        CardSide.AboveRight -> Column(modifier) { Box(Modifier.padding(start = 10.dp)) { card() }; Box(Modifier.padding(start = 5.dp)) { stemV() }; dot() }
        CardSide.BelowRight -> Column(modifier) { dot(); Box(Modifier.padding(start = 5.dp)) { stemV() }; Box(Modifier.padding(start = 10.dp)) { card() } }
    }
}

@Composable
private fun RegionCard(r: Region, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.kitShadow(shape).clip(shape).background(Palette.Bg).border(if (r.routing) 2.dp else 1.dp, if (r.routing) Palette.Primary else Palette.Line, shape)
            .semantics(mergeDescendants = true) { contentDescription = "${r.name}, ${r.area}, ${Strings.OPEN_GUIDE}" }
            .clickable(role = Role.Button, onClick = onClick).padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MockPhoto(r.id, Modifier.size(40.dp), KitIcons.landmark, radius = 10.dp, iconSize = 18.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(r.name, size = 14.sp, weight = if (r.routing) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1)
            Text(r.area, size = if (r.routing) 10.sp else 11.sp, color = if (r.routing) Palette.Primary else Palette.Muted, weight = if (r.routing) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        }
    }
}

/** Big outlined number + title of a guide section ("01 GIỚI THIỆU CHUNG"). */
@Composable
private fun SectionHead(num: String, title: String, dark: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(num, size = 40.sp, weight = FontWeight.Black, color = if (dark) Palette.Gold else Palette.Primary.copy(alpha = 0.35f))
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.padding(bottom = 6.dp).semantics { heading() }, size = 22.sp, weight = FontWeight.ExtraBold, color = if (dark) Color.White else Palette.Ink)
    }
}

/**
 * UX-2 screen 14: region guide (sample content for TP.HCM; other regions only say the guide is coming). The table of
 * contents scrolls to each numbered section.
 */
@Composable
fun RegionGuideScreen(
    id: String,
    onBack: () -> Unit,
    onHighlight: (String, Double, Double) -> Unit,
    onDish: (String) -> Unit,
    onTrip: (String) -> Unit,
    onPlan: () -> Unit,
    onRoute: () -> Unit,
    onShowMap: () -> Unit,
) {
    val r = MOCK_REGIONS.firstOrNull { it.id == id } ?: MOCK_REGIONS.first { it.routing }
    val notice = LocalNotice.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val at = remember { mutableStateMapOf<Int, Int>() }
    fun Modifier.anchor(i: Int) = onGloballyPositioned { at[i] = it.positionInParentY() }
    Column(Modifier.fillMaxSize().background(Palette.Bg).verticalScroll(scroll).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(bottom = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(400.dp)) {
            MockPhoto(r.id + "cover", Modifier.fillMaxSize(), KitIcons.landmark, radius = 0.dp, iconSize = 48.dp)
            Box(Modifier.fillMaxSize().background(Color(0x73082830)))
            Box(Modifier.safeDrawingPadding().padding(16.dp)) { Header("", floating = true, onBack = onBack) }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Strings.GUIDE, size = 14.sp, color = Color.White, weight = FontWeight.Bold)
                Text(if (r.routing) "TP. HỒ CHÍ MINH" else r.name.uppercase(), Modifier.semantics { heading() }, size = 40.sp, color = Color.White, weight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.routing) Text(Strings.HAS_ROUTING, Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Bg).padding(horizontal = 14.dp, vertical = 6.dp), size = 13.sp, color = Palette.Primary, weight = FontWeight.Bold)
                    Button(Strings.VIDEO, kind = ButtonKind.Accent, icon = ExtraIcons.play, compact = true, pill = true) { notice(Strings.VIDEO_LATER) }
                }
            }
            Text(Strings.SAMPLE_PHOTO_CREDIT, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 64.dp, end = 8.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Ink.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp), size = 10.sp, color = Color.White)
        }
        if (!r.routing) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(MOCK_OTHER_REGION_INTRO, size = 14.sp, color = Palette.Body)
                Button(Strings.PLAN_YOURSELF, Modifier.fillMaxWidth(), icon = KitIcons.plus, pill = true, onClick = onPlan)
            }
            return@Column
        }
        val toc = listOf("01 Giới thiệu", "02 Nổi bật", "03 Ẩm thực", "04 Văn hóa", "05 Lên chuyến")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            toc.forEachIndexed { i, label ->
                Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button) { scope.launch { scroll.animateScrollTo(at[i] ?: 0) } }, contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(17.dp)).background(if (i == 0) Palette.Ink else Palette.Bg).border(1.dp, if (i == 0) Palette.Ink else Palette.Line, RoundedCornerShape(17.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        size = 12.sp, color = if (i == 0) Color.White else Palette.Ink, weight = FontWeight.Bold,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Column(Modifier.anchor(0).padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHead("01", "GIỚI THIỆU CHUNG")
            Text(MOCK_GUIDE_INTRO, size = 14.sp, color = Palette.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Season(Modifier.weight(1f), Palette.Warn, Palette.Amber, "MÙA KHÔ", "Tháng 12 – 4", "Nắng, ít mưa: hợp đi bộ")
                Season(Modifier.weight(1f), Palette.Sky, Color(0xFF0B5F7A), "MÙA MƯA", "Tháng 5 – 11", "Mưa rào chiều, mang áo mưa")
            }
        }
        Column(Modifier.anchor(1).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(horizontal = 20.dp)) { SectionHead("02", "ĐIỂM NỔI BẬT") }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple("Bưu điện Trung tâm", "Kiến trúc", 10.77984 to 106.69995), Triple("Chợ Bến Thành", "Chợ", 10.77254 to 106.69797), Triple("Nhà hát Thành phố", "Kiến trúc", 10.77667 to 106.70305)).forEach { (name, kind, ll) ->
                    Column(
                        Modifier.width(220.dp).kitShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Palette.Bg)
                            .semantics(mergeDescendants = true) { contentDescription = "$name, $kind" }
                            .clickable(role = Role.Button) { onHighlight(name, ll.first, ll.second) },
                    ) {
                        MockPhoto(name, Modifier.fillMaxWidth().height(150.dp), KitIcons.landmark, radius = 0.dp)
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(name, size = 15.sp, weight = FontWeight.Bold)
                            Row { Text("$kind · ", size = 12.sp, color = Palette.Muted); Stars(com.gowander.mock.mockRating(name)) }
                        }
                    }
                }
            }
        }
        Column(Modifier.anchor(2).padding(top = 20.dp).fillMaxWidth().background(Palette.Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHead("03", "ẨM THỰC", dark = true)
            Text(Strings.DISH_PICKS, Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Gold).padding(horizontal = 12.dp, vertical = 4.dp), size = 12.sp, weight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOCK_DISHES.forEach { d ->
                    Column(Modifier.weight(1f).clickable(role = Role.Button) { onDish(d.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Night), contentAlignment = Alignment.Center) { Icon(KitIcons.star, 24.dp, Palette.Line) }
                        Text("${d.name} ›", size = 13.sp, color = Color.White, weight = FontWeight.Bold)
                    }
                }
            }
            Button(Strings.SHOP_NEAR_ON_MAP, kind = ButtonKind.Secondary, compact = true, pill = true, onClick = onShowMap)
        }
        Column(Modifier.anchor(3).padding(start = 20.dp, end = 20.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHead("04", "CON NGƯỜI & VĂN HÓA")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MockPhoto(MOCK_STORY_TITLE, Modifier.size(84.dp), KitIcons.book)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(Strings.STORY_LABEL, size = 12.sp, color = Palette.Primary, weight = FontWeight.Bold)
                    Text(MOCK_STORY_TITLE, size = 15.sp, weight = FontWeight.Bold)
                    Text(MOCK_STORY_META, size = 12.sp, color = Palette.Muted)
                }
            }
            Text(Strings.COMMUNITY_PHOTOS, Modifier.padding(top = 16.dp).semantics { heading() }, size = 20.sp, weight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    Box(Modifier.weight(1f).height(140.dp)) {
                        MockPhoto("community$i", Modifier.fillMaxSize(), KitIcons.camera)
                        Text(Strings.SAMPLE_USER_PHOTO, Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Ink.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 2.dp), size = 10.sp, color = Color.White)
                    }
                }
            }
            Note(Strings.COMMUNITY_RULE, Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Subtle).padding(12.dp))
        }
        Column(Modifier.anchor(4).padding(start = 20.dp, end = 20.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHead("05", "LÊN CHUYẾN Ở ĐÂY")
            listOf(MOCK_TRIPS[0], MOCK_TRIPS[3]).forEach { t -> TripRow(t.id, t.title, t.summary) { onTrip(t.id) } }
            Button(Strings.PLAN_YOURSELF, Modifier.fillMaxWidth(), icon = KitIcons.plus, pill = true, onClick = onPlan)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Soft).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Strings.GETTING_AROUND, Modifier.semantics { heading() }, size = 18.sp, weight = FontWeight.ExtraBold)
            Text(Strings.GETTING_AROUND_NOTE, size = 13.sp, color = Palette.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(Strings.FIND_ROUTE, Modifier.weight(1f), compact = true, pill = true, onClick = onRoute)
                Button(Strings.ONE_DAY_PLAN, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true, onClick = onPlan)
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParentY(): Int =
    (parentLayoutCoordinates?.let { it.localPositionOf(this, Offset.Zero).y } ?: 0f).toInt()

@Composable
private fun Season(modifier: Modifier, bg: Color, accent: Color, label: String, months: String, note: String) =
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, size = 12.sp, color = accent, weight = FontWeight.Bold)
        Text(months, size = 15.sp, weight = FontWeight.Bold)
        Text(note, size = 12.sp, color = Palette.Body)
    }

/** Trip line used by the guide and Journey: photo, "GỢI Ý", title, summary. */
@Composable
fun TripRow(seed: String, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MockPhoto(seed, Modifier.size(68.dp), KitIcons.landmark)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(Strings.SUGGESTION, size = 12.sp, color = Palette.Primary, weight = FontWeight.Bold)
            Text(title, size = 15.sp, weight = FontWeight.Bold)
            Text(summary, size = 12.sp, color = Palette.Muted)
        }
    }
}

/** UX-2 screen 15: a dish of the region (sample text and shops), on the map or into a plan. */
@Composable
fun DishScreen(id: String, onBack: () -> Unit, onShowMap: () -> Unit, onAddToPlan: (Dish) -> Unit) {
    val d = MOCK_DISHES.firstOrNull { it.id == id } ?: MOCK_DISHES.first()
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(Palette.Night), contentAlignment = Alignment.Center) {
            Text(Strings.SAMPLE_DISH_PHOTO, size = 13.sp, color = Palette.Line)
        }
        Box(Modifier.safeDrawingPadding().padding(16.dp)) {
            IconButton(ExtraIcons.close, Strings.CLOSE_GUIDE_ITEM, Modifier.clip(CircleShape).background(Palette.Bg), tint = Palette.Ink, onClick = onBack)
        }
        val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier.fillMaxSize().padding(top = 270.dp).clip(sheet).background(Palette.Bg).verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) }
            Text(Strings.FOOD_HCMC, Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Warn).padding(horizontal = 10.dp, vertical = 4.dp), size = 12.sp, color = Palette.Amber, weight = FontWeight.ExtraBold)
            Text(d.name, Modifier.semantics { heading() }, size = 26.sp, weight = FontWeight.ExtraBold)
            Text(d.description, size = 14.sp, color = Palette.Body)
            Text(Strings.SHOPS_NEAR, Modifier.padding(top = 6.dp).semantics { heading() }, size = 16.sp, weight = FontWeight.Bold)
            d.shops.forEach { shop ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Palette.Line, RoundedCornerShape(16.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MockPhoto(shop.name, Modifier.size(52.dp), KitIcons.star, radius = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shop.name, size = 14.sp, weight = FontWeight.Bold)
                        Row { Stars(shop.rating); Text(" · ${shop.detail}", size = 12.sp, color = Palette.Muted) }
                    }
                    IconButton(KitIcons.pin, "${Strings.SHOW_ON_MAP}: ${shop.name}", Modifier.border(1.dp, Palette.Line, CircleShape), onClick = onShowMap)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(Strings.SHOW_ON_MAP, Modifier.weight(1f), icon = KitIcons.map, compact = true, pill = true, onClick = onShowMap)
                Button(Strings.ADD_TO_PLAN, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true) { onAddToPlan(d) }
            }
        }
    }
}
