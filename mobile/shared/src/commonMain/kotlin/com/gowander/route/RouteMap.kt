package com.gowander.route

import com.gowander.ui.Theme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import org.maplibre.compose.camera.CameraMoveReason
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.gowander.ui.Card
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Icon
import com.gowander.ui.Palette
import com.gowander.ui.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gowander.ui.IconButton
import com.gowander.ui.KitIcons
import org.maplibre.compose.overlay.MapOverlayScope
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.gowander.ui.Note
import com.gowander.ui.Strings
import com.gowander.ui.kitShadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gowander.api.Route
import com.gowander.resources.Res
import kotlin.coroutines.cancellation.CancellationException
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.updateCamera
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

// ADR-025: OpenFreeMap, no API key. The bundled style is OpenFreeMap "liberty" with every name label preferring
// name:vi ("Biển Đông", "Trung Quốc"); the stock style prints English names. Owner, 15-11-2026.
// shortcut: a frozen copy of the style, re-download and re-patch it if OpenFreeMap retires its sprite or font paths.
private val STYLE_URL = Res.getUri("files/map_style.json")
private val STYLE_DARK_URL = Res.getUri("files/map_style_dark.json") // OpenFreeMap "fiord", patched the same way

// Light: the water colour of the bundled style, so the coast is seamless. Dark: darker than the style's water, whose
// colour is too close to the land for the country to stand out.
private val MASK_COLOR get() = if (Theme.dark) Color(0x1B, 0x25, 0x34) else Color(158, 189, 255)

private val STREET_LEVEL = CameraConstraints { maxZoom = 21.0; maxPitch = 65.0 }

/** Map view chosen by the user (Google Maps "Loại bản đồ"). Satellite waits for a licensed imagery provider (ADR-033). */
enum class MapView { Flat, Tilted, Satellite }

/** One choice for every map in the app; App provides it so the choice survives screen changes. */
val LocalMapView = compositionLocalOf<MutableState<MapView>> { mutableStateOf(MapView.Flat) }

private fun pitchFor(view: MapView, follow: Boolean) = when {
    view == MapView.Tilted -> 55.0
    view == MapView.Flat -> 0.0
    else -> if (follow) 55.0 else 0.0
}

/**
 * Real map with the selected route (metro solid, walking dashed: not colour only), origin and destination dots
 * and the user's location. Frames the route or endpoints; [stateOut] exposes the camera for the pick screen (M4).
 */
@Composable
fun RouteMap(
    route: Route?,
    origin: Endpoint?,
    destination: Endpoint?,
    location: LocationState?,
    modifier: Modifier = Modifier,
    // Margin around the framed route inside the visible area; cards are already excluded through [insets].
    framePadding: DpPadding = DpPadding(left = 40.dp, top = 40.dp, right = 40.dp, bottom = 40.dp),
    stateOut: MutableState<MapState?>? = null,
    follow: Boolean = false, // NAV-1: tilted camera that follows the user's position and direction of travel
    focus: CameraUpdate? = null, // NAV-1 preview: fixed camera on one instruction instead of framing the route
    highlight: Pair<Double, Double>? = null, // NAV-1 preview: the instruction's point
    viewSwitch: Boolean = false, // show the 2D / 3D / satellite button on this map
    insets: PaddingValues = PaddingValues(0.dp), // area covered by cards: logo, attribution and camera centre avoid it
    streetLevel: Boolean = false, // preview top half: allow eye-level zoom and tilt beyond MapLibre's defaults (20, 60°)
    followZoom: Double = 17.5, // UX-2 split navigation: street view on top follows closer than the 2D map below
    followPitch: Double? = null, // null: from the user's 2D / 3D choice
    credit: String = Strings.MAP_CREDIT, // the licence line; a screen that also shows street photos adds their credit
    style: BaseStyle? = null, // the country map passes the same style without its text labels; null = the bundled style of the current look
    maskJson: String? = null, // GeoJSON polygon painted over the basemap in the sea colour: hides what is outside one country
    markers: @Composable MapOverlayScope.() -> Unit = {}, // UX-2 screen 1: place cards pinned to coordinates
) {
    val viewState = LocalMapView.current
    val view = viewState.value
    // Decode polylines only when the route changes, not on every recomposition (GPS fixes recompose often).
    val walkJson = remember(route) { legsGeoJson(route, transit = false) }
    val transitJson = remember(route) { legsGeoJson(route, transit = true) }
    val originJson = remember(origin) { pointGeoJson(origin) }
    val destinationJson = remember(destination) { pointGeoJson(destination) }
    val highlightJson = remember(highlight) { pointGeoJson(highlight?.let { Endpoint(it.first, it.second, "") }) }

    val mapState = rememberMapState(
        baseStyle = style ?: BaseStyle.Uri(if (Theme.dark) STYLE_DARK_URL else STYLE_URL),
        initialCameraPosition = CameraPosition(center = Position(latitude = 10.785, longitude = 106.715), zoom = 12.0),
    ) {
        if (maskJson != null) FillLayer(id = "mask", source = rememberGeoJsonSource(GeoJsonData.JsonString(maskJson)), color = const(MASK_COLOR))
        // GeoJsonSource is immutable in maplibre-compose 0.20.0: key() recreates source + layer when the data changes.
        key(walkJson) { LineLayer(id = "route-walk", source = rememberGeoJsonSource(GeoJsonData.JsonString(walkJson)), color = const(Palette.Ink), width = const(4.dp), dasharray = const(listOf(1, 1.5))) }
        key(transitJson) { LineLayer(id = "route-transit", source = rememberGeoJsonSource(GeoJsonData.JsonString(transitJson)), color = const(Palette.Metro), width = const(6.dp)) }
        key(originJson) { CircleLayer(id = "origin", source = rememberGeoJsonSource(GeoJsonData.JsonString(originJson)), color = const(Palette.Primary), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp)) }
        key(destinationJson) { CircleLayer(id = "destination", source = rememberGeoJsonSource(GeoJsonData.JsonString(destinationJson)), color = const(Palette.Accent), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp)) }
        key(highlightJson) { CircleLayer(id = "step", source = rememberGeoJsonSource(GeoJsonData.JsonString(highlightJson)), color = const(Palette.Primary), radius = const(10.dp), strokeColor = const(Color.White), strokeWidth = const(4.dp)) }
        if (location != null) LocationIndicatorLayer(id = "user", locationState = location)
    }
    // Navigation: follow each GPS fix at the user's pace; a pan/zoom by hand pauses following until "Căn giữa lại".
    var following by remember { mutableStateOf(true) }
    if (follow && location != null) {
        val reason = mapState.cameraMoveReason
        LaunchedEffect(reason) { if (reason == CameraMoveReason.Gesture) following = false }
        LaunchedEffect(view, following) { if (following) mapState.animateCamera(CameraUpdate(zoom = followZoom, pitch = followPitch ?: if (view == MapView.Flat) 0.0 else 55.0)) }
        // Bearing from course or compass, whichever is more accurate.
        LocationTrackingEffect(locationState = location, enabled = following) { updateCamera(mapState, updateBearing = BearingUpdate.TrackAutomatic) }
    }
    if (focus != null) LaunchedEffect(focus) {
        try { mapState.animateCamera(focus) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* cosmetic */ }
    }
    if (stateOut != null) stateOut.value = mapState

    val frame = remember(route, origin, destination) { framePoints(route, origin, destination) }
    LaunchedEffect(frame) {
        if (frame.isEmpty() || follow || focus != null) return@LaunchedEffect
        try {
            if (frame.size == 1) {
                mapState.animateCamera(CameraUpdate(center = Position(latitude = frame[0].first, longitude = frame[0].second), zoom = 15.0))
            } else {
                mapState.animateCameraToBounds(
                    boundingBox = BoundingBox(west = frame.minOf { it.second }, south = frame.minOf { it.first }, east = frame.maxOf { it.second }, north = frame.maxOf { it.first }),
                    fit = CameraFit(fitPadding = framePadding),
                )
                if (view == MapView.Tilted) mapState.animateCamera(CameraUpdate(pitch = pitchFor(view, follow = false)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Framing is cosmetic; the map stays usable at its current camera.
        }
    }
    // Pitch changes on its own when the user picks 2D/3D; after a new route the fit above runs first (review: no race).
    if (!follow && focus == null) LaunchedEffect(view) {
        try { mapState.animateCamera(CameraUpdate(pitch = pitchFor(view, follow = false))) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* cosmetic */ }
    }
    Box(modifier) {
        // Logo + attribution only (required by the data licences). The default compass appeared unlabeled on top of the
        // "Loại bản đồ" button when the map rotates in navigation (UX audit T2/T3, 14-11-2026); "Căn giữa lại" covers it.
        // No built-in controls: the default compass sat unlabeled on our button, and the expanding attribution bar
        // opened across the middle of the map on iOS (14-11-2026). The licence credit is drawn below instead.
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState, viewportInsets = insets, overlay = markers,
            cameraConstraints = if (streetLevel) STREET_LEVEL else CameraConstraints.Standard,
        )
        // ODbL / OpenMapTiles credit: always visible, small, clear of the cards (plain text, nothing to mis-tap).
        Text(
            credit,
            Modifier.align(Alignment.BottomEnd).padding(insets).padding(end = 8.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp)).background(Palette.Bg.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
            size = 10.sp, color = Palette.Muted,
        )
        if (follow && !following) {
            Button(
                Strings.RECENTER, Modifier.align(Alignment.BottomStart).padding(insets).padding(start = 16.dp, bottom = 12.dp).kitShadow(RoundedCornerShape(14.dp)),
                kind = ButtonKind.Secondary, icon = KitIcons.locate,
            ) { following = true }
        }
        if (viewSwitch) {
            var open by remember { mutableStateOf(false) }
            MapViewSwitch(view, open, onOpenChange = { open = it }, modifier = Modifier.fillMaxSize(), buttonPadding = insets) { viewState.value = it }
        }
    }
}

/**
 * "Loại bản đồ" button at the right edge; its panel closes after a choice, on a tap outside it, or on the button again
 * (owner, 14-11-2026: nothing should stay open with no obvious way to close it). Public for UI tests.
 */
@Composable
fun MapViewSwitch(
    view: MapView,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonPadding: PaddingValues = PaddingValues(0.dp),
    onChange: (MapView) -> Unit,
) {
    var satelliteNote by remember(open) { mutableStateOf(false) }
    Box(modifier) {
        if (open) Box(
            Modifier.fillMaxSize().semantics { contentDescription = Strings.CLOSE_PANEL }
                .clickable(interactionSource = null, indication = null) { onOpenChange(false) },
        )
        // Top right, just below whatever covers the top of the map (search bar, banner), like Google's layers button.
        Column(
            // Below the status bar / notch first (taller on iPhone: the button hid behind "VI" on iOS, 14-11-2026),
            // then below whatever the screen puts at the top.
            Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = buttonPadding.calculateTopPadding()).padding(top = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(ExtraIcons.layers, Strings.MAP_VIEW, Modifier.kitShadow(CircleShape).clip(CircleShape).background(Palette.Bg)) { onOpenChange(!open) }
            // Google Maps "Loại bản đồ": title + close, then picture tiles side by side; the chosen one is outlined.
            if (open) Card(Modifier.width(296.dp), shadow = true, gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.MAP_VIEW, Modifier.weight(1f), size = 17.sp, weight = FontWeight.SemiBold)
                    IconButton(ExtraIcons.close, Strings.CLOSE, tint = Palette.Muted) { onOpenChange(false) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ViewTile(KitIcons.map, Strings.VIEW_2D, null, view == MapView.Flat, Modifier.weight(1f)) { onChange(MapView.Flat); onOpenChange(false) }
                    ViewTile(ExtraIcons.cube, Strings.VIEW_3D, null, view == MapView.Tilted, Modifier.weight(1f)) { onChange(MapView.Tilted); onOpenChange(false) }
                    // Owner, 14-11-2026: show the satellite choice now; it only explains until a provider is chosen (PIPELINE mock register).
                    ViewTile(KitIcons.globe, Strings.VIEW_SATELLITE, null, false, Modifier.weight(1f)) { satelliteNote = true }
                }
                if (satelliteNote) Note(Strings.SATELLITE_PENDING, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
private fun ViewTile(icon: ImageVector, label: String, badge: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).semantics(mergeDescendants = true) { this.selected = selected }.clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(shape).background(if (selected) Palette.Soft else Palette.Subtle)
                .border(2.dp, if (selected) Palette.Primary else Palette.Line, shape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, 28.dp, if (selected) Palette.Primary else if (badge != null) Palette.Muted else Palette.Ink) }
        Text(label, size = 13.sp, color = if (selected) Palette.Primary else Palette.Ink, weight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (badge != null) Text(badge, size = 11.sp, color = Palette.Muted)
    }
}
