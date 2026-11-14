package com.gowander.route

import com.gowander.api.Leg
import com.gowander.api.Route
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class RouteSort { Fastest, LeastWalking }

/** M5 sort chips: fastest = earliest arrival, then shortest; least walking = shortest walk, then earliest arrival. */
fun sortRoutes(routes: List<Route>, sort: RouteSort): List<Route> = when (sort) {
    RouteSort.Fastest -> routes.sortedWith(compareBy<Route>({ epochSeconds(it.endTime) ?: Long.MAX_VALUE }, { it.durationSec }))
    RouteSort.LeastWalking -> routes.sortedWith(compareBy<Route>({ it.walkDistanceM }, { epochSeconds(it.endTime) ?: Long.MAX_VALUE }))
}

/** Whole minutes of waiting before each leg (M6 "Chờ tàu khoảng X phút"); null for the first leg or gaps under 1 minute. */
fun waitsBeforeLegs(legs: List<Leg>): List<Int?> = legs.mapIndexed { i, leg ->
    if (i == 0) null else secondsBetween(legs[i - 1].endTime, leg.startTime)?.takeIf { it >= 60 }?.let { (it + 59) / 60 }
}

/** Seconds from [from] to [to] (RFC 3339 with offset); null if either is malformed. */
fun secondsBetween(from: String, to: String): Int? {
    val a = epochSeconds(from) ?: return null
    val b = epochSeconds(to) ?: return null
    return (b - a).toInt()
}

@OptIn(ExperimentalTime::class)
private fun epochSeconds(iso: String): Long? = try {
    Instant.parse(iso).epochSeconds
} catch (e: IllegalArgumentException) {
    null
}

// shortcut: the pilot area is one time zone without DST (Asia/Ho_Chi_Minh, UTC+7); use a time-zone library for multi-region.
private const val PILOT_OFFSET_SECONDS = 7 * 3600L

/** RFC 3339 time in the pilot time zone, as the API expects for "arrive by" (M3). */
fun pilotIso(epochMillis: Long): String {
    val (date, h, m, s) = pilotParts(epochMillis)
    return "${date}T${pad(h)}:${pad(m)}:${pad(s)}+07:00"
}

/** "HH:MM" in the pilot time zone. */
fun pilotClock(epochMillis: Long): String = pilotParts(epochMillis).let { "${pad(it.h)}:${pad(it.m)}" }

private data class Parts(val date: String, val h: Int, val m: Int, val s: Int)

private fun pilotParts(epochMillis: Long): Parts {
    val local = epochMillis.floorDiv(1000L) + PILOT_OFFSET_SECONDS
    val days = local.floorDiv(86_400L)
    val sec = local.mod(86_400L).toInt()
    // Civil date from days since 1970-01-01 (H. Hinnant's algorithm).
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val mo = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (mo <= 2) 1 else 0
    return Parts("$y-${pad(mo.toInt())}-${pad(d.toInt())}", sec / 3600, sec % 3600 / 60, sec % 60)
}

private fun pad(n: Int) = n.toString().padStart(2, '0')
