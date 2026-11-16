package com.gowander.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.route.nowMillis
import com.gowander.ui.Badge
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.IconButton
import com.gowander.ui.KitIcons
import com.gowander.ui.ListRow
import com.gowander.ui.Palette
import com.gowander.ui.Text

/**
 * A11 · Lịch sử tìm kiếm: everything the person chose, grouped by day, each removable, all removable, and a switch
 * that stops keeping it (and deletes what is there). It lives on this phone only (ADR-040 §7).
 */
@Composable
fun SearchHistoryScreen(
    history: SearchHistory,
    onBack: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
) {
    var entries by remember { mutableStateOf(history.all()) }
    var enabled by remember { mutableStateOf(history.enabled) }
    val now = remember { nowMillis() }

    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Header("Lịch sử tìm kiếm", onBack = onBack)
        }
        if (entries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) {
                        history.clear(); entries = history.all()
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("Xoá tất cả", size = 13.5.sp, color = Palette.Accent, weight = FontWeight.SemiBold) }
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Subtle).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Lưu lịch sử tìm kiếm", size = 15.sp, weight = FontWeight.SemiBold)
                Text("Chỉ lưu trên máy này, không gửi đi đâu", size = 12.5.sp, color = Palette.Muted)
            }
            Box(
                Modifier.size(48.dp).semantics { contentDescription = if (enabled) "Tắt lưu lịch sử" else "Bật lưu lịch sử" }
                    .clickable(role = Role.Switch) {
                        enabled = !enabled
                        history.setEnabled(enabled)
                        entries = history.all() // switching off also deletes: the list must follow
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (enabled) Palette.Primary else Palette.Line),
                ) {
                    Box(
                        Modifier.padding(top = 3.dp, start = if (enabled) 21.dp else 3.dp).size(20.dp).clip(CircleShape).background(Palette.Bg),
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                "Chưa có lịch sử. Những gì bạn chọn trong ô tìm địa điểm sẽ hiện ở đây để lần sau đi nhanh hơn.",
                size = 13.5.sp,
                color = Palette.Muted,
            )
        }

        groupByDay(entries, now).forEach { (day, list) ->
            Text(day, Modifier.semantics { heading() }, size = 12.5.sp, weight = FontWeight.Bold, color = Palette.Muted)
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Bg).border(1.dp, Palette.Line, RoundedCornerShape(18.dp))) {
                list.forEachIndexed { i, e ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
                    ListRow(
                        e.label,
                        subtitle = subtitleOf(e, now),
                        lead = {
                            Badge(
                                if (e.kind == HistoryEntry.Kind.ADDRESS) KitIcons.pin else if (e.kind == HistoryEntry.Kind.PLACE) KitIcons.landmark else KitIcons.clock,
                                Palette.Subtle,
                                Palette.Primary,
                            )
                        },
                        trail = {
                            IconButton(ExtraIcons.close, "Xoá khỏi lịch sử", tint = Palette.Muted) {
                                history.remove(e); entries = history.all()
                            }
                        },
                        onClick = { onOpen(e) },
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        Text(
            "GoWander không dùng lịch sử này cho thống kê. Tắt công tắc là xoá và ngừng lưu.",
            size = 12.5.sp,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun subtitleOf(e: HistoryEntry, now: Long): String {
    val what = when (e.kind) {
        HistoryEntry.Kind.PLACE -> "Địa điểm"
        HistoryEntry.Kind.ADDRESS -> "Địa chỉ"
        HistoryEntry.Kind.QUERY -> "Tìm kiếm"
    }
    return "$what · ${historyWhen(e, now)}"
}
