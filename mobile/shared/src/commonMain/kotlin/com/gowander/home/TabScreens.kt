package com.gowander.home

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import com.gowander.content.openingHours
import com.gowander.mock.Move
import com.gowander.mock.PlanItem
import com.gowander.mock.PlanStatus
import com.gowander.mock.spot
import com.gowander.mock.staleLegs
import com.gowander.mock.nextStop
import com.gowander.mock.walkTotalM
import com.gowander.route.distanceLabel
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
import com.gowander.content.GUIDE_TRIPS
import com.gowander.content.GUIDE_TRIPS_NOTE
import com.gowander.mock.MoveKind
import com.gowander.mock.Plan
import com.gowander.content.guideTrip
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
fun JourneyScreen(
    store: AppStore, recent: List<Endpoint>, onRoute: (Endpoint) -> Unit, onPlan: (String) -> Unit, onNewPlan: () -> Unit, onStartPlan: (Plan) -> Unit,
    onTrip: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var budget by rememberSaveable { mutableStateOf(90) }
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title(Strings.TAB_JOURNEY)
        Segmented(listOf(Strings.MINE, Strings.SUGGESTIONS), tab) { tab = it }
        if (tab == 0) {
            MyPlans(store, onPlan, onNewPlan, onStartPlan)
            Text(Strings.RECENT_ROUTES, Modifier.padding(top = 8.dp).semantics { heading() }, size = 15.sp, color = Palette.Muted, weight = FontWeight.Bold)
            if (recent.isEmpty()) Note(Strings.RECENT_TRIPS_EMPTY)
            recent.forEach { e -> ListRow(e.label, Strings.ROUTE_AGAIN, lead = { Badge(KitIcons.clock, Palette.Subtle, Palette.Primary) }, onClick = { onRoute(e) }) }
            return@Column
        }
        // "How long do you have?" only works with trips whose duration is known; the current ones have none yet.
        if (GUIDE_TRIPS.any { it.minutes != null }) {
            Text(Strings.HOW_LONG, size = 14.sp, weight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { m -> Pill("$m ${Strings.MINUTES_SHORT}", selected = budget == m) { budget = m } }
                Pill(Strings.ANY_TIME, selected = budget == 0) { budget = 0 }
            }
        }
        val trips = GUIDE_TRIPS.filter { budget == 0 || it.minutes == null || it.minutes <= budget }
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
        Note(GUIDE_TRIPS_NOTE)
    }
}

/**
 * The user's plans (UX-2 screen 17, in the Journey tab since 15-11-2026): the ones being followed first, with the
 * next stop and a button to go on; then upcoming, drafts and done.
 */
@Composable
private fun MyPlans(store: AppStore, onPlan: (String) -> Unit, onNewPlan: () -> Unit, onStart: (Plan) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val under = store.plans.filter { it.status != PlanStatus.Done && (it.going || it.next > 0) && it.nextStop() != null }
        val groups = listOf(
            Strings.GOING to under, Strings.UPCOMING to store.plans.filter { it.status == PlanStatus.Upcoming && it !in under },
            Strings.DRAFTS to store.plans.filter { it.status == PlanStatus.Draft && it !in under }, Strings.DONE to store.plans.filter { it.status == PlanStatus.Done },
        )
        if (store.plans.isEmpty()) Note(Strings.NO_PLANS)
        groups.forEach { (title, list) ->
            if (list.isEmpty()) return@forEach
            Text(title, Modifier.semantics { heading() }, size = 15.sp, color = Palette.Muted, weight = FontWeight.Bold)
            list.forEach { p ->
                val following = p in under
                val meta = when {
                    following -> "${Strings.NEXT_STOP}: ${p.items[p.nextStop()!!].title}"
                    p.status == PlanStatus.Upcoming -> "${p.date} · ${p.items.size} ${Strings.STOPS} · ${p.start} – ${planEnd(p.start, p.items)}"
                    p.status == PlanStatus.Draft -> "${p.items.size} ${Strings.STOPS}${if (!p.routing) " · ${Strings.GUIDE_ONLY}" else ""}"
                    else -> p.date
                }
                if (following || p.status == PlanStatus.Upcoming) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Soft).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MockPhoto(p.id, Modifier.size(60.dp), KitIcons.landmark)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, size = 16.sp, weight = FontWeight.Bold)
                            Text(meta, size = 12.sp, color = Palette.Body)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(Strings.OPEN_PLAN, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true) { onPlan(p.id) }
                        Button(if (following) Strings.CONTINUE else Strings.START, Modifier.weight(1f), compact = true, pill = true) { onStart(p) }
                    }
                } else ListRow(p.name, meta, lead = { MockPhoto(p.id, Modifier.size(52.dp), KitIcons.landmark, radius = 12.dp) }, onClick = { onPlan(p.id) })
            }
        }
        Button(Strings.NEW_PLAN, Modifier.fillMaxWidth(), kind = ButtonKind.Outline, icon = KitIcons.plus, pill = true, onClick = onNewPlan)
    }
}

/** Numbered stop: circle with the number, then the line down to the next stop (dotted on foot, solid on a vehicle). */
@Composable
private fun StopRow(n: Int, line: Color?, dotted: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.Primary), contentAlignment = Alignment.Center) { Text("$n", size = 13.sp, color = Palette.OnPrimary, weight = FontWeight.Bold) }
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

/** UX-2 screen 10: one suggested trip; "Bắt đầu chuyến" turns it into a plan the user follows stop by stop. */
@Composable
fun TripScreen(id: String, onBack: () -> Unit, onAddStop: () -> Unit, onStart: () -> Unit) {
    val t = guideTrip(id)
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                MockPhoto(t.id, Modifier.fillMaxSize(), KitIcons.landmark, radius = 0.dp, iconSize = 40.dp)
                Box(Modifier.safeDrawingPadding().padding(16.dp)) { Header("", floating = true, onBack = onBack) }
                Text(Strings.SAMPLE_PHOTO_CREDIT, Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Deep.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp), size = 10.sp, color = Color.White)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, Modifier.semantics { heading() }, size = 22.sp, weight = FontWeight.Bold)
                Text(t.summary, size = 13.sp, color = Palette.Muted)
                Text(t.intro, size = 14.sp, color = Palette.Body)
                Note(t.caution)
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
            Button(Strings.START_TRIP, Modifier.weight(1f), compact = true, pill = true, onClick = onStart)
        }
    }
}

/**
 * UX-2 screen 16: build a plan by hand and follow it. Travel between two stops is asked from the real router
 * ([measure]) whenever the stop before changes; times follow from the start time, each stay and that travel
 * (estimates). A row opens to change the stay, write a note or get directions to that stop. "Đi tới điểm kế tiếp"
 * follows the plan stop by stop and the progress is kept with the plan.
 */
@Composable
fun PlanBuilderScreen(
    plan: Plan, onChange: (Plan) -> Unit, onBack: () -> Unit, onAdd: () -> Unit, onSave: () -> Unit,
    measure: suspend (PlanItem, PlanItem) -> Move?, onGo: (Plan) -> Unit, onRoute: (Endpoint) -> Unit,
) {
    val notice = LocalNotice.current
    val times = planTimes(plan.start, plan.items)
    val current by rememberUpdatedState(plan)
    // Legs the router could not answer this time: not asked again until the screen is opened again.
    val failed = remember { mutableStateListOf<String>() }
    fun legKey(items: List<PlanItem>, i: Int) = "${items[i - 1].spot()}>${items[i].spot()}"
    val pending = staleLegs(plan.items).filter { legKey(plan.items, it) !in failed }
    val todo = pending.firstOrNull()?.let { legKey(plan.items, it) }
    LaunchedEffect(plan.id, todo) {
        val i = pending.firstOrNull() ?: return@LaunchedEffect
        val key = todo!!
        val move = measure(plan.items[i - 1], plan.items[i])
        val now = current // the plan may have changed while the router was working
        if (move == null) failed += key
        else if (i < now.items.size && legKey(now.items, i) == key) onChange(now.copy(items = now.items.mapIndexed { j, it -> if (j == i) it.copy(move = move) else it }))
    }
    var open by rememberSaveable { mutableStateOf(-1) }
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
                // The way here from the stop before: what the router measured, or why there is no time yet.
                if (i > 0 && item.spot() != null && plan.items[i - 1].spot() != null) {
                    val measured = item.move?.takeIf { it.from == plan.items[i - 1].spot() }
                    Text(
                        measured?.label ?: if (i in pending) Strings.LEG_PENDING else Strings.LEG_UNKNOWN,
                        Modifier.padding(start = 64.dp, top = 2.dp, bottom = 2.dp), size = 12.sp,
                        color = measured?.let { moveColor(it.kind) } ?: Palette.Muted, weight = FontWeight.SemiBold,
                    )
                }
                val done = i < plan.next
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.fillMaxWidth().clip(shape).then(if (i == 0) Modifier.background(Palette.Subtle) else Modifier.border(1.dp, if (open == i) Palette.Primary else Palette.Line, shape))) {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (open == i) -1 else i }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (done) "✓" else times[i], Modifier.width(48.dp).semantics { if (done) contentDescription = Strings.STOP_DONE }, size = 13.sp, color = if (done) Palette.Muted else Palette.Primary, weight = FontWeight.Bold)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, size = 14.sp, color = if (done) Palette.Muted else Palette.Ink, weight = FontWeight.Bold)
                            val sub = listOfNotNull(item.note.takeIf { it.isNotBlank() }, "${Strings.STAY} ${item.stayMin} ${Strings.MINUTES_SHORT}".takeIf { item.stayMin > 0 }).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, size = 12.sp, color = Palette.Muted)
                            if (item.memo.isNotBlank() && open != i) Text(item.memo, size = 12.sp, color = Palette.Body, maxLines = 2)
                            // Known opening hours (guide content): say so when the plan arrives outside them.
                            openingHours(item.title)?.let { (opens, closes) ->
                                val warn = when { times[i] < opens -> Strings.opensAt(times[i], opens); times[i] >= closes -> Strings.closedAt(times[i], closes); else -> null }
                                if (warn != null && !done) Text(warn, size = 12.sp, color = Palette.Accent, weight = FontWeight.SemiBold)
                            }
                        }
                        if (i > 0) IconButton(ExtraIcons.close, "${Strings.REMOVE_STOP}: ${item.title}", tint = Palette.Muted) {
                            open = -1
                            // Removing a stop before the next one keeps the progress pointing at the same stop.
                            onChange(plan.copy(items = plan.items.filterIndexed { j, _ -> j != i }, next = if (i < plan.next) plan.next - 1 else plan.next))
                        }
                    }
                    if (open == i) Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun put(changed: PlanItem) = onChange(plan.copy(items = plan.items.mapIndexed { j, it -> if (j == i) changed else it }))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("− 15", modifier = Modifier.semantics { contentDescription = Strings.STAY_LESS }) { put(item.copy(stayMin = (item.stayMin - 15).coerceAtLeast(0))) }
                            Text("${Strings.STAY} ${item.stayMin} ${Strings.MINUTES_SHORT}", size = 13.sp, weight = FontWeight.SemiBold)
                            Pill("+ 15", modifier = Modifier.semantics { contentDescription = Strings.STAY_MORE }) { put(item.copy(stayMin = (item.stayMin + 15).coerceAtMost(480))) }
                        }
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Subtle).padding(horizontal = 10.dp)) {
                            if (item.memo.isEmpty()) Text(Strings.MEMO_HINT, Modifier.padding(vertical = 14.dp), size = 13.sp, color = Palette.Muted)
                            BasicTextField(
                                item.memo, { put(item.copy(memo = it.take(200))) },
                                Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).padding(vertical = 14.dp).semantics { contentDescription = "${Strings.MEMO}: ${item.title}" },
                                textStyle = TextStyle(fontSize = 13.sp, color = Palette.Ink),
                            )
                        }
                        if (item.spot() != null && plan.routing) Button(Strings.ROUTE_HERE, kind = ButtonKind.Outline, compact = true, pill = true) { onRoute(Endpoint(item.lat!!, item.lon!!, item.title)) }
                    }
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
                        Text("+ ${s.note}", size = 11.sp, color = Palette.Muted)
                    }
                }
            }
            Button(Strings.REORDER, kind = ButtonKind.Soft, compact = true, pill = true) { open = -1; onChange(plan.copy(items = reorderNearest(plan.items), next = 0, going = false)) }
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, Palette.Line).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val nextIdx = plan.nextStop()
            val walk = walkTotalM(plan.items)
            Note(listOfNotNull(
                "${plan.items.size} ${Strings.STOPS}", "${Strings.UNTIL} ${planEnd(plan.start, plan.items)}",
                "${Strings.PLAN_WALK} ${distanceLabel(walk.toDouble())}".takeIf { walk > 0 }, Strings.TIMES_ESTIMATED,
            ).joinToString(" · "))
            if (plan.items.any { it.spot() != null }) Text(
                nextIdx?.let { "${Strings.NEXT_STOP}: ${plan.items[it].title}" } ?: Strings.PLAN_DONE,
                size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    // On the way: the user says when they are there (never guessed from GPS), or asks for the way again.
                    plan.going && nextIdx != null -> {
                        Button(Strings.ARRIVED_HERE, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true) {
                            val after = plan.copy(next = nextIdx + 1, going = false)
                            onChange(if (after.nextStop() == null) after.copy(status = PlanStatus.Done) else after)
                        }
                        Button(Strings.ROUTE_AGAIN_SHORT, Modifier.weight(1f), compact = true, pill = true) { onGo(plan) }
                    }
                    nextIdx == null && plan.items.any { it.spot() != null } -> {
                        Button(Strings.SAVE_TO_PROFILE, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true, onClick = onSave)
                        Button(Strings.PLAN_RESTART, Modifier.weight(1f), compact = true, pill = true) { onChange(plan.copy(next = 0, going = false, status = PlanStatus.Upcoming)) }
                    }
                    else -> {
                        Button(Strings.SAVE_TO_PROFILE, Modifier.weight(1f), kind = ButtonKind.Outline, compact = true, pill = true, onClick = onSave)
                        Button(Strings.GO_NEXT, Modifier.weight(1f), compact = true, pill = true) { onGo(plan) }
                    }
                }
            }
        }
    }
}
