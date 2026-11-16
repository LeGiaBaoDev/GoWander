package com.gowander.api

import kotlinx.serialization.Serializable

// DTOs of backend/api/openapi.yaml (0.2.0). Field names match the JSON exactly.

@Serializable
data class LatLon(val lat: Double, val lon: Double)

@Serializable
data class PlanRequest(
    val origin: LatLon,
    val destination: LatLon,
    val dateTime: String? = null, // RFC 3339 with offset; null = now (server time)
    val timeType: String = "DEPART",
    val maxResults: Int = 3,
    val modes: List<String>? = null, // null = server default (walk + public transport); ["CAR"] / ["BICYCLE"] alone
)

@Serializable
data class PlanResponse(val dataStatus: String, val routes: List<Route>)

@Serializable
data class Route(
    val startTime: String,
    val endTime: String,
    val durationSec: Int,
    val walkDistanceM: Double,
    val transfers: Int,
    val legs: List<Leg>,
)

@Serializable
data class Leg(
    val mode: String,
    val transit: Boolean,
    val from: RoutePlace,
    val to: RoutePlace,
    val startTime: String,
    val endTime: String,
    val durationSec: Int,
    val distanceM: Double,
    val realtime: Boolean,
    val headsign: String? = null,
    val line: Line? = null,
    val polyline: String,
    val steps: List<Step> = emptyList(), // turn-by-turn for walk/bicycle/car legs (NAV-1)
)

/** One manoeuvre; [direction] is OTP's relativeDirection (DEPART, LEFT, RIGHT, CONTINUE, ...). */
@Serializable
data class Step(val direction: String, val street: String, val distanceM: Double, val lat: Double, val lon: Double)

/** Street photo near a point (Mapillary, CC BY-SA 4.0); [attribution] must be shown with it (ADR-033). */
@Serializable
data class StreetImage(val id: String, val url: String, val capturedAt: String, val lat: Double, val lon: Double, val creator: String? = null, val pageUrl: String)

@Serializable
data class StreetImageSource(val provider: String, val license: String, val attribution: String)

@Serializable
data class StreetImageResponse(val image: StreetImage, val source: StreetImageSource)

@Serializable
data class RoutePlace(val name: String, val lat: Double, val lon: Double, val stopId: String? = null)

@Serializable
data class Line(val id: String, val shortName: String? = null, val longName: String? = null)

@Serializable
data class Category(val slug: String, val name: String, val nameEn: String? = null, val placeCount: Int)

@Serializable
data class CategoriesResponse(val categories: List<Category>)

@Serializable
data class CategoryRef(val slug: String, val name: String, val nameEn: String? = null)

/** Licence and attribution that must be shown next to the place (ADR-028). */
@Serializable
data class Source(val kind: String, val url: String? = null, val license: String, val attribution: String)

@Serializable
data class PlaceSummary(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val slug: String,
    val shortDescription: String? = null,
    val lat: Double,
    val lon: Double,
    val address: String? = null,
    val category: CategoryRef? = null,
    val distanceM: Double? = null,
    val source: Source,
)

/** Khám phá › "Đang được tìm nhiều": rank only, never a count (GET /api/v1/places/popular). */
@Serializable
data class PopularPlace(
    val id: String,
    val name: String,
    val category: CategoryRef? = null,
    val rank: Int,
    val thumbUrl: String? = null,
)

@Serializable
data class PopularPage(val days: Int = 7, val places: List<PopularPlace> = emptyList())

@Serializable
data class PlacePage(val places: List<PlaceSummary>, val nextCursor: String? = null)

@Serializable
data class Media(
    val storageKey: String,
    val mediaType: String,
    val altText: String? = null,
    val sourceCredit: String,
    val license: String,
    val isCover: Boolean = false,
)

@Serializable
data class Food(
    val name: String,
    val nameEn: String? = null,
    val slug: String,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val currency: String,
)

@Serializable
data class PlaceDetail(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val slug: String,
    val shortDescription: String? = null,
    val lat: Double,
    val lon: Double,
    val address: String? = null,
    val category: CategoryRef? = null,
    val source: Source,
    val categories: List<CategoryRef> = emptyList(),
    val media: List<Media> = emptyList(),
    val foods: List<Food> = emptyList(),
)

/** An address or named place from GET /api/v1/geocode (Photon, OSM data). */
@Serializable
data class GeoResult(
    val name: String,
    val detail: String? = null,
    val lat: Double,
    val lon: Double,
    val ref: String? = null,
    val approximate: Boolean = false, // a house number was typed but this result has none
)

@Serializable
internal data class GeocodeResponse(val results: List<GeoResult>)

@Serializable
internal data class ErrorEnvelope(val error: ErrorBody)

@Serializable
internal data class ErrorBody(val code: String, val message: String, val requestId: String = "")

/** Places of one grid cell of a zoomed-out map (UX-2 P3); [lat]/[lon] is where to draw the bubble. */
@Serializable
data class PlaceCluster(val lat: Double, val lon: Double, val count: Int)

@Serializable
data class ClustersResponse(val clusters: List<PlaceCluster>)
