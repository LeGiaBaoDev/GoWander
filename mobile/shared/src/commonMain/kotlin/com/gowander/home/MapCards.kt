package com.gowander.home

import com.gowander.api.PlaceSummary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** Map filter chips of screen 1 and the category slugs each one shows (null: everything). */
val MAP_FILTERS = listOf("Tất cả" to null, "Ăn uống" to setOf("food"), "Cà phê" to setOf("cafe"), "Di tích" to setOf("attraction", "museum", "historic"), "Mua sắm" to setOf("market"))

/**
 * Places that get a floating card: nearest first, each far enough from the cards already chosen that they do not cover
 * each other (cards are wider than tall: [minEastM] across, [minNorthM] up and down); at most [max]. The rest are dots.
 */
fun spreadOut(places: List<PlaceSummary>, minNorthM: Double, max: Int, minEastM: Double = minNorthM): List<PlaceSummary> {
    val chosen = mutableListOf<PlaceSummary>()
    for (p in places.sortedBy { it.distanceM ?: Double.MAX_VALUE }) {
        if (chosen.size == max) break
        val clear = chosen.all {
            abs(it.lat - p.lat) * M_PER_DEG >= minNorthM || abs(it.lon - p.lon) * M_PER_DEG * cos(p.lat * PI / 180) >= minEastM
        }
        if (clear) chosen += p
    }
    return chosen
}

private const val M_PER_DEG = 111_320.0

fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val k = 111_320.0
    return hypot((lat2 - lat1) * k, (lon2 - lon1) * k * cos(lat1 * PI / 180))
}
