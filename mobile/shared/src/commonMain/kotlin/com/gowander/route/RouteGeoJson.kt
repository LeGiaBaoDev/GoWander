package com.gowander.route

import com.gowander.api.Route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

const val EMPTY_FEATURES = """{"type":"FeatureCollection","features":[]}"""

/** Line features of a route's legs (transit or walking), decoded from the API polylines. GeoJSON is lon, lat. */
fun legsGeoJson(route: Route?, transit: Boolean): String {
    if (route == null) return EMPTY_FEATURES
    return buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            route.legs.filter { it.transit == transit }.forEach { leg ->
                val points = decodePolyline(leg.polyline)
                if (points.size < 2) return@forEach
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("mode", leg.mode) }
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        put("coordinates", JsonArray(points.map { (lat, lon) -> buildJsonArray { add(JsonPrimitive(lon)); add(JsonPrimitive(lat)) } }))
                    }
                })
            }
        }
    }.toString()
}

fun pointGeoJson(e: Endpoint?): String =
    if (e == null) EMPTY_FEATURES else """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${e.lon},${e.lat}]}}"""

/** Legs whose polyline cannot be drawn (missing, truncated, or a single point); shown to the user, not hidden. */
fun undrawableLegs(route: Route?): Int = route?.legs?.count { decodePolyline(it.polyline).size < 2 } ?: 0

/** All (lat, lon) points to frame: the selected route if any, else the chosen endpoints. */
fun framePoints(route: Route?, origin: Endpoint?, destination: Endpoint?): List<Pair<Double, Double>> =
    route?.legs?.flatMap { decodePolyline(it.polyline) }?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(origin, destination).map { it.lat to it.lon }
