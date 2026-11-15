package com.gowander.mock

import com.gowander.api.Route
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

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

/** Where a stop is, as the key a measured leg remembers; null when the stop has no coordinates. */
fun PlanItem.spot(): String? = if (lat != null && lon != null) "$lat,$lon" else null

/** Stops whose travel time from the stop before must be measured (again): both have a position and the leg was not measured from that stop. */
fun staleLegs(items: List<PlanItem>): List<Int> =
    items.indices.filter { i -> i > 0 && items[i].spot() != null && items[i - 1].spot() != null && items[i].move?.from != items[i - 1].spot() }

/** One line of the plan for a route the router found from [from]: how, how long, how much walking. */
fun moveOf(route: Route, from: String): Move {
    val min = (route.durationSec + 59) / 60
    val ride = route.legs.firstOrNull { it.transit }
    val kind = when { ride == null -> MoveKind.Walk; ride.mode == "BUS" -> MoveKind.Bus; else -> MoveKind.Metro }
    val label = when (kind) {
        MoveKind.Walk -> "Đi bộ $min phút"
        MoveKind.Bus -> listOfNotNull("Buýt", ride?.line?.shortName).joinToString(" ") + " · $min phút"
        MoveKind.Metro -> "Metro · $min phút"
    }
    return Move(label, kind, min, route.walkDistanceM.roundToInt(), from)
}

/** Walking in metres over the legs the router measured. */
fun walkTotalM(items: List<PlanItem>): Int = items.sumOf { it.move?.takeIf { m -> m.from != null }?.walkM ?: 0 }

/** The stop to go to next when following the plan: the first one from [Plan.next] on that has a position; null when all are done. */
fun Plan.nextStop(): Int? = (next.coerceAtLeast(0) until items.size).firstOrNull { items[it].spot() != null }

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
