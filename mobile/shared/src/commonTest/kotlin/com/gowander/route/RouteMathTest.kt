package com.gowander.route

import com.gowander.api.Fixtures
import com.gowander.api.PlanResponse
import com.gowander.api.apiJson
import com.gowander.ui.Lang
import com.gowander.ui.Language
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteMathTest {
    @BeforeTest
    fun vietnamese() = Language.set(Lang.VI)

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

class DistanceLabelTest {
    @BeforeTest
    fun vietnamese() = Language.set(Lang.VI)

    @kotlin.test.Test
    fun kilometresUseTheVietnameseDecimalComma() {
        kotlin.test.assertEquals("1,2 km", distanceLabel(1_234.0))
        kotlin.test.assertEquals("120 m", distanceLabel(118.0))
        kotlin.test.assertEquals("1,5 km", com.gowander.ui.Strings.meters(1_560.0))
    }

    @Test
    fun pilotDayAndWeekday() {
        val sat = 1_763_683_200_000L // 2025-11-21T00:00Z = 07:00 in TP.HCM, a Friday
        kotlin.test.assertEquals("2025-11-21" to 5, pilotDay(sat, 0))
        kotlin.test.assertEquals("2025-11-23" to 0, pilotDay(sat, 2))
        kotlin.test.assertEquals("2025-12-01" to 1, pilotDay(sat, 10))
    }

    @Test
    fun calendarMath() {
        kotlin.test.assertEquals(0L, daysFromCivil(1970, 1, 1))
        kotlin.test.assertEquals(6, weekdayOf(2026, 11, 21)) // a Saturday
        kotlin.test.assertEquals(29, daysInMonth(2028, 2))
        kotlin.test.assertEquals(28, daysInMonth(2026, 2))
        kotlin.test.assertEquals(31, daysInMonth(2026, 12))
    }
}
