package com.gowander.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Base URL of the Go API for this platform during development (ADR-029 §3). */
expect val apiBaseUrl: String

/** Tolerant to fields the server adds later; sends defaults (timeType) but not nulls (dateTime = now). */
val apiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Every failure of GoWanderApi is one of these; the UI never sees raw Ktor exceptions. */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The API answered with its error envelope; [userMessage] is Vietnamese and safe to show. */
    class Server(val code: String, val userMessage: String, val status: Int) : ApiError("$status $code")

    /** The API could not be reached (offline, refused, timed out). */
    class Network(cause: Throwable) : ApiError("network: ${cause.message}", cause)

    /** The API answered something outside the contract. */
    class Unexpected(detail: String, cause: Throwable? = null) : ApiError(detail, cause)
}

interface GoWanderApi {
    suspend fun planRoute(request: PlanRequest): PlanResponse
    suspend fun categories(): List<Category>
    suspend fun places(category: String?, query: String?, cursor: String?, limit: Int): PlacePage
    suspend fun nearby(at: LatLon, radiusM: Int, limit: Int): List<PlaceSummary>
    /** Places inside a map viewport (UX-2 P3); a dense viewport returns an even, stable sample. No distanceM. */
    suspend fun placesInBounds(south: Double, west: Double, north: Double, east: Double, categories: Set<String>?, limit: Int): List<PlaceSummary>
    /** How many places each cell of a grid over the viewport holds: for a map zoomed too far out to show single places. */
    suspend fun placeClusters(south: Double, west: Double, north: Double, east: Double, categories: Set<String>?): List<PlaceCluster>
    suspend fun place(id: String): PlaceDetail
    suspend fun geocode(query: String, near: LatLon?): List<GeoResult>
    suspend fun reverseGeocode(at: LatLon): List<GeoResult>
    /** Nearest street photo, or null when there is none near [at] (STREET_IMAGE_NOT_FOUND). */
    suspend fun streetImage(at: LatLon): StreetImageResponse?
    /** Raw bytes of an image URL returned by the API (street photo). */
    suspend fun imageBytes(url: String): ByteArray
}

fun HttpClientConfig<*>.configureGoWander() {
    expectSuccess = false
    followRedirects = false // neither the API nor the photo CDN should redirect; never follow one to another host
    install(ContentNegotiation) { json(apiJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000 // above the API's own OTP timeout (8 s), so the API reports ROUTING_TIMEOUT first
    }
}

fun createGoWanderApi(baseUrl: String = apiBaseUrl): GoWanderApi =
    KtorGoWanderApi(HttpClient { configureGoWander() }, baseUrl)

class KtorGoWanderApi(private val client: HttpClient, baseUrl: String) : GoWanderApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun planRoute(request: PlanRequest): PlanResponse = call("/api/v1/routes/plan") {
        method = HttpMethod.Post
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    override suspend fun categories(): List<Category> =
        call<CategoriesResponse>("/api/v1/place-categories") {}.categories

    override suspend fun places(category: String?, query: String?, cursor: String?, limit: Int): PlacePage =
        call("/api/v1/places") {
            parameter("category", category)
            parameter("q", query)
            parameter("cursor", cursor)
            parameter("limit", limit)
        }

    override suspend fun placesInBounds(south: Double, west: Double, north: Double, east: Double, categories: Set<String>?, limit: Int): List<PlaceSummary> =
        call<PlacePage>("/api/v1/places/map") {
            parameter("south", south)
            parameter("west", west)
            parameter("north", north)
            parameter("east", east)
            parameter("categories", categories?.joinToString(","))
            parameter("limit", limit)
        }.places

    override suspend fun placeClusters(south: Double, west: Double, north: Double, east: Double, categories: Set<String>?): List<PlaceCluster> =
        call<ClustersResponse>("/api/v1/places/map/clusters") {
            parameter("south", south)
            parameter("west", west)
            parameter("north", north)
            parameter("east", east)
            parameter("categories", categories?.joinToString(","))
        }.clusters

    override suspend fun nearby(at: LatLon, radiusM: Int, limit: Int): List<PlaceSummary> =
        call<PlacePage>("/api/v1/places/nearby") {
            parameter("lat", at.lat)
            parameter("lon", at.lon)
            parameter("radiusM", radiusM)
            parameter("limit", limit)
        }.places

    override suspend fun place(id: String): PlaceDetail = call("/api/v1/places/${id.encodeURLPathPart()}") {}

    override suspend fun geocode(query: String, near: LatLon?): List<GeoResult> =
        call<GeocodeResponse>("/api/v1/geocode") {
            parameter("q", query)
            parameter("lat", near?.lat)
            parameter("lon", near?.lon)
            parameter("limit", 5)
        }.results

    override suspend fun reverseGeocode(at: LatLon): List<GeoResult> =
        call<GeocodeResponse>("/api/v1/geocode/reverse") {
            parameter("lat", at.lat)
            parameter("lon", at.lon)
        }.results

    override suspend fun streetImage(at: LatLon): StreetImageResponse? = try {
        call<StreetImageResponse>("/api/v1/street-images/nearest") {
            parameter("lat", at.lat)
            parameter("lon", at.lon)
        }
    } catch (e: ApiError.Server) {
        if (e.code == "STREET_IMAGE_NOT_FOUND") null else throw e
    }

    override suspend fun imageBytes(url: String): ByteArray {
        // Only fetch photos from Mapillary's CDN over HTTPS, whatever the API returns (security review 14-11-2026).
        val parsed = try { Url(url) } catch (e: Exception) { throw ApiError.Unexpected("bad image url") }
        if (parsed.protocol != URLProtocol.HTTPS || IMAGE_HOSTS.none { parsed.host == it || parsed.host.endsWith(".$it") }) {
            throw ApiError.Unexpected("image host not allowed")
        }
        val response = try {
            client.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Network(e)
        }
        if (!response.status.isSuccess()) throw ApiError.Unexpected("image HTTP ${response.status.value}")
        val declared = response.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > MAX_IMAGE_BYTES) throw ApiError.Unexpected("image too large")
        return response.bodyAsBytes().also { if (it.size > MAX_IMAGE_BYTES) throw ApiError.Unexpected("image too large") }
    }

    private companion object {
        val IMAGE_HOSTS = listOf("fbcdn.net", "mapillary.com")
        const val MAX_IMAGE_BYTES = 2_000_000L
    }

    // Not named `build`: inside the request lambda that would call HttpRequestBuilder.build() instead.
    private suspend inline fun <reified T> call(path: String, crossinline configure: HttpRequestBuilder.() -> Unit): T {
        val (status, text) = try {
            val response = client.request(base + path) { configure() }
            response.status to response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) { // refused, unreachable, reset, connect/socket timeouts
            throw ApiError.Network(e)
        } catch (e: HttpRequestTimeoutException) {
            throw ApiError.Network(e)
        } catch (e: Exception) { // a bug, not the user's network: do not tell them to check their connection
            throw ApiError.Unexpected("request failed: ${e::class.simpleName}", e)
        }
        if (!status.isSuccess()) {
            val envelope = try { apiJson.decodeFromString<ErrorEnvelope>(text) } catch (e: SerializationException) { null }
            throw envelope?.let { ApiError.Server(it.error.code, it.error.message, status.value) }
                ?: ApiError.Unexpected("HTTP ${status.value} without error envelope")
        }
        return try {
            apiJson.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw ApiError.Unexpected("response does not match the API contract", e)
        } catch (e: IllegalArgumentException) {
            throw ApiError.Unexpected("response does not match the API contract", e)
        }
    }
}
