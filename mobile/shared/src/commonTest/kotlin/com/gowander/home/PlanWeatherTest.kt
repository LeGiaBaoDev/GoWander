package com.gowander.home

import com.gowander.mock.Plan
import com.gowander.mock.PlanItem
import com.gowander.mock.PlanStatus
import com.gowander.route.daysFromCivil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlanWeatherTest {
    private fun plan(day: String?, start: String = "15:00") = Plan(
        "p", "Test", "21-11", start, "Bus",
        listOf(PlanItem("Bưu điện Trung tâm", "Kiến trúc", 30, null, 10.77984, 106.69995)),
        PlanStatus.Upcoming, day = day,
    )

    // 12:00 in the pilot zone (UTC+7) on the day the test was written.
    private val now = (daysFromCivil(2026, 11, 17) * 86_400 + 12 * 3_600 - 7 * 3_600) * 1_000

    @Test
    fun spotNeedsAChosenDayInsideTheForecastWindow() {
        assertNull(planWeatherSpot(plan(null), now))
        assertNull(planWeatherSpot(plan("2026-11-16"), now))
        assertNull(planWeatherSpot(plan("2026-12-03"), now)) // today + 16: beyond the free Open-Meteo window
        val spot = planWeatherSpot(plan("2026-11-21"), now)
        assertNotNull(spot)
        assertEquals("2026-11-21T15:00:00+07:00", spot.at)
        assertEquals("10.77984,106.69995,2026-11-21T15:00:00+07:00", spot.key)
    }

    @Test
    fun spotNeedsACoordinate() {
        val noSpot = plan("2026-11-21").copy(items = listOf(PlanItem("[Điểm xuất phát]", "", 0)))
        assertNull(planWeatherSpot(noSpot, now))
        assertNull(planWeatherSpot(plan("2026-11-21", start = "15:7"), now))
    }

    @Test
    fun venuesAreCountedOnlyWhenRecognised() {
        assertEquals(Venue.Indoor, venueOf(PlanItem("Bưu điện Trung tâm", "Kiến trúc", 30)))
        assertEquals(Venue.Indoor, venueOf(PlanItem("Ăn sáng: bánh mì", "[Quán mẫu]", 30)))
        assertEquals(Venue.Outdoor, venueOf(PlanItem("Công viên Tao Đàn", "Đi bộ", 30)))
        assertEquals(Venue.Outdoor, venueOf(PlanItem("Phố đi bộ Nguyễn Huệ", "", 30)))
        assertEquals(Venue.Unknown, venueOf(PlanItem("Điểm hẹn", "", 0)))
        val mixed = plan("2026-11-21").copy(
            items = listOf(
                PlanItem("[Điểm xuất phát]", "", 0),
                PlanItem("Bưu điện Trung tâm", "", 30, null, 10.779, 106.699),
                PlanItem("Công viên Tao Đàn", "", 30, null, 10.775, 106.692),
                PlanItem("Điểm hẹn", "", 30, null, 10.77, 106.69),
            ),
        )
        assertEquals(1 to 1, planVenueCounts(mixed))
    }
}
