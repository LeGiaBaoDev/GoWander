package com.gowander.api

/** In-memory GoWanderApi for ViewModel tests; each call is recorded and answered by a replaceable lambda. */
class FakeApi : GoWanderApi {
    val planCalls = mutableListOf<PlanRequest>()
    val placeCalls = mutableListOf<Triple<String?, String?, String?>>() // category, query, cursor
    var plan: suspend (PlanRequest) -> PlanResponse = { apiJson.decodeFromString(Fixtures.ROUTE_OK) }
    var categoriesResult: suspend () -> List<Category> = { apiJson.decodeFromString<CategoriesResponse>(Fixtures.CATEGORIES).categories }
    var placesResult: suspend (String?, String?, String?) -> PlacePage = { _, _, _ -> apiJson.decodeFromString(Fixtures.PLACES_PAGE) }
    var detailResult: suspend (String) -> PlaceDetail = { apiJson.decodeFromString(Fixtures.PLACE_DETAIL) }

    override suspend fun planRoute(request: PlanRequest): PlanResponse { planCalls += request; return plan(request) }
    override suspend fun categories() = categoriesResult()
    override suspend fun places(category: String?, query: String?, cursor: String?, limit: Int): PlacePage {
        placeCalls += Triple(category, query, cursor)
        return placesResult(category, query, cursor)
    }
    val nearbyCalls = mutableListOf<LatLon>()
    override suspend fun nearby(at: LatLon, radiusM: Int, limit: Int): List<PlaceSummary> {
        nearbyCalls += at
        return apiJson.decodeFromString<PlacePage>(Fixtures.PLACES_NEARBY).places
    }
    override suspend fun placesInBounds(south: Double, west: Double, north: Double, east: Double, categories: Set<String>?, limit: Int): List<PlaceSummary> = emptyList()
    override suspend fun placeClusters(south: Double, west: Double, north: Double, east: Double, categories: Set<String>?): List<PlaceCluster> = emptyList()
    override suspend fun place(id: String) = detailResult(id)
    override suspend fun geocode(query: String, near: LatLon?): List<GeoResult> = emptyList()
    override suspend fun reverseGeocode(at: LatLon): List<GeoResult> = emptyList()
    var weatherResult: suspend (Double, Double, String?) -> WeatherReading = { _, _, at ->
        WeatherReading(at?.substringBefore('+') ?: "2026-11-17T15:00", 32.0, 37.0, 1.2, 80, 61, 12.0, true, "moderate")
    }
    override suspend fun weather(lat: Double, lon: Double, at: String?) = weatherResult(lat, lon, at)
    override suspend fun streetImage(at: LatLon): StreetImageResponse? = null
    override suspend fun imageBytes(url: String): ByteArray = ByteArray(0)
    override suspend fun guide(): com.gowander.content.GuideContent = com.gowander.content.GuideContent()
    override suspend fun recordOpen() {}
    var popularResult: suspend () -> List<PopularPlace> = { emptyList() }
    override suspend fun popular(): List<PopularPlace> = popularResult()
    val feedbacks = mutableListOf<com.gowander.feedback.FeedbackDraft>()
    var feedbackResult: suspend (com.gowander.feedback.FeedbackDraft) -> String = { "fb-1" }
    override suspend fun submitFeedback(draft: com.gowander.feedback.FeedbackDraft): String { feedbacks += draft; return feedbackResult(draft) }

    // ---- App accounts (ADR-040) ----
    var token: String? = null
    val tokenChanges = mutableListOf<String?>()
    override fun useToken(token: String?) { this.token = token; tokenChanges += token }

    var meResult: suspend () -> AppUser = { AppUser("u1", "Lan Anh", "lananh", "lananh@example.com", emailVerified = true) }
    var loginResult: suspend (String, String) -> AuthResponse = { _, _ -> AuthResponse(AppSession("tok", "2027-01-15T00:00:00Z"), meResult()) }
    var registerResult: suspend () -> AuthResponse = { AuthResponse(AppSession("tok", "2027-01-15T00:00:00Z"), meResult(), codeSent = true) }
    var error: Exception? = null

    private fun fail(): Nothing? { error?.let { throw it }; return null }

    override suspend fun register(name: String, username: String, email: String, phone: String, password: String, device: String) = registerResult()
    override suspend fun login(identifier: String, password: String, device: String): AuthResponse { fail(); return loginResult(identifier, password) }
    override suspend fun loginWithGoogle(idToken: String, device: String): AuthResponse = registerResult()
    override suspend fun verifyEmail(code: String): AppUser = meResult()
    override suspend fun resendCode(purpose: String) { fail() }
    override suspend fun forgotPassword(email: String) { fail() }
    override suspend fun resetPassword(email: String, code: String, newPassword: String) { fail() }
    override suspend fun usernameAvailable(username: String): Boolean = true
    override suspend fun me(): AppUser { fail(); return meResult() }
    override suspend fun updateMe(name: String?, username: String?, phone: String?): AppUser = meResult()
    override suspend fun changePassword(current: String, new: String) { fail() }
    override suspend fun linkGoogle(idToken: String): AppUser = meResult()
    override suspend fun unlinkGoogle(): AppUser = meResult()
    override suspend fun deleteMe(password: String?, idToken: String?) { fail() }
    override suspend fun logout() {}
    override suspend fun logoutEverywhere() {}
}
