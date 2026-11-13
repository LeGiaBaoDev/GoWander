package com.gowander.route

import com.gowander.api.Fixtures
import com.gowander.api.PlanResponse
import com.gowander.api.apiJson
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineTest {
    @Test
    fun decodesTheReferenceExample() {
        // Example from Google's "Encoded Polyline Algorithm Format" documentation.
        assertEquals(
            listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453),
            decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@"),
        )
    }

    @Test
    fun emptyAndTruncatedInputDoNotCrash() {
        assertEquals(emptyList(), decodePolyline(""))
        assertEquals(emptyList(), decodePolyline("_p~iF")) // latitude without longitude
    }

    @Test
    fun decodedMetroLegStartsAndEndsAtItsStations() {
        val metro = apiJson.decodeFromString<PlanResponse>(Fixtures.ROUTE_OK).routes.first().legs.first { it.transit }
        val points = decodePolyline(metro.polyline)
        assertTrue(points.size >= 2)
        fun near(a: Pair<Double, Double>, lat: Double, lon: Double) = abs(a.first - lat) < 0.001 && abs(a.second - lon) < 0.001
        assertTrue(near(points.first(), metro.from.lat, metro.from.lon), "start ${points.first()} vs ${metro.from}")
        assertTrue(near(points.last(), metro.to.lat, metro.to.lon), "end ${points.last()} vs ${metro.to}")
    }
}
