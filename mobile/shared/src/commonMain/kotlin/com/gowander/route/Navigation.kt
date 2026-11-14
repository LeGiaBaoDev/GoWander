package com.gowander.route

import com.gowander.api.Route
import com.gowander.ui.Strings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/** One instruction of the trip (NAV-1): a street manoeuvre, boarding/leaving a vehicle, or arrival. */
data class NavStep(val arrow: String, val text: String, val lat: Double, val lon: Double, val transit: Boolean = false)

// OTP's name for unnamed ways ("road", "path", "service road", "sidewalk", ...): read as "đường không tên".
private val GENERIC_STREETS = setOf("road", "path", "service road", "sidewalk", "footbridge", "track", "steps", "bike path", "parking aisle", "default level")

/** (arrow, Vietnamese instruction) for an OTP relativeDirection + street name. */
fun stepText(direction: String, street: String): Pair<String, String> {
    val name = street.takeUnless { it.isBlank() || it.lowercase() in GENERIC_STREETS } ?: Strings.UNNAMED_STREET
    return when (direction) {
        "DEPART" -> "↑" to "Bắt đầu đi trên $name"
        "LEFT" -> "↰" to "Rẽ trái vào $name"
        "RIGHT" -> "↱" to "Rẽ phải vào $name"
        "SLIGHTLY_LEFT" -> "↖" to "Chếch trái vào $name"
        "SLIGHTLY_RIGHT" -> "↗" to "Chếch phải vào $name"
        "HARD_LEFT" -> "↰" to "Rẽ gắt trái vào $name"
        "HARD_RIGHT" -> "↱" to "Rẽ gắt phải vào $name"
        "CONTINUE" -> "↑" to "Đi thẳng vào $name"
        "UTURN_LEFT", "UTURN_RIGHT" -> "↶" to "Quay đầu trên $name"
        "CIRCLE_CLOCKWISE", "CIRCLE_COUNTERCLOCKWISE" -> "⟳" to "Vào vòng xuyến, ra ở $name"
        "ELEVATOR" -> "⇅" to "Đi thang máy tới $name"
        else -> "↑" to "Tiếp tục trên $name"
    }
}

/**
 * Every instruction of [route] in order. Street legs use OTP's steps (or one "go to" line when it gave none);
 * transit legs become "board at … towards …" and "get off at …"; the trip ends with "arrive at [destination]".
 */
fun navSteps(route: Route, destination: String): List<NavStep> = buildList {
    route.legs.forEachIndexed { i, leg ->
        val last = i == route.legs.lastIndex
        if (leg.transit) {
            val vehicle = when (leg.mode) { "BUS" -> Strings.BUS; "SUBWAY", "RAIL", "TRAM" -> "Metro"; else -> leg.mode }
            val name = leg.line?.shortName?.let { "$vehicle $it" } ?: vehicle
            add(NavStep("⇧", "Lên $name tại ${leg.from.name}" + (leg.headsign?.let { ", hướng $it" } ?: ""), leg.from.lat, leg.from.lon, transit = true))
            add(NavStep("⇩", "Xuống tại ${leg.to.name}", leg.to.lat, leg.to.lon, transit = true))
        } else if (leg.steps.isNotEmpty()) {
            leg.steps.forEach { s -> stepText(s.direction, s.street).let { (arrow, text) -> add(NavStep(arrow, text, s.lat, s.lon)) } }
        } else {
            val target = if (last) destination else leg.to.name
            val verb = when (leg.mode) { "CAR" -> Strings.DRIVE; "BICYCLE" -> Strings.CYCLE; else -> Strings.WALK }
            add(NavStep("↑", "$verb tới $target", leg.from.lat, leg.from.lon))
        }
        if (last) add(NavStep("⚑", "Đến $destination", leg.to.lat, leg.to.lon))
    }
}

data class NavProgress(val nextStep: Int, val toNextM: Double, val remainingM: Double, val offRouteM: Double, val alongM: Double = 0.0)

/**
 * The route as one line with each instruction's position along it, so a GPS fix gives the next instruction
 * and the distance left. Distances use a local flat projection (exact enough within a city).
 */
class NavTrack(private val line: List<Pair<Double, Double>>, stepPoints: List<Pair<Double, Double>>) {
    private val cum = DoubleArray(line.size).also { c -> for (i in 1 until line.size) c[i] = c[i - 1] + meters(line[i - 1], line[i]) }
    val totalM: Double = cum.lastOrNull() ?: 0.0
    private val stepAt: List<Double> = run {
        var from = 0.0
        stepPoints.map { p -> project(p.first, p.second, minAlong = from).first.also { from = it } }
    }

    /**
     * [fromAlongM]: search the line only from here on (last position minus a margin), so a loop or a parallel street
     * cannot snap the user back to an earlier part of the route, and long routes are not scanned from the start.
     */
    fun progress(lat: Double, lon: Double, fromAlongM: Double = 0.0): NavProgress {
        val (along, off) = project(lat, lon, minAlong = fromAlongM.coerceIn(0.0, totalM))
        val next = stepAt.indexOfFirst { it > along + ARRIVAL_SLACK_M }.takeIf { it >= 0 } ?: stepAt.lastIndex.coerceAtLeast(0)
        val toNext = ((stepAt.getOrNull(next) ?: totalM) - along).coerceAtLeast(0.0)
        return NavProgress(next, toNext, (totalM - along).coerceAtLeast(0.0), off, along)
    }

    /** (distance along the line, distance from it) of the closest point on the line at or after [minAlong]. */
    private fun project(lat: Double, lon: Double, minAlong: Double): Pair<Double, Double> {
        if (line.size < 2) return 0.0 to (line.firstOrNull()?.let { meters(it, lat to lon) } ?: 0.0)
        var best = minAlong to Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            if (cum[i + 1] < minAlong) continue
            val (ax, ay) = local(line[i], lat)
            val (bx, by) = local(line[i + 1], lat)
            val (px, py) = local(lat to lon, lat)
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(px - (ax + t * dx), py - (ay + t * dy))
            val along = maxOf(cum[i] + t * (cum[i + 1] - cum[i]), minAlong)
            if (d < best.second) best = along to d
        }
        return best
    }

    private companion object {
        const val ARRIVAL_SLACK_M = 8.0 // a step this close behind counts as done (GPS noise)
        const val M_PER_DEG = 111_320.0
        fun local(p: Pair<Double, Double>, refLat: Double) = p.second * M_PER_DEG * cos(refLat * PI / 180) to p.first * M_PER_DEG
        fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val (ax, ay) = local(a, a.first)
            val (bx, by) = local(b, a.first)
            return hypot(bx - ax, by - ay)
        }
    }
}

fun navTrack(route: Route, steps: List<NavStep>) = NavTrack(route.legs.flatMap { decodePolyline(it.polyline) }, steps.map { it.lat to it.lon })
