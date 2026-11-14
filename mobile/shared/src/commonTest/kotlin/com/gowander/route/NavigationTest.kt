package com.gowander.route

import com.gowander.api.Leg
import com.gowander.api.Line
import com.gowander.api.Route
import com.gowander.api.RoutePlace
import com.gowander.api.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationTest {
    @Test
    fun stepTextReadsLikeSpokenDirections() {
        assertEquals("Rẽ trái vào Quang Trung", stepText("LEFT", "Quang Trung").second)
        assertEquals("Rẽ phải vào đường không tên", stepText("RIGHT", "road").second)
        assertEquals("Bắt đầu đi trên Phan Bội Châu", stepText("DEPART", "Phan Bội Châu").second)
        assertEquals("Đi thẳng vào Nguyễn Kiệm", stepText("CONTINUE", "Nguyễn Kiệm").second)
        assertEquals("Chếch phải vào Lê Lai", stepText("SLIGHTLY_RIGHT", "Lê Lai").second)
        assertEquals("Quay đầu trên Lê Lợi", stepText("UTURN_LEFT", "Lê Lợi").second)
        assertEquals("Tiếp tục trên Lê Lợi", stepText("SOMETHING_NEW", "Lê Lợi").second)
        assertEquals("↰", stepText("LEFT", "x").first)
        assertEquals("↱", stepText("RIGHT", "x").first)
    }

    private fun place(name: String, lat: Double, lon: Double) = RoutePlace(name, lat, lon)
    private fun leg(mode: String, transit: Boolean, from: RoutePlace, to: RoutePlace, steps: List<Step> = emptyList(), line: Line? = null, headsign: String? = null) =
        Leg(mode, transit, from, to, "2026-11-16T08:00:00+07:00", "2026-11-16T08:10:00+07:00", 600, 500.0, false, headsign, line, "", steps)

    @Test
    fun navStepsFlattenStreetStepsAndTransitBoardings() {
        val a = place("Origin", 10.0, 106.0)
        val stop1 = place("Trạm A", 10.0, 106.001)
        val stop2 = place("Trạm B", 10.0, 106.01)
        val dest = place("Destination", 10.0, 106.011)
        val route = Route(
            "2026-11-16T08:00:00+07:00", "2026-11-16T08:30:00+07:00", 1800, 200.0, 0,
            listOf(
                leg("WALK", false, a, stop1, listOf(Step("DEPART", "Lê Lợi", 100.0, 10.0, 106.0), Step("LEFT", "path", 10.0, 10.0, 106.0009))),
                leg("BUS", true, stop1, stop2, line = Line("bus:x", "18"), headsign = "Bến xe Miền Tây"),
                leg("WALK", false, stop2, dest),
            ),
        )
        val steps = navSteps(route, "Trương Đăng Quế")
        assertEquals(
            listOf(
                "Bắt đầu đi trên Lê Lợi", "Rẽ trái vào đường không tên", "Lên Buýt 18 tại Trạm A, hướng Bến xe Miền Tây",
                "Xuống tại Trạm B", "Đi bộ tới Trương Đăng Quế", "Đến Trương Đăng Quế",
            ),
            steps.map { it.text },
        )
        assertEquals(10.0 to 106.01, steps[3].lat to steps[3].lon) // alight point
        assertEquals(dest.lat to dest.lon, steps.last().lat to steps.last().lon)
    }

    @Test
    fun progressFindsNextStepAndRemainingDistance() {
        // Straight line east along the equator: 0.001° lon ≈ 111.3 m. Steps at 0, 300 m, 668 m (end).
        val line = (0..6).map { 0.0 to it * 0.001 }
        val track = NavTrack(line, listOf(0.0 to 0.0, 0.0 to 0.0027, 0.0 to 0.006))
        assertEquals(668.0, track.totalM, 2.0)

        val start = track.progress(0.0, 0.0)
        assertEquals(1, start.nextStep)
        assertEquals(300.0, start.toNextM, 3.0)

        val mid = track.progress(0.00005, 0.0015) // ~5 m off the line, 167 m along
        assertEquals(1, mid.nextStep)
        assertEquals(134.0, mid.toNextM, 3.0)
        assertEquals(501.0, mid.remainingM, 3.0)
        assertTrue(mid.offRouteM in 4.0..7.0)

        val past = track.progress(0.0, 0.004)
        assertEquals(2, past.nextStep)
        assertEquals(223.0, past.toNextM, 3.0)

        // Windowed search: a point near the start is matched onward from the last position, never back to the start.
        val onward = track.progress(0.0, 0.0001, fromAlongM = 400.0)
        assertTrue(onward.alongM >= 400.0)
        assertEquals(400.0, track.progress(0.0, 0.0036, fromAlongM = 400.0).alongM, 1.0)

        val end = track.progress(0.0, 0.006)
        assertEquals(2, end.nextStep)
        assertTrue(end.remainingM < 1.0)
    }
}
