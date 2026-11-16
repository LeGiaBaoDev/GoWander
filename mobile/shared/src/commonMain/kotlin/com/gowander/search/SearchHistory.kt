package com.gowander.search

import com.gowander.Prefs
import com.gowander.route.nowMillis
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
 * The search history of this phone (A11, A12): newest first, duplicates moved up, anything older than
 * [retentionDays] forgotten, switched off means nothing is kept.
 *
 * A guest keeps it **in RAM only** ([inMemory], empty again after the app closes); a signed-in account keeps it in
 * Prefs ([onDevice]), still on this phone and never sent to the server (ADR-040 §7).
 */
class SearchHistory(
    private val read: () -> String? = { null },
    private val write: (String) -> Unit = {},
    private val retentionDays: Int = 30,
) {
    private val loaded = read()?.let {
        try { historyJson.decodeFromString<Stored>(it) } catch (e: Exception) { null } // unreadable: start empty
    }
    private val entries = (loaded?.entries ?: emptyList()).toMutableList()
    var enabled: Boolean = loaded?.enabled ?: true
        private set

    init {
        prune()
        sort()
    }

    /** Newest first. */
    fun all(): List<HistoryEntry> = entries.toList()

    /** The ten under the empty search field (A12); "Xem thêm" opens the whole list. */
    fun recent(): List<HistoryEntry> = entries.take(RECENT_SHOWN)

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Records a choice. The same place (or the same label for a query) moves to the top instead of appearing twice;
     * with history switched off nothing is written.
     */
    fun add(entry: HistoryEntry) {
        if (!enabled) return
        entries.removeAll { same(it, entry) }
        entries.add(entry)
        prune()
        sort() // newest first by when it was chosen, whatever order the calls arrived in
        save()
    }

    private fun sort() = entries.sortByDescending { it.at }

    /** Anything older than the retention window is dropped, here and when the app starts. */
    private fun prune() {
        val oldest = nowMillis() - retentionDays * 24L * 3600_000L
        entries.removeAll { it.at < oldest }
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

    private fun save() = write(historyJson.encodeToString(Stored(entries.toList(), enabled)))

    companion object {
        const val KEY = "search.history.v1"
        const val RECENT_SHOWN = 10 // A12: ten lines, then "Xem thêm"

        /** Signed in: one JSON string in Prefs on this phone (the storage the plans use). */
        fun onDevice(): SearchHistory =
            SearchHistory(read = { Prefs.getString(KEY) }, write = { Prefs.setString(KEY, it) })

        /** Guest: memory only, so nothing survives the app closing and nothing is written to disk. */
        fun inMemory(): SearchHistory = SearchHistory()
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
