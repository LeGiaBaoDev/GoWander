package com.gowander.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import com.gowander.ui.kitShadow
import com.gowander.ui.MockPhoto
import com.gowander.ui.Pill
import com.gowander.ui.Stars
import com.gowander.home.MAP_FILTERS
import com.gowander.home.spreadOut
import com.gowander.mock.mockRating
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import org.maplibre.spatialk.geojson.Position
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.api.ApiError
import com.gowander.api.GeoResult
import com.gowander.api.Leg
import com.gowander.api.PlaceSummary
import com.gowander.api.Route
import com.gowander.onboarding.LoadingView
import com.gowander.ui.Badge
import com.gowander.ui.Banner
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Card
import com.gowander.ui.Chip
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.IconButton
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.ModeKind
import com.gowander.ui.ModeTabs
import com.gowander.ui.ModePill
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.PhotoTile
import com.gowander.ui.SearchBar
import com.gowander.ui.SectionTitle
import com.gowander.ui.Strings
import com.gowander.ui.Text
import com.gowander.ui.Title
import com.gowander.ui.kitShadow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.map.MapState
import kotlin.time.Clock

// ---------- M1 Map ----------

/**
 * UX-2 screen 1: real map with nearby places as floating cards (photo, name, category, sample stars, distance) pinned
 * to their coordinates, a place search, filter chips, "Danh sách" and the locate button. Places without a card are dots.
 */
@Composable
fun MapHome(
    state: RouteUiState,
    location: LocationState,
    nearby: List<PlaceSummary>,
    onSearch: () -> Unit,
    onLocate: () -> Unit,
    onOpenPlace: (PlaceSummary) -> Unit,
    onSeeAll: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val shown = remember(nearby, filter) { MAP_FILTERS[filter].second.let { slugs -> nearby.filter { slugs == null || it.category?.slug in slugs } } }
    // A card sits above its place (about 100 dp with the stem): skip places whose card would land under the search bar,
    // the filters or the map-type button. The camera is not observable state, so poll it (as on the pick screen).
    val mapRef = remember { mutableStateOf<MapState?>(null) }
    var camera by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(Unit) { while (true) { delay(500); mapRef.value?.cameraPosition?.let { if (it != camera) camera = it } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val cards = remember(shown, camera, width) {
            val free = shown.filter { p ->
                val at = mapRef.value?.screenLocationFromPosition(Position(latitude = p.lat, longitude = p.lon)) ?: return@filter true
                at.y > CARD_TOP_CLEAR && !(at.y < CARD_LAYERS_CLEAR && at.x + CARD_HALF_WIDTH > width - 72.dp)
            }
            spreadOut(free, minNorthM = CARD_SPACING_M, max = MAX_CARDS, minEastM = CARD_SPACING_EAST_M)
        }
        RouteMap(
            // No route here (design screen 1): a found route belongs to the route screens. The origin only centres the camera.
            null, state.origin, null, location, Modifier.fillMaxSize(), viewSwitch = true,
            insets = PaddingValues(top = 124.dp, bottom = 72.dp), stateOut = mapRef,
        ) {
            // Other places: small dots, spread out and decorative (too dense to tap safely); "Danh sách" lists them all.
            spreadOut(shown.filter { it !in cards }, minNorthM = DOT_SPACING_M, max = MAX_DOTS).forEach { p ->
                Box(Modifier.placedAt(Position(latitude = p.lat, longitude = p.lon)).size(12.dp).clearAndSetSemantics { }.kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg).padding(2.dp).clip(CircleShape).background(Palette.Primary))
            }
            cards.forEach { p -> PlaceCard(p, Modifier.placedAt(Position(latitude = p.lat, longitude = p.lon), Alignment.BottomCenter)) { onOpenPlace(p) } }
        }
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchBar(Strings.FIND_PLACE, Modifier.fillMaxWidth(), floating = true, onClick = onSearch)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_FILTERS.forEachIndexed { i, (label, _) -> Pill(label, selected = i == filter) { filter = i } }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.sizeIn(minHeight = 48.dp).kitShadow(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Palette.Bg)
                    .clickable(role = Role.Button, onClick = onSeeAll).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ExtraIcons.list, 18.dp, Palette.Primary)
                Spacer(Modifier.width(8.dp))
                Text(Strings.LIST, size = 13.sp, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg)
                    .semantics { contentDescription = Strings.USE_MY_LOCATION }
                    .clickable(role = Role.Button, onClick = onLocate),
                contentAlignment = Alignment.Center,
            ) { Icon(ExtraIcons.send, 22.dp, Palette.Primary) }
        }
    }
}

private const val CARD_SPACING_M = 380.0 // ~card height + stem at the home zoom (15)
private const val CARD_SPACING_EAST_M = 1100.0 // ~card width
private const val MAX_CARDS = 4
private val CARD_TOP_CLEAR = 300.dp // status bar + search + filters + card and stem
private val CARD_LAYERS_CLEAR = 360.dp // ... plus the map-type button on the right
private val CARD_HALF_WIDTH = 134.dp
private const val DOT_SPACING_M = 120.0
private const val MAX_DOTS = 12

/** Floating card + stem + red dot; the dot sits on the place. One spoken label for the whole card. */
@Composable
private fun PlaceCard(p: PlaceSummary, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val rating = mockRating(p.id)
    val meta = listOfNotNull(p.category?.name, null, p.distanceM?.let { distanceLabel(it) })
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = 268.dp).kitShadow(shape).clip(shape).background(Palette.Bg.copy(alpha = 0.96f)).border(1.dp, Palette.Line, shape)
                .semantics(mergeDescendants = true) { contentDescription = "${p.name}, ${meta.joinToString(", ")}, ${Strings.SAMPLE_STARS} $rating" }
                .clickable(role = Role.Button, onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MockPhoto(p.id, Modifier.size(48.dp), categoryIcon(p.category?.slug), radius = 10.dp, iconSize = 22.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(p.name, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    p.category?.name?.let { Text("$it · ", size = 12.sp, color = Palette.Muted, maxLines = 1) }
                    Stars(rating)
                    p.distanceM?.let { Text(" · ${distanceLabel(it)}", size = 12.sp, color = Palette.Muted, maxLines = 1) }
                }
            }
        }
        Box(Modifier.width(2.dp).height(28.dp).background(Palette.Primary))
        Box(Modifier.size(16.dp).clip(CircleShape).background(Palette.Bg).padding(3.dp).clip(CircleShape).background(Palette.Accent))
    }
}

@Composable
fun PlaceRow(p: PlaceSummary, onClick: () -> Unit) {
    val station = p.category?.slug == "station"
    val sub = listOfNotNull(p.category?.name, p.distanceM?.let { Strings.meters(it) }, p.address?.takeIf { !station }).joinToString(" · ")
    ListRow(
        p.name, sub.ifEmpty { null },
        lead = { if (station) Badge(KitIcons.metro, Palette.Metro, Palette.OnPrimary) else PhotoTile(categoryIcon(p.category?.slug)) },
        onClick = onClick,
    )
}

fun categoryIcon(slug: String?) = when (slug) {
    "station" -> KitIcons.metro
    "food", "cafe" -> KitIcons.star
    "park" -> KitIcons.flag
    "market" -> KitIcons.bookmark
    else -> KitIcons.landmark
}

// ---------- M2 search while typing ----------

/** M2: accent-free search over stations and places (API q, 2+ characters), plus "Chọn trên bản đồ". */
@Composable
fun SearchScreen(
    search: suspend (String) -> List<PlaceSummary>,
    geocode: suspend (String) -> List<GeoResult>,
    onBack: () -> Unit,
    onPick: (PlaceSummary) -> Unit,
    onPickAddress: (GeoResult) -> Unit,
    onPickOnMap: () -> Unit,
    origin: String? = null,
    onChangeOrigin: () -> Unit = {},
    recent: List<Endpoint> = emptyList(),
    onPickRecent: (Endpoint) -> Unit = {},
    forPlaces: Boolean = false, // screen 1 place search: no "Từ:" line, place-search placeholder
) {
    var text by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSummary>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    fun message(e: ApiError) = if (e is ApiError.Server) e.userMessage else Strings.NETWORK_ERROR
    LaunchedEffect(text) {
        val q = text.trim()
        if (q.length < 2) { results = emptyList(); addresses = emptyList(); error = null; addressError = null; return@LaunchedEffect }
        delay(300) // debounce typing
        // Stations/places and addresses are independent: one failing must not hide the other.
        coroutineScope {
            launch { try { results = search(q); error = null } catch (e: ApiError) { results = emptyList(); error = message(e) } }
            launch { try { addresses = geocode(q); addressError = null } catch (e: ApiError) { addresses = emptyList(); addressError = message(e) } }
        }
    }
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()) {
        // UX-2 screen 3: back + pill field on one row, "Từ: … · Đổi" under it, a divider, then the list.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(KitIcons.back, Strings.BACK, tint = Palette.Ink, onClick = onBack)
                Spacer(Modifier.width(6.dp))
                val shape = RoundedCornerShape(24.dp)
                Row(Modifier.weight(1f).height(48.dp).border(2.dp, Palette.Primary, shape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        text, { text = it }, Modifier.weight(1f).semantics { contentDescription = Strings.SEARCH_DESTINATION },
                        singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Palette.Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner -> if (text.isEmpty()) Text(if (forPlaces) Strings.FIND_PLACE else Strings.WHERE_TO, size = 16.sp, color = Palette.Muted, maxLines = 1); inner() },
                    )
                }
            }
            if (!forPlaces) Row(Modifier.padding(start = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Primary))
                Spacer(Modifier.width(8.dp))
                Text("${Strings.FROM}: ", size = 13.sp, color = Palette.Muted)
                Text(origin ?: Strings.NOT_SET, Modifier.weight(1f), size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onChangeOrigin), contentAlignment = Alignment.CenterEnd) {
                    Text(Strings.CHANGE, size = 13.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ListRow(Strings.PICK_ON_MAP, lead = { Badge(KitIcons.map, Palette.Soft, Palette.Primary) }, onClick = onPickOnMap)
            // Design screen 3: places and addresses under one "Kết quả"; the subtitle says which is which.
            if (results.isNotEmpty() || addresses.isNotEmpty()) SectionTitle(Strings.RESULTS, Modifier.padding(top = 8.dp))
            error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Palette.Accent, bold = true) }
            results.forEach { p ->
                val station = p.category?.slug == "station"
                ListRow(
                    p.name, if (station) p.category?.name ?: Strings.STATION else "${Strings.PLACE} · ${p.category?.name ?: ""}",
                    lead = { if (station) Badge(KitIcons.metro, Palette.Metro, Palette.OnPrimary) else Badge(categoryIcon(p.category?.slug), Palette.Subtle, Palette.Primary) },
                    onClick = { onPick(p) },
                )
            }
            if (addresses.isNotEmpty() || addressError != null) {
                addressError?.let { Note(it) }
                addresses.forEach { a ->
                    // Honest about OSM gaps: a typed house number that was not found is shown as approximate.
                    val detail = listOfNotNull(Strings.ADDRESS, a.detail, Strings.APPROXIMATE.takeIf { a.approximate }).joinToString(" · ")
                    ListRow(a.name, detail, lead = { Badge(KitIcons.pin, Palette.AccentSoft, Palette.Accent) }, onClick = { onPickAddress(a) })
                }
                if (addresses.isNotEmpty()) Note(Strings.OSM_ATTRIBUTION)
            }
            if (text.trim().length < 2 && recent.isNotEmpty()) {
                SectionTitle(Strings.RECENT, Modifier.padding(top = 16.dp))
                recent.forEach { e -> ListRow(e.label, null, lead = { Badge(KitIcons.clock, Palette.Subtle, Palette.Muted) }, onClick = { onPickRecent(e) }) }
            }
        }
    }
}

// ---------- M3 plan ----------

private val TRAVEL_MODES = listOf(TravelMode.Bus, TravelMode.Metro, TravelMode.Car, TravelMode.Bicycle, TravelMode.Walk)

@Composable
fun TravelModeTabs(mode: TravelMode, onMode: (TravelMode) -> Unit) = ModeTabs(
    TRAVEL_MODES.map {
        when (it) {
            TravelMode.Bus -> KitIcons.bus to Strings.MODE_BUS
            TravelMode.Metro -> KitIcons.metro to Strings.MODE_METRO
            TravelMode.Car -> ExtraIcons.car to Strings.MODE_CAR
            TravelMode.Bicycle -> ExtraIcons.bike to Strings.MODE_BICYCLE
            TravelMode.Walk -> KitIcons.walk to Strings.MODE_WALK
        }
    },
    TRAVEL_MODES.indexOf(mode),
) { onMode(TRAVEL_MODES[it]) }

/** "Khởi hành ngay" or "Đến trước HH:MM" with 15-minute steps (simple, accessible, no time-picker library). */
@Composable
private fun TimeChips(arriveBy: Long?, onArriveBy: (Long?) -> Unit) {
    // UX-2 screen 4: one "Đi ngay ▾" button; the choices open under it and close again on the same button.
    var open by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        // 48 dp touch target around the 36 dp pill of the design.
        Modifier.padding(start = 52.dp).sizeIn(minHeight = 48.dp).clickable(role = Role.Button) { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(36.dp).clip(shape).background(Palette.Bg).border(1.dp, Palette.Line, shape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KitIcons.clock, 16.dp, Palette.Primary)
            Spacer(Modifier.width(6.dp))
            Text("${if (arriveBy != null) "${Strings.ARRIVE_BY} ${pilotClock(arriveBy)}" else Strings.GO_NOW} ${if (open) "▴" else "▾"}", size = 13.sp, weight = FontWeight.SemiBold)
        }
    }
    if (!open) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Chip(Strings.LEAVE_NOW, selected = arriveBy == null) { onArriveBy(null) }
        Spacer(Modifier.width(8.dp))
        Chip(if (arriveBy != null) "${Strings.ARRIVE_BY} ${pilotClock(arriveBy)}" else Strings.ARRIVE_BY, selected = arriveBy != null) {
            if (arriveBy == null) onArriveBy(roundUpToQuarter(nowMillis() + 3_600_000L))
        }
    }
    arriveBy?.let { arrive ->
        Row {
            Chip("−15 phút") { onArriveBy(arrive - 900_000L) }
            Spacer(Modifier.width(8.dp))
            Chip("+15 phút") { onArriveBy(arrive + 900_000L) }
        }
    }
}

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

private fun roundUpToQuarter(ms: Long) = ((ms + 899_999L) / 900_000L) * 900_000L

/** Route first, description second: the way back to the place page after tapping a place. */
@Composable
fun PlaceInfoRow(name: String, onClick: () -> Unit) =
    ListRow("${Strings.ABOUT_PLACE} $name", Strings.ABOUT_PLACE_HINT, lead = { Badge(KitIcons.landmark, Palette.Soft, Palette.Primary) }, onClick = onClick)

/** UX-2 screen 5: "Bắt đầu" + "Xem từng bước" (one step-by-step screen, no separate preview); one line each on an iPhone SE. */
@Composable
fun RouteActions(onStart: () -> Unit, onSteps: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(Strings.START, Modifier.weight(1f), icon = ExtraIcons.send, compact = true, pill = true, onClick = onStart)
        Button(Strings.STEP_BY_STEP, Modifier.weight(1f), kind = ButtonKind.Outline, icon = ExtraIcons.list, compact = true, pill = true, onClick = onSteps)
    }
}

/** Back, then From / To rows (each tappable to change it) and the swap button: Google Maps' directions header. */
@Composable
private fun TripHeader(state: RouteUiState, onBack: () -> Unit, onPickOrigin: () -> Unit, onUseMyLocation: () -> Unit, onChangeDestination: () -> Unit, onSwap: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(KitIcons.back, Strings.BACK, tint = Palette.Ink, onClick = onBack)
        Spacer(Modifier.width(4.dp))
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.weight(1f).clip(shape).border(1.dp, Palette.Line, shape)) {
            // Origin: round teal dot; the locate icon brings back "my location" after the origin was changed.
            EndpointRow(state.origin?.label ?: Strings.NOT_SET, Strings.ORIGIN, CircleShape, Palette.Primary, bold = false, onClick = onPickOrigin) {
                IconButton(KitIcons.locate, Strings.USE_MY_LOCATION, onClick = onUseMyLocation)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
            EndpointRow(state.destination?.label ?: Strings.NOT_SET, Strings.DESTINATION, RoundedCornerShape(2.dp), Palette.Accent, bold = true, onClick = onChangeDestination)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(ExtraIcons.swap, Strings.SWAP, Modifier.border(1.dp, Palette.Line, CircleShape), onClick = onSwap)
    }
}

/** One From / To line of the trip header: shape + colour say which end it is, the spoken label says it too. */
@Composable
private fun EndpointRow(label: String, role: String, dot: androidx.compose.ui.graphics.Shape, color: androidx.compose.ui.graphics.Color, bold: Boolean, onClick: () -> Unit, trail: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$role: $label" }.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(dot).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), size = 14.sp, weight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        trail?.invoke()
    }
}

/** Languages by how many GoWander users are expected to need them (HCMC residents first, then visitors). */
private val LANGUAGES = listOf("VI" to "Tiếng Việt", "EN" to "English", "ZH" to "中文", "KO" to "한국어", "JA" to "日本語")

/**
 * The "VI" button of the kit: a dropdown that floats over the map (a Popup, so nothing next to it gets squeezed).
 * Only Vietnamese exists today (ADR-022); the others say "Sắp có" instead of doing nothing. Closes on a choice or
 * a tap outside.
 */
@Composable
fun LanguageButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val drop = with(LocalDensity.current) { IntOffset(0, 60.dp.roundToPx()) }
    Box(modifier) {
        Box(
            Modifier.size(52.dp).kitShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Palette.Bg)
                .semantics { contentDescription = Strings.LANGUAGE_MENU }
                .clickable(role = Role.Button) { open = !open; note = null },
            contentAlignment = Alignment.Center,
        ) { Text(Strings.LANGUAGE_VI + " ▾", size = 13.sp, color = Palette.Primary, weight = FontWeight.SemiBold) }
        if (open) Popup(alignment = Alignment.TopEnd, offset = drop, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
            Card(Modifier.width(260.dp), shadow = true, gap = 2.dp) {
                Text(Strings.CHOOSE_LANGUAGE, Modifier.padding(bottom = 4.dp), size = 15.sp, weight = FontWeight.SemiBold)
                LANGUAGES.forEach { (code, name) ->
                    val current = code == "VI"
                    Row(
                        Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).clip(RoundedCornerShape(10.dp))
                            .semantics(mergeDescendants = true) { selected = current }
                            .clickable(role = Role.RadioButton) { if (current) open = false else note = "$name: ${Strings.SOON.lowercase()}" }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, Modifier.weight(1f), color = if (current) Palette.Primary else Palette.Ink, weight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                        if (current) Icon(KitIcons.check, 18.dp, Palette.Primary) else Text(Strings.SOON, size = 12.sp, color = Palette.Muted)
                    }
                }
                note?.let { Note(it) }
            }
        }
    }
}

// ---------- M4 pick on map ----------

private val PICK_CARD_SPACE = 300.dp

/** A choice under the pin: the exact point, an address at the point, or a nearby place/station (M4 suggestions). */
data class PinOption(val name: String, val detail: String?, val lat: Double, val lon: Double)

/**
 * M4: centred pin over the real map. When the map stops moving, the address at the pin and nearby places are offered,
 * so the user can snap to a real place instead of trusting an approximate pin; "Đúng điểm ghim" keeps the raw point.
 */
@Composable
fun PickScreen(
    forOrigin: Boolean,
    location: LocationState,
    state: RouteUiState,
    suggest: suspend (Double, Double) -> List<PinOption>,
    onBack: () -> Unit,
    onConfirm: (Endpoint) -> Unit,
) {
    val mapRef = remember { mutableStateOf<MapState?>(null) }
    var options by remember { mutableStateOf<List<PinOption>>(emptyList()) }
    var chosen by remember { mutableStateOf<PinOption?>(null) } // null = the pin itself
    var pin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // The camera is not observable state here, so poll it; look up suggestions once the pin rests (~0.7 s).
    LaunchedEffect(Unit) {
        var lastAsked: Pair<Double, Double>? = null
        while (true) {
            delay(700)
            val c = mapRef.value?.cameraPosition?.center ?: continue
            val now = c.latitude to c.longitude
            if (now == pin) {
                if (now != lastAsked) {
                    lastAsked = now
                    chosen = null
                    options = try { suggest(now.first, now.second) } catch (e: ApiError) { emptyList() } // suggestions are optional
                }
            } else {
                pin = now // still moving: wait for the next tick
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        // The card covers the lower part: the map's centre (where the pin points) and its logo/attribution sit above it.
        RouteMap(null, null, if (forOrigin) state.origin else state.destination, location, Modifier.fillMaxSize(), stateOut = mapRef, insets = PaddingValues(bottom = PICK_CARD_SPACE))
        Box(Modifier.fillMaxSize().padding(bottom = PICK_CARD_SPACE)) {
            Icon(KitIcons.pin, 44.dp, Palette.Accent, Modifier.align(Alignment.Center).padding(bottom = 44.dp)) // tip sits on the centre
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            Header(Strings.PICK_ON_MAP, floating = true, onBack = onBack)
            Spacer(Modifier.weight(1f))
            Card(Modifier.fillMaxWidth(), shadow = true, gap = 4.dp) {
                Note(if (forOrigin) Strings.ORIGIN else Strings.DESTINATION)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    OptionRow(Strings.EXACT_PIN, Strings.PICKED_ON_MAP, KitIcons.pin, selected = chosen == null) { chosen = null }
                    if (options.isNotEmpty()) Note(Strings.NEAR_PIN, Modifier.padding(top = 4.dp))
                    options.forEach { o -> OptionRow(o.name, o.detail, KitIcons.landmark, selected = chosen == o) { chosen = o } }
                }
                Button(if (forOrigin) Strings.CONFIRM_ORIGIN else Strings.CONFIRM_DESTINATION, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val o = chosen
                    if (o != null) onConfirm(Endpoint(o.lat, o.lon, o.name))
                    else {
                        val c = mapRef.value?.cameraPosition?.center ?: return@Button
                        onConfirm(Endpoint(c.latitude, c.longitude, Strings.PICKED_ON_MAP))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, detail: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    ListRow(
        title, detail, Modifier.semantics { this.selected = selected },
        lead = { Badge(icon, if (selected) Palette.Primary else Palette.Subtle, if (selected) Palette.OnPrimary else Palette.Primary, 36.dp) },
        trail = { if (selected) Icon(KitIcons.check, 20.dp, Palette.Primary) },
        onClick = onClick,
    )
}

// ---------- M5 results, M7 no route, M8 offline ----------

/** M5 with its states: loading, results, M7 (user-fixable: no route / out of coverage), M8 (network) and retryable errors. */
@Composable
fun ResultsScreen(
    state: RouteUiState,
    onBack: () -> Unit,
    onMode: (TravelMode) -> Unit,
    onSort: (RouteSort) -> Unit,
    onOpen: (Int) -> Unit,
    onRetry: () -> Unit,
    onChangeDestination: () -> Unit,
    placeInfo: String? = null,
    onPlaceInfo: () -> Unit = {},
    onUseMyLocation: () -> Unit = {},
    onPickOrigin: () -> Unit = {},
    onSwap: () -> Unit = {},
    onArriveBy: (Long?) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Palette.Subtle).verticalScroll(rememberScrollState())) {
        // UX-2 screen 4: white header (From / To edited in place, swap, time, modes) over a light list background.
        Column(
            Modifier.fillMaxWidth().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TripHeader(state, onBack, onPickOrigin, onUseMyLocation, onChangeDestination, onSwap)
            TimeChips(state.arriveBy, onArriveBy)
            TravelModeTabs(state.mode, onMode) // switch mode on the results, as in Google/Naver/Kakao
        }
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (placeInfo != null) PlaceInfoRow(placeInfo, onPlaceInfo)
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val p = state.phase) {
                // No origin yet (location not allowed or no fix): ask here instead of searching from a made-up point (T5).
                RoutePhase.Idle -> if (state.origin == null) {
                    Note(Strings.NEED_ORIGIN)
                    Button(Strings.USE_MY_LOCATION, Modifier.fillMaxWidth(), onClick = onUseMyLocation)
                    Button(Strings.ORIGIN_ON_MAP, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, onClick = onPickOrigin)
                } else Note(Strings.NEED_BOTH)
                RoutePhase.Loading -> LoadingView(Strings.SEARCHING)
                is RoutePhase.Failed -> if (p.retryable) {
                    // M8 shape: banner + retry. Origin and destination stay as they were.
                    Banner(if (p.message == Strings.NETWORK_ERROR) Strings.OFFLINE else p.message, if (p.message == Strings.NETWORK_ERROR) p.message else null, KitIcons.flag, Palette.AccentSoft)
                    Button(Strings.RETRY, Modifier.fillMaxWidth(), onClick = onRetry)
                } else {
                    // M7: nothing to retry; the user changes the trip.
                    Spacer(Modifier.height(40.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Badge(KitIcons.route, Palette.Warn, Palette.Ink, 96.dp) }
                    Text(Strings.NO_ROUTE_TITLE, size = 20.sp, weight = FontWeight.SemiBold)
                    Text(p.message, color = Palette.Muted)
                    Button(Strings.CHANGE_DESTINATION, Modifier.fillMaxWidth(), onClick = onChangeDestination)
                }
                is RoutePhase.Results -> {
                    if (state.mode.isTransit) Row {
                        Chip(Strings.FASTEST, selected = state.sort == RouteSort.Fastest) { onSort(RouteSort.Fastest) }
                        Spacer(Modifier.width(8.dp))
                        Chip(Strings.LEAST_WALKING, selected = state.sort == RouteSort.LeastWalking) { onSort(RouteSort.LeastWalking) }
                    }
                    // The same trip at later times is one card with its departures (plan §7.3); the sort chips above
                    // already say what "first" means, so no repeated "Nhanh nhất" label on the card.
                    val groups = remember(p.routes) { groupTrips(p.routes) }
                    groups.forEachIndexed { gi, g -> RouteOptionCard(p.routes[g.first], highlight = gi == 0, later = g.later.map { it to clock(p.routes[it].startTime) }, onOpenLater = onOpen) { onOpen(g.first) } }
                    if (state.mode == TravelMode.Metro) Note(Strings.METRO_WITH_BUS)
                    if (p.assumedData) Note(Strings.ASSUMED_NOTE)
                    if (p.routes.any { r -> r.legs.any { it.mode == "CAR" } }) Note(Strings.CAR_NOTE)
                }
            }
        }
        }
    }
}

@Composable
private fun RouteOptionCard(r: Route, highlight: Boolean, later: List<Pair<Int, String>>, onOpenLater: (Int) -> Unit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shadow = highlight, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.minutes(r.durationSec), Modifier.weight(1f), size = 26.sp, weight = FontWeight.Bold)
            Text("${clock(r.startTime)} → ${clock(r.endTime)}", size = 13.sp, color = Palette.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            r.legs.forEachIndexed { j, leg ->
                if (j > 0) { Icon(KitIcons.chevron, 12.dp, Palette.Muted); Spacer(Modifier.width(4.dp)) }
                ModePill(modeOf(leg), if (leg.transit) leg.line?.shortName ?: leg.mode else "${(leg.durationSec + 59) / 60}′")
                Spacer(Modifier.width(4.dp))
            }
        }
        // Bus/metro trips always include walking to and from the stops: say how much (owner, 14-11-2026).
        val walkMin = r.legs.filter { it.mode == "WALK" }.sumOf { it.durationSec } / 60
        val walk = if (r.legs.any { it.transit } && walkMin > 0) "${Strings.WALK_TOTAL} $walkMin ${Strings.MINUTES_SHORT} · " else ""
        Note("$walk${if (r.transfers == 0) Strings.NO_TRANSFER else "${r.transfers} ${Strings.TRANSFERS}"}")
        // Each later departure is its own target: it opens that exact trip.
        if (later.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Note(Strings.LATER_TRIPS)
            later.forEach { (i, time) -> Chip(time) { onOpenLater(i) } }
        }
    }
}

private fun modeOf(leg: Leg) = when (leg.mode) {
    "BUS" -> ModeKind.Bus
    "CAR" -> ModeKind.Car
    "BICYCLE" -> ModeKind.Bicycle
    else -> if (leg.transit) ModeKind.Metro else ModeKind.Walk
}

// ---------- M6 route detail ----------

/** M6: route on the map, duration, legs with waiting time, places near the station where the user gets off. */
@Composable
fun DetailScreen(
    state: RouteUiState,
    location: LocationState,
    onBack: () -> Unit,
    onOpenPlace: (PlaceSummary) -> Unit,
    onStart: () -> Unit,
    onSteps: () -> Unit,
    placeInfo: String? = null,
    onPlaceInfo: () -> Unit = {},
) {
    val p = state.phase as? RoutePhase.Results ?: return
    val route = p.routes.getOrNull(p.selected) ?: return
    val waits = remember(route) { waitsBeforeLegs(route.legs) }
    val undrawable = remember(route) { undrawableLegs(route) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Full-screen map like the pick screen (owner, 14-11-2026: the 220 dp map was too small); details in a bottom sheet.
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        RouteMap(
            route, state.origin, state.destination, location, Modifier.fillMaxSize(),
            viewSwitch = true, insets = PaddingValues(top = 80.dp, bottom = if (expanded) 600.dp else 390.dp),
        )
        // Design screen 5: only a round back button over the map (the sheet says what this is).
        Box(Modifier.safeDrawingPadding().padding(16.dp)) { Header("", floating = true, onBack = onBack) }
        val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = if (expanded) 620.dp else 400.dp)
                .kitShadow(sheet).clip(sheet).background(Palette.Bg)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Handle + summary are one 48 dp+ target that opens or closes the leg list (a drag sheet is more code than
            // this MVP needs); the arrow says which way it goes.
            Column(
                Modifier.fillMaxWidth().semantics { contentDescription = if (expanded) Strings.COLLAPSE else Strings.EXPAND }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    // Drag up to open, down to close (like a bottom sheet); tapping still works for users who cannot drag.
                    .pointerInput(Unit) {
                        var dy = 0f
                        detectVerticalDragGestures(onDragEnd = {
                            if (dy < -40f) expanded = true else if (dy > 40f) expanded = false
                            dy = 0f
                        }) { _, d -> dy += d }
                    },
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Strings.minutes(route.durationSec), Modifier.weight(1f), size = 26.sp, weight = FontWeight.Bold)
                    Note("${clock(route.startTime)} → ${clock(route.endTime)} · ${Strings.TO_PLACE} ${state.destination?.label ?: Strings.DESTINATION.lowercase()}")
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (placeInfo != null) PlaceInfoRow(placeInfo, onPlaceInfo)
                if (undrawable > 0) Note(Strings.PARTIAL_DRAW)
                // Timeline like the design: dot per stop, solid line on a vehicle, dotted line on foot.
                route.legs.forEachIndexed { i, leg ->
                    waits[i]?.let { TimelineRow(TimelineMark.Wait, "${Strings.WAIT} $it phút", if (leg.transit) Strings.SCHEDULED else null) }
                    LegRow(leg, first = i == 0)
                }
                TimelineRow(TimelineMark.End, "${Strings.ARRIVE_AT} ${state.destination?.label ?: Strings.DESTINATION.lowercase()}", null)
                if (state.nearAlighting.isNotEmpty()) {
                    val alightLeg = route.legs.lastOrNull { it.transit }
                    val near = if (alightLeg != null) "${Strings.NEAR_STATION} ${alightLeg.to.name}" else "${Strings.NEAR} ${state.destination?.label ?: ""}"
                    SectionTitle(near.trim())
                    state.nearAlighting.forEach { PlaceRow(it) { onOpenPlace(it) } }
                }
                if (p.assumedData) Note(Strings.ASSUMED_NOTE)
                if (route.legs.any { it.mode == "CAR" }) Note(Strings.CAR_NOTE)
            }
            RouteActions(onStart, onSteps) // design: the two actions sit at the bottom of the sheet
        }
    }
}

internal enum class TimelineMark { Start, Stop, Wait, End }

/** One line of the route timeline: a mark, the line down to the next row (solid = vehicle, dotted = on foot), text. */
@Composable
private fun TimelineRow(mark: TimelineMark, title: String, detail: String?, line: Color? = null, dotted: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.dp))
            when (mark) {
                TimelineMark.Start -> Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.Primary))
                TimelineMark.End -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Accent))
                TimelineMark.Wait -> Icon(KitIcons.clock, 12.dp, Palette.Muted)
                TimelineMark.Stop -> Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.Bg).border(3.dp, line ?: Palette.Bus, CircleShape))
            }
            if (line != null) Canvas(Modifier.weight(1f).width(4.dp).padding(vertical = 2.dp)) {
                val x = size.width / 2
                if (dotted) drawLine(Palette.Muted, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx())))
                else drawLine(line, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, size = 14.sp, weight = FontWeight.SemiBold)
            if (detail != null) Text(detail, size = 12.sp, color = Palette.Muted)
        }
    }
}

@Composable
private fun LegRow(leg: Leg, first: Boolean) {
    if (leg.transit) {
        val bus = leg.mode == "BUS"
        val name = leg.line?.shortName?.let { "${if (bus) Strings.BUS else "Metro"} $it" } ?: leg.mode
        TimelineRow(
            TimelineMark.Stop,
            "$name · ${leg.headsign?.let { "${Strings.TOWARDS} $it" } ?: ""}".trimEnd(' ', '·'),
            "${clock(leg.startTime)} ${leg.from.name} → ${leg.to.name} · ${Strings.minutes(leg.durationSec)}" + if (!leg.realtime) " · ${Strings.SCHEDULED}" else "",
            line = if (bus) Palette.Bus else Palette.Metro,
        )
    } else {
        val verb = when (leg.mode) { "CAR" -> Strings.DRIVE; "BICYCLE" -> Strings.CYCLE; else -> Strings.WALK }
        TimelineRow(
            if (first) TimelineMark.Start else TimelineMark.Stop,
            "$verb ${Strings.minutes(leg.durationSec)}",
            "${if (leg.to.stopId != null) "Tới ga ${leg.to.name}" else "Tới ${leg.to.name.takeUnless { it == "Destination" } ?: Strings.DESTINATION.lowercase()}"} · ${Strings.meters(leg.distanceM)}",
            line = Palette.Muted, dotted = leg.mode == "WALK",
        )
    }
}

/** "2026-11-19T14:02:43+07:00" -> "14:02"; never throws on a malformed value. */
fun clock(isoTime: String) = if (isoTime.length >= 16 && isoTime[13] == ':') isoTime.substring(11, 16) else "--:--"
