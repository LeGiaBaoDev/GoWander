package com.gowander.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetroL1DemoTest {
    @Test
    fun stationsMatchTheAssumedFeedInOrder() {
        assertEquals(14, MetroL1Demo.stations.size)
        assertEquals((1..14).map { "L1-" + it.toString().padStart(2, '0') }, MetroL1Demo.stations.map { it.id })
        assertEquals("Bến Thành", MetroL1Demo.stations.first().name)
        assertEquals("Bến xe Suối Tiên", MetroL1Demo.stations.last().name)
    }

    @Test
    fun stationsGeoJsonIsAFeatureCollectionOfPointsInLonLatOrder() {
        val root = Json.parseToJsonElement(MetroL1Demo.stationsGeoJson()).jsonObject
        assertEquals("FeatureCollection", root["type"]!!.jsonPrimitive.content)
        val features = root["features"]!!.jsonArray
        assertEquals(14, features.size)
        val first = features.first().jsonObject
        assertEquals("Point", first["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val coords = first["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(106.697569, coords[0].jsonPrimitive.content.toDouble())
        assertEquals(10.770775, coords[1].jsonPrimitive.content.toDouble())
        assertEquals("L1-01", first["properties"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Bến Thành", first["properties"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun demoRouteIsRc01LineThroughAllStationsAndMarkedDemo() {
        val feature = Json.parseToJsonElement(MetroL1Demo.demoRouteGeoJson()).jsonObject
        assertEquals("LineString", feature["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val coords = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(14, coords.size)
        assertEquals(106.814170, (coords.last() as JsonArray)[0].jsonPrimitive.content.toDouble())
        val props = feature["properties"]!!.jsonObject
        assertEquals("RC-01", props["case"]!!.jsonPrimitive.content)
        assertTrue(props["label"]!!.jsonPrimitive.content.contains("DEMO"))
    }

    @Test
    fun namesWithQuotesStayValidJson() {
        val json = MetroL1Demo.stationsGeoJson(listOf(Station("X", "Ga \"A\" \\ B", 1.0, 2.0)))
        val name = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray[0]
            .jsonObject["properties"]!!.jsonObject["name"]!!.jsonPrimitive.content
        assertEquals("Ga \"A\" \\ B", name)
    }
}
