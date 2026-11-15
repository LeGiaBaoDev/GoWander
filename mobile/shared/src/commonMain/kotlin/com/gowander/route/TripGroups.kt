package com.gowander.route

import com.gowander.api.Route

/** Card [first] in the results, plus the same trip leaving later ([later], indices into the same list). */
data class TripGroup(val first: Int, val later: List<Int>)

// Walking that differs by more than this is a different choice for the user, not the same trip at another time.
private const val SAME_WALK_SEC = 120

/**
 * UX-2 plan §7.3: group the same public-transport trip at different times into one card ("Chuyến sau: 17:22 · 17:34").
 * Same = same chain of legs (mode, line, boarding and alighting stop) and walking within 2 minutes. Car, bicycle and
 * walking-only routes are never grouped: they have no timetable, two of them are two different roads.
 */
fun groupTrips(routes: List<Route>): List<TripGroup> {
    val groups = mutableListOf<Pair<TripGroup, Route>>()
    routes.forEachIndexed { i, r ->
        val key = tripKey(r)
        val k = if (key == null) -1 else groups.indexOfFirst { (_, head) -> tripKey(head) == key && kotlin.math.abs(walkSec(head) - walkSec(r)) <= SAME_WALK_SEC }
        if (k < 0) groups += TripGroup(i, emptyList()) to r
        else groups[k] = groups[k].let { (g, head) -> g.copy(later = g.later + i) to head }
    }
    return groups.map { it.first }
}

private fun tripKey(r: Route): List<String>? =
    if (r.legs.none { it.transit }) null
    else r.legs.map { if (it.transit) "${it.mode}|${it.line?.id}|${it.from.stopId ?: it.from.name}|${it.to.stopId ?: it.to.name}" else it.mode }

private fun walkSec(r: Route) = r.legs.filter { it.mode == "WALK" }.sumOf { it.durationSec }
