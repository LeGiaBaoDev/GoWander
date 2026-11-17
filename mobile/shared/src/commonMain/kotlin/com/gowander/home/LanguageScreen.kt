package com.gowander.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.ui.Card
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.Lang
import com.gowander.ui.Language
import com.gowander.ui.Note
import com.gowander.ui.Palette
import com.gowander.ui.Strings
import com.gowander.ui.Text

/**
 * Language picker, reached from Settings → Ngôn ngữ. Vietnamese and English are selectable; Chinese,
 * German, Korean and Japanese keep their place with "Sắp có" until their strings exist (ADR-022).
 */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(Strings.LANGUAGE, onBack = onBack)
        Note(Strings.LANGUAGE_NOTE)
        Card(gap = 0.dp) {
            LanguageRow("VI", Strings.LANGUAGE_VI_LABEL, Strings.VIETNAMESE, Language.current == Lang.VI, enabled = true) {
                Language.set(Lang.VI)
            }
            Divider()
            LanguageRow("EN", Strings.LANGUAGE_EN_LABEL, Strings.ENGLISH, Language.current == Lang.EN, enabled = true) {
                Language.set(Lang.EN)
            }
            Divider()
            LanguageRow("ZH", "中文", "Chinese", selected = false, enabled = false, soon = Strings.SOON)
            Divider()
            LanguageRow("DE", "Deutsch", "German", selected = false, enabled = false, soon = Strings.SOON)
            Divider()
            LanguageRow("KO", "한국어", "Korean", selected = false, enabled = false, soon = Strings.SOON)
            Divider()
            LanguageRow("JA", "日本語", "Japanese", selected = false, enabled = false, soon = Strings.SOON)
        }
        Note(Strings.LANGUAGE_ADD_LATER)
    }
}

@Composable
private fun LanguageRow(
    code: String,
    name: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    soon: String? = null,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = 64.dp)
            .then(if (enabled) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Palette.Soft else Palette.Subtle),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, size = 11.sp, weight = FontWeight.Bold, color = if (selected) Palette.Primary else Palette.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, size = 14.sp, weight = FontWeight.SemiBold, color = if (enabled) Palette.Ink else Palette.Muted)
            Text(subtitle, size = 12.sp, color = Palette.Muted)
        }
        when {
            selected -> Icon(KitIcons.check, 20.dp, Palette.Primary)
            soon != null -> Text(
                soon,
                Modifier.clip(RoundedCornerShape(999.dp)).background(Palette.Warn).padding(horizontal = 8.dp, vertical = 4.dp),
                size = 10.sp, weight = FontWeight.Bold, color = Palette.Amber,
            )
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
