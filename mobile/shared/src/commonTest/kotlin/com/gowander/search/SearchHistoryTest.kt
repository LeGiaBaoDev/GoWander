package com.gowander.search

import com.gowander.route.nowMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchHistoryTest {
    // Relative to the real clock: the retention rule compares against now, so a made-up constant drifts.
    private val now = nowMillis()
    private val day = 24L * 3600_000L
    private fun place(name: String, id: String, at: Long) = HistoryEntry(HistoryEntry.Kind.PLACE, name, id, 10.7, 106.7, at)
    private fun address(name: String, at: Long) = HistoryEntry(HistoryEntry.Kind.ADDRESS, name, null, 10.7, 106.7, at)

    @Test
    fun theNewestChoiceComesFirstAndDuplicatesMoveUp() {
        var diskText: String? = null
        val h = SearchHistory(read = { diskText }, write = { diskText = it })
        h.add(place("Chợ Bến Thành", "p1", now - 3000))
        h.add(place("Dinh Độc Lập", "p2", now - 2000))
        h.add(place("Chợ Bến Thành", "p1", now - 1000)) // chosen again: moved to the top, not duplicated

        assertEquals(listOf("p1", "p2"), h.all().map { it.placeId })
        assertEquals(1, h.all().count { it.placeId == "p1" })
        assertTrue(diskText!!.contains("Chợ Bến Thành")) // it is written for the next start

        val again = SearchHistory(read = { diskText }, write = { diskText = it }) // the app starts again
        assertEquals(listOf("p1", "p2"), again.all().map { it.placeId })
    }

    @Test
    fun theQuickListShowsTenAtMost() {
        val h = SearchHistory()
        repeat(14) { i -> h.add(place("Nơi $i", "p$i", now - i * 1000)) }
        assertEquals(14, h.all().size) // one month of choices is not thrown away
        assertEquals(SearchHistory.RECENT_SHOWN, h.recent().size)
        assertEquals(10, h.recent().size)
        assertEquals("p0", h.recent().first().placeId)
    }

    @Test
    fun anythingOlderThanThirtyDaysIsForgotten() {
        val h = SearchHistory()
        h.add(place("Hôm nay", "new", now))
        h.add(place("31 ngày trước", "old", now - 31 * day))
        h.add(place("29 ngày trước", "kept", now - 29 * day))

        assertEquals(listOf("new", "kept"), h.all().map { it.placeId })
    }

    @Test
    fun oldEntriesAreAlsoDroppedWhenTheAppStarts() {
        val stored = """{"entries":[
            {"kind":"PLACE","label":"Cũ","placeId":"old","lat":10.7,"lon":106.7,"at":${now - 40 * day}},
            {"kind":"PLACE","label":"Mới","placeId":"new","lat":10.7,"lon":106.7,"at":$now}
        ],"enabled":true}"""
        val h = SearchHistory(read = { stored }, write = {})
        assertEquals(listOf("new"), h.all().map { it.placeId })
    }

    @Test
    fun aGuestKeepsNothingBeyondTheSession() {
        val guest = SearchHistory.inMemory()
        guest.add(place("Chợ Bến Thành", "p1", now))
        assertEquals(1, guest.all().size) // visible while the app is open
        assertTrue(SearchHistory.inMemory().isEmpty) // the next launch starts empty: nothing was stored
    }

    @Test
    fun switchingOffDeletesAndStopsWriting() {
        val h = SearchHistory()
        h.add(place("Chợ Bến Thành", "p1", now))
        h.setEnabled(false)
        assertTrue(h.isEmpty)
        h.add(place("Dinh Độc Lập", "p2", now))
        assertTrue(h.isEmpty) // nothing is kept while it is off
        h.setEnabled(true)
        h.add(address("26 Lê Thị Riêng", now))
        assertEquals(1, h.all().size)
    }

    @Test
    fun removingOneOrAll() {
        val h = SearchHistory()
        h.add(place("A", "a", now))
        h.add(place("B", "b", now))
        h.remove(place("A", "a", now))
        assertEquals(listOf("B"), h.all().map { it.label })
        h.clear()
        assertTrue(h.isEmpty)
    }

    @Test
    fun unreadableStorageDoesNotCrash() {
        val h = SearchHistory(read = { "not json" }, write = {})
        assertTrue(h.isEmpty)
        assertTrue(h.enabled)
    }

    @Test
    fun daysAreGroupedTodayYesterdayEarlier() {
        val today = dayStart(now)
        val entries = listOf(
            place("Hôm nay", "a", today + 3600_000),
            place("Hôm qua", "b", today - 3600_000),
            place("Cũ", "c", today - 50 * 3600_000),
        )
        val groups = groupByDay(entries, now)
        assertEquals(listOf("Hôm nay", "Hôm qua", "Trước đó"), groups.map { it.first })
        assertEquals(listOf("a"), groups[0].second.map { it.placeId })
        assertEquals(listOf("b"), groups[1].second.map { it.placeId })
        assertEquals(listOf("c"), groups[2].second.map { it.placeId })
    }

    @Test
    fun theTimeLabelIsShortAndLocal() {
        val today = dayStart(now)
        assertEquals(5, historyWhen(place("x", "a", today + 65 * 60_000), now).length) // "HH:MM"
        assertEquals("Hôm qua", historyWhen(place("x", "b", today - 3600_000), now))
        assertTrue(historyWhen(place("x", "c", today - 50 * 3600_000), now).contains("/"))
    }

    @Test
    fun theDayStartsAtMidnightVietnamTime() {
        val midnight = dayStart(now)
        assertEquals(midnight, dayStart(midnight + 30 * 60_000))
        assertEquals(midnight - day, dayStart(midnight - 30 * 60_000))
        assertFalse(dayStart(midnight - 1) == midnight)
    }
}
