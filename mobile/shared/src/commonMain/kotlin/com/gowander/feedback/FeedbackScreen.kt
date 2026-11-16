package com.gowander.feedback

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.api.ApiError
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.IconButton
import com.gowander.ui.KitIcons
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.Pill
import com.gowander.ui.Strings
import com.gowander.ui.Text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

/** Preview v2: a round button over every screen; drag it out of the way, tap it to send a feedback. */
@Composable
fun FeedbackBubble(onClick: () -> Unit) {
    var dx by rememberSaveable { mutableStateOf(0f) }
    var dy by rememberSaveable { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(end = 16.dp, bottom = 120.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); dx += drag.x; dy += drag.y } }
                .size(56.dp).clip(CircleShape).background(Palette.Primary)
                .clickable(role = Role.Button, onClickLabel = "Gửi feedback", onClick = onClick)
                .semantics { contentDescription = "Feedbacks: góp ý cho GoWander" },
            contentAlignment = Alignment.Center,
        ) { Icon(ExtraIcons.chat, 26.dp, Palette.OnPrimary) }
    }
}

/**
 * The form of preview v2: what the feedback is about, kind, text, an optional picture (the captured screen or one from
 * the library) and, for guests, an optional name and email. Nothing leaves the phone before Send.
 */
@Composable
fun FeedbackScreen(start: FeedbackDraft, screenshot: ByteArray?, images: FeedbackImages, send: suspend (FeedbackDraft) -> String, onDone: (sent: Boolean) -> Unit) {
    var draft by remember { mutableStateOf(start) }
    var sending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picture = remember(draft.image) { draft.image?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header("Feedbacks", onBack = { onDone(false) })
        if (draft.context.isNotBlank()) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Subtle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(KitIcons.pin, 18.dp, Palette.Primary)
            Text("Đang góp ý: ${draft.context}", size = 14.sp, weight = FontWeight.SemiBold)
        }
        Text("Loại feedback", Modifier.semantics { heading() }, size = 15.sp, weight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FeedbackKind.entries.take(2).forEach { k -> Pill(k.label, selected = draft.kind == k) { draft = draft.copy(kind = k) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FeedbackKind.entries.drop(2).forEach { k -> Pill(k.label, selected = draft.kind == k) { draft = draft.copy(kind = k) } }
        }
        Field("Nội dung", draft.message, "Ví dụ: Giờ mở cửa chưa đúng, nhờ GoWander kiểm tra lại.", minLines = 4) { draft = draft.copy(message = it.take(1000)) }
        Text("Ảnh đính kèm${if (draft.image != null) " (1)" else ""}", size = 15.sp, weight = FontWeight.Bold)
        picture?.let { img ->
            Box {
                Image(img, "Ảnh sẽ gửi kèm", Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Line, RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                IconButton(ExtraIcons.close, "Bỏ ảnh đính kèm", Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(Palette.Bg), tint = Palette.Ink) {
                    draft = draft.copy(image = null)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (screenshot != null && draft.image == null) Button("Ảnh màn hình", kind = ButtonKind.Secondary, icon = KitIcons.camera, compact = true) { draft = draft.copy(image = screenshot) }
            Button("Chọn ảnh", kind = ButtonKind.Secondary, compact = true) {
                images.pick { r ->
                    r.onSuccess { bytes -> if (bytes != null) draft = draft.copy(image = bytes) }.onFailure { problem = it.message }
                }
            }
        }
        Note("Ảnh chỉ được gửi khi bạn bấm Gửi feedback.")
        Field("Tên của bạn (không bắt buộc)", draft.name, "Ví dụ: Lan Anh") { draft = draft.copy(name = it.take(100)) }
        Field("Email (không bắt buộc, để GoWander trả lời)", draft.email, "ban@example.com", keyboard = KeyboardType.Email) { draft = draft.copy(email = it.take(254)) }
        Note("Chỉ đội GoWander xem tên và email; GoWander không gửi vị trí của bạn.")
        problem?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Palette.Accent, weight = FontWeight.SemiBold) }
        Button(if (sending) "Đang gửi…" else "Gửi feedback", Modifier.fillMaxWidth(), icon = ExtraIcons.send) {
            if (sending) return@Button
            val error = draft.validationError()
            if (error != null) { problem = error; return@Button }
            sending = true
            problem = null
            scope.launch {
                try {
                    send(draft)
                    onDone(true)
                } catch (e: ApiError) {
                    problem = when (e) {
                        is ApiError.Server -> e.userMessage
                        is ApiError.Network -> Strings.NETWORK_ERROR
                        else -> "Chưa gửi được. Vui lòng thử lại."
                    }
                } finally {
                    sending = false
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, minLines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, size = 15.sp, weight = FontWeight.Bold)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().heightIn(min = if (minLines > 1) 110.dp else 48.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Subtle)
                .border(1.dp, Palette.Line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = label },
            singleLine = minLines == 1, minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(fontSize = 15.sp, color = Palette.Ink),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Palette.Muted); inner() },
        )
    }
}
