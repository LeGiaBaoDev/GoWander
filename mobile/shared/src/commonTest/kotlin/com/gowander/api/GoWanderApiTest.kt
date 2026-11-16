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
import io.ktor.http.HttpMethod
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
    fun imageBytesOnlyFetchesHttpsFromTheMapillaryCdn() = runTest {
        val client = api(HttpStatusCode.OK, "jpeg-bytes")
        for (bad in listOf("http://scontent.fbcdn.net/a.jpg", "https://evil.test/a.jpg", "https://fbcdn.net.evil.test/a.jpg", "not a url")) {
            assertFailsWith<ApiError.Unexpected>(bad) { client.imageBytes(bad) }
        }
        assertEquals("jpeg-bytes", client.imageBytes("https://scontent-sin6-1.xx.fbcdn.net/m1/a.jpg").decodeToString())
    }

    @Test
    fun ownPhotosLoadFromThisApiOnly() = runTest {
        val client = api { req -> respond("jpeg:" + req.url.toString(), HttpStatusCode.OK) } as KtorGoWanderApi
        assertEquals("http://api.test/media/img/ab-480.jpg", client.photoUrl("/media/img/ab-480.jpg"))
        assertEquals("http://api.test/media/img/ab-480.jpg", client.photoUrl("http://api.test/media/img/ab-480.jpg"))
        val r2 = "https://pub-808b6bd1a8f74ab5b380ecebe7156d61.r2.dev/img/ab-480.jpg"
        assertEquals(r2, client.photoUrl(r2))
        assertNull(client.photoUrl("http://pub-808b6bd1a8f74ab5b380ecebe7156d61.r2.dev/img/ab-480.jpg"), "R2 over plain http")
        assertNull(client.photoUrl("https://pub-other.r2.dev/img/ab-480.jpg"), "another bucket")
        for (bad in listOf("/media/../.env", "//evil.test/media/a.jpg", "http://evil.test/media/a.jpg", "https://api.test/media/a.jpg", "http://api.test/admin/", "/admin/x")) {
            assertNull(client.photoUrl(bad), bad)
        }
        assertEquals("jpeg:http://api.test/media/img/ab-1600.jpg", client.imageBytes("/media/img/ab-1600.jpg").decodeToString())
        assertFailsWith<ApiError.Unexpected> { client.imageBytes("http://evil.test/media/a.jpg") }
    }

    @Test
    fun feedbackIsSentAsMultipart() = runTest {
        val id = api { req ->
            val body = (req.body as io.ktor.http.content.OutgoingContent).let { it.contentType.toString() }
            assertEquals("/api/v1/feedbacks", req.url.encodedPath)
            assertEquals(HttpMethod.Post, req.method)
            assertTrue(body.startsWith("multipart/form-data"), body)
            respondJson(HttpStatusCode.Created, """{"id":"fb-9"}""")
        }.submitFeedback(com.gowander.feedback.FeedbackDraft(message = "Giờ mở cửa chưa đúng", context = "Địa điểm · Chợ Bến Thành", image = byteArrayOf(1, 2)))
        assertEquals("fb-9", id)
    }

    @Test
    fun popularParsesRanksFromTheServer() = runTest {
        // Shape of GET /api/v1/places/popular (backend discover.PopularPlace).
        val body = """{"days":7,"places":[{"id":"p1","name":"Chợ Bến Thành","slug":"cho","lat":10.77,"lon":106.69,
            "category":{"slug":"market","name":"Chợ"},"source":{"kind":"OSM"},"rank":1,"thumbUrl":"https://cdn.example.vn/media/img/a-480.jpg"}]}"""
        val p = api(HttpStatusCode.OK, body).popular()
        assertEquals("/api/v1/places/popular", requests.single().url.encodedPath)
        assertEquals(1, p.single().rank)
        assertEquals("Chợ", p.single().category?.name)
    }

    @Test
    fun guideParsesTheServerDishesAndStories() = runTest {
        // Shape of GET /api/v1/guide/hcm (backend discover.Guide); unknown fields are ignored.
        val body = """{"dishes":[{"id":"hcm-food-pho","name":"Phở","summary":"S","description":"D","shops":[{"name":"Quán A","address":"1 Lê Lợi"}],"sources":"Nguồn A","extra":1}],
            "stories":[{"id":"hcm-story-x","title":"T","summary":"S","paragraphs":["Một.","Hai."],"tryThis":"Thử","sources":"Nguồn B"}]}"""
        val g = api(HttpStatusCode.OK, body).guide()
        assertEquals("/api/v1/guide/hcm", requests.single().url.encodedPath)
        assertEquals("1 Lê Lợi", g.dishes.single().shops.single().address)
        assertEquals("Nguồn A", g.dishes.single().sources)
        assertEquals(listOf("Một.", "Hai."), g.stories.single().paragraphs)
    }

    @Test
    fun recordOpenPostsNothingButTheRequest() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.recordOpen()
        val req = requests.single()
        assertEquals("/api/v1/usage/open", req.url.encodedPath)
        assertEquals("POST", req.method.value)
        assertEquals("", req.url.encodedQuery)
        assertEquals(0L, req.body.contentLength ?: 0L)
    }

    @Test
    fun planRouteSendsCarMode() = runTest {
        api(HttpStatusCode.OK, Fixtures.ROUTE_OK).planRoute(PlanRequest(origin, destination, modes = listOf("CAR")))
        val sent = Json.parseToJsonElement((requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
        assertEquals("[\"CAR\"]", sent["modes"].toString())
    }

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
        assertTrue("modes" !in sent) // null = server default (walk + public transport)

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
    fun geocodeSendsQueryAndBiasAndParsesResults() = runTest {
        val body = """{"results":[{"name":"770 Tỉnh lộ 15","detail":"Xã Tân Thạnh Đông, Huyện Củ Chi","lat":11.0,"lon":106.5,"ref":"way/2"}],
            "source":{"provider":"Photon","attribution":"© OpenStreetMap contributors","license":"ODbL-1.0"}}"""
        val res = api(HttpStatusCode.OK, body).geocode("770 tinh lo 15", near = LatLon(10.77, 106.7))
        val url = requests.last().url
        assertEquals("/api/v1/geocode", url.encodedPath)
        assertEquals("770 tinh lo 15", url.parameters["q"])
        assertEquals("10.77", url.parameters["lat"])
        assertEquals("770 Tỉnh lộ 15", res.single().name)
        assertEquals(false, res.single().approximate)

        val rev = api(HttpStatusCode.OK, """{"results":[{"name":"Lê Lợi","detail":"Phường Bến Thành","lat":10.7726,"lon":106.6996}],"source":{}}""")
            .reverseGeocode(LatLon(10.7725, 106.6990))
        assertEquals("/api/v1/geocode/reverse", requests.last().url.encodedPath)
        assertEquals("106.699", requests.last().url.parameters["lon"])
        assertEquals("Lê Lợi", rev.single().name)
        val down = assertFailsWith<ApiError.Server> {
            api(HttpStatusCode.ServiceUnavailable, """{"error":{"code":"GEOCODER_UNAVAILABLE","message":"Tìm địa chỉ tạm thời không hoạt động.","requestId":"x"}}""").geocode("abc", null)
        }
        assertEquals("GEOCODER_UNAVAILABLE", down.code)
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

        api(HttpStatusCode.OK, Fixtures.PLACES_NEARBY).placesInBounds(10.76, 106.69, 10.78, 106.71, setOf("food", "cafe"), limit = 60)
        assertEquals("/api/v1/places/map", requests.last().url.encodedPath)
        assertEquals(listOf("10.76", "106.69", "10.78", "106.71", "food,cafe"), listOf("south", "west", "north", "east", "categories").map { requests.last().url.parameters[it] })
        api(HttpStatusCode.OK, Fixtures.PLACES_NEARBY).placesInBounds(10.76, 106.69, 10.78, 106.71, null, limit = 60)
        assertNull(requests.last().url.parameters["categories"])

        val clusters = api(HttpStatusCode.OK, """{"clusters":[{"lat":10.77,"lon":106.7,"count":12}]}""").placeClusters(10.7, 106.6, 10.86, 106.8, setOf("cafe"))
        assertEquals("/api/v1/places/map/clusters", requests.last().url.encodedPath)
        assertEquals("cafe", requests.last().url.parameters["categories"])
        assertEquals(listOf(PlaceCluster(10.77, 106.7, 12)), clusters)

        val detail = api(HttpStatusCode.OK, Fixtures.PLACE_DETAIL).place("01a1d0db-2bcf-7fb7-b314-f42c1e9c7156")
        assertEquals("Bảo tàng Chiến dịch Hồ Chí Minh", detail.name)
        assertEquals("ODbL-1.0", detail.source.license)

        val missing = assertFailsWith<ApiError.Server> { api(HttpStatusCode.NotFound, Fixtures.PLACE_NOT_FOUND).place("x") }
        assertEquals("PLACE_NOT_FOUND", missing.code)
    }
}
