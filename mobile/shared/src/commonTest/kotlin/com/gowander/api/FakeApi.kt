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
    override suspend fun place(id: String) = detailResult(id)
    override suspend fun geocode(query: String, near: LatLon?): List<GeoResult> = emptyList()
    override suspend fun reverseGeocode(at: LatLon): List<GeoResult> = emptyList()
}
