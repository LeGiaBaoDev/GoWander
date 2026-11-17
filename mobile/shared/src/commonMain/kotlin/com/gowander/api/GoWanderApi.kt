package com.gowander.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import com.gowander.content.GuideContent
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
    /** Most searched-for places of the last 7 days; empty until there is enough real use. */
    suspend fun popular(): List<PopularPlace>
    /** Sends a feedback (guest allowed); returns its id. The image, if any, is a JPEG or PNG under 5 MB. */
    suspend fun submitFeedback(draft: com.gowander.feedback.FeedbackDraft): String
    suspend fun geocode(query: String, near: LatLon?): List<GeoResult>
    suspend fun reverseGeocode(at: LatLon): List<GeoResult>
    /** Hourly forecast at [at] (RFC 3339 with offset), or now when null; Open-Meteo through the API. */
    suspend fun weather(lat: Double, lon: Double, at: String? = null): WeatherReading
    /** Nearest street photo, or null when there is none near [at] (STREET_IMAGE_NOT_FOUND). */
    suspend fun streetImage(at: LatLon): StreetImageResponse?
    /** Raw bytes of an image URL returned by the API (street photo). */
    suspend fun imageBytes(url: String): ByteArray
    /** Published dishes and stories of the TP.HCM guide (M07 slice 3). */
    suspend fun guide(): GuideContent
    /** One app start, counted per day on the server (ADR-038). Sends nothing else: no id, no location. */
    suspend fun recordOpen()

    // ---- App accounts (AUTH-1/2, ADR-040): every call below sends the token set with [useToken] when there is one. ----

    /** Bearer token for the calls that need an account; null signs out of the API (the token itself lives in the Keychain). */
    fun useToken(token: String?)
    suspend fun register(name: String, username: String, email: String, phone: String, password: String, device: String): AuthResponse
    suspend fun login(identifier: String, password: String, device: String): AuthResponse
    suspend fun loginWithGoogle(idToken: String, device: String): AuthResponse
    suspend fun verifyEmail(code: String): AppUser
    suspend fun resendCode(purpose: String): Unit
    suspend fun forgotPassword(email: String): Unit
    suspend fun resetPassword(email: String, code: String, newPassword: String): Unit
    suspend fun usernameAvailable(username: String): Boolean
    suspend fun me(): AppUser
    suspend fun updateMe(name: String?, username: String?, phone: String?): AppUser
    suspend fun changePassword(current: String, new: String): Unit
    suspend fun linkGoogle(idToken: String): AppUser
    suspend fun unlinkGoogle(): AppUser
    suspend fun deleteMe(password: String?, idToken: String?): Unit
    suspend fun logout(): Unit
    suspend fun logoutEverywhere(): Unit
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

    /** Kept in memory only: the account store owns the real copy (Keychain / Keystore). */
    private var token: String? = null

    override fun useToken(token: String?) { this.token = token }

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

    override suspend fun guide(): GuideContent = call("/api/v1/guide/hcm") {}

    override suspend fun popular(): List<PopularPlace> = call<PopularPage>("/api/v1/places/popular") {}.places

    override suspend fun submitFeedback(draft: com.gowander.feedback.FeedbackDraft): String =
        call<FeedbackCreated>("/api/v1/feedbacks") {
            method = HttpMethod.Post
            setBody(MultiPartFormDataContent(formData {
                append("kind", draft.kind.api)
                append("message", draft.message.trim())
                if (draft.context.isNotBlank()) append("context", draft.context.take(200))
                if (draft.name.isNotBlank()) append("senderName", draft.name.trim())
                if (draft.email.isNotBlank()) append("senderEmail", draft.email.trim())
                draft.image?.let {
                    append("image", it, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"feedback.jpg\"")
                    })
                }
            }))
        }.id

    override suspend fun recordOpen() {
        val status = try {
            client.request("$base/api/v1/usage/open") { method = HttpMethod.Post }.status
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiError.Network(e)
        }
        if (!status.isSuccess()) throw ApiError.Unexpected("HTTP ${status.value} for an app start")
    }

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

    override suspend fun weather(lat: Double, lon: Double, at: String?): WeatherReading =
        call<WeatherResponse>("/api/v1/weather") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("at", at)
        }.weather

    override suspend fun streetImage(at: LatLon): StreetImageResponse? = try {
        call<StreetImageResponse>("/api/v1/street-images/nearest") {
            parameter("lat", at.lat)
            parameter("lon", at.lon)
        }
    } catch (e: ApiError.Server) {
        if (e.code == "STREET_IMAGE_NOT_FOUND") null else throw e
    }

    override suspend fun imageBytes(url: String): ByteArray {
        val target = photoUrl(url) ?: run {
            // Otherwise only Mapillary's CDN over HTTPS, whatever the API returns (security review 14-11-2026).
            val parsed = try { Url(url) } catch (e: Exception) { throw ApiError.Unexpected("bad image url") }
            if (parsed.protocol != URLProtocol.HTTPS || IMAGE_HOSTS.none { parsed.host == it || parsed.host.endsWith(".$it") }) {
                throw ApiError.Unexpected("image host not allowed")
            }
            url
        }
        val response = try {
            client.get(target)
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

    /**
     * GoWander's own photos (ADR-035): a path under /media/ on this API (development), or an https address on
     * [PHOTO_HOSTS] (the R2 bucket's domain, once it exists). Null for anything else.
     */
    fun photoUrl(raw: String): String? {
        if (raw.startsWith("/media/") && !raw.contains("..") && !raw.contains("//")) return base + raw
        val parsed = try { Url(raw) } catch (e: Exception) { return null }
        val api = try { Url(base) } catch (e: Exception) { return null }
        val sameApi = parsed.protocol == api.protocol && parsed.host == api.host && parsed.port == api.port
        val cdn = parsed.protocol == URLProtocol.HTTPS && PHOTO_HOSTS.any { parsed.host == it }
        return if (sameApi && parsed.encodedPath.startsWith("/media/") || cdn) raw else null
    }

    // ---------- App accounts (ADR-040) ----------

    @Serializable
    private data class RegisterBody(val name: String, val username: String, val email: String, val phone: String, val password: String, val device: String)

    @Serializable
    private data class LoginBody(val identifier: String, val password: String, val device: String)

    @Serializable
    private data class TokenBody(val idToken: String, val device: String? = null)

    @Serializable
    private data class ProfileBody(val name: String? = null, val username: String? = null, val phone: String? = null)

    @Serializable
    private data class PasswordBody(val current: String, val new: String)

    @Serializable
    private data class EmailBody(val email: String)

    @Serializable
    private data class CodeBody(val code: String)

    @Serializable
    private data class ResetBody(val email: String, val code: String, val newPassword: String)

    @Serializable
    private data class DeleteBody(val password: String? = null, val idToken: String? = null)

    @Serializable
    private data class PurposeBody(val purpose: String)

    override suspend fun register(name: String, username: String, email: String, phone: String, password: String, device: String): AuthResponse =
        call("/api/v1/auth/register") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(RegisterBody(name, username, email, phone, password, device))
        }

    override suspend fun login(identifier: String, password: String, device: String): AuthResponse =
        call("/api/v1/auth/login") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(LoginBody(identifier, password, device))
        }

    override suspend fun loginWithGoogle(idToken: String, device: String): AuthResponse =
        call("/api/v1/auth/google") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(TokenBody(idToken, device))
        }

    override suspend fun verifyEmail(code: String): AppUser =
        call<MeResponse>("/api/v1/auth/verify-email") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(CodeBody(code))
        }.user

    override suspend fun resendCode(purpose: String) {
        callNoContent("/api/v1/auth/resend-code") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(PurposeBody(purpose))
        }
    }

    override suspend fun forgotPassword(email: String) {
        callNoContent("/api/v1/auth/forgot") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(EmailBody(email))
        }
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        callNoContent("/api/v1/auth/reset") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(ResetBody(email, code, newPassword))
        }
    }

    override suspend fun usernameAvailable(username: String): Boolean =
        call<AvailableResponse>("/api/v1/auth/username-available") { parameter("u", username) }.available

    override suspend fun me(): AppUser = call<MeResponse>("/api/v1/me") {}.user

    override suspend fun updateMe(name: String?, username: String?, phone: String?): AppUser =
        call<MeResponse>("/api/v1/me") {
            method = HttpMethod.Patch
            contentType(ContentType.Application.Json)
            setBody(ProfileBody(name, username, phone))
        }.user

    override suspend fun changePassword(current: String, new: String) {
        callNoContent("/api/v1/me/password") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(PasswordBody(current, new))
        }
    }

    override suspend fun linkGoogle(idToken: String): AppUser =
        call<MeResponse>("/api/v1/me/google") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(TokenBody(idToken))
        }.user

    override suspend fun unlinkGoogle(): AppUser = call<MeResponse>("/api/v1/me/google") { method = HttpMethod.Delete }.user

    override suspend fun deleteMe(password: String?, idToken: String?) {
        callNoContent("/api/v1/me") {
            method = HttpMethod.Delete
            contentType(ContentType.Application.Json)
            setBody(DeleteBody(password?.takeIf { it.isNotBlank() }, idToken?.takeIf { it.isNotBlank() }))
        }
    }

    override suspend fun logout() { callNoContent("/api/v1/auth/logout") { method = HttpMethod.Post } }

    override suspend fun logoutEverywhere() { callNoContent("/api/v1/auth/logout-all") { method = HttpMethod.Post } }

    private companion object {
        val IMAGE_HOSTS = listOf("fbcdn.net", "mapillary.com")
        // The R2 bucket's public address (docs/runbooks/cloudflare-r2.md). shortcut: r2.dev is rate-limited by
        // Cloudflare; replace it with the bucket's custom domain before launch.
        val PHOTO_HOSTS = listOf("pub-808b6bd1a8f74ab5b380ecebe7156d61.r2.dev")
        const val MAX_IMAGE_BYTES = 2_000_000L
    }

    // Not named `build`: inside the request lambda that would call HttpRequestBuilder.build() instead.
    private suspend inline fun <reified T> call(path: String, crossinline configure: HttpRequestBuilder.() -> Unit): T {
        val (status, text) = try {
            val response = client.request(base + path) {
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                configure()
            }
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

    /** For 204 answers and other responses whose body carries nothing (the status is the whole answer). */
    private suspend fun callNoContent(path: String, configure: HttpRequestBuilder.() -> Unit) {
        val (status, text) = try {
            val response = client.request(base + path) {
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                configure()
            }
            response.status to response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Network(e)
        } catch (e: HttpRequestTimeoutException) {
            throw ApiError.Network(e)
        } catch (e: Exception) {
            throw ApiError.Unexpected("request failed: ${e::class.simpleName}", e)
        }
        if (!status.isSuccess()) {
            val envelope = try { apiJson.decodeFromString<ErrorEnvelope>(text) } catch (e: SerializationException) { null }
            throw envelope?.let { ApiError.Server(it.error.code, it.error.message, status.value) }
                ?: ApiError.Unexpected("HTTP ${status.value} without error envelope")
        }
    }
}
