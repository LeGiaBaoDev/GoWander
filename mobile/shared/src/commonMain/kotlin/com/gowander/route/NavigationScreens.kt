package com.gowander.route

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import com.gowander.ui.ExtraIcons
import com.gowander.ui.IconButton
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Card
import com.gowander.ui.Header
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Text
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.location.LocationState
import org.maplibre.spatialk.geojson.Position

/** A street photo ready to show, with the credit line its licence requires (ADR-033). */
data class StreetPhoto(val image: ImageBitmap, val credit: String, val pageUrl: String? = null)

internal val NavBanner = Color(0xFF035E61) // darker than Primary so white text keeps contrast over the map
private const val OFF_ROUTE_M = 60.0
private const val ARRIVED_M = 25.0
private const val JOIN_M = 30.0
private const val SWIPE_PX = 80f
private const val BACKTRACK_M = 50.0
private const val DRIVE_SPACING_M = 12.0
private const val STREET_ZOOM = 20.5 // ~eye level: buildings and the road at near 1:1 scale
private const val STREET_PITCH = 65.0 // 78° ran out of memory (tiles to the horizon, 1.1 GB on the emulator, 14-11-2026)
private const val DRIVE_TICK_MS = 160L
private const val NUDGE_M = 20.0 // one tap on ︿ / ﹀ moves this far along the route (owner: small steps, like Street View)
private const val LOOK_AHEAD_M = 8.0

fun distanceLabel(m: Double): String = if (m < 1000) "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} m" else "${(m / 100).roundToInt() / 10.0} km"

// ---------- Navigation mode (Google Maps "Bắt đầu") ----------

/**
 * Full-screen map that follows the user at their pace: next instruction on top, time left / distance / arrival and
 * "Thoát" at the bottom, plus the instruction list and the route preview. Off the route: offer a new route.
 */
@Composable
fun NavigationScreen(
    state: RouteUiState,
    location: LocationState,
    onExit: () -> Unit,
    onSteps: () -> Unit,
    onPreview: (Int) -> Unit,
    onReroute: () -> Unit,
) {
    val p = state.phase as? RoutePhase.Results ?: return
    val route = p.routes.getOrNull(p.selected) ?: return
    val destination = state.destination?.label ?: Strings.DESTINATION
    val steps = remember(route) { navSteps(route, destination) }
    val track = remember(route, steps) { navTrack(route, steps) }
    val fix = location.lastLocation?.position
    // Matched once per GPS fix (not per recomposition), searching onward from the last match (review 14-11-2026).
    var lastAlong by rememberSaveable(route) { mutableStateOf(0.0) }
    val progress = remember(track, fix) {
        fix?.let { track.progress(it.latitude, it.longitude, fromAlongM = lastAlong - BACKTRACK_M) }?.also { lastAlong = it.alongM }
    }
    val next = steps.getOrNull(progress?.nextStep ?: 0)
    val remainingM = progress?.remainingM ?: track.totalM
    val remainingSec = if (track.totalM > 0) (route.durationSec * remainingM / track.totalM).roundToInt() else route.durationSec
    val arrived = progress != null && progress.remainingM < ARRIVED_M
    // The start is often off the street graph (inside a market, a car park): warn only after the user has joined the route.
    var joined by rememberSaveable(route) { mutableStateOf(false) }
    if (progress != null && progress.offRouteM < JOIN_M) joined = true
    val offRoute = joined && progress != null && !arrived && progress.offRouteM > OFF_ROUTE_M

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        RouteMap(route, state.origin, state.destination, location, Modifier.fillMaxSize(), follow = true, viewSwitch = true, insets = PaddingValues(top = 140.dp, bottom = 220.dp))
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NavBanner).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(next?.arrow ?: "↑", Modifier.clearAndSetSemantics { }, size = 40.sp, color = Color.White, weight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (progress != null && !arrived) Text(distanceLabel(progress.toNextM), size = 15.sp, color = Color.White.copy(alpha = 0.85f))
                    // Only the instruction is a live region: announcing the distance on every GPS fix would flood TalkBack/VoiceOver.
                    Text(
                        if (arrived) "${Strings.ARRIVED} $destination" else next?.text ?: Strings.WAITING_GPS,
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, size = 22.sp, color = Color.White, weight = FontWeight.SemiBold, maxLines = 3,
                    )
                }
            }
            if (fix == null) Note(Strings.WAITING_GPS, Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Bg).padding(8.dp))
            if (offRoute) Card(Modifier.fillMaxWidth(), shadow = true, gap = 8.dp) {
                Text(Strings.OFF_ROUTE, weight = FontWeight.SemiBold)
                Button(Strings.REROUTE, Modifier.fillMaxWidth(), onClick = onReroute)
            }
        }
        Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(12.dp), shadow = true, gap = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.minutes(remainingSec), size = 26.sp, weight = FontWeight.Bold, color = Palette.Primary)
                    Note("${distanceLabel(remainingM)} · ${Strings.ARRIVE_ABOUT} ${pilotClock(nowMillis() + remainingSec * 1000L)}")
                }
                Button(Strings.EXIT, kind = ButtonKind.Accent, onClick = onExit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(Strings.STEPS, Modifier.weight(1f), kind = ButtonKind.Soft, icon = KitIcons.route, onClick = onSteps)
                Button(Strings.PREVIEW, Modifier.weight(1f), kind = ButtonKind.Soft, icon = KitIcons.camera) { onPreview(progress?.nextStep ?: 0) }
            }
        }
    }
}

// ---------- "Đường đi": every instruction ----------

@Composable
fun StepsScreen(state: RouteUiState, onBack: () -> Unit, onPreview: (Int) -> Unit) {
    val p = state.phase as? RoutePhase.Results ?: return
    val route = p.routes.getOrNull(p.selected) ?: return
    val steps = remember(route) { navSteps(route, state.destination?.label ?: Strings.DESTINATION) }
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Header(Strings.STEPS, onBack = onBack)
        steps.forEachIndexed { i, s ->
            ListRow(s.text, null, lead = { Text(s.arrow, Modifier.width(36.dp), size = 26.sp, weight = FontWeight.Bold) }, onClick = { onPreview(i) })
        }
        Note(Strings.STEPS_HINT)
    }
}

// ---------- "Xem trước tuyến đường": street photo on top, 2D map below ----------

/**
 * Split preview of instruction [start] (Google Maps "Xem trước tuyến đường"): the top half shows a street photo near the
 * instruction (Mapillary via the API) or, without one, a tilted 3D map looking along the route; the bottom half is the 2D
 * map at the same point. Previous/next walk through the instructions; "Quay lại tuyến đường" closes it.
 */
@Composable
fun PreviewScreen(state: RouteUiState, start: Int, loadPhoto: suspend (Double, Double) -> StreetPhoto?, onBack: () -> Unit, onBackToRoute: () -> Unit = onBack) {
    val p = state.phase as? RoutePhase.Results ?: return
    val route = p.routes.getOrNull(p.selected) ?: return
    val steps = remember(route) { navSteps(route, state.destination?.label ?: Strings.DESTINATION) }
    if (steps.isEmpty()) return
    val track = remember(route, steps) { navTrack(route, steps) }
    // Position along the route (metres). The card shows the last instruction at or before it. Keyed on the route: a
    // re-route or mode switch while the preview is open restarts it instead of pointing past the end.
    var along by rememberSaveable(route) { mutableStateOf(track.stepAlong(start.coerceIn(0, steps.lastIndex))) }
    val i = track.stepIndexAt(along).coerceIn(0, steps.lastIndex)
    val step = steps[i]
    val pos = remember(along) { track.pointAt(along) }
    // Look where the route goes next (or where it came from at the very end).
    val heading = remember(along) {
        val ahead = track.pointAt((along + LOOK_AHEAD_M).coerceAtMost(track.totalM))
        val from = if (ahead == pos) track.pointAt((along - LOOK_AHEAD_M).coerceAtLeast(0.0)) else pos
        val to = if (ahead == pos) pos else ahead
        if (from == to) 0.0 else bearingDeg(from.first, from.second, to.first, to.second)
    }
    var playing by remember { mutableStateOf(false) }

    fun jumpTo(k: Int) { playing = false; along = track.stepAlong(k.coerceIn(0, steps.lastIndex)) } // Trước / Sau / swipe
    fun nudge(meters: Double) { playing = false; along = (along + meters).coerceIn(0.0, track.totalM) } // ︿ / ﹀ chevrons
    LaunchedEffect(playing) { // ▶▶: glide along the whole route at walking-video pace
        while (playing && along < track.totalM) { along = (along + DRIVE_SPACING_M).coerceAtMost(track.totalM); delay(DRIVE_TICK_MS) }
        playing = false
    }
    val moving = playing

    var photo by remember(i) { mutableStateOf<StreetPhoto?>(null) }
    var photoTried by remember(i) { mutableStateOf(false) }
    LaunchedEffect(i) {
        photo = loadPhoto(step.lat, step.lon) // null: no photo near here, no token, or offline -> street-level 3D map instead
        photoTried = true
    }
    val here = Position(latitude = pos.first, longitude = pos.second)
    val view = LocalMapView.current.value
    // Street level: close and tilted like the view from the road, looking where the route goes next.
    val streetCamera = remember(pos, heading) { CameraUpdate(center = here, zoom = STREET_ZOOM, pitch = STREET_PITCH, bearing = heading) }
    val flatCamera = remember(pos, heading) { CameraUpdate(center = here, zoom = 17.5, pitch = 0.0, bearing = heading) }
    // 3D outside satellite: the usual tilted map (as in navigation), not eye level (owner: eye level looked wrong).
    val tiltedCamera = remember(pos, heading) { CameraUpdate(center = here, zoom = 17.5, pitch = 55.0, bearing = heading) }
    val overviewCamera = remember(pos) { CameraUpdate(center = here, zoom = 16.5, pitch = 0.0, bearing = 0.0) }
    val uriHandler = LocalUriHandler.current
    var photoOpen by remember(i) { mutableStateOf(false) }

    @Composable
    fun StepCard(modifier: Modifier) {
        var drag by remember { mutableStateOf(0f) }
        Card(
            modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp)
                // Swipe like Google's step cards; the buttons do the same for users who cannot swipe.
                .pointerInput(steps.size) {
                    detectHorizontalDragGestures(onDragEnd = {
                        // Read the position now: this handler outlives recompositions, so a captured `i` would be stale.
                        val now = track.stepIndexAt(along)
                        if (drag < -SWIPE_PX && now < steps.lastIndex) jumpTo(now + 1) else if (drag > SWIPE_PX && now > 0) jumpTo(now - 1)
                        drag = 0f
                    }) { _, dx -> drag += dx }
                },
            shadow = true, gap = 10.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.arrow, Modifier.width(40.dp).clearAndSetSemantics { }, size = 30.sp, weight = FontWeight.Bold, color = Palette.Primary)
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(step.text, weight = FontWeight.SemiBold, maxLines = 2)
                    Note("${Strings.STEP} ${i + 1}/${steps.size}")
                }
                // ▶▶ plays the whole route step by step; tap again to pause.
                IconButton(
                    if (playing) ExtraIcons.pause else ExtraIcons.fastForward, if (playing) Strings.PAUSE_PREVIEW else Strings.PLAY_PREVIEW,
                    Modifier.clip(CircleShape).background(Palette.Soft),
                ) { playing = !playing }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button("‹ ${Strings.PREVIOUS}", Modifier.weight(1f), kind = ButtonKind.Secondary, compact = true) { if (i > 0) jumpTo(i - 1) }
                Button("${Strings.NEXT} ›", Modifier.weight(1f), kind = ButtonKind.Secondary, compact = true) { if (i < steps.lastIndex) jumpTo(i + 1) }
            }
            Row(
                Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onBackToRoute)
                    .semantics { contentDescription = Strings.BACK_TO_ROUTE },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Text("‹ ${Strings.BACK_TO_ROUTE}", color = Palette.Primary, weight = FontWeight.SemiBold) }
        }
    }

    @Composable
    fun Controls(modifier: Modifier) = RoadControls(
        canForward = along < track.totalM, canBack = along > 0.0, modifier = modifier,
        onForward = { nudge(NUDGE_M) },
        onBack = { nudge(-NUDGE_M) },
    )

    @Composable
    fun PhotoCredit(shot: StreetPhoto, modifier: Modifier) = Text(
        // CC BY-SA: credit the photographer and link to the photo page (tap opens Mapillary).
        shot.credit,
        modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
            .then(if (shot.pageUrl != null) Modifier.clickable(role = Role.Button) { uriHandler.openUri(shot.pageUrl) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        size = 12.sp, color = Color.White,
    )

    if (view != MapView.Satellite) {
        // One full-screen map in the user's 2D/3D choice (owner: the split view is only for satellite imagery).
        Box(Modifier.fillMaxSize().background(Palette.Bg)) {
            RouteMap(
                route, state.origin, state.destination, null, Modifier.fillMaxSize(),
                focus = if (view == MapView.Tilted) tiltedCamera else flatCamera, highlight = pos, insets = PaddingValues(bottom = 230.dp),
            )
            // The road arrow and ︿ / ﹀ chevrons belong to the satellite street view only (owner, 14-11-2026).
            val shot = photo
            if (shot != null) {
                // Street photo as a thumbnail (like Google's), tap to see it big.
                Image(
                    shot.image, contentDescription = "${Strings.STREET_PHOTO}: ${step.text}",
                    Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp).size(width = 132.dp, height = 96.dp)
                        .clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { photoOpen = true },
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.safeDrawingPadding().padding(12.dp)) { Header("", floating = true, onBack = onBack) }
            StepCard(Modifier.align(Alignment.BottomCenter))
            if (shot != null && photoOpen) Box(Modifier.fillMaxSize().background(Color.Black).clickable(role = Role.Button) { photoOpen = false }) {
                Image(shot.image, contentDescription = "${Strings.STREET_PHOTO}: ${step.text}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                PhotoCredit(shot, Modifier.align(Alignment.BottomStart).safeDrawingPadding())
            }
        }
        return
    }

    // Satellite: split like Google's street-level preview: imagery on top, 2D overview below.
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.fillMaxWidth().weight(1.15f)) {
            val shot = photo
            if (shot != null && !moving) {
                Image(shot.image, contentDescription = "${Strings.STREET_PHOTO}: ${step.text}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                PhotoCredit(shot, Modifier.align(Alignment.BottomStart))
            } else {
                RouteMap(route, null, null, null, Modifier.fillMaxSize(), focus = streetCamera, highlight = pos, streetLevel = true)
                if (photoTried && !moving) Note(Strings.NO_STREET_PHOTO, Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Bg).padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Controls(Modifier.align(Alignment.Center).padding(top = 40.dp))
            Box(Modifier.safeDrawingPadding().padding(12.dp)) { Header("", floating = true, onBack = onBack) }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            RouteMap(route, state.origin, state.destination, null, Modifier.fillMaxSize(), focus = overviewCamera, highlight = pos, insets = PaddingValues(bottom = 200.dp))
            StepCard(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** White road arrow + ︿ / ﹀ chevrons in perspective, like Google's street-level preview. */
@Composable
private fun RoadControls(canForward: Boolean, canBack: Boolean, modifier: Modifier, onForward: () -> Unit, onBack: () -> Unit) {
    val shade = Color.Black.copy(alpha = 0.18f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (canForward) {
            // Arrow lying on the road: wide tail near the viewer, narrow tip far away; a dark copy below gives depth.
            Canvas(Modifier.size(width = 70.dp, height = 96.dp).clearAndSetSemantics { }) {
                fun arrow(dy: Float) = Path().apply {
                    val w = size.width
                    val h = size.height
                    moveTo(w * 0.5f, 0f + dy)
                    lineTo(w * 0.82f, h * 0.36f + dy); lineTo(w * 0.6f, h * 0.36f + dy)
                    lineTo(w * 0.66f, h + dy); lineTo(w * 0.34f, h + dy)
                    lineTo(w * 0.4f, h * 0.36f + dy); lineTo(w * 0.18f, h * 0.36f + dy)
                    close()
                }
                drawPath(arrow(3.dp.toPx()), shade)
                drawPath(arrow(0f), Color.White)
            }
            Spacer(Modifier.height(18.dp))
            Chevron(up = true, width = 92.dp, stroke = 9.dp, label = Strings.GO_FORWARD, onClick = onForward)
        }
        Spacer(Modifier.height(26.dp))
        if (canBack) Chevron(up = false, width = 128.dp, stroke = 12.dp, label = Strings.GO_BACK, onClick = onBack)
    }
}

@Composable
private fun Chevron(up: Boolean, width: Dp, stroke: Dp, label: String, onClick: () -> Unit) {
    // The visible stroke is thin and flat (perspective); the touch target around it is at least 48 dp tall.
    Box(
        Modifier.size(width = width + 16.dp, height = 56.dp).semantics { contentDescription = label }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = width, height = 26.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (up) { moveTo(0f, h); lineTo(w / 2, 0f); lineTo(w, h) } else { moveTo(0f, 0f); lineTo(w / 2, h); lineTo(w, 0f) }
            }
            drawPath(path.apply { translate(Offset(0f, 4.dp.toPx())) }, Color.Black.copy(alpha = 0.25f), style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path.apply { translate(Offset(0f, -4.dp.toPx())) }, Color.White, style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Compass bearing in degrees from (lat1, lon1) to (lat2, lon2): camera looks along the route in the preview. */
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = PI / 180
    val y = sin((lon2 - lon1) * r) * cos(lat2 * r)
    val x = cos(lat1 * r) * sin(lat2 * r) - sin(lat1 * r) * cos(lat2 * r) * cos((lon2 - lon1) * r)
    return (atan2(y, x) / r + 360) % 360
}
