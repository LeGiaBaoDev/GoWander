package com.gowander

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gowander.api.ApiError
import com.gowander.api.LatLon
import com.gowander.api.PlaceSummary
import com.gowander.api.createGoWanderApi
import com.gowander.discover.DiscoverScreen
import com.gowander.discover.DiscoverViewModel
import com.gowander.discover.PlaceScreen
import com.gowander.home.JourneyScreen
import com.gowander.home.ProfileScreen
import com.gowander.nav.AppNav
import com.gowander.nav.Screen
import com.gowander.nav.Tab
import com.gowander.onboarding.LoadingView
import com.gowander.onboarding.WelcomeScreen
import com.gowander.route.DetailScreen
import com.gowander.route.Endpoint
import com.gowander.route.MapHome
import com.gowander.route.NavigationScreen
import com.gowander.route.PickScreen
import com.gowander.route.PinOption
import com.gowander.route.PlanScreen
import com.gowander.route.PreviewScreen
import com.gowander.route.ResultsScreen
import com.gowander.route.RoutePhase
import com.gowander.route.RouteViewModel
import com.gowander.route.SearchScreen
import com.gowander.route.StepsScreen
import com.gowander.route.StreetPhoto
import com.gowander.ui.BottomNav
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.KitTheme
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Text
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState

// One HTTP client for the whole process: created on first use, shared by both ViewModels, never per Activity.
private val sharedApi by lazy { createGoWanderApi() }

// shortcut: centre of the pilot area (Ga Bến Thành) when there is no GPS fix yet; replace with the user's city when multi-city.
private val PILOT_CENTRE = LatLon(10.770775, 106.697569)

private const val ONBOARDED = "onboarded"
private const val PHOTO_CACHE_SIZE = 24

/** The back stacks survive rotation and process death as plain strings (UX-2, ADR-034). */
private val NavSaver = Saver<AppNav, List<String>>(save = { it.save() }, restore = { AppNav.restore(it) })

@Composable
fun App() = KitTheme {
    // Kit S0 on the first launch only; no first-run form: location is asked when a feature needs it (ADR-034).
    var onboarded by rememberSaveable { mutableStateOf(Prefs.getBool(ONBOARDED)) }
    if (!onboarded) {
        WelcomeScreen { Prefs.setBool(ONBOARDED, true); onboarded = true }
        return@KitTheme
    }
    val mapView = rememberSaveable { mutableStateOf(com.gowander.route.MapView.Flat) }
    CompositionLocalProvider(com.gowander.route.LocalMapView provides mapView) { AppContent() }
}

@OptIn(ExperimentalComposeUiApi::class) // BackHandler is experimental in Compose Multiplatform 1.12 (ADR-029)
@Composable
private fun AppContent() {
    val routeVm = viewModel { RouteViewModel(sharedApi) }
    val discoverVm = viewModel { DiscoverViewModel(sharedApi) }
    val route by routeVm.state.collectAsState()
    var nav by rememberSaveable(stateSaver = NavSaver) { mutableStateOf(AppNav()) }
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

    // The place a route was opened for: the results offer "Thông tin về …" while the destination is still that place.
    var infoPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoPlaceName by rememberSaveable { mutableStateOf<String?>(null) }
    val placeInfo = infoPlaceName?.takeIf { it == route.destination?.label }

    // A search started without an origin runs as soon as the GPS fix arrives (or the user picks one).
    var searchWhenOrigin by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(route.origin, route.destination) {
        if (searchWhenOrigin && route.origin != null && route.destination != null) {
            searchWhenOrigin = false
            routeVm.search()
        }
    }

    fun back() {
        if (nav.top == Screen.Detail) routeVm.closeDetail()
        nav = nav.pop()
    }

    /** Every "go there" ends here (ADR-034): the results, from wherever the trip started. */
    fun showRoutes() {
        val s = routeVm.state.value
        if (s.origin == null) {
            searchWhenOrigin = true
            // Ask for location in context, only if never answered; a refusal is not asked again (T7).
            if (location.permission is LocationPermission.NotDetermined) useMyLocation()
        } else routeVm.search()
        nav = nav.showResults()
    }

    fun chooseDestination(e: Endpoint, placeId: String?) {
        routeVm.setDestination(e)
        infoPlaceId = placeId
        infoPlaceName = placeId?.let { e.label }
        // Inside the trip form the pick only fills the form; everywhere else it goes straight to the routes.
        if (Screen.Plan in nav.stack) nav = nav.popTo { it == Screen.Plan } else showRoutes()
    }

    fun chooseOrigin(e: Endpoint) {
        routeVm.setOrigin(e)
        if (Screen.Plan in nav.stack) nav = nav.popTo { it == Screen.Plan } else showRoutes()
    }

    // After process death the ViewModel restarts empty while the stacks are restored: route screens would be blank.
    // Checked once on entry only: later a swap can empty the destination on purpose (trip form) without leaving it.
    LaunchedEffect(Unit) {
        val s = routeVm.state.value
        if (s.phase is RoutePhase.Idle && s.destination == null) nav = nav.dropRouteScreens()
    }

    // Back pops the current tab's stack; at a tab root it returns to the map tab, then leaves the app.
    BackHandler(enabled = !nav.canPop && nav.tab != Tab.Map) { nav = nav.select(Tab.Map) }
    BackHandler(enabled = nav.canPop) { back() }

    fun startNavigation() {
        if (location.permission !is LocationPermission.Granted) location.requestPermission()
        nav = nav.push(Screen.Navigation)
    }

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

    @Composable
    fun Results() = ResultsScreen(
        route, onBack = ::back, onMode = { routeVm.setMode(it); routeVm.search() }, onSort = routeVm::setSort,
        onOpen = { i -> routeVm.openDetail(i); nav = nav.push(Screen.Detail) },
        onRetry = routeVm::retry, onChangeDestination = { nav = nav.push(Screen.Search) },
        placeInfo = placeInfo, onPlaceInfo = { infoPlaceId?.let { nav = nav.openPlace(it) } },
        onEditTrip = { nav = nav.push(Screen.Plan) }, onUseMyLocation = ::useMyLocation, onPickOrigin = { nav = nav.push(Screen.PickOrigin) },
    )

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val top = nav.top) {
                Screen.Search -> SearchScreen(
                    search = { q -> sharedApi.places(category = null, query = q, cursor = null, limit = 10).places },
                    geocode = { q -> sharedApi.geocode(q, near) },
                    onBack = ::back,
                    onPick = { p -> chooseDestination(Endpoint(p.lat, p.lon, p.name), p.id) },
                    // Address or place: the same flow, straight to the routes (ADR-034, T2).
                    onPickAddress = { a -> chooseDestination(Endpoint(a.lat, a.lon, a.name), null) },
                    onPickOnMap = { nav = nav.push(Screen.PickDestination) },
                )
                Screen.PickOrigin, Screen.PickDestination -> PickScreen(
                    forOrigin = top == Screen.PickOrigin, location = location, state = route,
                    suggest = { lat, lon ->
                        coroutineScope {
                            // Address at the pin + nearest places/stations; either part may fail on its own.
                            val addr = async { try { sharedApi.reverseGeocode(LatLon(lat, lon)) } catch (e: ApiError) { emptyList() } }
                            val close = async { try { sharedApi.nearby(LatLon(lat, lon), radiusM = 150, limit = 3) } catch (e: ApiError) { emptyList() } }
                            addr.await().map { PinOption(it.name, it.detail, it.lat, it.lon) } +
                                close.await().map { PinOption(it.name, listOfNotNull(it.category?.name, it.distanceM?.let(Strings::meters)).joinToString(" · "), it.lat, it.lon) }
                        }
                    },
                    onBack = ::back,
                ) { e -> if (top == Screen.PickOrigin) chooseOrigin(e) else chooseDestination(e, null) }
                Screen.Plan -> PlanScreen(
                    route, onEditDestination = { nav = nav.push(Screen.Search) }, onUseMyLocation = ::useMyLocation,
                    onPickOrigin = { nav = nav.push(Screen.PickOrigin) }, onPickDestination = { nav = nav.push(Screen.PickDestination) },
                    onArriveBy = routeVm::setArriveBy, onMode = routeVm::setMode, onRecent = routeVm::setDestination, onSwap = routeVm::swap,
                    onBack = ::back, onSearch = ::showRoutes,
                )
                Screen.Results -> Results()
                // While a mode switch reloads, the results screen shows the loading and any failure in place.
                Screen.Detail -> if (route.phase is RoutePhase.Results) DetailScreen(
                    route, location, onBack = ::back, onOpenPlace = { p -> nav = nav.openPlace(p.id) },
                    onStart = ::startNavigation, onPreview = { nav = nav.push(Screen.Preview(0)) }, onSteps = { nav = nav.push(Screen.Steps) },
                    onMode = routeVm::switchModeInDetail, placeInfo = placeInfo, onPlaceInfo = { infoPlaceId?.let { nav = nav.openPlace(it) } },
                ) else Results()
                Screen.Steps -> StepsScreen(route, onBack = ::back, onPreview = { nav = nav.push(Screen.Preview(it)) })
                is Screen.Preview -> PreviewScreen(
                    route, top.start, loadPhoto, onBack = ::back,
                    onBackToRoute = { nav = nav.popTo { it == Screen.Navigation || it == Screen.Detail } },
                )
                Screen.Navigation -> if (route.phase is RoutePhase.Results) NavigationScreen(
                    route, location, onExit = ::back, onSteps = { nav = nav.push(Screen.Steps) }, onPreview = { nav = nav.push(Screen.Preview(it)) },
                    onReroute = { location.lastLocation?.position?.let { routeVm.setOrigin(Endpoint(it.latitude, it.longitude, Strings.MY_LOCATION)); routeVm.search() } },
                ) else Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val failed = route.phase as? RoutePhase.Failed
                    if (failed != null) Text(failed.message) else LoadingView(Strings.SEARCHING, cards = 1)
                    Button(Strings.EXIT, Modifier.fillMaxWidth(), kind = ButtonKind.Accent, onClick = ::back)
                }
                is Screen.Place -> PlaceScreen(discoverVm, top.id, onBack = ::back) { p -> chooseDestination(Endpoint(p.lat, p.lon, p.name), p.id) }
                null -> when (nav.tab) {
                    Tab.Map -> MapHome(
                        route, location, nearby, onSearch = { nav = nav.push(Screen.Search) }, onLocate = ::useMyLocation,
                        onOpenPlace = { p -> nav = nav.openPlace(p.id) }, onSeeAll = { nav = nav.select(Tab.Discover) },
                    )
                    Tab.Discover -> DiscoverScreen(discoverVm, placesListState) { p -> nav = nav.openPlace(p.id) }
                    Tab.Journey -> JourneyScreen(route.recentDestinations) { e -> chooseDestination(e, null) }
                    Tab.Profile -> ProfileScreen()
                }
            }
        }
        if (!nav.canPop) {
            BottomNav(nav.tab, Modifier.windowInsetsPadding(WindowInsets.navigationBars), onGo = { nav = nav.push(Screen.Search) }) { nav = nav.select(it) }
        }
    }
}
