package com.gowander.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.mock.MOCK_PLAN_SUGGESTIONS
import com.gowander.mock.MOCK_TRIPS
import com.gowander.mock.MoveKind
import com.gowander.mock.Plan
import com.gowander.mock.mockTrip
import com.gowander.mock.planEnd
import com.gowander.mock.planTimes
import com.gowander.mock.reorderNearest
import com.gowander.route.Endpoint
import com.gowander.ui.Badge
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.Header
import com.gowander.ui.IconButton
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.MockPhoto
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.Pill
import com.gowander.ui.Segmented
import com.gowander.ui.Strings
import com.gowander.ui.Text
import com.gowander.ui.Title
import com.gowander.ui.ExtraIcons

internal fun moveColor(k: MoveKind) = when (k) { MoveKind.Walk -> Palette.Bus; MoveKind.Bus -> Palette.Bus; MoveKind.Metro -> Palette.Metro }

/**
 * UX-2 screen 9: suggested trips (sample, mock register) filtered by how much time the user has; "Memory của tôi"
 * holds the real destinations searched in this session.
 */
@Composable
fun JourneyScreen(recent: List<Endpoint>, onRoute: (Endpoint) -> Unit, onTrip: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var budget by rememberSaveable { mutableStateOf(90) }
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title(Strings.TAB_JOURNEY)
        Segmented(listOf(Strings.SUGGESTIONS, Strings.MY_MEMORY), tab) { tab = it }
        if (tab == 1) {
            if (recent.isEmpty()) Note(Strings.RECENT_TRIPS_EMPTY)
            recent.forEach { e -> ListRow(e.label, Strings.ROUTE_AGAIN, lead = { Badge(KitIcons.clock, Palette.Subtle, Palette.Primary) }, onClick = { onRoute(e) }) }
            return@Column
        }
        Text(Strings.HOW_LONG, size = 14.sp, weight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90).forEach { m -> Pill("$m ${Strings.MINUTES_SHORT}", selected = budget == m) { budget = m } }
            Pill(Strings.ANY_TIME, selected = budget == 0) { budget = 0 }
        }
        val trips = MOCK_TRIPS.filter { budget == 0 || it.minutes <= budget }
        if (trips.isEmpty()) Note(Strings.NO_TRIP_FITS)
        trips.forEachIndexed { i, t ->
            if (i == 0 && t.editorsPick) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Palette.Line, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onTrip(t.id) },
            ) {
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    MockPhoto(t.id, Modifier.fillMaxSize(), KitIcons.landmark, radius = 0.dp, iconSize = 40.dp)
                    Text(Strings.EDITORS_PICK, Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Bg).padding(horizontal = 10.dp, vertical = 4.dp), size = 12.sp, color = Palette.Primary, weight = FontWeight.Bold)
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.title, size = 17.sp, weight = FontWeight.Bold)
                    Text(t.summary, size = 13.sp, color = Palette.Muted)
                }
            } else Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp)).clickable(role = Role.Button) { onTrip(t.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MockPhoto(t.id, Modifier.size(76.dp), KitIcons.landmark)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(t.title, size = 15.sp, weight = FontWeight.Bold)
                    Text(t.summary, size = 13.sp, color = Palette.Muted)
                }
            }
        }
        Note(Strings.TRIPS_SAMPLE_NOTE)
    }
}

/** Numbered stop: circle with the number, then the line down to the next stop (dotted on foot, solid on a vehicle). */
@Composable
private fun StopRow(n: Int, line: Color?, dotted: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.Primary), contentAlignment = Alignment.Center) { Text("$n", size = 13.sp, color = Color.White, weight = FontWeight.Bold) }
            if (line != null) Canvas(Modifier.weight(1f).width(4.dp).padding(vertical = 4.dp)) {
                val x = size.width / 2
                if (dotted) drawLine(Palette.Muted, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx())))
                else drawLine(line, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(bottom = 14.dp)) { content() }
    }
}

/** UX-2 screen 10: one suggested trip; "Bắt đầu chuyến" asks the real router for the way to the first stop. */
@Composable
fun TripScreen(id: String, onBack: () -> Unit, onAddStop: () -> Unit, onStart: (Endpoint) -> Unit) {
    val t = mockTrip(id)
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                MockPhoto(t.id, Modifier.fillMaxSize(), KitIcons.landmark, radius = 0.dp, iconSize = 40.dp)
                Box(Modifier.safeDrawingPadding().padding(16.dp)) { Header("", floating = true, onBack = onBack) }
                Text(Strings.SAMPLE_PHOTO_CREDIT, Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Ink.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp), size = 10.sp, color = Color.White)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, Modifier.semantics { heading() }, size = 22.sp, weight = FontWeight.Bold)
                Text(t.summary, size = 13.sp, color = Palette.Muted)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                t.stops.forEachIndexed { i, s ->
                    val next = t.stops.getOrNull(i + 1)?.move
                    StopRow(i + 1, next?.let { moveColor(it.kind) }, dotted = next?.kind == MoveKind.Walk) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.move?.let { Text(it.label, size = 12.sp, color = moveColor(it.kind), weight = FontWeight.SemiBold) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MockPhoto(s.name, Modifier.size(56.dp), KitIcons.landmark, radius = 12.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(s.name, size = 15.sp, weight = FontWeight.SemiBold)
                                    Text(s.note, size = 12.sp, color = Palette.Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(Strings.ADD_STOP, Modifier.weight(1f), kind = ButtonKind.Outline, icon = KitIcons.plus, compact = true, pill = true, onClick = onAddStop)
            Button(Strings.START_TRIP, Modifier.weight(1f), compact = true, pill = true) { t.stops.first().let { onStart(Endpoint(it.lat, it.lon, it.name)) } }
        }
    }
}

/**
 * UX-2 screen 16: build a plan by hand. Times follow from the start time, each stay and the travel between stops
 * (estimates); stops can be removed, added (search or suggestions) and reordered to avoid going back and forth.
 */
@Composable
fun PlanBuilderScreen(plan: Plan, onChange: (Plan) -> Unit, onBack: () -> Unit, onAdd: () -> Unit, onSave: () -> Unit, onStart: (Endpoint) -> Unit) {
    val notice = LocalNotice.current
    val times = planTimes(plan.start, plan.items)
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(KitIcons.back, Strings.BACK, tint = Palette.Ink, onClick = onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(Strings.PLAN_NAME, size = 11.sp, color = Palette.Muted)
                    BasicTextField(
                        plan.name, { onChange(plan.copy(name = it)) }, Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).semantics { contentDescription = Strings.PLAN_NAME },
                        singleLine = true, textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink),
                    )
                }
            }
            Row(Modifier.padding(start = 52.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("${plan.date} ▾", "${Strings.START_AT} ${plan.start} ▾", "${plan.mode} ▾").forEach { Pill(it) { notice(Strings.PLAN_OPTIONS_LATER) } }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (plan.items.isEmpty()) Note(Strings.PLAN_EMPTY)
            plan.items.forEachIndexed { i, item ->
                item.move?.let { if (i > 0) Text(it.label, Modifier.padding(start = 64.dp, top = 2.dp, bottom = 2.dp), size = 12.sp, color = moveColor(it.kind), weight = FontWeight.SemiBold) }
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).then(if (i == 0) Modifier.background(Palette.Subtle) else Modifier.border(1.dp, Palette.Line, shape)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(times[i], Modifier.width(48.dp), size = 13.sp, color = Palette.Primary, weight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, size = 14.sp, weight = FontWeight.Bold)
                        Text(item.note, size = 12.sp, color = Palette.Muted)
                    }
                    if (i > 0) IconButton(ExtraIcons.close, "${Strings.REMOVE_STOP}: ${item.title}", tint = Palette.Muted) { onChange(plan.copy(items = plan.items.filterIndexed { j, _ -> j != i })) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(Strings.ADD_STOP_LONG, Modifier.fillMaxWidth(), kind = ButtonKind.Outline, compact = true, pill = true, onClick = onAdd)
            Text(Strings.MORE_NEARBY, Modifier.padding(top = 8.dp), size = 13.sp, weight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOCK_PLAN_SUGGESTIONS.filter { s -> plan.items.none { it.title == s.title } }.forEach { s ->
                    Column(
                        Modifier.weight(1f).sizeIn(minHeight = 48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onChange(plan.copy(items = plan.items + s)) }.padding(10.dp),
                    ) {
                        Text(s.title, size = 13.sp, weight = FontWeight.Bold)
                        Text("+ ${s.move?.label?.lowercase() ?: ""}", size = 11.sp, color = Palette.Muted)
                    }
                }
            }
            Button(Strings.REORDER, kind = ButtonKind.Soft, compact = true, pill = true) { onChange(plan.copy(items = reorderNearest(plan.items))) }
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, Palette.Line).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Note("${plan.items.size} ${Strings.STOPS} · ${Strings.UNTIL} ${planEnd(plan.start, plan.items)} · ${Strings.TIMES_ESTIMATED}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(Strings.SAVE_TO_PROFILE, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true, onClick = onSave)
                val first = plan.items.firstOrNull { it.lat != null && it.lon != null }
                Button(Strings.START, Modifier.weight(1f), compact = true, pill = true) {
                    if (first != null && plan.routing) onStart(Endpoint(first.lat!!, first.lon!!, first.title)) else notice(Strings.NO_ROUTING_HERE)
                }
            }
        }
    }
}
