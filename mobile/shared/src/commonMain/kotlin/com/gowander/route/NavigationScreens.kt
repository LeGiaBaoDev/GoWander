package com.gowander.route

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
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
    // Keyed on the route: a re-route or mode switch while the preview is open restarts it instead of indexing past the end.
    var i by rememberSaveable(route) { mutableStateOf(start.coerceIn(0, steps.lastIndex)) }
    val step = steps[i.coerceIn(0, steps.lastIndex)]
    val after = steps.getOrNull(i + 1)
    val bearing = after?.let { bearingDeg(step.lat, step.lon, it.lat, it.lon) } ?: 0.0
    var photo by remember(i) { mutableStateOf<StreetPhoto?>(null) }
    var photoTried by remember(i) { mutableStateOf(false) }
    LaunchedEffect(i) {
        photo = loadPhoto(step.lat, step.lon) // null: no photo near here, no token, or offline -> 3D map instead
        photoTried = true
    }
    val point = Position(latitude = step.lat, longitude = step.lon)
    // Stable camera targets: a new CameraUpdate per recomposition would restart the camera effect and fight the user.
    val topCamera = remember(step, bearing) { CameraUpdate(center = point, zoom = 17.2, pitch = 60.0, bearing = bearing) }
    val bottomCamera = remember(step) { CameraUpdate(center = point, zoom = 16.3, pitch = 0.0, bearing = 0.0) }
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val shot = photo
            if (shot != null) {
                Image(shot.image, contentDescription = "${Strings.STREET_PHOTO}: ${step.text}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // CC BY-SA: credit the photographer and link to the photo page (tap opens Mapillary).
                Text(
                    shot.credit,
                    Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .then(if (shot.pageUrl != null) Modifier.clickable(role = Role.Button) { uriHandler.openUri(shot.pageUrl) } else Modifier)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    size = 12.sp, color = Color.White,
                )
            } else {
                RouteMap(route, null, null, null, Modifier.fillMaxSize(), focus = topCamera, highlight = step.lat to step.lon)
                if (photoTried) Note(Strings.NO_STREET_PHOTO, Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Bg).padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Box(Modifier.safeDrawingPadding().padding(12.dp)) { Header("", floating = true, onBack = onBack) }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            RouteMap(route, state.origin, state.destination, null, Modifier.fillMaxSize(), focus = bottomCamera, highlight = step.lat to step.lon, insets = PaddingValues(bottom = 260.dp))
            var drag by remember { mutableStateOf(0f) }
            Card(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(12.dp)
                    // Swipe like Google's step cards; the buttons below do the same for users who cannot swipe.
                    .pointerInput(steps.size) {
                        detectHorizontalDragGestures(onDragEnd = {
                            if (drag < -SWIPE_PX && i < steps.lastIndex) i++ else if (drag > SWIPE_PX && i > 0) i--
                            drag = 0f
                        }) { _, dx -> drag += dx }
                    },
                shadow = true, gap = 10.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(step.arrow, Modifier.width(40.dp), size = 30.sp, weight = FontWeight.Bold, color = Palette.Primary)
                    Column(Modifier.weight(1f)) {
                        Text(step.text, weight = FontWeight.SemiBold, maxLines = 3)
                        Note("${Strings.STEP} ${i + 1}/${steps.size}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("‹ ${Strings.PREVIOUS}", Modifier.weight(1f), kind = ButtonKind.Secondary) { if (i > 0) i-- }
                    Button("${Strings.NEXT} ›", Modifier.weight(1f), kind = ButtonKind.Secondary) { if (i < steps.lastIndex) i++ }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onBackToRoute).padding(12.dp)
                        .semantics { contentDescription = Strings.BACK_TO_ROUTE },
                    horizontalArrangement = Arrangement.Center,
                ) { Text("‹ ${Strings.BACK_TO_ROUTE}", color = Palette.Primary, weight = FontWeight.SemiBold) }
            }
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
