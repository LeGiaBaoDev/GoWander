package com.gowander.mock

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanMathTest {
    private val walk = Move("Đi bộ", MoveKind.Walk, 10)

    @Test
    fun timesAddStayAndTravel() {
        val items = listOf(PlanItem("A", "", 30), PlanItem("B", "", 45, walk), PlanItem("C", "", 20, walk))
        assertEquals(listOf("08:00", "08:40", "09:35"), planTimes("08:00", items))
        assertEquals("09:55", planEnd("08:00", items))
        assertEquals("08:00", planEnd("08:00", emptyList()))
    }

    @Test
    fun legsAreMeasuredAgainOnlyWhenTheStopBeforeChanged() {
        val a = PlanItem("A", "", 30, null, 10.0, 106.0)
        val b = PlanItem("B", "", 30, Move("Đi bộ 5 phút", MoveKind.Walk, 5, 300, a.spot()), 10.01, 106.0)
        val c = PlanItem("C", "", 30, null, 10.02, 106.0)
        val start = PlanItem("Nhà", "", 0) // no position: the leg to A cannot be measured
        assertEquals(listOf(3), staleLegs(listOf(start, a, b, c)))
        assertEquals(listOf(1, 2), staleLegs(listOf(a, c, b))) // reordered: both legs are stale now
        assertEquals(300, walkTotalM(listOf(start, a, b, c)))
    }

    @Test
    fun aRouteBecomesOneLineOfThePlan() {
        fun leg(mode: String, transit: Boolean, line: String? = null) = com.gowander.api.Leg(
            mode, transit, com.gowander.api.RoutePlace("x", 0.0, 0.0), com.gowander.api.RoutePlace("y", 0.0, 0.0), "", "", 0, 0.0, false,
            line = line?.let { com.gowander.api.Line("id", it) }, polyline = "",
        )
        fun route(sec: Int, walk: Double, vararg legs: com.gowander.api.Leg) = com.gowander.api.Route("", "", sec, walk, 0, legs.toList())
        assertEquals(Move("Đi bộ 7 phút", MoveKind.Walk, 7, 480, "k", listOf("")), moveOf(route(361, 480.4, leg("WALK", false)), "k"))
        assertEquals(Move("Buýt 18 · 12 phút", MoveKind.Bus, 12, 200, "k", listOf("", "")), moveOf(route(720, 200.0, leg("WALK", false), leg("BUS", true, "18")), "k"))
        assertEquals("Metro · 9 phút", moveOf(route(540, 100.0, leg("SUBWAY", true)), "k").label)
    }

    @Test
    fun followingAPlanSkipsStopsWithoutAPosition() {
        val items = listOf(PlanItem("Nhà", "", 0), PlanItem("A", "", 30, null, 10.0, 106.0), PlanItem("B", "", 30, null, 10.01, 106.0))
        val plan = Plan("p", "P", "", "08:00", "", items, PlanStatus.Upcoming)
        assertEquals(1, plan.nextStop())
        assertEquals(2, plan.copy(next = 2).nextStop())
        assertEquals(null, plan.copy(next = 3).nextStop())
    }

    @Test
    fun straightLengthComparesTwoOrders() {
        val a = PlanItem("A", "", 30, null, 10.00, 106.0)
        val b = PlanItem("B", "", 30, null, 10.01, 106.0)
        val c = PlanItem("C", "", 30, null, 10.02, 106.0)
        val back = straightTotalM(listOf(a, c, b)) // A -> C -> B goes back
        val straight = straightTotalM(listOf(a, b, c))
        kotlin.test.assertTrue(straight in 2200.0..2250.0 && back > straight, "$straight $back")
        assertEquals(straight, straightTotalM(listOf(PlanItem("Nhà", "", 0), a, b, c))) // stops without a position are skipped
    }

    @Test
    fun legsAreMeasuredAgainWhenTheDayOrTheWayOfTravellingChanges() {
        val a = PlanItem("A", "", 30, null, 10.0, 106.0)
        val plan = Plan("p", "P", "", "08:00", "", emptyList(), PlanStatus.Draft, day = "2026-11-21", travel = "Bus")
        val b = PlanItem("B", "", 30, Move("Buýt · 9 phút", MoveKind.Bus, 9, 100, legFrom(a, plan.legContext())), 10.01, 106.0)
        assertEquals(emptyList(), staleLegs(listOf(a, b), plan.legContext()))
        assertEquals(listOf(1), staleLegs(listOf(a, b), plan.copy(travel = "Walk").legContext()))
        assertEquals(listOf(1), staleLegs(listOf(a, b), plan.copy(day = "2026-11-22").legContext()))
    }

    @Test
    fun departureAndShiftedTimes() {
        val items = listOf(PlanItem("A", "", 30), PlanItem("B", "", 45, walk), PlanItem("C", "", 20, walk))
        assertEquals("08:30", departTime("08:00", items, 1)) // arrive A 08:00, stay 30
        assertEquals("09:25", departTime("08:00", items, 2)) // arrive B 08:40, stay 45
        assertEquals("08:15", shiftClock("08:00", 15))
        assertEquals("00:00", shiftClock("00:10", -15))
        assertEquals("23:45", shiftClock("23:50", 30))
    }

    @Test
    fun badStartFallsBackToEight() = assertEquals(listOf("08:00"), planTimes("??", listOf(PlanItem("A", "", 0))))

    @Test
    fun reorderGoesToTheNearestStopEachTime() {
        val start = PlanItem("start", "", 0, null, 10.0, 106.0)
        val far = PlanItem("far", "", 0, null, 10.0, 106.03)
        val near = PlanItem("near", "", 0, null, 10.0, 106.01)
        val mid = PlanItem("mid", "", 0, null, 10.0, 106.02)
        val home = PlanItem("no place", "", 0)
        assertEquals(listOf("start", "near", "mid", "far", "no place"), reorderNearest(listOf(start, far, home, near, mid)).map { it.title })
    }

    @Test
    fun startWithoutPlaceKeepsFirstThenOrdersTheRest() {
        val home = PlanItem("home", "", 0)
        val a = PlanItem("a", "", 0, null, 10.0, 106.0)
        val c = PlanItem("c", "", 0, null, 10.0, 106.02)
        val b = PlanItem("b", "", 0, null, 10.0, 106.01)
        assertEquals(listOf("home", "a", "b", "c"), reorderNearest(listOf(home, a, c, b)).map { it.title })
    }
}
