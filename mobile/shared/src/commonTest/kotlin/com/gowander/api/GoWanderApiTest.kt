package com.gowander.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoWanderApiTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(status: HttpStatusCode, body: String) = api { respondJson(status, body) }

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): GoWanderApi {
        val engine = MockEngine { req -> requests += req; handler(req) }
        return KtorGoWanderApi(HttpClient(engine) { configureGoWander() }, "http://api.test")
    }

    private fun MockRequestHandleScope.respondJson(status: HttpStatusCode, body: String) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    private val origin = LatLon(10.7725, 106.6990)
    private val destination = LatLon(10.8480, 106.7730)

    @Test
    fun planRouteSendsContractBodyAndParsesItinerary() = runTest {
        val res = api(HttpStatusCode.OK, Fixtures.ROUTE_OK)
            .planRoute(PlanRequest(origin, destination, dateTime = "2026-11-19T14:00:00+07:00", maxResults = 2))

        val req = requests.single()
        assertEquals("http://api.test/api/v1/routes/plan", req.url.toString())
        val sent = Json.parseToJsonElement((req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
        assertEquals("10.7725", sent["origin"]!!.jsonObject["lat"]!!.jsonPrimitive.content)
        assertEquals("DEPART", sent["timeType"]!!.jsonPrimitive.content)
        assertEquals("2026-11-19T14:00:00+07:00", sent["dateTime"]!!.jsonPrimitive.content)

        assertEquals("ASSUMED", res.dataStatus)
        val legs = res.routes.first().legs
        assertEquals(listOf("WALK", "SUBWAY", "WALK"), legs.map { it.mode })
        val metro = legs[1]
        assertTrue(metro.transit)
        assertEquals("Bến Thành", metro.from.name)
        assertEquals("Thủ Đức", metro.to.name)
        assertEquals(1230, metro.durationSec)
        assertEquals("L1", metro.line?.shortName)
        assertTrue(metro.polyline.isNotEmpty())
    }

    @Test
    fun errorEnvelopesBecomeServerErrorsWithTheApiMessage() = runTest {
        val cases = listOf(
            Triple(HttpStatusCode.UnprocessableEntity, Fixtures.ROUTE_OUT_OF_COVERAGE, "OUT_OF_COVERAGE"),
            Triple(HttpStatusCode.BadRequest, Fixtures.ROUTE_INVALID, "INVALID_REQUEST"),
            Triple(HttpStatusCode.ServiceUnavailable, Fixtures.ROUTING_UNAVAILABLE, "ROUTING_UNAVAILABLE"),
            Triple(HttpStatusCode.GatewayTimeout, Fixtures.ROUTING_TIMEOUT, "ROUTING_TIMEOUT"),
        )
        for ((status, body, code) in cases) {
            val e = assertFailsWith<ApiError.Server> { api(status, body).planRoute(PlanRequest(origin, destination)) }
            assertEquals(code, e.code)
            assertEquals(status.value, e.status)
            val expected = Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
            assertEquals(expected, e.userMessage) // the API's Vietnamese message is shown as-is
        }
    }

    @Test
    fun nonEnvelopeErrorsAndBrokenJsonAreUnexpected() = runTest {
        assertIs<ApiError.Unexpected>(assertFailsWith<ApiError> {
            api(HttpStatusCode.BadGateway, "<html>bad gateway</html>").planRoute(PlanRequest(origin, destination))
        })
        assertIs<ApiError.Unexpected>(assertFailsWith<ApiError> {
            api(HttpStatusCode.OK, """{"dataStatus":"ASSUMED","routes":[{"oops":1}]}""").planRoute(PlanRequest(origin, destination))
        })
    }

    @Test
    fun networkFailureIsNetworkError() = runTest {
        val e = assertFailsWith<ApiError> { api { throw IOException("connection refused") }.categories() }
        assertIs<ApiError.Network>(e)
    }

    @Test
    fun programmingErrorsAreNotReportedAsNetworkProblems() = runTest {
        val e = assertFailsWith<ApiError> { api { throw IllegalStateException("bad engine setup") }.categories() }
        assertIs<ApiError.Unexpected>(e)
    }

    @Test
    fun discoverEndpoints() = runTest {
        val cats = api(HttpStatusCode.OK, Fixtures.CATEGORIES).categories()
        assertEquals("museum", cats.first().slug)
        assertTrue(cats.first().placeCount > 0)

        val page = api(HttpStatusCode.OK, Fixtures.PLACES_PAGE).places(category = "museum", query = "bến thành", cursor = "abc", limit = 2)
        val url = requests.last().url
        assertEquals("/api/v1/places", url.encodedPath)
        assertEquals("museum", url.parameters["category"])
        assertEquals("bến thành", url.parameters["q"])
        assertEquals("abc", url.parameters["cursor"])
        assertEquals("2", url.parameters["limit"])
        assertEquals(2, page.places.size)
        assertTrue(page.nextCursor!!.isNotEmpty())
        assertEquals("© OpenStreetMap contributors", page.places.first().source.attribution)

        api(HttpStatusCode.OK, Fixtures.PLACES_PAGE).places(category = null, query = null, cursor = null, limit = 20)
        assertNull(requests.last().url.parameters["category"])
        assertNull(requests.last().url.parameters["q"])

        val near = api(HttpStatusCode.OK, Fixtures.PLACES_NEARBY).nearby(LatLon(10.770775, 106.697569), radiusM = 300, limit = 2)
        assertEquals("/api/v1/places/nearby", requests.last().url.encodedPath)
        assertTrue(near.all { (it.distanceM ?: -1.0) >= 0 })

        val detail = api(HttpStatusCode.OK, Fixtures.PLACE_DETAIL).place("01a1d0db-2bcf-7fb7-b314-f42c1e9c7156")
        assertEquals("Bảo tàng Chiến dịch Hồ Chí Minh", detail.name)
        assertEquals("ODbL-1.0", detail.source.license)

        val missing = assertFailsWith<ApiError.Server> { api(HttpStatusCode.NotFound, Fixtures.PLACE_NOT_FOUND).place("x") }
        assertEquals("PLACE_NOT_FOUND", missing.code)
    }
}
