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
