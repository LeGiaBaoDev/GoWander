package com.gowander.route

/**
 * Decodes a Google encoded polyline (precision 5, as OTP and the GoWander API send it) into (lat, lon) pairs.
 * Stops at the last complete point if the input is truncated.
 */
fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lon = 0
    fun next(): Int? {
        var result = 0
        var shift = 0
        while (index < encoded.length) {
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            if (b < 0x20) return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
        return null
    }
    while (index < encoded.length) {
        val dLat = next() ?: break
        val dLon = next() ?: break
        lat += dLat
        lon += dLon
        points += lat / 1e5 to lon / 1e5
    }
    return points
}
