package com.gowander.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gowander.api.ApiError
import com.gowander.api.Category
import com.gowander.api.GoWanderApi
import com.gowander.api.PlaceDetail
import com.gowander.api.PlaceSummary
import com.gowander.ui.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ListPhase {
    data object Loading : ListPhase
    data object Ready : ListPhase
    data class Failed(val message: String) : ListPhase
}

sealed interface DetailState {
    data object None : DetailState
    data class Loading(val id: String) : DetailState
    data class Loaded(val place: PlaceDetail) : DetailState
    data class Failed(val id: String, val message: String) : DetailState
}

data class DiscoverUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val query: String? = null, // null = no search; always at least 2 characters when set
    val places: List<PlaceSummary> = emptyList(),
    val nextCursor: String? = null,
    val phase: ListPhase = ListPhase.Loading,
    val loadingMore: Boolean = false,
    val moreError: String? = null,
    val detail: DetailState = DetailState.None,
)

/** Flow B state: categories, search, paged list and place detail. */
class DiscoverViewModel(private val api: GoWanderApi) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()
    private var listJob: Job? = null
    private var detailJob: Job? = null

    init {
        reload()
    }

    fun selectCategory(slug: String?) {
        if (slug == _state.value.selectedCategory) return
        _state.update { it.copy(selectedCategory = slug) }
        reload()
    }

    /** Searches only with 2+ characters (the API minimum); a shorter text clears the search. */
    fun submitQuery(text: String) {
        val q = text.trim().takeIf { it.length >= 2 }
        if (q == _state.value.query) return
        _state.update { it.copy(query = q) }
        reload()
    }

    fun retry() = reload()

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.phase != ListPhase.Ready) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        listJob = viewModelScope.launch {
            try {
                val page = api.places(s.selectedCategory, s.query, cursor, PAGE_SIZE)
                _state.update { it.copy(places = it.places + page.places, nextCursor = page.nextCursor, loadingMore = false) }
            } catch (e: ApiError) {
                _state.update { it.copy(loadingMore = false, moreError = message(e)) }
            }
        }
    }

    fun openPlace(id: String) {
        detailJob?.cancel()
        _state.update { it.copy(detail = DetailState.Loading(id)) }
        detailJob = viewModelScope.launch {
            val d = try {
                DetailState.Loaded(api.place(id))
            } catch (e: ApiError) {
                DetailState.Failed(id, message(e))
            }
            _state.update { it.copy(detail = d) }
        }
    }

    fun closePlace() {
        detailJob?.cancel()
        _state.update { it.copy(detail = DetailState.None) }
    }

    private fun reload() {
        listJob?.cancel()
        val s = _state.value
        _state.update { it.copy(phase = ListPhase.Loading, places = emptyList(), nextCursor = null, moreError = null, loadingMore = false) }
        listJob = viewModelScope.launch {
            try {
                val categories = s.categories.ifEmpty { api.categories() }
                val page = api.places(s.selectedCategory, s.query, null, PAGE_SIZE)
                _state.update { it.copy(categories = categories, places = page.places, nextCursor = page.nextCursor, phase = ListPhase.Ready) }
            } catch (e: ApiError) {
                _state.update { it.copy(phase = ListPhase.Failed(message(e))) }
            }
        }
    }

    private fun message(e: ApiError) = when (e) {
        is ApiError.Server -> e.userMessage
        is ApiError.Network -> Strings.NETWORK_ERROR
        is ApiError.Unexpected -> Strings.UNEXPECTED_ERROR
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
