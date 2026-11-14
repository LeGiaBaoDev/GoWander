package com.gowander.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gowander.route.Endpoint
import com.gowander.ui.Badge
import com.gowander.ui.Card
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.SectionTitle
import com.gowander.ui.Strings
import com.gowander.ui.Text
import com.gowander.ui.Title

/**
 * UX-2 P1 (ADR-034): the Journey tab before suggested trips and the planner exist (P5). Only real content:
 * the destinations searched in this session, one tap from their routes again.
 */
@Composable
fun JourneyScreen(recent: List<Endpoint>, onRoute: (Endpoint) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title(Strings.TAB_JOURNEY)
        SectionTitle(Strings.RECENT_TRIPS)
        if (recent.isEmpty()) Note(Strings.RECENT_TRIPS_EMPTY)
        recent.forEach { e -> ListRow(e.label, Strings.ROUTE_AGAIN, lead = { Badge(KitIcons.clock, Palette.Subtle, Palette.Primary) }, onClick = { onRoute(e) }) }
    }
}

/**
 * UX-2 P1: the Profile tab before accounts exist (ADR-022, COM-1). Guest mode, and the data sources with their
 * licences, which the app must show anyway (OSM ODbL attribution).
 */
@Composable
fun ProfileScreen() {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title(Strings.TAB_PROFILE)
        Card(gap = 6.dp) {
            Text(Strings.GUEST, bold = true)
            Text(Strings.GUEST_NOTE, color = Palette.Muted)
        }
        SectionTitle(Strings.DATA_SOURCES)
        Strings.SOURCES.forEach { (what, source) -> ListRow(what, source, lead = { Badge(KitIcons.globe, Palette.Subtle, Palette.Primary) }) }
    }
}
