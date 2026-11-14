package com.gowander.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gowander.api.Route
import com.gowander.ui.Palette
import kotlin.coroutines.cancellation.CancellationException
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.map.MapState
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
    framePadding: DpPadding = DpPadding(left = 40.dp, top = 160.dp, right = 40.dp, bottom = 280.dp),
    stateOut: MutableState<MapState?>? = null,
) {
    // Decode polylines only when the route changes, not on every recomposition (GPS fixes recompose often).
    val walkJson = remember(route) { legsGeoJson(route, transit = false) }
    val transitJson = remember(route) { legsGeoJson(route, transit = true) }
    val originJson = remember(origin) { pointGeoJson(origin) }
    val destinationJson = remember(destination) { pointGeoJson(destination) }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(STYLE_URL),
        initialCameraPosition = CameraPosition(center = Position(latitude = 10.785, longitude = 106.715), zoom = 12.0),
    ) {
        // GeoJsonSource is immutable in maplibre-compose 0.20.0: key() recreates source + layer when the data changes.
        key(walkJson) { LineLayer(id = "route-walk", source = rememberGeoJsonSource(GeoJsonData.JsonString(walkJson)), color = const(Palette.Ink), width = const(4.dp), dasharray = const(listOf(1, 1.5))) }
        key(transitJson) { LineLayer(id = "route-transit", source = rememberGeoJsonSource(GeoJsonData.JsonString(transitJson)), color = const(Palette.Metro), width = const(6.dp)) }
        key(originJson) { CircleLayer(id = "origin", source = rememberGeoJsonSource(GeoJsonData.JsonString(originJson)), color = const(Palette.Primary), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp)) }
        key(destinationJson) { CircleLayer(id = "destination", source = rememberGeoJsonSource(GeoJsonData.JsonString(destinationJson)), color = const(Palette.Accent), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp)) }
        if (location != null) LocationIndicatorLayer(id = "user", locationState = location)
    }
    if (stateOut != null) stateOut.value = mapState

    val frame = remember(route, origin, destination) { framePoints(route, origin, destination) }
    LaunchedEffect(frame) {
        if (frame.isEmpty()) return@LaunchedEffect
        try {
            if (frame.size == 1) {
                mapState.animateCamera(CameraUpdate(center = Position(latitude = frame[0].first, longitude = frame[0].second), zoom = 15.0))
            } else {
                mapState.animateCameraToBounds(
                    boundingBox = BoundingBox(west = frame.minOf { it.second }, south = frame.minOf { it.first }, east = frame.maxOf { it.second }, north = frame.maxOf { it.first }),
                    fit = CameraFit(fitPadding = framePadding),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Framing is cosmetic; the map stays usable at its current camera.
        }
    }
    MaplibreMap(modifier = modifier, state = mapState)
}
