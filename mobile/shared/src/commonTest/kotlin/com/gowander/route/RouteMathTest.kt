package com.gowander.route

import com.gowander.api.Fixtures
import com.gowander.api.PlanResponse
import com.gowander.api.apiJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteMathTest {
    private val routes = apiJson.decodeFromString<PlanResponse>(Fixtures.ROUTE_OK).routes

    @Test
    fun sortsByArrivalOrByWalking() {
        val a = routes[0].copy(endTime = "2026-11-19T14:40:00+07:00", walkDistanceM = 900.0)
        val b = routes[0].copy(endTime = "2026-11-19T14:35:00+07:00", walkDistanceM = 1200.0)
        assertEquals(listOf(b, a), sortRoutes(listOf(a, b), RouteSort.Fastest))
        assertEquals(listOf(a, b), sortRoutes(listOf(b, a), RouteSort.LeastWalking))
    }

    @Test
    fun waitBeforeEachLegFromTheTimetable() {
        val legs = routes[0].legs // walk 14:02:43–14:08:25? then metro, then walk
        val waits = waitsBeforeLegs(legs)
        assertEquals(legs.size, waits.size)
        assertNull(waits[0]) // nothing before the first leg
        // Metro departs after the walk ends: the gap is a wait, rounded up to whole minutes, ignored under 1 minute.
        val gap = secondsBetween(legs[0].endTime, legs[1].startTime)!!
        assertEquals(if (gap >= 60) (gap + 59) / 60 else null, waits[1])
    }

    @Test
    fun secondsBetweenHandlesOffsetsAndBadInput() {
        assertEquals(330, secondsBetween("2026-11-19T14:02:43+07:00", "2026-11-19T14:08:13+07:00"))
        assertEquals(0, secondsBetween("2026-11-19T07:00:00Z", "2026-11-19T14:00:00+07:00"))
        assertNull(secondsBetween("garbage", "2026-11-19T14:00:00+07:00"))
    }

    @Test
    fun arrivalTimeIsSentInPilotTimeZone() {
        // 2026-11-19T07:30:00Z + 90 min = 09:00 UTC = 16:00 in Ho Chi Minh City.
        assertEquals("2026-11-19T16:00:00+07:00", pilotIso(1795073400_000L + 90 * 60_000L))
        assertEquals("16:00", pilotClock(1795073400_000L + 90 * 60_000L))
    }
}
