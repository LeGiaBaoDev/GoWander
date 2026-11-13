package com.gowander.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.api.PlaceDetail
import com.gowander.api.PlaceSummary
import com.gowander.ui.Action
import com.gowander.ui.Chip
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Text

/** Flow B: categories, search and list; place detail with source and a Directions button. */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler is experimental in Compose Multiplatform 1.12 (ADR-029)
@Composable
fun DiscoverScreen(vm: DiscoverViewModel, listState: LazyListState, onDirections: (PlaceDetail) -> Unit) {
    val s by vm.state.collectAsState()
    // Declared after App's handler, so it wins while a place is open: Back closes the detail first.
    BackHandler(enabled = s.detail != DetailState.None, onBack = vm::closePlace)
    when (val d = s.detail) {
        is DetailState.Loaded -> PlaceDetailView(d.place, onBack = vm::closePlace) { onDirections(d.place) }
        is DetailState.Loading -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Chip(Strings.BACK, onClick = vm::closePlace)
            Text(Strings.LOADING, Modifier.padding(top = 16.dp))
        }
        is DetailState.Failed -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Chip(Strings.BACK, onClick = vm::closePlace)
            Text(d.message, Modifier.padding(vertical = 16.dp), color = Palette.Pin, bold = true)
            Action(Strings.RETRY) { vm.openPlace(d.id) }
        }
        DetailState.None -> PlaceList(s, vm, listState)
    }
}

@Composable
private fun PlaceList(s: DiscoverUiState, vm: DiscoverViewModel, listState: LazyListState) {
    var text by rememberSaveable { mutableStateOf(s.query ?: "") }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = Palette.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitQuery(text) }),
                modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp)
                    .border(1.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(12.dp)
                    .semantics { contentDescription = Strings.SEARCH_PLACES },
                decorationBox = { inner -> if (text.isEmpty()) Text(Strings.SEARCH_PLACES, color = Palette.Muted, size = 16.sp); inner() },
            )
            Spacer(Modifier.width(8.dp))
            Action(Strings.SEARCH) { vm.submitQuery(text) }
        }
        if (text.isNotBlank() && text.trim().length < 2) Text(Strings.SEARCH_MIN, Modifier.padding(top = 4.dp), color = Palette.Muted, size = 13.sp)

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState())) {
            Chip(Strings.ALL, selected = s.selectedCategory == null) { vm.selectCategory(null) }
            s.categories.forEach { c ->
                Spacer(Modifier.width(6.dp))
                Chip("${c.name} (${c.placeCount})", selected = s.selectedCategory == c.slug) { vm.selectCategory(c.slug) }
            }
        }

        when (val p = s.phase) {
            ListPhase.Loading -> Text(Strings.LOADING, Modifier.padding(16.dp))
            is ListPhase.Failed -> Column(Modifier.padding(16.dp)) {
                Text(p.message, color = Palette.Pin, bold = true)
                Action(Strings.RETRY, Modifier.padding(top = 8.dp), onClick = vm::retry)
            }
            ListPhase.Ready -> if (s.places.isEmpty()) Text(Strings.EMPTY_PLACES, Modifier.padding(16.dp)) else LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(s.places, key = { it.id }) { PlaceCard(it) { vm.openPlace(it.id) } }
                item {
                    when {
                        s.loadingMore -> Text(Strings.LOADING, Modifier.padding(16.dp))
                        s.moreError != null -> Column(Modifier.padding(vertical = 8.dp)) {
                            Text(s.moreError, color = Palette.Pin)
                            Action(Strings.RETRY, Modifier.padding(top = 6.dp), onClick = vm::loadMore)
                        }
                        s.nextCursor != null -> Chip(Strings.LOAD_MORE, modifier = Modifier.padding(vertical = 8.dp), onClick = vm::loadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(p: PlaceSummary, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
    ) {
        Text(p.name, bold = true, size = 16.sp)
        p.nameEn?.let { Text(it, color = Palette.Muted) }
        Text(listOfNotNull(p.category?.name, p.address).joinToString(" · "), color = Palette.Muted, size = 13.sp)
    }
}

@Composable
private fun PlaceDetailView(p: PlaceDetail, onBack: () -> Unit, onDirections: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Chip(Strings.BACK, onClick = onBack)
        Text(p.name, Modifier.padding(top = 16.dp), size = 22.sp, bold = true)
        p.nameEn?.let { Text(it, color = Palette.Muted, size = 16.sp) }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
            p.categories.forEach { c ->
                Text(c.name, Modifier.padding(end = 6.dp).background(Palette.Warn, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        p.address?.let { Text("${Strings.ADDRESS}: $it", Modifier.padding(top = 12.dp)) }
        Text(p.shortDescription ?: Strings.NO_DESCRIPTION, Modifier.padding(top = 12.dp), color = if (p.shortDescription == null) Palette.Muted else Palette.Ink)
        p.foods.forEach { f ->
            val price = listOfNotNull(f.priceMin, f.priceMax).joinToString("–") { it.toLong().toString() }
            Text("• ${f.name}" + if (price.isNotEmpty()) " ($price ${f.currency})" else "", Modifier.padding(top = 4.dp))
        }
        Action(Strings.DIRECTIONS, Modifier.padding(top = 20.dp).fillMaxWidth(), onClick = onDirections)
        // Licence and attribution must stay visible next to the data (ADR-028).
        Text("${Strings.SOURCE}: ${p.source.attribution} · ${Strings.LICENSE}: ${p.source.license}", Modifier.padding(top = 20.dp), color = Palette.Muted, size = 13.sp)
        p.source.url?.let { Text(it, color = Palette.Muted, size = 12.sp) }
    }
}
