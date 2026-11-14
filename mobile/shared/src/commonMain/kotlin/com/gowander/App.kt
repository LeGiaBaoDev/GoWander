package com.gowander

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowander.api.ApiError
import com.gowander.api.LatLon
import com.gowander.api.PlaceSummary
import com.gowander.api.createGoWanderApi
import com.gowander.discover.DiscoverScreen
import com.gowander.discover.DiscoverViewModel
import com.gowander.route.DetailScreen
import com.gowander.route.Endpoint
import com.gowander.route.MapHome
import com.gowander.route.PickScreen
import com.gowander.route.PinOption
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.gowander.route.PlanScreen
import com.gowander.route.ResultsScreen
import com.gowander.route.RouteViewModel
import com.gowander.route.SearchScreen
import com.gowander.ui.BottomNav
import com.gowander.ui.KitTheme
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Tab
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState

// One HTTP client for the whole process: created on first use, shared by both ViewModels, never per Activity.
private val sharedApi by lazy { createGoWanderApi() }

/** Screens stacked over the route tabs (kit M2, M4, M5, M6). */
private enum class Overlay { None, Search, PickOrigin, PickDestination, Results }

// shortcut: centre of the pilot area (Ga Bến Thành) when there is no GPS fix yet; replace with the user's city when multi-city.
private val PILOT_CENTRE = LatLon(10.770775, 106.697569)

@OptIn(ExperimentalComposeUiApi::class) // BackHandler is experimental in Compose Multiplatform 1.12 (ADR-029)
@Composable
fun App() = KitTheme {
    val routeVm = viewModel { RouteViewModel(sharedApi) }
    val discoverVm = viewModel { DiscoverViewModel(sharedApi) }
    val route by routeVm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Map) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    val placesListState = rememberLazyListState()

    // Location is shared by every map; GPS becomes the origin only when there is none yet or the user asks (M06 fix).
    val location = rememberLocationState(provider = rememberDefaultLocationProvider())
    var userAskedGps by rememberSaveable { mutableStateOf(false) }
    val fix = location.lastLocation?.position
    LaunchedEffect(fix != null, userAskedGps) {
        val p = location.lastLocation?.position ?: return@LaunchedEffect
        if (userAskedGps || routeVm.state.value.origin == null) {
            routeVm.setOrigin(Endpoint(p.latitude, p.longitude, Strings.MY_LOCATION))
            userAskedGps = false
        }
    }
    LaunchedEffect(location.permission) { if (location.permission is LocationPermission.NotGranted) userAskedGps = false }
    fun useMyLocation() {
        userAskedGps = true
        if (location.permission !is LocationPermission.Granted) location.requestPermission()
    }

    // M1 "Gợi ý gần bạn": around the GPS fix, else the pilot centre. A failure only hides the sheet.
    var nearby by remember { mutableStateOf<List<PlaceSummary>>(emptyList()) }
    val near = fix?.let { LatLon(it.latitude, it.longitude) } ?: PILOT_CENTRE
    LaunchedEffect(near.lat.toFloat(), near.lon.toFloat()) {
        nearby = try { sharedApi.nearby(near, radiusM = 600, limit = 3) } catch (e: ApiError) { emptyList() }
    }

    fun search() {
        routeVm.search()
        // Read the ViewModel, not `route`: right after setDestination the composed value is still the old one.
        val now = routeVm.state.value
        if (now.origin != null && now.destination != null) overlay = Overlay.Results
    }
    fun goToPlace(p: PlaceSummary) {
        routeVm.closeDetail() // otherwise the route detail keeps covering the place (review M06b)
        tab = Tab.Discover
        overlay = Overlay.None
        discoverVm.openPlace(p.id)
    }
    // After process death the overlay is restored but the ViewModel is new: an empty Results overlay falls back to the tab.
    if (overlay == Overlay.Results && route.phase is com.gowander.route.RoutePhase.Idle && (route.origin == null || route.destination == null)) overlay = Overlay.None

    // Back: close the top overlay, then the route detail, then return to the map tab.
    BackHandler(enabled = route.detailOpen) { routeVm.closeDetail() }
    BackHandler(enabled = !route.detailOpen && overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && tab != Tab.Map) { tab = Tab.Map }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                route.detailOpen -> DetailScreen(route, location, onBack = routeVm::closeDetail, onOpenPlace = ::goToPlace)
                overlay == Overlay.Search -> SearchScreen(
                    search = { q -> sharedApi.places(category = null, query = q, cursor = null, limit = 10).places },
                    geocode = { q -> sharedApi.geocode(q, near) },
                    onBack = { overlay = Overlay.None },
                    onPick = { p -> routeVm.setDestination(Endpoint(p.lat, p.lon, p.name)); overlay = Overlay.None; tab = Tab.Plan },
                    onPickAddress = { a -> routeVm.setDestination(Endpoint(a.lat, a.lon, a.name)); overlay = Overlay.None; tab = Tab.Plan },
                    onPickOnMap = { overlay = Overlay.PickDestination },
                )
                overlay == Overlay.PickOrigin || overlay == Overlay.PickDestination -> PickScreen(
                    forOrigin = overlay == Overlay.PickOrigin, location = location, state = route,
                    suggest = { lat, lon ->
                        coroutineScope {
                            // Address at the pin + nearest places/stations; either part may fail on its own.
                            val addr = async { try { sharedApi.reverseGeocode(LatLon(lat, lon)) } catch (e: ApiError) { emptyList() } }
                            val near = async { try { sharedApi.nearby(LatLon(lat, lon), radiusM = 150, limit = 3) } catch (e: ApiError) { emptyList() } }
                            addr.await().map { PinOption(it.name, it.detail, it.lat, it.lon) } +
                                near.await().map { PinOption(it.name, listOfNotNull(it.category?.name, it.distanceM?.let(Strings::meters)).joinToString(" · "), it.lat, it.lon) }
                        }
                    },
                    onBack = { overlay = Overlay.None },
                ) { e ->
                    if (overlay == Overlay.PickOrigin) routeVm.setOrigin(e) else routeVm.setDestination(e)
                    overlay = Overlay.None
                    tab = Tab.Plan
                }
                overlay == Overlay.Results -> ResultsScreen(
                    route, onBack = { overlay = Overlay.None }, onSort = routeVm::setSort, onOpen = routeVm::openDetail,
                    onRetry = routeVm::retry, onChangeDestination = { overlay = Overlay.Search },
                )
                tab == Tab.Map -> MapHome(
                    route, location, nearby, onSearch = { overlay = Overlay.Search }, onLocate = ::useMyLocation,
                    onOpenPlace = ::goToPlace, onSeeAll = { tab = Tab.Discover },
                )
                tab == Tab.Plan -> PlanScreen(
                    route, onEditDestination = { overlay = Overlay.Search }, onUseMyLocation = ::useMyLocation,
                    onPickOrigin = { overlay = Overlay.PickOrigin }, onPickDestination = { overlay = Overlay.PickDestination },
                    onArriveBy = routeVm::setArriveBy, onRecent = routeVm::setDestination, onSwap = routeVm::swap, onSearch = ::search,
                )
                else -> DiscoverScreen(discoverVm, placesListState) { place ->
                    routeVm.setDestination(Endpoint(place.lat, place.lon, place.name))
                    tab = Tab.Plan
                    search() // Flow B: straight to results when the origin is known
                }
            }
        }
        if (overlay == Overlay.None && !route.detailOpen) {
            BottomNav(tab, Modifier.windowInsetsPadding(WindowInsets.navigationBars)) { tab = it; overlay = Overlay.None }
        }
    }
}
