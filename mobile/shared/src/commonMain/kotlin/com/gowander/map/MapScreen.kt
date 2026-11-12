package com.gowander.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.serialization.json.jsonPrimitive

// ADR-025: OpenFreeMap, no API key.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
// Colours: "ui" palette of GoWander-Design-Kit-v4 (transit/metro, brand/teal-strong, neutral/ink, tint/sun, accent/coral-strong).
private val Metro = Color(0xFF0079B8)
private val Primary = Color(0xFF047A7D)
private val Ink = Color(0xFF123C48)
private val Warn = Color(0xFFFFF4D6)
private val Pin = Color(0xFFC8234F)

@Composable
fun MapScreen() {
    var selectedStation by rememberSaveable { mutableStateOf<String?>(null) }
    // Manual origin fallback when GPS is denied or unavailable: long-press the map. Stored as "lat,lon" to survive rotation.
    var manualOrigin by rememberSaveable { mutableStateOf<String?>(null) }
    // Frame the whole line once; saved so rotation keeps the user's camera.
    var framed by rememberSaveable { mutableStateOf(false) }
    // Android only reports "denied for good" after a request, and forgets it on recreation; remember it across rotation.
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }

    val locationState = rememberLocationState(provider = rememberDefaultLocationProvider())
    LaunchedEffect(locationState.permission) {
        when (val p = locationState.permission) {
            is LocationPermission.Granted -> permissionBlocked = false
            is LocationPermission.NotGranted -> if (p.canRequest == false) permissionBlocked = true
            else -> Unit
        }
    }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(STYLE_URL),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = 10.8215, longitude = 106.7560),
            zoom = 11.5,
        ),
    ) {
        val route = rememberGeoJsonSource(GeoJsonData.JsonString(MetroL1Demo.demoRouteGeoJson()))
        LineLayer(id = "demo-route", source = route, color = const(Metro), width = const(5.dp))

        val stations = rememberGeoJsonSource(GeoJsonData.JsonString(MetroL1Demo.stationsGeoJson()))
        CircleLayer(
            id = "stations",
            source = stations,
            color = const(Color.White),
            radius = const(7.dp),
            strokeColor = const(Metro),
            strokeWidth = const(3.dp),
            hitPadding = 12.dp,
            onClick = { features ->
                selectedStation = features.firstOrNull()?.properties?.get("name")?.jsonPrimitive?.content
                ClickResult.Consume
            },
        )

        manualOrigin?.let { origin ->
            val (lat, lon) = origin.split(",").map(String::toDouble)
            val point = rememberGeoJsonSource(
                GeoJsonData.JsonString("""{"type":"Point","coordinates":[$lon,$lat]}"""),
            )
            CircleLayer(id = "manual-origin", source = point, color = const(Pin), strokeColor = const(Color.White), strokeWidth = const(3.dp), radius = const(8.dp))
        }

        LocationIndicatorLayer(id = "user", locationState = locationState)
    }

    LaunchedEffect(mapState) {
        if (framed) return@LaunchedEffect
        val s = MetroL1Demo.stations
        try {
            mapState.animateCameraToBounds(
                boundingBox = BoundingBox(west = s.minOf { it.lon }, south = s.minOf { it.lat }, east = s.maxOf { it.lon }, north = s.maxOf { it.lat }),
                fit = CameraFit(fitPadding = DpPadding(left = 32.dp, top = 200.dp, right = 32.dp, bottom = 48.dp)),
            )
            framed = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Framing is cosmetic: keep the initial camera instead of crashing; framed stays false so the next start retries.
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
                            event.position?.let { manualOrigin = "${it.latitude},${it.longitude}" }
                            ClickResult.Consume
                        }
                    }
                }
            },
        )
        // Scrim so map labels drawn edge-to-edge do not cover the status bar clock.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.White.copy(alpha = 0.85f)))
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(12.dp),
        ) {
            Label(MetroL1Demo.LABEL, background = Warn)
            selectedStation?.let { Label("Ga: $it") }
            Label(originText(locationState, manualOrigin))
            LocationAction(locationState, permissionBlocked, manualOrigin != null) { manualOrigin = null }
        }
    }
}

private fun originText(location: LocationState, manualOrigin: String?): String {
    val gps = location.lastLocation
    return when {
        // The user's explicit choice wins over GPS until they ask for GPS again.
        manualOrigin != null -> "Điểm xuất phát: điểm đã chọn trên bản đồ"
        gps != null && location.permission is LocationPermission.Granted -> "Điểm xuất phát: vị trí của bạn"
        location.status is LocationTrackingStatus.Unavailable -> "Không lấy được vị trí. Nhấn giữ bản đồ để chọn điểm xuất phát"
        else -> "Nhấn giữ bản đồ để chọn điểm xuất phát"
    }
}

@Composable
private fun LocationAction(location: LocationState, blocked: Boolean, hasManualOrigin: Boolean, clearManualOrigin: () -> Unit) {
    val settings = rememberSystemSettingsLauncher()
    val permission = location.permission
    when {
        location.status is LocationTrackingStatus.Unavailable -> Action("Thử lại", location::retry)
        permission is LocationPermission.Granted -> {
            if (hasManualOrigin) Action("Dùng vị trí của tôi", clearManualOrigin)
        }
        permission is LocationPermission.NotGranted && (blocked || permission.canRequest == false) -> {
            if (settings.canOpenApplicationSettings) Action("Mở Cài đặt để bật vị trí") { settings.openApplicationSettings() }
        }
        permission is LocationPermission.NotGranted || permission is LocationPermission.NotDetermined ->
            Action("Dùng vị trí của tôi") { clearManualOrigin(); location.requestPermission() }
        else -> Unit
    }
}

@Composable
private fun Label(text: String, background: Color = Color.White) {
    BasicText(
        text,
        style = TextStyle(fontSize = 14.sp, color = Ink),
        modifier = Modifier.padding(bottom = 6.dp).background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    BasicText(
        text,
        style = TextStyle(fontSize = 15.sp, color = Color.White),
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .background(Primary, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
