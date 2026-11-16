package com.gowander.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchHistoryTest {
    private val now = 1_795_000_000_000L // any fixed moment; the helpers work from it
    private fun place(name: String, id: String, at: Long) = HistoryEntry(HistoryEntry.Kind.PLACE, name, id, 10.7, 106.7, at)
    private fun address(name: String, at: Long) = HistoryEntry(HistoryEntry.Kind.ADDRESS, name, null, 10.7, 106.7, at)

    @Test
    fun theNewestChoiceComesFirstAndDuplicatesMoveUp() {
        var disk: String? = null
        val h = SearchHistory(null, persist = { disk = it })
        h.add(place("Chợ Bến Thành", "p1", now - 3000))
        h.add(place("Dinh Độc Lập", "p2", now - 2000))
        h.add(place("Chợ Bến Thành", "p1", now - 1000)) // chosen again: moved to the top, not duplicated

        assertEquals(listOf("p1", "p2"), h.all().map { it.placeId })
        assertEquals(1, h.all().count { it.placeId == "p1" })
        assertTrue(disk!!.contains("Chợ Bến Thành")) // it is persisted for the next start

        val again = SearchHistory(disk, persist = { disk = it }) // the app starts again
        assertEquals(listOf("p1", "p2"), again.all().map { it.placeId })
    }

    @Test
    fun theListStopsAtTheLimit() {
        val h = SearchHistory(null, persist = {}, limit = 3)
        repeat(5) { i -> h.add(place("Nơi $i", "p$i", now - i * 1000)) }
        assertEquals(listOf("p4", "p3", "p2"), h.all().map { it.placeId })
        assertEquals(3, h.recent().size)
    }

    @Test
    fun switchingOffDeletesAndStopsWriting() {
        val h = SearchHistory(null, persist = {})
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
        val h = SearchHistory(null, persist = {})
        h.add(place("A", "a", now))
        h.add(place("B", "b", now))
        h.remove(place("A", "a", now))
        assertEquals(listOf("B"), h.all().map { it.label })
        h.clear()
        assertTrue(h.isEmpty)
    }

    @Test
    fun unreadableStorageDoesNotCrash() {
        val h = SearchHistory("not json", persist = {})
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
        // 00:30 Vietnam time is still today; 23:30 the evening before is yesterday.
        val midnight = dayStart(now)
        assertEquals(midnight, dayStart(midnight + 30 * 60_000))
        assertEquals(midnight - 24 * 3600_000, dayStart(midnight - 30 * 60_000))
        assertFalse(dayStart(midnight - 1) == midnight)
    }
}
