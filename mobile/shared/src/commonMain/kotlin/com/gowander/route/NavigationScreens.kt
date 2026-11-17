package com.gowander.route

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.semantics.selected
import com.gowander.ui.kitShadow
import com.gowander.Prefs
import com.gowander.home.LocalNotice
import com.gowander.ui.Icon
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

internal val NavBanner = Color(0xFF0B5F62) // darker than Primary so white text keeps contrast over the map
private const val OFF_ROUTE_M = 60.0
private const val ARRIVED_M = 25.0
private const val JOIN_M = 30.0
private const val SWIPE_PX = 80f
private const val BACKTRACK_M = 50.0
// ▶▶ Chạy: 1.5 m every 100 ms = 15 m/s (~54 km/h, about 10x walking). It was 12 m / 160 ms (~270 km/h): far too fast
// to read the street (owner, 15-11-2026). A 2 km route plays in about 2 minutes; Trước / Sau still jump step by step.
private const val DRIVE_SPACING_M = 1.5
private const val STREET_ZOOM = 19.0 // matches the Esri satellite source's max zoom (z20+ is its grey "no data" tile)
private const val STREET_PITCH = 65.0 // 78° ran out of memory (tiles to the horizon, 1.1 GB on the emulator, 14-11-2026)
private const val DRIVE_TICK_MS = 100L
private const val NUDGE_M = 20.0 // one tap on ︿ / ﹀ moves this far along the route (owner: small steps, like Street View)
private const val LOOK_AHEAD_M = 8.0
private const val NAV_STREET_ZOOM = 19.0 // moving: one step back from the preview's eye level, fewer tiles per second
private const val OVERVIEW_ZOOM = 16.5
private const val NO_VOICE_TOLD = "no_vi_voice_told"

// Vietnamese decimal comma: "1,2 km" (design screen 7).
fun distanceLabel(m: Double): String = if (m < 1000) "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} m" else "${(m / 100).roundToInt() / 10.0} km".replace('.', ',')

// ---------- Navigation mode (Google Maps "Bắt đầu") ----------

/**
 * UX-2 screen 7: split navigation like the street-level preview. Top: 3D view from the road that follows the user;
 * bottom: 2D map. Next instruction over the top half; time left / distance / arrival, the step list and "Thoát" at the
 * bottom. Off the route: offer a new route.
 */
@Composable
fun NavigationScreen(
    state: RouteUiState,
    location: LocationState,
    onExit: () -> Unit,
    onSteps: () -> Unit,
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

    // P2b voice (plan §8.3): the device's vi-VN voice; the sentence is also shown under the banner.
    val notice = LocalNotice.current
    val speaker = rememberSpeaker()
    var voice by rememberSaveable { mutableStateOf(VoiceMode.load()) }
    val cues = remember(route) { VoiceCues() }
    var spoken by remember(route) { mutableStateOf<String?>(null) }
    // Off-route safety warning: full-screen, haptic when voice is off, voice when it is on; stops the moment the user
    // is back on the route or taps the manual dismiss button.
    var warningDismissed by rememberSaveable(route) { mutableStateOf(false) }
    LaunchedEffect(offRoute) { if (!offRoute) warningDismissed = false }
    val warningHaptics = rememberWarningHaptics()
    LaunchedEffect(offRoute, warningDismissed, voice) {
        val active = offRoute && !warningDismissed
        if (!active) { warningHaptics.stop(); return@LaunchedEffect }
        if (voice == VoiceMode.Off) { warningHaptics.start(); return@LaunchedEffect }
        while (true) { speaker.speak(Strings.WRONG_WAY_SPOKEN); delay(6000) }
    }
    DisposableEffect(Unit) { onDispose { warningHaptics.stop() } }
    LaunchedEffect(progress, arrived, offRoute) {
        val cue = cues.next(progress, steps, arrived, offRoute, destination) ?: return@LaunchedEffect
        if (audible(cue, voice)) { spoken = cue.text; speaker.speak(cue.text) }
    }
    LaunchedEffect(speaker.ready) {
        // Said once per device, then guidance is text only.
        if (speaker.ready == false && !Prefs.getBool(NO_VOICE_TOLD)) { Prefs.setBool(NO_VOICE_TOLD, true); notice(Strings.NO_VI_VOICE) }
    }

    val topView = remember { mutableStateOf(MapView.Satellite) }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.fillMaxWidth().weight(420f)) {
            // Top half is always satellite, first-person: the user sees the real streets, not a schematic.
            CompositionLocalProvider(LocalMapView provides topView) {
                RouteMap(
                    route, state.origin, state.destination, location, Modifier.fillMaxSize(), follow = true, streetLevel = true,
                    followZoom = NAV_STREET_ZOOM, followPitch = STREET_PITCH, insets = PaddingValues(top = 130.dp),
                )
            }
            Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().kitShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(NavBanner).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(next?.arrow ?: "↑", Modifier.clearAndSetSemantics { }, size = 34.sp, color = Color.White, weight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (progress != null && !arrived) Text(distanceLabel(progress.toNextM), size = 26.sp, color = Color.White, weight = FontWeight.Bold)
                        // Only the instruction is a live region: announcing the distance on every GPS fix would flood TalkBack/VoiceOver.
                        Text(
                            if (arrived) "${Strings.ARRIVED} $destination" else next?.text ?: Strings.WAITING_GPS,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, size = 15.sp, color = Color.White, weight = FontWeight.SemiBold, maxLines = 3,
                        )
                    }
                }
                spoken?.takeIf { voice != VoiceMode.Off }?.let { line ->
                    Row(
                        Modifier.fillMaxWidth().kitShadow(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Palette.Bg.copy(alpha = 0.95f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(ExtraIcons.volume, 18.dp, Palette.Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("“$line”", size = 13.sp, maxLines = 2)
                    }
                }
                if (fix == null) Note(Strings.WAITING_GPS, Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Bg).padding(8.dp))
                if (offRoute) Card(Modifier.fillMaxWidth(), shadow = true, gap = 8.dp) {
                    Text(Strings.OFF_ROUTE, weight = FontWeight.SemiBold)
                    Button(Strings.REROUTE, Modifier.fillMaxWidth(), onClick = onReroute)
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(432f)) {
            val viewState = LocalMapView.current
            LaunchedEffect(Unit) { if (viewState.value == MapView.Satellite) viewState.value = MapView.Tilted }
            RouteMap(
                route, state.origin, state.destination, location, Modifier.fillMaxSize().padding(top = 3.dp), follow = true,
                followZoom = OVERVIEW_ZOOM, insets = PaddingValues(bottom = 146.dp), // pitch from the 2D / 3D choice below
            )
            // Design screen 7: speaker (Bật -> Chỉ cảnh báo -> Tắt, saved) above the 2D / 3D button.
            val voiceLabel = when (voice) { VoiceMode.On -> Strings.VOICE_ON; VoiceMode.AlertsOnly -> Strings.VOICE_ALERTS; VoiceMode.Off -> Strings.VOICE_OFF }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 15.dp, end = 12.dp).size(48.dp).kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg)
                    .semantics { contentDescription = "${Strings.VOICE}: $voiceLabel" }
                    .clickable(role = Role.Button) {
                        voice = voice.next()
                        VoiceMode.save(voice)
                        if (voice == VoiceMode.Off) speaker.stop()
                        notice("${Strings.VOICE}: ${when (voice) { VoiceMode.On -> Strings.VOICE_ON; VoiceMode.AlertsOnly -> Strings.VOICE_ALERTS; VoiceMode.Off -> Strings.VOICE_OFF }}")
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(when (voice) { VoiceMode.On -> ExtraIcons.volume; VoiceMode.AlertsOnly -> KitIcons.bell; VoiceMode.Off -> ExtraIcons.volumeOff }, 22.dp, Palette.Primary) }
            // One round button names the lower map's view; a tap switches 2D <-> 3D.
            val tilted = viewState.value == MapView.Tilted
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 71.dp, end = 12.dp).size(48.dp).kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg)
                    .semantics { contentDescription = "${Strings.MAP_VIEW}: ${if (tilted) Strings.VIEW_3D else Strings.VIEW_2D}" }
                    .clickable(role = Role.Button) { viewState.value = if (tilted) MapView.Flat else MapView.Tilted },
                contentAlignment = Alignment.Center,
            ) { Text(if (tilted) Strings.VIEW_3D else Strings.VIEW_2D, size = 13.sp, weight = FontWeight.Bold, color = Palette.Primary) }
            BottomSheet(Modifier.align(Alignment.BottomCenter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Strings.minutes(remainingSec), size = 22.sp, weight = FontWeight.Bold, color = Palette.Bus)
                        Note("${distanceLabel(remainingM)} · ${Strings.ARRIVE_ABOUT} ${pilotClock(nowMillis() + remainingSec * 1000L)}")
                    }
                    IconButton(ExtraIcons.list, Strings.STEPS, Modifier.border(1.dp, Palette.Line, CircleShape), onClick = onSteps)
                    Spacer(Modifier.width(12.dp))
                    Button(Strings.EXIT, kind = ButtonKind.Accent, pill = true, onClick = onExit)
                }
            }
        }
    }
    if (offRoute && !warningDismissed) WrongWayDialog { warningDismissed = true }
}

@Composable
private fun WrongWayDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Palette.Accent.copy(alpha = 0.97f)).safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⚠", size = 64.sp, color = Color.White, weight = FontWeight.Bold)
                Text(Strings.WRONG_WAY_TITLE, size = 26.sp, color = Color.White, weight = FontWeight.Bold)
                Text(Strings.WRONG_WAY_BODY, size = 15.sp, color = Color.White)
                Button(Strings.WRONG_WAY_DISMISS, kind = ButtonKind.Secondary, onClick = onDismiss)
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

// ---------- "Xem từng bước": street view on top, 2D map below (UX-2 screen 6) ----------

/**
 * Step-by-step view of instruction [start], always split (owner, 14-11-2026: 3D on top, 2D below): the top half is the
 * street-level view looking along the route (a street photo near the instruction when Mapillary has one), with ︿ / ﹀
 * to move 20 m; the bottom half is the 2D map at the same point. The card walks through the instructions; Back returns
 * to the route.
 */
@Composable
fun PreviewScreen(state: RouteUiState, start: Int, loadPhoto: suspend (Double, Double) -> StreetPhoto?, onBack: () -> Unit, onList: () -> Unit) {
    val p = state.phase as? RoutePhase.Results ?: return
    val route = p.routes.getOrNull(p.selected) ?: return
    val steps = remember(route) { navSteps(route, state.destination?.label ?: Strings.DESTINATION) }
    if (steps.isEmpty()) return
    val track = remember(route, steps) { navTrack(route, steps) }
    // Position along the route (metres). The card shows the last instruction at or before it. Keyed on the route and the
    // start step: a re-route, a mode switch or a step chosen in the list restarts it instead of keeping an old position.
    var along by rememberSaveable(route, start) { mutableStateOf(track.stepAlong(start.coerceIn(0, steps.lastIndex))) }
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

    val here = Position(latitude = pos.first, longitude = pos.second)
    // Street level: close and tilted like the view from the road, looking where the route goes next.
    val streetCamera = remember(pos, heading) { CameraUpdate(center = here, zoom = STREET_ZOOM, pitch = STREET_PITCH, bearing = heading) }
    val viewState = LocalMapView.current
    // The lower map only offers 2D / 3D; if a previous screen stored Satellite, fall back to 3D.
    LaunchedEffect(Unit) { if (viewState.value == MapView.Satellite) viewState.value = MapView.Tilted }
    val overviewCamera = remember(pos, viewState.value) { CameraUpdate(center = here, zoom = OVERVIEW_ZOOM, pitch = if (viewState.value == MapView.Tilted) 55.0 else 0.0, bearing = 0.0) }
    var drag by remember { mutableStateOf(0f) }

    val topView = remember { mutableStateOf(MapView.Satellite) }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.fillMaxWidth().weight(370f)) {
            // Top half is always satellite, first-person; the photo was useful but the owner wants imagery here.
            CompositionLocalProvider(LocalMapView provides topView) {
                RouteMap(route, null, null, null, Modifier.fillMaxSize(), focus = streetCamera, highlight = pos, streetLevel = true)
            }
            if (along < track.totalM) RoadArrow(Modifier.align(Alignment.Center).padding(top = 60.dp))
            // Design screen 6: ︿ / ﹀ as two round buttons at the right edge (disabled at the ends instead of vanishing).
            Column(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 96.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NudgeButton("︿", Strings.GO_FORWARD, enabled = along < track.totalM) { nudge(NUDGE_M) }
                NudgeButton("﹀", Strings.GO_BACK, enabled = along > 0.0) { nudge(-NUDGE_M) }
            }
            // Header fills the row width, so the label sits beside it on its own (it was pushed off-screen, E2E 14-11-2026).
            Box(Modifier.safeDrawingPadding().padding(12.dp)) { Header("", floating = true, onBack = onBack) }
            Text(
                Strings.VIEW_SATELLITE,
                Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = 72.dp, top = 24.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Palette.Deep.copy(alpha = 0.78f)).padding(horizontal = 10.dp, vertical = 4.dp),
                size = 12.sp, color = Color.White, weight = FontWeight.SemiBold,
            )
        }
        Box(Modifier.fillMaxWidth().weight(482f)) {
            RouteMap(route, state.origin, state.destination, null, Modifier.fillMaxSize().padding(top = 3.dp), focus = overviewCamera, highlight = pos, insets = PaddingValues(bottom = 206.dp))
            ViewSegment(viewState.value, Modifier.align(Alignment.TopEnd).padding(top = 15.dp, end = 12.dp)) { viewState.value = it }
            BottomSheet(
                Modifier.align(Alignment.BottomCenter)
                    // Swipe like Google's step cards; the buttons do the same for users who cannot swipe.
                    .pointerInput(steps.size) {
                        detectHorizontalDragGestures(onDragEnd = {
                            // Read the position now: this handler outlives recompositions, so a captured `i` would be stale.
                            val now = track.stepIndexAt(along)
                            if (drag < -SWIPE_PX && now < steps.lastIndex) jumpTo(now + 1) else if (drag > SWIPE_PX && now > 0) jumpTo(now - 1)
                            drag = 0f
                        }) { _, dx -> drag += dx }
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Primary).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                        Text(step.arrow, size = 22.sp, weight = FontWeight.Bold, color = Palette.OnPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(step.text, size = 16.sp, weight = FontWeight.Bold, maxLines = 2)
                        val toNext = (if (i < steps.lastIndex) track.stepAlong(i + 1) else track.totalM) - along
                        Note("${Strings.STEP} ${i + 1}/${steps.size} · ${Strings.REMAINING} ${distanceLabel(toNext.coerceAtLeast(0.0))}", )
                    }
                    IconButton(ExtraIcons.list, Strings.STEPS, Modifier.border(1.dp, Palette.Line, CircleShape), onClick = onList)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button("‹ ${Strings.PREVIOUS}", Modifier.weight(1f), kind = ButtonKind.Secondary, compact = true, pill = true) { if (i > 0) jumpTo(i - 1) }
                    // ▶▶ Chạy plays the whole route; tap again to pause. The spoken label says what it does.
                    Button(
                        if (playing) Strings.PAUSE else Strings.RUN_PREVIEW,
                        Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = if (playing) Strings.PAUSE_PREVIEW else Strings.PLAY_PREVIEW },
                        kind = ButtonKind.Outline, icon = if (playing) ExtraIcons.pause else ExtraIcons.fastForward, compact = true, pill = true,
                    ) { playing = !playing }
                    Button("${Strings.NEXT} ›", Modifier.weight(1f), compact = true, pill = true) { if (i < steps.lastIndex) jumpTo(i + 1) }
                }
            }
        }
    }
}

/** White arrow lying on the road (wide near the viewer, narrow far away), like Google's street-level preview. */
@Composable
private fun RoadArrow(modifier: Modifier) {
    val shade = Color.Black.copy(alpha = 0.18f)
    Canvas(modifier.size(width = 70.dp, height = 96.dp).clearAndSetSemantics { }) {
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
}

@Composable
private fun NudgeButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg.copy(alpha = 0.95f))
            .semantics { contentDescription = label }.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, size = 20.sp, weight = FontWeight.Bold, color = if (enabled) Palette.Ink else Palette.Line) }
}

/** "2D | 3D" switch on the lower map (design screen 6); each half is a 48 dp target, the chosen one filled. */
@Composable
internal fun ViewSegment(view: MapView, modifier: Modifier = Modifier, onChange: (MapView) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.kitShadow(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Palette.Bg).semantics { contentDescription = Strings.LOWER_MAP_VIEW }) {
        listOf(MapView.Flat to Strings.VIEW_2D, MapView.Tilted to Strings.VIEW_3D, MapView.Satellite to Strings.VIEW_SATELLITE).forEach { (v, label) ->
            val on = view == v
            Box(
                Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp).semantics { selected = on }
                    .clickable(role = Role.RadioButton) { onChange(v) }.padding(4.dp)
                    .clip(RoundedCornerShape(20.dp)).background(if (on) Palette.Primary else Palette.Bg),
                contentAlignment = Alignment.Center,
            ) { Text(label, Modifier.padding(horizontal = 8.dp), size = 12.sp, weight = FontWeight.Bold, color = if (on) Palette.OnPrimary else Palette.Ink) }
        }
    }
    }
}

/** Full-width sheet stuck to the bottom with rounded top corners (design screens 5-7). */
@Composable
internal fun BottomSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        modifier.fillMaxWidth().kitShadow(shape).clip(shape).background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Compass bearing in degrees from (lat1, lon1) to (lat2, lon2): camera looks along the route in the preview. */
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = PI / 180
    val y = sin((lon2 - lon1) * r) * cos(lat2 * r)
    val x = cos(lat1 * r) * sin(lat2 * r) - sin(lat1 * r) * cos(lat2 * r) * cos((lon2 - lon1) * r)
    return (atan2(y, x) / r + 360) % 360
}
