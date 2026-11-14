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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.gowander.onboarding.FirstRunScreen
import com.gowander.onboarding.LoadingView
import com.gowander.onboarding.WelcomeScreen
import androidx.compose.foundation.layout.safeDrawingPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong
import com.gowander.route.NavigationScreen
import com.gowander.route.PreviewScreen
import com.gowander.route.StepsScreen
import com.gowander.route.StreetPhoto
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.decodeToImageBitmap
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
    // Kit S0 + S1 on the first launch only; the flag survives app restarts (Prefs).
    var onboarded by rememberSaveable { mutableStateOf(Prefs.getBool(ONBOARDED)) }
    var firstRunStep by rememberSaveable { mutableStateOf(0) }
    var askLocation by rememberSaveable { mutableStateOf(false) }
    fun finishOnboarding(withLocation: Boolean) {
        Prefs.setBool(ONBOARDED, true)
        askLocation = withLocation
        onboarded = true
    }
    if (!onboarded) {
        if (firstRunStep == 0) WelcomeScreen { firstRunStep = 1 }
        else FirstRunScreen(onAllowLocation = { finishOnboarding(true) }, onLater = { finishOnboarding(false) })
        return@KitTheme
    }
    val mapView = rememberSaveable { mutableStateOf(com.gowander.route.MapView.Flat) }
    CompositionLocalProvider(com.gowander.route.LocalMapView provides mapView) { AppContent(askLocation) }
}

private const val ONBOARDED = "onboarded"
private const val PHOTO_CACHE_SIZE = 24

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppContent(askLocationNow: Boolean) {
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
    fun goToPlaceId(id: String) {
        routeVm.closeDetail() // otherwise the route detail keeps covering the place (review M06b)
        tab = Tab.Discover
        overlay = Overlay.None
        discoverVm.openPlace(id)
    }
    fun goToPlace(p: PlaceSummary) = goToPlaceId(p.id)
    // Tapping any place goes to the route first (owner, 14-11-2026); its description is one tap away from there.
    var infoPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoPlaceName by rememberSaveable { mutableStateOf<String?>(null) }
    fun routeToPlace(id: String, name: String, lat: Double, lon: Double) {
        routeVm.setDestination(Endpoint(lat, lon, name))
        infoPlaceId = id
        infoPlaceName = name
        tab = Tab.Plan
        overlay = Overlay.None
        search()
    }
    // Only while the destination is still that place.
    val placeInfo = infoPlaceName?.takeIf { it == route.destination?.label }
    // After process death the overlay is restored but the ViewModel is new: an empty Results overlay falls back to the tab.
    if (overlay == Overlay.Results && route.phase is com.gowander.route.RoutePhase.Idle && (route.origin == null || route.destination == null)) overlay = Overlay.None

    // Back: close the top overlay, then the route detail, then return to the map tab.
    BackHandler(enabled = route.detailOpen) { routeVm.closeDetail() }
    BackHandler(enabled = !route.detailOpen && overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && tab != Tab.Map) { tab = Tab.Map }

    // NAV-1: navigation mode and its two sub-screens sit above everything else; back closes the sub-screen first.
    var navigating by rememberSaveable { mutableStateOf(false) }
    var navSub by rememberSaveable { mutableStateOf<String?>(null) } // "steps" or "preview"
    var previewStart by rememberSaveable { mutableStateOf(0) }
    var previewFromSteps by rememberSaveable { mutableStateOf(false) }
    // Back goes to the screen the user came from: preview -> steps when opened from the list, otherwise the route.
    fun closeSub() { navSub = if (navSub == "preview" && previewFromSteps) "steps" else null }
    // After process death the ViewModel restarts at Idle while these flags are restored: drop them instead of showing
    // an empty navigation screen (review 14-11-2026). Re-route and mode switch go straight to Loading, never Idle.
    LaunchedEffect(route.phase is com.gowander.route.RoutePhase.Idle) {
        if (route.phase is com.gowander.route.RoutePhase.Idle) { navigating = false; navSub = null }
    }
    BackHandler(enabled = navigating && navSub == null) { navigating = false }
    BackHandler(enabled = navSub != null) { closeSub() }
    fun startNavigation() {
        if (location.permission !is LocationPermission.Granted) location.requestPermission()
        navigating = true
    }
    fun openPreview(i: Int, fromSteps: Boolean = false) { previewStart = i; previewFromSteps = fromSteps; navSub = "preview" }
    // One loader for the whole session: remembered (no new lambda per GPS fix), results cached per point so moving
    // back and forth between steps does not download or decode again, decoding off the main thread.
    val loadPhoto: suspend (Double, Double) -> StreetPhoto? = remember {
        val cache = LinkedHashMap<String, StreetPhoto?>()
        val loader: suspend (Double, Double) -> StreetPhoto? = loader@{ lat, lon ->
            val key = "${(lat * 10_000).roundToLong()}:${(lon * 10_000).roundToLong()}" // ~11 m
            if (key in cache) return@loader cache[key]
            val photo = try {
                sharedApi.streetImage(LatLon(lat, lon))?.let { r ->
                    val bytes = sharedApi.imageBytes(r.image.url)
                    StreetPhoto(withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }, "${r.source.attribution} · ${r.image.capturedAt.take(7)}", r.image.pageUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("GoWander: street photo unavailable: ${e.message}") // the preview shows the 3D map instead
                null
            }
            cache[key] = photo
            if (cache.size > PHOTO_CACHE_SIZE) cache.remove(cache.keys.first())
            photo
        }
        loader
    }
    LaunchedEffect(askLocationNow) { if (askLocationNow) useMyLocation() }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                navSub == "preview" -> PreviewScreen(route, previewStart, loadPhoto, onBack = ::closeSub, onBackToRoute = { navSub = null })
                navSub == "steps" -> StepsScreen(route, onBack = { navSub = null }, onPreview = { openPreview(it, fromSteps = true) })
                navigating && route.phase is com.gowander.route.RoutePhase.Results -> NavigationScreen(
                    route, location, onExit = { navigating = false }, onSteps = { navSub = "steps" }, onPreview = { openPreview(it) },
                    onReroute = { location.lastLocation?.position?.let { routeVm.setOrigin(Endpoint(it.latitude, it.longitude, Strings.MY_LOCATION)); routeVm.search() } },
                )
                navigating -> Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val failed = route.phase as? com.gowander.route.RoutePhase.Failed
                    if (failed != null) Text(failed.message) else LoadingView(Strings.SEARCHING, cards = 1)
                    Button(Strings.EXIT, Modifier.fillMaxWidth(), kind = ButtonKind.Accent) { navigating = false }
                }
                route.detailOpen -> DetailScreen(
                    route, location, onBack = routeVm::closeDetail, onOpenPlace = ::goToPlace,
                    onStart = ::startNavigation, onPreview = { openPreview(0) }, onSteps = { navSub = "steps" }, onMode = routeVm::switchModeInDetail, placeInfo = placeInfo, onPlaceInfo = { infoPlaceId?.let(::goToPlaceId) },
                )
                overlay == Overlay.Search -> SearchScreen(
                    search = { q -> sharedApi.places(category = null, query = q, cursor = null, limit = 10).places },
                    geocode = { q -> sharedApi.geocode(q, near) },
                    onBack = { overlay = Overlay.None },
                    onPick = { p -> routeToPlace(p.id, p.name, p.lat, p.lon) },
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
                    route, onBack = { overlay = Overlay.None }, onMode = { routeVm.setMode(it); routeVm.search() }, onSort = routeVm::setSort, onOpen = routeVm::openDetail,
                    onRetry = routeVm::retry, onChangeDestination = { overlay = Overlay.Search },
                    placeInfo = placeInfo, onPlaceInfo = { infoPlaceId?.let(::goToPlaceId) },
                )
                tab == Tab.Map -> MapHome(
                    route, location, nearby, onSearch = { overlay = Overlay.Search }, onLocate = ::useMyLocation,
                    onOpenPlace = { p -> routeToPlace(p.id, p.name, p.lat, p.lon) }, onSeeAll = { tab = Tab.Discover },
                )
                tab == Tab.Plan -> PlanScreen(
                    route, onEditDestination = { overlay = Overlay.Search }, onUseMyLocation = ::useMyLocation,
                    onPickOrigin = { overlay = Overlay.PickOrigin }, onPickDestination = { overlay = Overlay.PickDestination },
                    onArriveBy = routeVm::setArriveBy, onMode = { routeVm.setMode(it); if (route.destination != null) search() }, onRecent = routeVm::setDestination, onSwap = routeVm::swap, onSearch = ::search,
                )
                else -> DiscoverScreen(discoverVm, placesListState, onOpen = { p -> routeToPlace(p.id, p.name, p.lat, p.lon) }) { place ->
                    routeToPlace(place.id, place.name, place.lat, place.lon) // "Chỉ đường" from the place page
                }
            }
        }
        if (overlay == Overlay.None && !route.detailOpen && !navigating && navSub == null) {
            BottomNav(tab, Modifier.windowInsetsPadding(WindowInsets.navigationBars)) { tab = it; overlay = Overlay.None }
        }
    }
}
