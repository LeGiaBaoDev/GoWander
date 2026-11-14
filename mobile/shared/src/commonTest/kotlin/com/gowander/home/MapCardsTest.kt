package com.gowander.home

import com.gowander.api.PlaceSummary
import com.gowander.api.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapCardsTest {
    private fun place(id: String, lat: Double, d: Double) =
        PlaceSummary(id, id, slug = id, lat = lat, lon = 106.7, distanceM = d, source = Source(kind = "osm", license = "ODbL", attribution = "OSM"))

    @Test
    fun cardsKeepTheirDistanceAndNearestComeFirst() {
        // 0.001° lat ≈ 111 m
        val ps = listOf(place("far", 10.010, 1100.0), place("a", 10.000, 10.0), place("b", 10.001, 120.0), place("c", 10.005, 560.0))
        assertEquals(listOf("a", "c", "far"), spreadOut(ps, minNorthM = 400.0, max = 4).map { it.id })
        assertEquals(listOf("a"), spreadOut(ps, minNorthM = 400.0, max = 1).map { it.id })
    }

    @Test
    fun cardsSideBySideNeedMoreRoomThanAboveEachOther() {
        // Same latitude, 450 m apart east-west: too close for two wide cards; 450 m north is fine.
        val a = place("a", 10.0, 0.0)
        val east = a.copy(id = "east", lon = 106.7 + 450 / (111_320.0 * kotlin.math.cos(10.0 * kotlin.math.PI / 180)), distanceM = 450.0)
        val north = place("north", 10.0 + 450 / 111_320.0, 460.0)
        assertEquals(listOf("a", "north"), spreadOut(listOf(a, east, north), minNorthM = 380.0, max = 4, minEastM = 1100.0).map { it.id })
    }

    @Test
    fun metersBetweenIsRoughlyRight() = assertTrue(metersBetween(10.0, 106.0, 10.001, 106.0) in 110.0..112.0)
}
