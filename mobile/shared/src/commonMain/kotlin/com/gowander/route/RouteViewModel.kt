package com.gowander.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gowander.api.ApiError
import com.gowander.api.GoWanderApi
import com.gowander.api.LatLon
import com.gowander.api.PlaceSummary
import com.gowander.api.PlanRequest
import com.gowander.api.Route
import com.gowander.ui.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Endpoint(val lat: Double, val lon: Double, val label: String)

sealed interface RoutePhase {
    data object Idle : RoutePhase
    data object Loading : RoutePhase
    data class Results(val routes: List<Route>, val selected: Int, val assumedData: Boolean) : RoutePhase
    data class Failed(val message: String, val retryable: Boolean) : RoutePhase
}

data class RouteUiState(
    val origin: Endpoint? = null,
    val destination: Endpoint? = null,
    val phase: RoutePhase = RoutePhase.Idle,
    val arriveBy: Long? = null, // epoch millis; null = leave now (M3 chips)
    val sort: RouteSort = RouteSort.Fastest,
    val recentDestinations: List<Endpoint> = emptyList(), // newest first, this session only
    val detailOpen: Boolean = false, // M6 for the selected route
    val nearAlighting: List<PlaceSummary> = emptyList(), // M6 "Gần ga …"
)

/** Flow A state: origin and destination survive failures and retries; changing either clears stale results. */
class RouteViewModel(private val api: GoWanderApi) : ViewModel() {
    private val _state = MutableStateFlow(RouteUiState())
    val state: StateFlow<RouteUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var nearbyJob: Job? = null

    fun setOrigin(e: Endpoint?) = changeEndpoints { it.copy(origin = e) }
    fun setDestination(e: Endpoint?) {
        if (e != null) _state.update { s -> s.copy(recentDestinations = (listOf(e) + s.recentDestinations.filter { it.label != e.label }).take(RECENT_MAX)) }
        changeEndpoints { it.copy(destination = e) }
    }

    /** M3: "Đến trước lúc" with a time, or null for "Khởi hành ngay". Clears stale results like an endpoint change. */
    fun setArriveBy(epochMillis: Long?) {
        if (epochMillis == _state.value.arriveBy) return
        job?.cancel()
        _state.update { it.copy(arriveBy = epochMillis, phase = RoutePhase.Idle, detailOpen = false) }
    }

    /** M5 chips: reorder without a new request; the chosen route stays chosen. */
    fun setSort(sort: RouteSort) = _state.update { s ->
        val p = s.phase as? RoutePhase.Results ?: return@update s.copy(sort = sort)
        val chosen = p.routes.getOrNull(p.selected)
        val sorted = sortRoutes(p.routes, sort)
        s.copy(sort = sort, phase = p.copy(routes = sorted, selected = sorted.indexOf(chosen).coerceAtLeast(0)))
    }

    /** M6: open the detail of route [i] and look up places near where the user leaves the train (or the destination). */
    fun openDetail(i: Int) {
        selectRoute(i)
        val p = _state.value.phase as? RoutePhase.Results ?: return
        val route = p.routes.getOrNull(p.selected) ?: return
        val alight = route.legs.lastOrNull { it.transit }?.to ?: route.legs.last().to
        _state.update { it.copy(detailOpen = true, nearAlighting = emptyList()) }
        nearbyJob?.cancel()
        nearbyJob = viewModelScope.launch {
            // Nice-to-have list: on failure the detail simply shows no nearby places (the route itself is unaffected).
            val places = try { api.nearby(LatLon(alight.lat, alight.lon), radiusM = 400, limit = 3) } catch (e: ApiError) { emptyList() }
            _state.update { it.copy(nearAlighting = places) }
        }
    }

    fun closeDetail() = _state.update { it.copy(detailOpen = false) }
    fun swap() = changeEndpoints { it.copy(origin = it.destination, destination = it.origin) }

    fun selectRoute(i: Int) = _state.update { s ->
        val p = s.phase
        if (p is RoutePhase.Results && i in p.routes.indices) s.copy(phase = p.copy(selected = i)) else s
    }

    fun retry() = search()

    fun search() {
        val (origin, destination) = _state.value.let { (it.origin ?: return) to (it.destination ?: return) }
        job?.cancel() // the newest request wins
        _state.update { it.copy(phase = RoutePhase.Loading, detailOpen = false) }
        job = viewModelScope.launch {
            val phase = try {
                val arrive = _state.value.arriveBy
                val res = api.planRoute(
                    PlanRequest(
                        LatLon(origin.lat, origin.lon), LatLon(destination.lat, destination.lon),
                        dateTime = arrive?.let(::pilotIso), timeType = if (arrive != null) "ARRIVE" else "DEPART",
                    ),
                )
                if (res.routes.isEmpty()) RoutePhase.Failed(Strings.NO_ROUTE, retryable = false)
                else RoutePhase.Results(sortRoutes(res.routes, _state.value.sort), selected = 0, assumedData = res.dataStatus == "ASSUMED")
            } catch (e: ApiError) {
                failure(e)
            }
            _state.update { it.copy(phase = phase) }
        }
    }

    private fun changeEndpoints(change: (RouteUiState) -> RouteUiState) {
        val next = change(_state.value)
        if (next.origin == _state.value.origin && next.destination == _state.value.destination) return
        job?.cancel()
        _state.value = next.copy(phase = RoutePhase.Idle, detailOpen = false)
    }

    private fun failure(e: ApiError): RoutePhase.Failed = when (e) {
        // Codes the user fixes by changing the trip; retrying the same request cannot help.
        is ApiError.Server -> RoutePhase.Failed(e.userMessage, retryable = e.code !in USER_FIXABLE)
        is ApiError.Network -> RoutePhase.Failed(Strings.NETWORK_ERROR, retryable = true)
        is ApiError.Unexpected -> RoutePhase.Failed(Strings.UNEXPECTED_ERROR, retryable = true)
    }

    private companion object {
        const val RECENT_MAX = 5
        val USER_FIXABLE = setOf("NO_ROUTE_FOUND", "OUT_OF_COVERAGE", "DATA_UNAVAILABLE", "INVALID_REQUEST")
    }
}
