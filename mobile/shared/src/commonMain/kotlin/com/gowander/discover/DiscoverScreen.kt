package com.gowander.discover

import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
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

/** D1 Discover list and D2 place detail (kit). Directions sends the place to route planning (Flow B). */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler is experimental in Compose Multiplatform 1.12 (ADR-029)
@Composable
fun DiscoverScreen(vm: DiscoverViewModel, listState: LazyListState, onDirections: (PlaceDetail) -> Unit) {
    val s by vm.state.collectAsState()
    // Declared after App's handlers, so it wins while a place is open: Back closes the detail first.
    BackHandler(enabled = s.detail != DetailState.None, onBack = vm::closePlace)
    when (val d = s.detail) {
        is DetailState.Loaded -> PlaceDetailView(d.place, onBack = vm::closePlace) { onDirections(d.place) }
        is DetailState.Loading -> Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(20.dp)) {
            Header(Strings.PLACE, onBack = vm::closePlace)
            Text(Strings.LOADING, Modifier.padding(top = 24.dp))
        }
        is DetailState.Failed -> Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header(Strings.PLACE, onBack = vm::closePlace)
            Text(d.message, color = Palette.Accent, bold = true)
            Button(Strings.RETRY, Modifier.fillMaxWidth()) { vm.openPlace(d.id) }
        }
        DetailState.None -> PlaceList(s, vm, listState)
    }
}

@Composable
private fun PlaceList(s: DiscoverUiState, vm: DiscoverViewModel, listState: LazyListState) {
    var text by rememberSaveable { mutableStateOf(s.query ?: "") }
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(horizontal = 20.dp)) {
        Title(Strings.TAB_DISCOVER_TITLE, Modifier.padding(top = 20.dp, bottom = 12.dp))
        // Kit D1 search: subtle fill, radius 16. Searching starts from 2 characters (API minimum).
        Row(
            Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Subtle).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KitIcons.search, 18.dp, Palette.Muted)
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
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).horizontalScroll(rememberScrollState())) {
            Chip(Strings.ALL, selected = s.selectedCategory == null) { vm.selectCategory(null) }
            s.categories.forEach { c ->
                Spacer(Modifier.width(8.dp))
                Chip("${c.name} (${c.placeCount})", selected = s.selectedCategory == c.slug) { vm.selectCategory(c.slug) }
            }
        }
        Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when (val p = s.phase) {
                ListPhase.Loading -> Text(Strings.LOADING, Modifier.padding(vertical = 16.dp))
                is ListPhase.Failed -> Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(p.message, color = Palette.Accent, bold = true)
                    Button(Strings.RETRY, Modifier.fillMaxWidth(), onClick = vm::retry)
                }
                ListPhase.Ready -> if (s.places.isEmpty()) Text(Strings.EMPTY_PLACES, Modifier.padding(vertical = 16.dp)) else LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(s.places, key = { it.id }) { PlaceRow(it) { vm.openPlace(it.id) } }
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
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailView(p: PlaceDetail, onBack: () -> Unit, onDirections: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(Strings.PLACE, onBack = onBack)
        // Kit D2 hero photo; the data has no photos yet, so the wireframe tile stands in.
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Soft), contentAlignment = Alignment.Center) {
            Icon(categoryIcon(p.category?.slug), 40.dp, Palette.Primary)
        }
        Title(p.name, size = 24.sp)
        p.nameEn?.let { Text(it, color = Palette.Muted) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            p.categories.forEach { c ->
                Text(c.name, Modifier.padding(end = 8.dp).clip(RoundedCornerShape(999.dp)).background(Palette.Subtle).padding(horizontal = 14.dp, vertical = 8.dp), size = 14.sp, weight = FontWeight.Medium)
            }
        }
        p.address?.let { Text("${Strings.ADDRESS}: $it") }
        Text(p.shortDescription ?: Strings.NO_DESCRIPTION, color = if (p.shortDescription == null) Palette.Muted else Palette.Ink)
        if (p.foods.isNotEmpty()) Card(Modifier.fillMaxWidth(), bg = Palette.Subtle, gap = 4.dp) {
            p.foods.forEach { f ->
                val price = listOfNotNull(f.priceMin, f.priceMax).joinToString("–") { it.toLong().toString() }
                Text(f.name + if (price.isNotEmpty()) " · $price ${f.currency}" else "")
            }
        }
        Button(Strings.DIRECTIONS, Modifier.fillMaxWidth(), icon = KitIcons.route, onClick = onDirections)
        // Licence and attribution must stay visible next to the data (ADR-028); kit shows photo credits the same way.
        Note("${Strings.SOURCE}: ${p.source.attribution} · ${Strings.LICENSE}: ${p.source.license}")
        p.source.url?.let { Text(it, size = 11.sp, color = Palette.Muted) }
    }
}
