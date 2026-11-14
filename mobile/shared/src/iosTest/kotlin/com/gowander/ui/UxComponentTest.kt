@file:OptIn(ExperimentalTestApi::class)

package com.gowander.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.gowander.route.MapView
import com.gowander.route.MapViewSwitch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UX rules as tests (docs/testing/ux-checklist.md): touch targets >= 48 dp, every icon-only control has a spoken name,
 * the selected state is exposed, and anything that opens has a visible way to close.
 */
class UxComponentTest {
    @Test
    fun modeTabsAreBigEnoughAndExposeSelection() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent {
            ModeTabs(listOf(KitIcons.bus to "Công cộng", KitIcons.walk to "Đi bộ"), selected) { selected = it }
        }
        onNodeWithText("Công cộng").assertIsSelected().assertHeightIsAtLeast(48.dp)
        onNodeWithText("Đi bộ").assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick()
        onNodeWithText("Đi bộ").assertIsSelected()
        onNodeWithText("Công cộng").assertIsNotSelected()
        assertEquals(1, selected)
    }

    @Test
    fun iconOnlyButtonsHaveANameAndA48dpTarget() = runComposeUiTest {
        var clicks = 0
        setContent {
            IconButton(KitIcons.locate, "Dùng vị trí của tôi") { clicks++ }
            Header("Chi tiết tuyến", floating = true, onBack = { clicks++ })
        }
        onNodeWithContentDescription("Dùng vị trí của tôi").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        onNodeWithContentDescription(Strings.BACK).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun chipsMeetTheTouchTargetAndShowSelection() = runComposeUiTest {
        setContent { Chip("Khởi hành ngay", selected = true) {} }
        onNodeWithText("Khởi hành ngay").assertHeightIsAtLeast(48.dp).assertIsSelected()
    }

    @Test
    fun mapViewPanelOpensClosesAfterChoiceAndOnOutsideTap() = runComposeUiTest {
        var view by mutableStateOf(MapView.Flat)
        var open by mutableStateOf(false)
        setContent { MapViewSwitch(view, open, onOpenChange = { open = it }) { view = it } }

        onNodeWithText(Strings.VIEW_2D).assertDoesNotExist()
        onNodeWithContentDescription(Strings.MAP_VIEW).assertHeightIsAtLeast(48.dp).performClick()
        onNodeWithText(Strings.VIEW_2D).assertIsSelected()
        onNodeWithText(Strings.VIEW_3D).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(MapView.Tilted, view)
        onNodeWithText(Strings.VIEW_2D).assertDoesNotExist() // closes after a choice

        onNodeWithContentDescription(Strings.MAP_VIEW).performClick()
        onNodeWithContentDescription(Strings.CLOSE_PANEL).performClick() // tap outside the panel
        onNodeWithText(Strings.VIEW_2D).assertDoesNotExist()

        onNodeWithContentDescription(Strings.MAP_VIEW).performClick()
        onNodeWithText(Strings.VIEW_SATELLITE).performClick()
        onNodeWithText(Strings.SATELLITE_PENDING).assertExists() // explains instead of failing silently
        assertEquals(MapView.Tilted, view) // nothing switched to an empty satellite map

        onNodeWithContentDescription(Strings.MAP_VIEW).performClick() // the same button closes it again
        onNodeWithText(Strings.VIEW_2D).assertDoesNotExist()
    }
}
