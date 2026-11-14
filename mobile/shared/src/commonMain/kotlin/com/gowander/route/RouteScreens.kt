package com.gowander.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import com.gowander.ui.Badge
import com.gowander.ui.Banner
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Card
import com.gowander.ui.Chip
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.ModeKind
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
import org.maplibre.compose.util.DpPadding
import kotlin.time.Clock

// ---------- M1 Map ----------

/** M1: real map, floating search, locate button and "Gợi ý gần bạn" sheet; shows the selected route if there is one. */
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
    val results = state.phase as? RoutePhase.Results
    Box(Modifier.fillMaxSize()) {
        RouteMap(results?.routes?.getOrNull(results.selected), state.origin, state.destination, location, Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(Strings.WHERE_TO, Modifier.weight(1f), floating = true, onClick = onSearch)
                Spacer(Modifier.width(8.dp))
                // Language switch of the kit; English comes later (ADR-022), so it only shows the current language.
                Box(Modifier.size(52.dp).kitShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Palette.Bg), contentAlignment = Alignment.Center) {
                    Text(Strings.LANGUAGE_VI, size = 14.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
                }
            }
            if (results?.assumedData == true) Banner(Strings.DEMO_LABEL)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.End) {
            Box(
                Modifier.size(48.dp).kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg)
                    .semantics { contentDescription = Strings.USE_MY_LOCATION }
                    .clickable(role = Role.Button, onClick = onLocate),
                contentAlignment = Alignment.Center,
            ) { Icon(KitIcons.locate, 24.dp, Palette.Primary) }
            Spacer(Modifier.height(12.dp))
            if (nearby.isNotEmpty()) Card(Modifier.fillMaxWidth(), shadow = true, gap = 0.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.NEAR_YOU, Modifier.weight(1f), size = 18.sp, weight = FontWeight.SemiBold)
                    Box(Modifier.sizeIn(minHeight = 48.dp, minWidth = 48.dp).clickable(role = Role.Button, onClick = onSeeAll), contentAlignment = Alignment.CenterEnd) {
                        Text(Strings.SEE_ALL, size = 14.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
                    }
                }
                nearby.take(2).forEach { p -> PlaceRow(p, onClick = { onOpenPlace(p) }) }
            }
        }
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
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(Strings.SEARCH_DESTINATION, onBack = onBack)
        val shape = RoundedCornerShape(16.dp)
        Row(Modifier.fillMaxWidth().border(1.5.dp, Palette.Primary, shape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KitIcons.search, 18.dp, Palette.Primary)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                text, { text = it }, Modifier.weight(1f).semantics { contentDescription = Strings.SEARCH_DESTINATION },
                singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = Palette.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner -> if (text.isEmpty()) Text(Strings.TYPE_TO_SEARCH, color = Palette.Muted, maxLines = 1); inner() },
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
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
                SectionTitle(Strings.ADDRESSES, Modifier.padding(top = 8.dp))
                addressError?.let { Note(it) }
                addresses.forEach { a ->
                    // Honest about OSM gaps: a typed house number that was not found is shown as approximate.
                    val detail = listOfNotNull(a.detail, Strings.APPROXIMATE.takeIf { a.approximate }).joinToString(" · ").ifEmpty { null }
                    ListRow(a.name, detail, lead = { Badge(KitIcons.pin, Palette.AccentSoft, Palette.Accent) }, onClick = { onPickAddress(a) })
                }
                if (addresses.isNotEmpty()) Note(Strings.OSM_ATTRIBUTION)
            }
            ListRow(Strings.PICK_ON_MAP, lead = { Badge(KitIcons.map, Palette.Subtle, Palette.Primary) }, onClick = onPickOnMap)
        }
    }
}

// ---------- M3 plan ----------

/** M3: origin and destination card, leave-now / arrive-by chips, pick on map, recent destinations, search button. */
@Composable
fun PlanScreen(
    state: RouteUiState,
    onEditDestination: () -> Unit,
    onUseMyLocation: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onArriveBy: (Long?) -> Unit,
    onRecent: (Endpoint) -> Unit,
    onSwap: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(Strings.TAB_PLAN)
        Card(Modifier.fillMaxWidth(), gap = 4.dp) {
            ListRow(
                state.origin?.label ?: Strings.NOT_SET, Strings.ORIGIN,
                lead = { Badge(KitIcons.locate, Palette.Soft, Palette.Primary, 36.dp) },
                trail = { Text(Strings.SWAP, Modifier.padding(8.dp), size = 14.sp, color = Palette.Primary, weight = FontWeight.SemiBold) },
                onClick = onSwap,
            )
            ListRow(
                state.destination?.label ?: Strings.NOT_SET, Strings.DESTINATION,
                lead = { Badge(KitIcons.pin, Palette.AccentSoft, Palette.Accent, 36.dp) }, onClick = onEditDestination,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Chip(Strings.USE_MY_LOCATION, onClick = onUseMyLocation)
            Spacer(Modifier.width(8.dp))
            Chip(Strings.PICK_ON_MAP + " (đi)", onClick = onPickOrigin)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chip(Strings.LEAVE_NOW, selected = state.arriveBy == null) { onArriveBy(null) }
            Spacer(Modifier.width(8.dp))
            val arrive = state.arriveBy
            Chip(if (arrive != null) "${Strings.ARRIVE_BY} ${pilotClock(arrive)}" else Strings.ARRIVE_BY, selected = arrive != null) {
                if (arrive == null) onArriveBy(roundUpToQuarter(nowMillis() + 3_600_000L))
            }
        }
        state.arriveBy?.let { arrive ->
            Row { // 15-minute steps: simple, accessible, no time picker library
                Chip("−15 phút") { onArriveBy(arrive - 900_000L) }
                Spacer(Modifier.width(8.dp))
                Chip("+15 phút") { onArriveBy(arrive + 900_000L) }
            }
        }
        ListRow(Strings.PICK_ON_MAP, lead = { Badge(KitIcons.map, Palette.Subtle, Palette.Primary) }, onClick = onPickDestination)
        if (state.recentDestinations.isNotEmpty()) {
            SectionTitle(Strings.RECENT)
            state.recentDestinations.forEach { e -> ListRow(e.label, null, lead = { Badge(KitIcons.clock, Palette.Subtle, Palette.Muted) }, onClick = { onRecent(e) }) }
        }
        if (state.origin == null || state.destination == null) Note(Strings.NEED_BOTH)
        Button(Strings.FIND_ROUTE, Modifier.fillMaxWidth(), icon = KitIcons.route, onClick = onSearch)
    }
}

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

private fun roundUpToQuarter(ms: Long) = ((ms + 899_999L) / 900_000L) * 900_000L

// ---------- M4 pick on map ----------

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
        RouteMap(null, null, if (forOrigin) state.origin else state.destination, location, Modifier.fillMaxSize(), stateOut = mapRef)
        Icon(KitIcons.pin, 44.dp, Palette.Accent, Modifier.align(Alignment.Center).padding(bottom = 44.dp)) // tip sits on the centre
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
fun ResultsScreen(state: RouteUiState, onBack: () -> Unit, onSort: (RouteSort) -> Unit, onOpen: (Int) -> Unit, onRetry: () -> Unit, onChangeDestination: () -> Unit) {
    val title = "${state.origin?.label ?: "?"} → ${state.destination?.label ?: "?"}"
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(title, onBack = onBack)
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val p = state.phase) {
                RoutePhase.Idle -> Note(Strings.NEED_BOTH)
                RoutePhase.Loading -> Text(Strings.SEARCHING, Modifier.padding(vertical = 24.dp))
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
                    Row {
                        Chip(Strings.FASTEST, selected = state.sort == RouteSort.Fastest) { onSort(RouteSort.Fastest) }
                        Spacer(Modifier.width(8.dp))
                        Chip(Strings.LEAST_WALKING, selected = state.sort == RouteSort.LeastWalking) { onSort(RouteSort.LeastWalking) }
                    }
                    p.routes.forEachIndexed { i, r -> RouteOptionCard(r, highlight = i == 0, tag = if (i == 0) (if (state.sort == RouteSort.Fastest) Strings.FASTEST else Strings.LEAST_WALKING) else null) { onOpen(i) } }
                    if (p.assumedData) Note(Strings.ASSUMED_NOTE)
                }
            }
        }
    }
}

@Composable
private fun RouteOptionCard(r: Route, highlight: Boolean, tag: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shadow = highlight, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.minutes(r.durationSec), Modifier.weight(1f), size = 26.sp, weight = FontWeight.Bold)
            if (tag != null) Text(tag, Modifier.clip(RoundedCornerShape(999.dp)).background(Palette.Soft).padding(horizontal = 10.dp, vertical = 5.dp), size = 12.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            r.legs.forEachIndexed { j, leg ->
                if (j > 0) { Icon(KitIcons.chevron, 12.dp, Palette.Muted); Spacer(Modifier.width(4.dp)) }
                ModePill(modeOf(leg), if (leg.transit) leg.line?.shortName ?: leg.mode else "${(leg.durationSec + 59) / 60}′")
                Spacer(Modifier.width(4.dp))
            }
        }
        Note("${Strings.LEAVE_AT} ${clock(r.startTime)} · ${if (r.transfers == 0) Strings.NO_TRANSFER else "${r.transfers} ${Strings.TRANSFERS}"}")
    }
}

private fun modeOf(leg: Leg) = when {
    !leg.transit -> ModeKind.Walk
    leg.mode == "BUS" -> ModeKind.Bus
    else -> ModeKind.Metro
}

// ---------- M6 route detail ----------

/** M6: route on the map, duration, legs with waiting time, places near the station where the user gets off. */
@Composable
fun DetailScreen(state: RouteUiState, location: LocationState, onBack: () -> Unit, onOpenPlace: (PlaceSummary) -> Unit) {
    val p = state.phase as? RoutePhase.Results ?: return
    val route = p.routes.getOrNull(p.selected) ?: return
    val waits = remember(route) { waitsBeforeLegs(route.legs) }
    val undrawable = remember(route) { undrawableLegs(route) }
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(Strings.ROUTE_DETAIL, onBack = onBack)
        RouteMap(route, state.origin, state.destination, location, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)), DpPadding(24.dp, 24.dp, 24.dp, 24.dp))
        if (undrawable > 0) Note(Strings.PARTIAL_DRAW)
        Text(Strings.minutes(route.durationSec), size = 28.sp, weight = FontWeight.Bold)
        Note("${if (p.assumedData) "Theo lịch ước tính" else Strings.SCHEDULED} · ${Strings.ARRIVE_ABOUT} ${clock(route.endTime)}")
        Card(Modifier.fillMaxWidth(), gap = 4.dp) {
            route.legs.forEachIndexed { i, leg ->
                waits[i]?.let { ListRow("${Strings.WAIT} $it phút", if (leg.transit) Strings.SCHEDULED else null, lead = { Badge(KitIcons.clock, Palette.Subtle, Palette.Ink) }) }
                LegRow(leg)
            }
        }
        if (state.nearAlighting.isNotEmpty()) {
            val alight = route.legs.lastOrNull { it.transit }?.to?.name ?: state.destination?.label ?: ""
            SectionTitle("${Strings.NEAR_STATION} $alight")
            state.nearAlighting.forEach { PlaceRow(it) { onOpenPlace(it) } }
        }
        if (p.assumedData) Note(Strings.ASSUMED_NOTE)
    }
}

@Composable
private fun LegRow(leg: Leg) {
    if (leg.transit) {
        val name = leg.line?.shortName?.let { "Metro $it" } ?: leg.mode
        ListRow(
            "$name · ${leg.headsign?.let { "${Strings.TOWARDS} $it" } ?: ""}".trimEnd(' ', '·'),
            "${clock(leg.startTime)} ${leg.from.name} → ${leg.to.name} · ${Strings.minutes(leg.durationSec)}" + if (!leg.realtime) " · ${Strings.SCHEDULED}" else "",
            lead = { Badge(KitIcons.metro, Palette.Metro, Palette.OnPrimary) },
        )
    } else {
        ListRow(
            "${Strings.WALK} ${Strings.minutes(leg.durationSec)}",
            "${if (leg.to.stopId != null) "Tới ga ${leg.to.name}" else "Tới ${leg.to.name.takeUnless { it == "Destination" } ?: Strings.DESTINATION.lowercase()}"} · ${Strings.meters(leg.distanceM)}",
            lead = { Badge(KitIcons.walk, Palette.Subtle, Palette.Ink) },
        )
    }
}

/** "2026-11-19T14:02:43+07:00" -> "14:02"; never throws on a malformed value. */
fun clock(isoTime: String) = if (isoTime.length >= 16 && isoTime[13] == ':') isoTime.substring(11, 16) else "--:--"
