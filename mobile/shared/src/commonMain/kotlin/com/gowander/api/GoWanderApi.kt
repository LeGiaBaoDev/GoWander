package com.gowander.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
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
    suspend fun place(id: String): PlaceDetail
}

fun HttpClientConfig<*>.configureGoWander() {
    expectSuccess = false
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

    override suspend fun nearby(at: LatLon, radiusM: Int, limit: Int): List<PlaceSummary> =
        call<PlacePage>("/api/v1/places/nearby") {
            parameter("lat", at.lat)
            parameter("lon", at.lon)
            parameter("radiusM", radiusM)
            parameter("limit", limit)
        }.places

    override suspend fun place(id: String): PlaceDetail = call("/api/v1/places/${id.encodeURLPathPart()}") {}

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
