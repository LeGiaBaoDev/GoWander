package com.gowander.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import com.gowander.home.LocalNotice
import com.gowander.mock.MOCK_RATING_SPLIT
import com.gowander.mock.MOCK_REVIEWS
import com.gowander.content.Guide
import com.gowander.content.GUIDE_TRIPS
import com.gowander.mock.mockRating
import com.gowander.mock.mockReviewCount
import com.gowander.route.Endpoint
import com.gowander.route.RouteMap
import com.gowander.ui.ExtraIcons
import com.gowander.ui.IconButton
import com.gowander.ui.ListRow
import com.gowander.ui.MockPhoto
import com.gowander.ui.Pill
import com.gowander.ui.Segmented
import com.gowander.ui.Stars
import com.gowander.ui.kitShadow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.api.PlaceDetail
import com.gowander.api.PlaceSummary
import com.gowander.api.PopularPlace
import com.gowander.route.PlaceRow
import com.gowander.route.categoryIcon
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Card
import com.gowander.ui.Chip
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.PhotoTile
import com.gowander.ui.Strings
import com.gowander.ui.Text
import com.gowander.ui.Title

/** D1 Discover list (kit). The place page is its own screen ([PlaceScreen]) so any tab can open it (UX-2). */
@Composable
fun DiscoverScreen(
    vm: DiscoverViewModel,
    listState: LazyListState,
    onOpen: (PlaceSummary) -> Unit,
    onOpenId: (String) -> Unit = {},
    nearby: List<PlaceSummary> = emptyList(),
    onRegion: (String) -> Unit = {},
    onTrip: (String) -> Unit = {},
    onShowMap: () -> Unit = {},
) {
    val s by vm.state.collectAsState()
    // UX-2 screens 8 and 13: "Gần bạn" list or the nationwide region map, one switch at the top.
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title(Strings.TAB_DISCOVER_TITLE)
            Segmented(listOf(Strings.NEAR_YOU_TAB, Strings.VIETNAM_PLACES), tab) { tab = it }
        }
        if (tab == 1) RegionsMap(Modifier.weight(1f).padding(top = 12.dp), onRegion)
        else PlaceList(s, vm, listState, onOpen, onOpenId, nearby, onTrip, onShowMap)
    }
}

/** D2 place page for [id]; Back and Directions are handled by the caller's back stack. */
@Composable
fun PlaceScreen(vm: DiscoverViewModel, id: String, saved: Boolean = false, onSave: (PlaceDetail) -> Unit = {}, onBack: () -> Unit, onDirections: (PlaceDetail) -> Unit) {
    val s by vm.state.collectAsState()
    // One detail slot in the ViewModel: (re)load when this screen shows a different place than the slot holds.
    LaunchedEffect(id) {
        val d = vm.state.value.detail
        val holds = (d is DetailState.Loaded && d.place.id == id) || (d is DetailState.Loading && d.id == id)
        if (!holds) vm.openPlace(id) // coming back to the page (Back from the route) does not download it again
    }
    when (val d = s.detail) {
        is DetailState.Loaded -> if (d.place.id == id) PlaceDetailView(d.place, saved, { onSave(d.place) }, s.popular.firstOrNull { it.id == id }?.rank, onBack = onBack) { onDirections(d.place) } else PlaceLoading(onBack)
        is DetailState.Failed -> if (d.id != id) PlaceLoading(onBack) else Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header(Strings.PLACE, onBack = onBack)
            Text(d.message, color = Palette.Accent, bold = true)
            Button(Strings.RETRY, Modifier.fillMaxWidth()) { vm.openPlace(d.id) }
        }
        else -> PlaceLoading(onBack)
    }
}

@Composable
private fun PlaceLoading(onBack: () -> Unit) = Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(20.dp)) {
    Header(Strings.PLACE, onBack = onBack)
    Text(Strings.LOADING, Modifier.padding(top = 24.dp))
}

@Composable
private fun PlaceList(
    s: DiscoverUiState, vm: DiscoverViewModel, listState: LazyListState, onOpen: (PlaceSummary) -> Unit, onOpenId: (String) -> Unit,
    nearby: List<PlaceSummary>, onTrip: (String) -> Unit, onShowMap: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(s.query ?: "") }
    val browsing = s.query == null && s.selectedCategory == null
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            // Searching starts from 2 characters (API minimum).
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Subtle).border(1.dp, Palette.Line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KitIcons.search, 20.dp, Palette.Muted)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    text, { text = it }, Modifier.weight(1f).semantics { contentDescription = Strings.SEARCH_PLACES },
                    singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = Palette.Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submitQuery(text) }),
                    decorationBox = { inner -> if (text.isEmpty()) Text(Strings.SEARCH_PLACES_HINT, color = Palette.Muted, maxLines = 1); inner() },
                )
            }
            if (text.isNotBlank() && text.trim().length < 2) Note(Strings.SEARCH_MIN, Modifier.padding(top = 4.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(Strings.ALL, selected = s.selectedCategory == null) { vm.selectCategory(null) }
                s.categories.forEach { c -> Pill(c.name, selected = s.selectedCategory == c.slug) { vm.selectCategory(c.slug) } }
            }
        }
        if (browsing) item {
            val trip = GUIDE_TRIPS.first()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Soft).clickable(role = Role.Button) { onTrip(trip.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MockPhoto(trip.id, Modifier.size(72.dp), KitIcons.landmark)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(Strings.SUGGESTED_TRIP, size = 12.sp, color = Palette.Primary, weight = FontWeight.Bold)
                    Text(trip.title, size = 15.sp, weight = FontWeight.Bold)
                    Text(trip.summary, size = 12.sp, color = Palette.Muted)
                }
            }
        }
        // UX-2 screen 19 (owner, 16-11-2026): ranks only, never counts; hidden until the API has enough real use.
        if (browsing && s.popular.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(Strings.POPULAR, Modifier.weight(1f).semantics { heading() }, size = 17.sp, weight = FontWeight.Bold)
                    Text(Strings.POPULAR_SPAN, size = 12.sp, color = Palette.Muted)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.popular.forEach { PopularCard(it) { onOpenId(it.id) } }
                }
            }
        }
        if (browsing && nearby.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.NEAR_YOU_TAB, Modifier.weight(1f).semantics { heading() }, size = 17.sp, weight = FontWeight.Bold)
                    Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onShowMap), contentAlignment = Alignment.CenterEnd) {
                        Text(Strings.SHOW_ON_MAP, size = 13.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
                    }
                }
            }
            items(nearby.take(5), key = { "near-" + it.id }) { DiscoverRow(it) { onOpen(it) } }
            item { Text(Strings.ALL_PLACES, Modifier.padding(top = 8.dp).semantics { heading() }, size = 17.sp, weight = FontWeight.Bold) }
        }
        item {
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when (val p = s.phase) {
                    ListPhase.Loading -> Text(Strings.LOADING, Modifier.padding(vertical = 16.dp))
                    is ListPhase.Failed -> Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.message, color = Palette.Accent, bold = true)
                        Button(Strings.RETRY, Modifier.fillMaxWidth(), onClick = vm::retry)
                    }
                    ListPhase.Ready -> if (s.places.isEmpty()) Text(Strings.EMPTY_PLACES, Modifier.padding(vertical = 16.dp))
                }
            }
        }
        if (s.phase == ListPhase.Ready) items(s.places, key = { it.id }) { DiscoverRow(it) { onOpen(it) } }
        item {
            when {
                s.loadingMore -> Text(Strings.LOADING, Modifier.padding(vertical = 16.dp))
                s.moreError != null -> Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.moreError, color = Palette.Accent)
                    Button(Strings.RETRY, Modifier.fillMaxWidth(), onClick = vm::loadMore)
                }
                s.nextCursor != null -> Button(Strings.LOAD_MORE, Modifier.fillMaxWidth().padding(vertical = 8.dp), ButtonKind.Secondary, onClick = vm::loadMore)
            }
        }
        val story = Guide.stories.firstOrNull()
        if (browsing && story != null) item {
            // First story of the guide; the full text is read in the region guide.
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Strings.STORIES, Modifier.semantics { heading() }, size = 17.sp, weight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp))) {
                    MockPhoto(story.id, Modifier.fillMaxWidth().height(120.dp), KitIcons.book, radius = 0.dp)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(story.title, size = 15.sp, weight = FontWeight.Bold)
                        Text(Strings.readMinutes(story.minutes), size = 12.sp, color = Palette.Muted)
                    }
                }
            }
        }
    }
}

/** Screen 19 card: photo with the rank on it, name and category; read out as one button. */
@Composable
private fun PopularCard(p: PopularPlace, onClick: () -> Unit) {
    Column(
        Modifier.width(132.dp).clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = Strings.popularCard(p.rank, p.name, p.category?.name); role = Role.Button },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            MockPhoto(p.id, Modifier.size(132.dp, 96.dp), categoryIcon(p.category?.slug))
            Text(
                "#${p.rank}", Modifier.padding(8.dp).clip(RoundedCornerShape(11.dp)).background(Palette.Ink).padding(horizontal = 8.dp, vertical = 2.dp),
                size = 12.sp, color = Palette.Bg, weight = FontWeight.Bold,
            )
        }
        Text(p.name, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
        p.category?.name?.let { Text(it, size = 12.sp, color = Palette.Muted, maxLines = 1) }
    }
}

/** Screen 8 row: 64 dp photo, name, category · sample stars, and how far on foot when the distance is known. */
@Composable
private fun DiscoverRow(p: PlaceSummary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MockPhoto(p.id, Modifier.size(64.dp), categoryIcon(p.category?.slug))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                p.category?.name?.let { Text("$it · ", size = 12.sp, color = Palette.Muted) }
                Stars(mockRating(p.id))
            }
            // ~80 m a minute on foot.
            p.distanceM?.let { Text("${Strings.WALK} ${((it / 80).toInt() + 1)} ${Strings.MINUTES_SHORT} ${Strings.FROM_YOU}", size = 12.sp, color = Palette.Bus, weight = FontWeight.SemiBold) }
        }
    }
}

/**
 * UX-2 screen 2: the place on a small map, then a sheet with name, sample stars, Chỉ đường · Lưu · Chia sẻ, photos and
 * three tabs. Stars, reviews and photos are sample data (PIPELINE §5c mock register); the overview is real (OSM).
 */
@Composable
private fun PlaceDetailView(p: PlaceDetail, saved: Boolean, onSave: () -> Unit, popularRank: Int?, onBack: () -> Unit, onDirections: () -> Unit) {
    val notice = LocalNotice.current
    var tab by rememberSaveable(p.id) { mutableStateOf(0) }
    val rating = mockRating(p.id)
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        RouteMap(null, null, Endpoint(p.lat, p.lon, p.name), null, Modifier.fillMaxWidth().height(260.dp), insets = PaddingValues(top = 60.dp))
        Box(Modifier.safeDrawingPadding().padding(16.dp)) { Header("", floating = true, onBack = onBack) }
        val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier.fillMaxSize().padding(top = 230.dp).kitShadow(sheet).clip(sheet).background(Palette.Bg)
                .verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // UX-2 screen 20: this week's rank, when the place is among the most searched-for.
                popularRank?.let {
                    Row(
                        Modifier.clip(RoundedCornerShape(15.dp)).background(Palette.Warn).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(ExtraIcons.trend, 16.dp, Palette.Amber)
                        Text(Strings.popularBadge(it), size = 13.sp, color = Palette.Amber, weight = FontWeight.Bold)
                    }
                }
                Text(p.name, Modifier.semantics { heading() }, size = 22.sp, weight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stars(rating, size = 13)
                    Text(" (${mockReviewCount(p.id)} ${Strings.SAMPLE_REVIEWS})${p.category?.name?.let { " · $it" } ?: ""}", size = 13.sp, color = Palette.Muted, maxLines = 1)
                }
            }
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(Strings.DIRECTIONS, Modifier.weight(1f), icon = KitIcons.route, pill = true, onClick = onDirections)
                IconButton(KitIcons.bookmark, if (saved) Strings.UNSAVE_PLACE else Strings.SAVE_PLACE, Modifier.border(1.dp, if (saved) Palette.Primary else Palette.Line, CircleShape).background(if (saved) Palette.Soft else Palette.Bg, CircleShape), onClick = onSave)
                IconButton(ExtraIcons.share, Strings.SHARE) { notice(Strings.SHARE_LATER) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MockPhoto(p.id, Modifier.size(196.dp, 128.dp), categoryIcon(p.category?.slug))
                MockPhoto(p.id + "2", Modifier.size(150.dp, 128.dp), KitIcons.camera)
            }
            Note(Strings.SAMPLE_PHOTOS, Modifier.padding(horizontal = 20.dp))
            UnderlineTabs(listOf(Strings.OVERVIEW, Strings.REVIEWS, Strings.DISHES), tab) { tab = it }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> {
                        p.address?.let { Text("${Strings.ADDRESS}: $it") }
                        Text(p.shortDescription ?: Strings.NO_DESCRIPTION, color = if (p.shortDescription == null) Palette.Muted else Palette.Ink)
                        if (p.categories.isNotEmpty()) Note(p.categories.joinToString(" · ") { it.name })
                        // Licence and attribution stay visible next to the data (ADR-028).
                        Note("${Strings.SOURCE}: ${p.source.attribution} · ${Strings.LICENSE}: ${p.source.license}")
                    }
                    1 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(rating.toString().replace('.', ','), size = 36.sp, weight = FontWeight.Bold)
                                Text("★★★★★", size = 13.sp, color = Palette.Amber)
                                Text("${mockReviewCount(p.id)} ${Strings.TIMES}", size = 11.sp, color = Palette.Muted)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MOCK_RATING_SPLIT.forEachIndexed { i, pct ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${5 - i}", size = 11.sp, color = Palette.Muted)
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) {
                                            Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Primary))
                                        }
                                    }
                                }
                            }
                        }
                        Button(Strings.WRITE_REVIEW, Modifier.fillMaxWidth(), kind = ButtonKind.Outline, icon = KitIcons.pen, pill = true) { notice(Strings.REVIEW_LATER) }
                        MOCK_REVIEWS.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Palette.Soft), contentAlignment = Alignment.Center) { Text(r.initial, size = 14.sp, weight = FontWeight.Bold, color = Palette.Primary) }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(Strings.SAMPLE_USER, size = 13.sp, weight = FontWeight.SemiBold)
                                    Text("${"★".repeat(r.stars)} · ${r.ago}", size = 11.sp, color = Palette.Amber)
                                }
                            }
                            Text(r.text, size = 13.sp)
                        }
                    }
                    else -> if (p.foods.isEmpty()) Note(Strings.NO_DISHES) else p.foods.forEach { f ->
                        val price = listOfNotNull(f.priceMin, f.priceMax).joinToString("–") { it.toLong().toString() }
                        ListRow(f.name, if (price.isNotEmpty()) "$price ${f.currency}" else null, lead = { MockPhoto(f.name, Modifier.size(44.dp), KitIcons.star, radius = 10.dp, iconSize = 18.dp) })
                    }
                }
            }
        }
    }
}

/** Text tabs with a 3 dp underline under the chosen one (screen 2). */
@Composable
private fun UnderlineTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items.forEachIndexed { i, label ->
                val on = i == selected
                Column(Modifier.width(IntrinsicSize.Max).sizeIn(minHeight = 48.dp).semantics { this.selected = on }.clickable(role = Role.Tab) { onSelect(i) }, verticalArrangement = Arrangement.Bottom) {
                    Text(label, Modifier.padding(vertical = 10.dp), size = 14.sp, color = if (on) Palette.Primary else Palette.Muted, weight = if (on) FontWeight.Bold else FontWeight.Medium)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) Palette.Primary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
    }
}
