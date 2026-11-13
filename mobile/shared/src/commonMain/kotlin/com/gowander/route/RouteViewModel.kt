package com.gowander.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gowander.api.ApiError
import com.gowander.api.GoWanderApi
import com.gowander.api.LatLon
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
)

/** Flow A state: origin and destination survive failures and retries; changing either clears stale results. */
class RouteViewModel(private val api: GoWanderApi) : ViewModel() {
    private val _state = MutableStateFlow(RouteUiState())
    val state: StateFlow<RouteUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun setOrigin(e: Endpoint?) = changeEndpoints { it.copy(origin = e) }
    fun setDestination(e: Endpoint?) = changeEndpoints { it.copy(destination = e) }
    fun swap() = changeEndpoints { it.copy(origin = it.destination, destination = it.origin) }

    fun selectRoute(i: Int) = _state.update { s ->
        val p = s.phase
        if (p is RoutePhase.Results && i in p.routes.indices) s.copy(phase = p.copy(selected = i)) else s
    }

    fun retry() = search()

    fun search() {
        val (origin, destination) = _state.value.let { (it.origin ?: return) to (it.destination ?: return) }
        job?.cancel() // the newest request wins
        _state.update { it.copy(phase = RoutePhase.Loading) }
        job = viewModelScope.launch {
            val phase = try {
                val res = api.planRoute(PlanRequest(LatLon(origin.lat, origin.lon), LatLon(destination.lat, destination.lon)))
                if (res.routes.isEmpty()) RoutePhase.Failed(Strings.NO_ROUTE, retryable = false)
                else RoutePhase.Results(res.routes, selected = 0, assumedData = res.dataStatus == "ASSUMED")
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
        _state.value = next.copy(phase = RoutePhase.Idle)
    }

    private fun failure(e: ApiError): RoutePhase.Failed = when (e) {
        // Codes the user fixes by changing the trip; retrying the same request cannot help.
        is ApiError.Server -> RoutePhase.Failed(e.userMessage, retryable = e.code !in USER_FIXABLE)
        is ApiError.Network -> RoutePhase.Failed(Strings.NETWORK_ERROR, retryable = true)
        is ApiError.Unexpected -> RoutePhase.Failed(Strings.UNEXPECTED_ERROR, retryable = true)
    }

    private companion object {
        val USER_FIXABLE = setOf("NO_ROUTE_FOUND", "OUT_OF_COVERAGE", "DATA_UNAVAILABLE", "INVALID_REQUEST")
    }
}
