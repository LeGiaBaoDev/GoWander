package com.gowander.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.api.Leg
import com.gowander.api.Route
import com.gowander.ui.Action
import com.gowander.ui.Chip
import com.gowander.ui.Label
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Text
import kotlin.coroutines.cancellation.CancellationException
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.LocationTrackingStatus
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

// ADR-025: OpenFreeMap, no API key.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private enum class PickTarget { ORIGIN, DESTINATION }

/** Flow A: pick origin (GPS or long-press) and destination (long-press or Discover), plan, show options, legs and line. */
@Composable
fun RouteMapScreen(vm: RouteViewModel) {
    val state by vm.state.collectAsState()
    var pick by rememberSaveable { mutableStateOf(PickTarget.DESTINATION) }
    // GPS becomes the origin only when there is none yet or the user asks for it. This screen leaves composition
    // when the Discover tab is shown, so a "first fix" flag here would re-apply GPS and cancel a Directions search.
    var userAskedGps by rememberSaveable { mutableStateOf(false) }
    // Android only reports "denied for good" after a request, and forgets it on recreation (M03).
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }

    val location = rememberLocationState(provider = rememberDefaultLocationProvider())
    LaunchedEffect(location.permission) {
        when (val p = location.permission) {
            is LocationPermission.Granted -> permissionBlocked = false
            is LocationPermission.NotGranted -> {
                if (p.canRequest == false) permissionBlocked = true
                userAskedGps = false // a later grant in Settings must not silently replace the origin
            }
            else -> Unit
        }
    }
    val fix = location.lastLocation?.position
    LaunchedEffect(fix != null, userAskedGps) {
        val p = location.lastLocation?.position ?: return@LaunchedEffect
        if (userAskedGps || vm.state.value.origin == null) {
            vm.setOrigin(Endpoint(p.latitude, p.longitude, Strings.MY_LOCATION))
            userAskedGps = false
        }
    }

    val results = state.phase as? RoutePhase.Results
    val route = results?.routes?.getOrNull(results.selected)
    // Decode polylines only when the route changes, not on every recomposition (GPS fixes recompose often).
    val walkJson = remember(route) { legsGeoJson(route, transit = false) }
    val transitJson = remember(route) { legsGeoJson(route, transit = true) }
    val undrawable = remember(route) { undrawableLegs(route) }
    val originJson = remember(state.origin) { pointGeoJson(state.origin) }
    val destinationJson = remember(state.destination) { pointGeoJson(state.destination) }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(STYLE_URL),
        initialCameraPosition = CameraPosition(center = Position(latitude = 10.80, longitude = 106.74), zoom = 11.5),
    ) {
        // GeoJsonSource is immutable in maplibre-compose 0.20.0: key() recreates source + layer when the data changes,
        // keeping the layer order. Walking is dashed and transit solid, so the two differ without relying on colour.
        key(walkJson) {
            LineLayer(id = "route-walk", source = rememberGeoJsonSource(GeoJsonData.JsonString(walkJson)), color = const(Palette.Ink), width = const(4.dp), dasharray = const(listOf(1, 1.5)))
        }
        key(transitJson) {
            LineLayer(id = "route-transit", source = rememberGeoJsonSource(GeoJsonData.JsonString(transitJson)), color = const(Palette.Metro), width = const(6.dp))
        }
        key(originJson) {
            CircleLayer(id = "origin", source = rememberGeoJsonSource(GeoJsonData.JsonString(originJson)), color = const(Palette.Primary), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp))
        }
        key(destinationJson) {
            CircleLayer(id = "destination", source = rememberGeoJsonSource(GeoJsonData.JsonString(destinationJson)), color = const(Palette.Pin), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp))
        }
        LocationIndicatorLayer(id = "user", locationState = location)
    }

    val frame = remember(route, state.origin, state.destination) { framePoints(route, state.origin, state.destination) }
    LaunchedEffect(frame) {
        if (frame.size < 2) return@LaunchedEffect
        try {
            mapState.animateCameraToBounds(
                boundingBox = BoundingBox(
                    west = frame.minOf { it.second }, south = frame.minOf { it.first },
                    east = frame.maxOf { it.second }, north = frame.maxOf { it.first },
                ),
                fit = CameraFit(fitPadding = DpPadding(left = 40.dp, top = 260.dp, right = 40.dp, bottom = 300.dp)),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Framing is cosmetic; the map stays usable at its current camera.
        }
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            interactions = MapInteractions {
                callbacks {
                    longClick {
                        onEvent { event ->
                            event.position?.let { p ->
                                val e = Endpoint(p.latitude, p.longitude, Strings.PICKED_ON_MAP)
                                if (pick == PickTarget.ORIGIN) vm.setOrigin(e) else vm.setDestination(e)
                            }
                            ClickResult.Consume
                        }
                    }
                }
            },
        )
        // Scrim so map labels drawn edge-to-edge do not cover the status bar clock.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.White.copy(alpha = 0.85f)))

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(12.dp)) {
            if (results?.assumedData == true) Label(Strings.DEMO_LABEL, background = Palette.Warn)
            Column(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(16.dp)).padding(12.dp)) {
                Text("${Strings.ORIGIN}: ${state.origin?.label ?: Strings.NOT_SET}")
                Text("${Strings.DESTINATION}: ${state.destination?.label ?: Strings.NOT_SET}", Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp)) {
                    Chip(Strings.SET_ORIGIN_MODE, selected = pick == PickTarget.ORIGIN, Modifier.weight(1f)) { pick = PickTarget.ORIGIN }
                    Spacer(Modifier.width(8.dp))
                    Chip(Strings.SET_DESTINATION_MODE, selected = pick == PickTarget.DESTINATION, Modifier.weight(1f)) { pick = PickTarget.DESTINATION }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LocationAction(location, permissionBlocked) { userAskedGps = true }
                    Spacer(Modifier.width(8.dp))
                    Chip(Strings.SWAP) { vm.swap() }
                    Spacer(Modifier.width(8.dp))
                    Action(Strings.FIND_ROUTE, onClick = vm::search)
                }
                PhaseLine(state, vm)
            }
        }

        if (results != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
                    .background(Palette.Surface, RoundedCornerShape(16.dp))
                    .heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                if (undrawable > 0) Text(Strings.PARTIAL_DRAW, Modifier.padding(bottom = 8.dp), color = Palette.Muted, size = 13.sp)
                results.routes.forEachIndexed { i, r -> RouteCard(r, selected = i == results.selected) { vm.selectRoute(i) } }
                route?.legs?.forEach { LegRow(it) }
            }
        }
    }
}

@Composable
private fun PhaseLine(state: RouteUiState, vm: RouteViewModel) {
    // Announced by screen readers when it changes (loading, error, results).
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { PhaseContent(state, vm) }
}

@Composable
private fun PhaseContent(state: RouteUiState, vm: RouteViewModel) {
    when (val p = state.phase) {
        RoutePhase.Idle -> if (state.origin == null || state.destination == null) Text(Strings.NEED_BOTH, Modifier.padding(top = 8.dp), color = Palette.Muted)
        RoutePhase.Loading -> Text(Strings.SEARCHING, Modifier.padding(top = 8.dp))
        is RoutePhase.Failed -> Column(Modifier.padding(top = 8.dp)) {
            Text(p.message, color = Palette.Pin, bold = true)
            if (p.retryable) Action(Strings.RETRY, Modifier.padding(top = 6.dp), onClick = vm::retry)
        }
        is RoutePhase.Results -> Unit
    }
}

@Composable
private fun RouteCard(r: Route, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Primary else Palette.Line, RoundedCornerShape(12.dp))
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick).padding(10.dp),
    ) {
        Text("${clock(r.startTime)} → ${clock(r.endTime)} · ${Strings.minutes(r.durationSec)}" + if (selected) " · ${Strings.SELECTED}" else "", bold = true)
        Text(
            "${Strings.WALK} ${Strings.meters(r.walkDistanceM)} · ${r.transfers} ${Strings.TRANSFERS} · " +
                r.legs.joinToString(" › ") { if (it.transit) (it.line?.shortName ?: it.mode) else Strings.WALK },
            color = Palette.Muted,
        )
    }
}

@Composable
private fun LegRow(leg: Leg) {
    val title = if (leg.transit) "${leg.line?.shortName ?: leg.mode} ${leg.from.name} → ${leg.to.name}" else "${Strings.WALK} ${Strings.meters(leg.distanceM)}"
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("${clock(leg.startTime)}  $title", bold = leg.transit)
        val detail = buildList {
            add(Strings.minutes(leg.durationSec))
            leg.headsign?.let { add("${Strings.TOWARDS} $it") }
            if (leg.transit && !leg.realtime) add(Strings.SCHEDULED) // never imply live data we do not have
        }.joinToString(" · ")
        Text(detail, color = Palette.Muted, size = 13.sp)
    }
}

/** "2026-11-19T14:02:43+07:00" -> "14:02"; never throws on a malformed value.
 * shortcut: shows the API's local time as sent (pilot is one time zone); revisit for multi-region. */
private fun clock(isoTime: String) = if (isoTime.length >= 16 && isoTime[13] == ':') isoTime.substring(11, 16) else "--:--"

@Composable
private fun LocationAction(location: LocationState, blocked: Boolean, useGps: () -> Unit) {
    val settings = rememberSystemSettingsLauncher()
    val permission = location.permission
    when {
        location.status is LocationTrackingStatus.Unavailable -> Chip(Strings.RETRY_GPS, onClick = location::retry)
        permission is LocationPermission.Granted -> Chip(Strings.USE_MY_LOCATION, onClick = useGps)
        permission is LocationPermission.NotGranted && (blocked || permission.canRequest == false) ->
            if (settings.canOpenApplicationSettings) Chip(Strings.OPEN_LOCATION_SETTINGS) { settings.openApplicationSettings() }
        else -> Chip(Strings.USE_MY_LOCATION) { useGps(); location.requestPermission() }
    }
}
