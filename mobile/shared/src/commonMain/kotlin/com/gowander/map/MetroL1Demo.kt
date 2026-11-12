package com.gowander.map

import kotlinx.serialization.json.JsonPrimitive

data class Station(val id: String, val name: String, val lat: Double, val lon: Double)

/**
 * DEMO data for the M03 map PoC. Not routing output and not official data.
 *
 * shortcut: copied by hand from data/assumed/hcmc-metro-l1-gtfs/stops.txt (ADR-020 assumed feed);
 * replace with the Go API response in M06.
 */
object MetroL1Demo {
    const val LABEL = "DEMO - dữ liệu giả định, không phải kết quả tìm tuyến"

    val stations = listOf(
        Station("L1-01", "Bến Thành", 10.770775, 106.697569),
        Station("L1-02", "Nhà Hát Thành Phố", 10.775511, 106.702262),
        Station("L1-03", "Ba Son", 10.781987, 106.708464),
        Station("L1-04", "Công viên Văn Thánh", 10.796045, 106.715521),
        Station("L1-05", "Tân Cảng", 10.798581, 106.723324),
        Station("L1-06", "Thảo Điền", 10.800471, 106.733654),
        Station("L1-07", "An Phú", 10.802084, 106.742253),
        Station("L1-08", "Rạch Chiếc", 10.808538, 106.755280),
        Station("L1-09", "Phước Long", 10.821417, 106.758197),
        Station("L1-10", "Bình Thái", 10.832660, 106.763920),
        Station("L1-11", "Thủ Đức", 10.846403, 106.771666),
        Station("L1-12", "Khu Công Nghệ Cao", 10.858974, 106.788839),
        Station("L1-13", "Đại học Quốc gia", 10.866286, 106.801209),
        Station("L1-14", "Bến xe Suối Tiên", 10.879569, 106.814170),
    )

    fun stationsGeoJson(list: List<Station> = stations): String =
        list.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") {
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]},""" +
                """"properties":{"id":${str(it.id)},"name":${str(it.name)}}}"""
        }

    // RC-01 (Bến Thành → Suối Tiên): the feed has no shapes.txt, so OTP's legGeometry is the 14 stations in order.
    fun demoRouteGeoJson(): String =
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[""" +
            stations.joinToString(",") { "[${it.lon},${it.lat}]" } +
            """]},"properties":{"case":"RC-01","label":${str(LABEL)}}}"""

    private fun str(s: String) = JsonPrimitive(s).toString()
}
