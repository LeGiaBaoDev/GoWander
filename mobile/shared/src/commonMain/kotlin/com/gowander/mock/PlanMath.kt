package com.gowander.mock

import kotlin.math.PI
import kotlin.math.cos

private fun minutesOf(hhmm: String): Int {
    val h = hhmm.substringBefore(':').toIntOrNull() ?: 8
    val m = hhmm.substringAfter(':', "").toIntOrNull() ?: 0
    return h * 60 + m
}

private fun clockOf(min: Int) = "${(min / 60 % 24).toString().padStart(2, '0')}:${(min % 60).toString().padStart(2, '0')}"

/** "HH:MM" of each stop: the start time, then previous stay + travel to this stop. Bad input starts at 08:00. */
fun planTimes(start: String, items: List<PlanItem>): List<String> {
    var t = minutesOf(start)
    return items.mapIndexed { i, it ->
        if (i > 0) t += items[i - 1].stayMin + (it.move?.minutes ?: 0)
        clockOf(t)
    }
}

/** When the plan ends: the last stop's time plus its stay. */
fun planEnd(start: String, items: List<PlanItem>): String =
    if (items.isEmpty()) start else clockOf(minutesOf(planTimes(start, items).last()) + items.last().stayMin)

/**
 * "Sắp xếp lại cho đỡ đi vòng": keep the first stop, then always go to the nearest remaining stop (greedy).
 * Stops without coordinates keep their order at the end.
 */
fun reorderNearest(items: List<PlanItem>): List<PlanItem> {
    if (items.size < 3) return items
    val left = items.drop(1).filter { it.lat != null && it.lon != null }.toMutableList()
    val noPlace = items.drop(1).filter { it.lat == null || it.lon == null }
    val out = mutableListOf(items.first())
    var here: PlanItem? = items.first().takeIf { it.lat != null && it.lon != null }
    while (left.isNotEmpty()) {
        val from = here
        val next = if (from == null) left.first() else left.minBy { dist2(from, it) }
        out += next
        left.remove(next)
        here = next
    }
    return out + noPlace
}

// Squared equirectangular distance: enough to compare stops a few km apart.
private fun dist2(a: PlanItem, b: PlanItem): Double {
    val dLat = a.lat!! - b.lat!!
    val dLon = (a.lon!! - b.lon!!) * cos(a.lat * PI / 180)
    return dLat * dLat + dLon * dLon
}
