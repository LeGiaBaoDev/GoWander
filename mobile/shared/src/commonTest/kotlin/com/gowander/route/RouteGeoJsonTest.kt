package com.gowander.route

import com.gowander.api.Fixtures
import com.gowander.api.PlanResponse
import com.gowander.api.apiJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteGeoJsonTest {
    private val route = apiJson.decodeFromString<PlanResponse>(Fixtures.ROUTE_OK).routes.first()

    @Test
    fun splitsTransitAndWalkingLegsAsLonLatLines() {
        val transit = Json.parseToJsonElement(legsGeoJson(route, transit = true)).jsonObject["features"]!!.jsonArray
        val walk = Json.parseToJsonElement(legsGeoJson(route, transit = false)).jsonObject["features"]!!.jsonArray
        assertEquals(1, transit.size)
        assertEquals(2, walk.size)
        val first = transit[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray[0].jsonArray
        assertEquals(106.69757, first[0].jsonPrimitive.content.toDouble(), 0.001) // longitude first
        assertEquals(10.77078, first[1].jsonPrimitive.content.toDouble(), 0.001)
    }

    @Test
    fun countsLegsThatCannotBeDrawn() {
        assertEquals(0, undrawableLegs(route))
        val broken = route.copy(legs = route.legs.mapIndexed { i, l -> if (i == 1) l.copy(polyline = "_p~iF") else l })
        assertEquals(1, undrawableLegs(broken))
    }

    @Test
    fun emptyWhenNothingToDraw() {
        assertEquals(EMPTY_FEATURES, legsGeoJson(null, transit = true))
        assertEquals(EMPTY_FEATURES, pointGeoJson(null))
        assertEquals(emptyList(), framePoints(null, null, null))
        val a = Endpoint(10.0, 106.0, "a")
        assertEquals(listOf(10.0 to 106.0), framePoints(null, a, null))
    }
}
