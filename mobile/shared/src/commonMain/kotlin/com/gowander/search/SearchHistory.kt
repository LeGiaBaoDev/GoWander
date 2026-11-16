package com.gowander.search

import com.gowander.Prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One thing the person chose in a search field (A11/A12, ADR-040 §7): a place, an address or a typed query. It stays
 * on the device only: nothing here is sent to the server in this round.
 */
@Serializable
data class HistoryEntry(
    val kind: Kind,
    val label: String,
    val placeId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val at: Long, // milliseconds; only used to group and to keep the newest 30
) {
    enum class Kind { QUERY, PLACE, ADDRESS }
}

@Serializable
private data class Stored(val entries: List<HistoryEntry> = emptyList(), val enabled: Boolean = true)

private val historyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * The search history of this device (A11, A12): at most [limit] entries, newest first, duplicates moved up, switched
 * off means nothing is kept. A file makes it testable without preferences.
 */
class SearchHistory(
    stored: String? = null,
    private val persist: (String) -> Unit = {},
    private val limit: Int = 30,
) {
    private val loaded = stored?.let {
        try { historyJson.decodeFromString<Stored>(it) } catch (e: Exception) { null } // unreadable: start empty
    }
    private val entries = (loaded?.entries ?: emptyList()).toMutableList()
    var enabled: Boolean = loaded?.enabled ?: true
        private set

    /** Newest first. */
    fun all(): List<HistoryEntry> = entries.toList()

    /** The five under the empty search field (A12). */
    fun recent(): List<HistoryEntry> = entries.take(5)

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Records a choice. The same place (or the same label for a query) moves to the top instead of appearing twice;
     * with history switched off nothing is written.
     */
    fun add(entry: HistoryEntry) {
        if (!enabled) return
        entries.removeAll { same(it, entry) }
        entries.add(0, entry)
        while (entries.size > limit) entries.removeAt(entries.lastIndex)
        save()
    }

    fun remove(entry: HistoryEntry) {
        entries.removeAll { same(it, entry) }
        save()
    }

    fun clear() {
        entries.clear()
        save()
    }

    /** Switching off also deletes what is already there, as the screen promises. */
    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) entries.clear()
        save()
    }

    private fun same(a: HistoryEntry, b: HistoryEntry): Boolean = when {
        a.placeId != null || b.placeId != null -> a.placeId == b.placeId && a.kind == b.kind
        else -> a.kind == b.kind && a.label.equals(b.label, ignoreCase = true)
    }

    private fun save() = persist(historyJson.encodeToString(Stored(entries.toList(), enabled)))

    companion object {
        const val KEY = "search.history.v1"

        /** The device's history, kept as one JSON string in Prefs (same storage the plans use). */
        fun fromDevice(): SearchHistory = SearchHistory(Prefs.getString(KEY), persist = { Prefs.setString(KEY, it) })
    }
}

// Vietnam time, like the rest of the app (the History page says so); no date library in the shared module.
private const val DAY_MS = 24 * 3600_000L
private const val OFFSET_MS = 7 * 3600_000L

/** Start of the day that contains [millis], in Vietnam time. */
fun dayStart(millis: Long): Long = (millis + OFFSET_MS).floorDiv(DAY_MS) * DAY_MS - OFFSET_MS

/** Today / yesterday / earlier, newest first. */
fun groupByDay(entries: List<HistoryEntry>, now: Long): List<Pair<String, List<HistoryEntry>>> {
    val today = dayStart(now)
    val yesterday = today - DAY_MS
    val groups = linkedMapOf(
        "Hôm nay" to mutableListOf<HistoryEntry>(),
        "Hôm qua" to mutableListOf(),
        "Trước đó" to mutableListOf(),
    )
    entries.forEach { e ->
        groups.getValue(
            when {
                e.at >= today -> "Hôm nay"
                e.at >= yesterday -> "Hôm qua"
                else -> "Trước đó"
            },
        ) += e
    }
    return groups.filterValues { it.isNotEmpty() }.map { it.key to it.value.toList() }
}

/** "18:12" today, "Hôm qua" yesterday, "16/11" before that: short, local, nothing to translate. */
fun historyWhen(entry: HistoryEntry, now: Long): String {
    val today = dayStart(now)
    return when {
        entry.at >= today -> two(localHour(entry.at)) + ":" + two(localMinute(entry.at))
        entry.at >= today - DAY_MS -> "Hôm qua"
        else -> {
            val (day, month) = localDayMonth(entry.at)
            two(day) + "/" + two(month)
        }
    }
}

private fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

private fun localHour(millis: Long): Int = ((millis + OFFSET_MS).mod(DAY_MS) / 3600_000L).toInt()
private fun localMinute(millis: Long): Int = ((millis + OFFSET_MS).mod(3600_000L) / 60_000L).toInt()

/** (day, month) of a civil date, from days since 1970-01-01 (H. Hinnant's inverse algorithm). */
private fun localDayMonth(millis: Long): Pair<Int, Int> {
    val z = (millis + OFFSET_MS).floorDiv(DAY_MS) + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return d to m
}
