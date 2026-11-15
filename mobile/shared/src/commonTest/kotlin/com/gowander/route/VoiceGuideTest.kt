package com.gowander.route

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceGuideTest {
    private val steps = listOf(
        NavStep("↰", "Rẽ trái vào Phan Bội Châu", 0.0, 0.0),
        NavStep("⇧", "Lên Buýt 18 tại Bến Thành", 0.0, 0.0, transit = true),
        NavStep("⇩", "Xuống tại Chợ Lớn", 0.0, 0.0, transit = true),
    )
    private fun at(step: Int, toNext: Double, off: Double = 0.0) = NavProgress(step, toNext, 1000.0, off)

    @Test
    fun turnIsAnnouncedAt200AndAt30MetresOnceEach() {
        val v = VoiceCues()
        assertNull(v.next(at(0, 350.0), steps, false, false, "Chợ"))
        assertEquals("Đi thêm 190 mét, rẽ trái vào Phan Bội Châu", v.next(at(0, 190.0), steps, false, false, "Chợ")?.text)
        assertNull(v.next(at(0, 150.0), steps, false, false, "Chợ")) // not again
        assertEquals("Rẽ trái vào Phan Bội Châu", v.next(at(0, 25.0), steps, false, false, "Chợ")?.text)
        assertNull(v.next(at(0, 10.0), steps, false, false, "Chợ"))
    }

    @Test
    fun alightingIsAnAlertAndBoardingIsSilent() {
        val v = VoiceCues()
        assertNull(v.next(at(1, 100.0), steps, false, false, "Chợ"))
        val cue = v.next(at(2, 380.0), steps, false, false, "Chợ")!!
        assertTrue(cue.alert)
        assertEquals("Sắp tới trạm, xuống tại Chợ Lớn, chuẩn bị xuống", cue.text)
    }

    @Test
    fun offRouteIsSaidAgainOnlyAfterComingBack() {
        val v = VoiceCues()
        assertEquals("Bạn đã đi lệch tuyến", v.next(at(0, 500.0, 80.0), steps, false, true, "Chợ")?.text)
        assertNull(v.next(at(0, 500.0, 90.0), steps, false, true, "Chợ"))
        assertNull(v.next(at(0, 500.0), steps, false, false, "Chợ"))
        assertEquals("Bạn đã đi lệch tuyến", v.next(at(0, 500.0, 80.0), steps, false, true, "Chợ")?.text)
    }

    @Test
    fun arrivalOnceAndModes() {
        val v = VoiceCues()
        val arrive = v.next(at(0, 5.0), steps, true, false, "Chợ Bến Thành")!!
        assertEquals("Bạn đã tới Chợ Bến Thành", arrive.text)
        assertNull(v.next(at(0, 5.0), steps, true, false, "Chợ Bến Thành"))
        assertTrue(audible(arrive, VoiceMode.AlertsOnly))
        assertFalse(audible(Cue("Rẽ trái", alert = false), VoiceMode.AlertsOnly))
        assertFalse(audible(arrive, VoiceMode.Off))
        assertEquals(VoiceMode.AlertsOnly, VoiceMode.On.next())
        assertEquals(VoiceMode.On, VoiceMode.Off.next())
        assertEquals("1,2 ki lô mét", spokenDistance(1234.0))
    }
}
