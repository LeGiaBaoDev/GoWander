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
import com.gowander.home.AppStore
import com.gowander.home.metersBetween
import com.gowander.home.LocalNotice
import com.gowander.home.PlanBuilderScreen
import com.gowander.home.PortfolioEditScreen
import com.gowander.home.SavedPlace
import com.gowander.home.SettingsScreen
import com.gowander.home.TripScreen
import com.gowander.discover.DishScreen
import com.gowander.discover.RegionGuideScreen
import com.gowander.mock.MOCK_PLANS
import com.gowander.mock.Move
import com.gowander.mock.MoveKind
import com.gowander.mock.Plan
import com.gowander.mock.PlanItem
import com.gowander.mock.PlanStatus
import com.gowander.mock.mockTrip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
private const val STORE_KEY = "store.v1" // plans + saved places (ADR-036)
private const val PHOTO_CACHE_SIZE = 24
private const val NEARBY_RADIUS_M = 1500 // screen 1: cards spread over the map around the user
private const val NEARBY_LIMIT = 40
private val NAV_CLEAR_STRIP = 32.dp // BottomNav's transparent top, where the centre circle rises
private const val MAP_PLACES_LIMIT = 60 // P3: a screen shows 4 cards and 12 dots; more only costs data

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
    val store = remember { AppStore(Prefs.getString(STORE_KEY)) { Prefs.setString(STORE_KEY, it) } }
    val scope = rememberCoroutineScope()
    var noticeText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(noticeText) { if (noticeText != null) { delay(3500); noticeText = null } }

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
        nearby = (try { sharedApi.nearby(near, radiusM = NEARBY_RADIUS_M, limit = NEARBY_LIMIT) } catch (e: ApiError) { emptyList() })
            .map { it.copy(distanceM = metersBetween(near.lat, near.lon, it.lat, it.lon)) }.sortedBy { it.distanceM }
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
        showRoutes()
    }

    fun chooseOrigin(e: Endpoint) {
        routeVm.setOrigin(e)
        // Changed from the search screen before a destination exists: back to the search, not to empty results.
        if (routeVm.state.value.destination == null) nav = nav.pop() else showRoutes()
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

    fun startPlan(p: Plan) {
        val first = p.items.firstOrNull { it.lat != null && it.lon != null }
        if (first == null || !p.routing) noticeText = Strings.NO_ROUTING_HERE else chooseDestination(Endpoint(first.lat!!, first.lon!!, first.title), null)
    }

    // Guide highlights are names: open the real place page when the API knows it, else go straight to its routes.
    fun openByName(name: String, lat: Double, lon: Double) {
        scope.launch {
            val hit = try { sharedApi.places(category = null, query = name, cursor = null, limit = 1).places.firstOrNull() } catch (e: ApiError) { null }
            if (hit != null) nav = nav.openPlace(hit.id) else chooseDestination(Endpoint(lat, lon, name), null)
        }
    }

    fun addToPlan(planId: String, item: PlanItem) {
        val plan = store.plan(planId) ?: return
        store.put(plan.copy(items = plan.items + item.copy(move = item.move ?: Move("${Strings.WALK} 10 ${Strings.MINUTES_SHORT}", MoveKind.Walk, 10))))
    }

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
        onUseMyLocation = { useMyLocation(); searchWhenOrigin = true }, onPickOrigin = { nav = nav.push(Screen.PickOrigin) },
        onSwap = { routeVm.swap(); routeVm.search() }, onArriveBy = { routeVm.setArriveBy(it); routeVm.search() },
    )

    CompositionLocalProvider(LocalNotice provides { noticeText = it }) {
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        // The bar floats over the content: the map runs down to the bar itself, under the clear strip that holds the
        // raised "Đi đâu?" circle (no white band, owner 15-11-2026). Lists do the same: rows scroll under the circle like
        // under any floating button (stopping them above it left a white band, owner 15-11-2026).
        var barHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        val below = if (nav.canPop) 0.dp else (barHeight - NAV_CLEAR_STRIP).coerceAtLeast(0.dp)
        Box(Modifier.fillMaxSize().padding(bottom = below)) {
            when (val top = nav.top) {
                Screen.Search -> SearchScreen(
                    search = { q -> sharedApi.places(category = null, query = q, cursor = null, limit = 10).places },
                    geocode = { q -> sharedApi.geocode(q, near) },
                    onBack = ::back,
                    onPick = { p -> chooseDestination(Endpoint(p.lat, p.lon, p.name), p.id) },
                    // Address or place: the same flow, straight to the routes (ADR-034, T2).
                    onPickAddress = { a -> chooseDestination(Endpoint(a.lat, a.lon, a.name), null) },
                    onPickOnMap = { nav = nav.push(Screen.PickDestination) },
                    origin = route.origin?.label, onChangeOrigin = { nav = nav.push(Screen.PickOrigin) },
                    recent = route.recentDestinations, onPickRecent = { e -> chooseDestination(e, null) },
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
                Screen.Results -> Results()
                // While a mode switch reloads, the results screen shows the loading and any failure in place.
                Screen.Detail -> if (route.phase is RoutePhase.Results) DetailScreen(
                    route, location, onBack = ::back, onOpenPlace = { p -> nav = nav.openPlace(p.id) },
                    onStart = ::startNavigation, onSteps = { nav = nav.push(Screen.Preview(0)) }, // "Xem từng bước" (screen 6)
                    placeInfo = placeInfo, onPlaceInfo = { infoPlaceId?.let { nav = nav.openPlace(it) } },
                ) else Results()
                // The step list: opened from the step-by-step view it jumps back to it at the chosen step; from navigation it opens it.
                Screen.Steps -> StepsScreen(route, onBack = ::back) { i ->
                    nav = if (nav.stack.getOrNull(nav.stack.size - 2) is Screen.Preview) nav.pop().pop().push(Screen.Preview(i)) else nav.push(Screen.Preview(i))
                }
                is Screen.Preview -> PreviewScreen(route, top.start, loadPhoto, onBack = ::back, onList = { nav = nav.push(Screen.Steps) })
                Screen.Navigation -> if (route.phase is RoutePhase.Results) NavigationScreen(
                    route, location, onExit = ::back, onSteps = { nav = nav.push(Screen.Steps) },
                    onReroute = { location.lastLocation?.position?.let { routeVm.setOrigin(Endpoint(it.latitude, it.longitude, Strings.MY_LOCATION)); routeVm.search() } },
                ) else Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val failed = route.phase as? RoutePhase.Failed
                    if (failed != null) Text(failed.message) else LoadingView(Strings.SEARCHING, cards = 1)
                    Button(Strings.EXIT, Modifier.fillMaxWidth(), kind = ButtonKind.Accent, onClick = ::back)
                }
                is Screen.Place -> PlaceScreen(
                    discoverVm, top.id, saved = store.saved.any { it.id == top.id },
                    onSave = { d -> store.toggleSaved(SavedPlace(d.id, d.name, d.category?.name)) }, onBack = ::back,
                ) { p -> chooseDestination(Endpoint(p.lat, p.lon, p.name), p.id) }
                is Screen.FindPlace -> SearchScreen(
                    search = { q -> sharedApi.places(category = null, query = q, cursor = null, limit = 10).places },
                    geocode = { q -> sharedApi.geocode(q, near) },
                    onBack = ::back,
                    onPick = { p ->
                        val plan = top.planId
                        if (plan != null) { addToPlan(plan, PlanItem(p.name, p.category?.name ?: Strings.PLACE, 30, null, p.lat, p.lon)); nav = nav.pop() } else nav = nav.pop().openPlace(p.id)
                    },
                    onPickAddress = { a ->
                        val plan = top.planId
                        if (plan != null) { addToPlan(plan, PlanItem(a.name, a.detail ?: Strings.ADDRESS, 30, null, a.lat, a.lon)); nav = nav.pop() } else chooseDestination(Endpoint(a.lat, a.lon, a.name), null)
                    },
                    onPickOnMap = { nav = nav.push(Screen.PickDestination) },
                    forPlaces = true,
                )
                is Screen.Region -> RegionGuideScreen(
                    top.id, onBack = ::back, onHighlight = ::openByName, onDish = { nav = nav.push(Screen.Dish(it)) },
                    onTrip = { nav = nav.push(Screen.Trip(it)) },
                    onPlan = { val p = store.newPlan(); store.put(p); nav = nav.push(Screen.PlanEdit(p.id)) },
                    onRoute = { nav = nav.push(Screen.Search) }, onShowMap = { nav = nav.select(Tab.Map) },
                )
                is Screen.Dish -> DishScreen(top.id, onBack = ::back, onShowMap = { nav = nav.select(Tab.Map) }) { d ->
                    addToPlan(MOCK_PLANS.first().id, PlanItem("${Strings.EAT}: ${d.name.lowercase()}", d.shops.first().name, 40))
                    nav = nav.push(Screen.PlanEdit(MOCK_PLANS.first().id))
                }
                is Screen.Trip -> TripScreen(
                    top.id, onBack = ::back,
                    onAddStop = {
                        val t = mockTrip(top.id)
                        val p = store.newPlan().copy(name = t.title, items = t.stops.map { PlanItem(it.name, it.note, it.stayMin, it.move, it.lat, it.lon) })
                        store.put(p)
                        nav = nav.push(Screen.PlanEdit(p.id)).push(Screen.FindPlace(p.id))
                    },
                ) { e -> chooseDestination(e, null) }
                is Screen.PlanEdit -> {
                    val plan = store.plan(top.id)
                    if (plan == null) LaunchedEffect(Unit) { nav = nav.pop() } else PlanBuilderScreen(
                        plan, onChange = store::put, onBack = ::back, onAdd = { nav = nav.push(Screen.FindPlace(plan.id)) },
                        onSave = {
                            store.put(if (plan.status == PlanStatus.Draft && plan.routing) plan.copy(status = PlanStatus.Upcoming) else plan)
                            noticeText = Strings.SAVED_TO_PROFILE
                            nav = nav.pop()
                        },
                        onStart = { e -> chooseDestination(e, null) },
                    )
                }
                Screen.PortfolioEdit -> PortfolioEditScreen(onBack = ::back)
                Screen.Settings -> SettingsScreen(onBack = ::back)
                null -> when (nav.tab) {
                    Tab.Map -> MapHome(
                        route, location, nearby, loadPhoto = loadPhoto,
                        loadPlaces = { south, west, north, east, slugs ->
                            try { sharedApi.placesInBounds(south, west, north, east, slugs, limit = MAP_PLACES_LIMIT).map { it.copy(distanceM = metersBetween(near.lat, near.lon, it.lat, it.lon)) } } catch (e: ApiError) { null }
                        },
                        onSearch = { nav = nav.push(Screen.FindPlace()) }, onLocate = ::useMyLocation,
                        onOpenPlace = { p -> nav = nav.openPlace(p.id) }, onSeeAll = { nav = nav.select(Tab.Discover) },
                    )
                    Tab.Discover -> DiscoverScreen(
                        discoverVm, placesListState, onOpen = { p -> nav = nav.openPlace(p.id) }, nearby = nearby,
                        onRegion = { nav = nav.push(Screen.Region(it)) }, onTrip = { nav = nav.push(Screen.Trip(it)) }, onShowMap = { nav = nav.select(Tab.Map) },
                    )
                    Tab.Journey -> JourneyScreen(route.recentDestinations, onRoute = { e -> chooseDestination(e, null) }) { nav = nav.push(Screen.Trip(it)) }
                    Tab.Profile -> ProfileScreen(
                        store, onDesign = { nav = nav.push(Screen.PortfolioEdit) }, onSettings = { nav = nav.push(Screen.Settings) },
                        onPlan = { nav = nav.push(Screen.PlanEdit(it)) },
                        onNewPlan = { val p = store.newPlan(); store.put(p); nav = nav.push(Screen.PlanEdit(p.id)) },
                        onStartPlan = ::startPlan, onPlace = { nav = nav.openPlace(it) }, onTrip = { nav = nav.push(Screen.Trip(it)) },
                    )
                }
            }
        }
        if (!nav.canPop) {
            Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } }) {
                BottomNav(nav.tab, onGo = { nav = nav.push(Screen.Search) }) { nav = nav.select(it) }
                Box(Modifier.fillMaxWidth().background(Palette.Bg).windowInsetsBottomHeight(WindowInsets.navigationBars)) // solid under the gesture bar
            }
        }
    }
    // Short message for actions that only explain themselves (share, video, satellite…); read out by screen readers.
    noticeText?.let { msg ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg, Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Ink).clickable { noticeText = null }.padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = Palette.OnPrimary, size = 14.sp,
            )
        }
    }
    }
}
