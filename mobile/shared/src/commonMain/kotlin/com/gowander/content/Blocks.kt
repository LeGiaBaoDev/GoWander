package com.gowander.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.ui.Palette
import com.gowander.ui.RemotePhoto
import com.gowander.ui.Text
import kotlinx.serialization.Serializable

/**
 * A story as the Admin wrote it with the rich editor (ADR-041): paragraph, header, list, quote, delimiter. The app renders
 * these natively, so the text follows the Design Kit and the phone's font size; stories written before the editor have no
 * blocks and are shown as plain paragraphs.
 */
@Serializable
data class ContentBlock(val type: String, val data: BlockData = BlockData())

@Serializable
data class BlockData(
    val text: String = "",
    val level: Int = 2,
    val style: String = "unordered",
    val items: List<String> = emptyList(),
    val caption: String = "",
    val key: String = "",
    val alt: String = "",
    val credit: String = "",
    val license: String = "",
    val url: String = "",
)

/** The body of a story: blocks when the server sends them, the plain paragraphs otherwise. */
@Composable
fun StoryBody(blocks: List<ContentBlock>, paragraphs: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (blocks.isEmpty()) {
            paragraphs.forEach { Text(it, size = 14.sp, color = Palette.Body) }
            return@Column
        }
        blocks.forEach { block ->
            when (block.type) {
                "header" -> Text(
                    block.data.text,
                    Modifier.padding(top = 6.dp).semantics { heading() },
                    size = if (block.data.level >= 3) 16.sp else 18.sp,
                    weight = FontWeight.Bold,
                )
                "paragraph" -> Text(block.data.text, size = 14.sp, color = Palette.Body)
                "list" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.data.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(if (block.data.style == "ordered") "•" else "•", size = 14.sp, color = Palette.Primary, weight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(item, Modifier.weight(1f), size = 14.sp, color = Palette.Body)
                        }
                    }
                }
                "quote" -> Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(3.dp).height(20.dp).background(Palette.Primary))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("“${block.data.text}”", size = 14.5.sp, weight = FontWeight.Medium)
                        if (block.data.caption.isNotBlank()) {
                            Text("— ${block.data.caption}", size = 12.5.sp, color = Palette.Muted)
                        }
                    }
                }
                "image" -> if (block.data.url.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RemotePhoto(
                            block.data.url, block.data.key, Modifier.fillMaxWidth().height(190.dp),
                            icon = com.gowander.ui.KitIcons.camera, description = block.data.alt.ifBlank { null },
                        )
                        Text(
                            listOf(block.data.credit, block.data.license).filter { it.isNotBlank() }.joinToString(" · "),
                            size = 11.5.sp,
                            color = Palette.Muted,
                        )
                    }
                }
                "delimiter" -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).clip(RoundedCornerShape(1.dp)).background(Palette.Line),
                )
            }
        }
    }
}
