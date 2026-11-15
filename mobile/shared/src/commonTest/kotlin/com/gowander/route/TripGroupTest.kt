package com.gowander.route

import com.gowander.api.Leg
import com.gowander.api.Line
import com.gowander.api.Route
import com.gowander.api.RoutePlace
import kotlin.test.Test
import kotlin.test.assertEquals

/** UX-2 plan §7.3: only the same trip at another time is grouped; anything that changes the user's choice stays apart. */
class TripGroupTest {
    private val a = RoutePlace("Trạm A", 10.0, 106.0, "stop:a")
    private val b = RoutePlace("Trạm B", 10.0, 106.01, "stop:b")
    private val c = RoutePlace("Trạm C", 10.0, 106.02, "stop:c")
    private val home = RoutePlace("Origin", 10.0, 105.99)
    private val dest = RoutePlace("Destination", 10.0, 106.03)

    private fun walk(from: RoutePlace, to: RoutePlace, min: Int) = Leg("WALK", false, from, to, "", "", min * 60, 0.0, false, polyline = "")
    private fun ride(mode: String, line: String, from: RoutePlace, to: RoutePlace, start: String) =
        Leg(mode, true, from, to, start, start, 900, 0.0, false, line = Line("l:$line", line), polyline = "")
    private fun trip(start: String, vararg legs: Leg) = Route(start, start, legs.sumOf { it.durationSec }, 0.0, 0, legs.toList())

    private val bus18At = { t: String, walkEnd: Int -> trip(t, walk(home, a, 5), ride("BUS", "18", a, b, t), walk(b, dest, walkEnd)) }

    @Test
    fun sameTripAtLaterTimesIsOneCard() {
        val groups = groupTrips(listOf(bus18At("17:11", 4), bus18At("17:22", 4), bus18At("17:34", 5)))
        assertEquals(listOf(TripGroup(0, listOf(1, 2))), groups)
    }

    @Test
    fun differentStopTransferOrWalkingStaysApart() {
        val otherStop = trip("17:15", walk(home, a, 5), ride("BUS", "18", a, c, "17:15"), walk(c, dest, 4))
        val transfer = trip("17:16", walk(home, a, 5), ride("BUS", "18", a, b, "17:16"), ride("BUS", "56", b, c, "17:30"), walk(c, dest, 2))
        val moreWalking = bus18At("17:20", 12) // 7 more minutes on foot is a different choice
        val metro = trip("17:18", walk(home, a, 5), ride("SUBWAY", "1", a, b, "17:18"), walk(b, dest, 4))
        val groups = groupTrips(listOf(bus18At("17:11", 4), otherStop, transfer, moreWalking, metro))
        assertEquals(5, groups.size)
        assertEquals(groups.map { it.first }, listOf(0, 1, 2, 3, 4))
    }

    @Test
    fun carAndBicycleRoutesAreNeverGrouped() {
        val car = { t: String -> trip(t, Leg("CAR", false, home, dest, t, t, 600, 3000.0, false, polyline = "")) }
        assertEquals(2, groupTrips(listOf(car("17:00"), car("17:05"))).size)
    }
}
