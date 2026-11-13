package com.gowander

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowander.api.createGoWanderApi
import com.gowander.discover.DiscoverScreen
import com.gowander.discover.DiscoverViewModel
import com.gowander.route.Endpoint
import com.gowander.route.RouteMapScreen
import com.gowander.route.RouteViewModel
import com.gowander.ui.Chip
import com.gowander.ui.Palette
import com.gowander.ui.Strings

// One HTTP client for the whole process: created on first use, shared by both ViewModels, never per Activity.
private val sharedApi by lazy { createGoWanderApi() }

/** Two tabs (Map, Discover); "Directions" on a place switches to Map with that place as destination (Flow B). */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler is experimental in Compose Multiplatform 1.12 (ADR-029)
@Composable
fun App() {
    val routeVm = viewModel { RouteViewModel(sharedApi) }
    val discoverVm = viewModel { DiscoverViewModel(sharedApi) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Hoisted so the list keeps its scroll position across tab switches (the Discover screen leaves composition).
    val placesListState = rememberLazyListState()
    // System Back from the Discover list returns to the map instead of closing the app.
    BackHandler(enabled = tab == 1) { tab = 0 }

    Column(Modifier.fillMaxSize().background(Palette.Surface)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> RouteMapScreen(routeVm)
                else -> DiscoverScreen(discoverVm, placesListState) { place ->
                    routeVm.setDestination(Endpoint(place.lat, place.lon, place.name))
                    tab = 0
                    routeVm.search() // no-op until an origin is known
                }
            }
        }
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(8.dp)) {
            Chip(Strings.TAB_MAP, selected = tab == 0, Modifier.weight(1f).padding(4.dp)) { tab = 0 }
            Chip(Strings.TAB_DISCOVER, selected = tab == 1, Modifier.weight(1f).padding(4.dp)) { tab = 1 }
        }
    }
}
