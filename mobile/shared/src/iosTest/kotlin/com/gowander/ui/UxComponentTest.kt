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
import com.gowander.route.LanguageButton
import com.gowander.route.RouteActions
import com.gowander.route.TravelMode
import com.gowander.route.TravelModeTabs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.assertTrue
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

    /** iPhone SE (375 pt) minus the sheet margins: the three route actions must each stay on one line. */
    @Test
    fun routeActionsStayOnOneLineOnSmallIphones() = runComposeUiTest {
        setContent { Box(Modifier.width(319.dp)) { RouteActions({}, {}, {}) } }
        for (label in listOf(Strings.START, Strings.PREVIEW, Strings.STEPS)) {
            // Labels are maxLines = 1, so a label that does not fit is cut, not wrapped: check the text layout itself.
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            // didOverflowWidth misreports for maxLines = 1; compare the text's natural width with the width it got.
            val natural = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue(natural <= layout.size.width + 0.5f && !layout.didOverflowHeight, "$label is cut: needs ${natural}px, has ${layout.size.width}px")
            onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
    }

    /** Five vehicle tabs (Buýt, Metro, Ô tô, Xe đạp, Đi bộ) on an iPhone SE: no label may be cut. */
    @Test
    fun fiveModeTabsFitOnSmallIphones() = runComposeUiTest {
        setContent { Box(Modifier.width(319.dp)) { TravelModeTabs(TravelMode.Bus) {} } }
        for (label in listOf(Strings.MODE_BUS, Strings.MODE_METRO, Strings.MODE_CAR, Strings.MODE_BICYCLE, Strings.MODE_WALK)) {
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            assertTrue(layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width + 0.5f && layout.lineCount == 1, "$label is cut")
            onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
        onNodeWithText(Strings.MODE_TRANSIT).assertDoesNotExist() // no vague "Công cộng" tab any more
    }

    @Test
    fun languageButtonOpensAMenuAndExplainsEnglish() = runComposeUiTest {
        setContent { LanguageButton() }
        onNodeWithContentDescription(Strings.LANGUAGE_MENU).assertHeightIsAtLeast(48.dp).performClick()
        onNodeWithText(Strings.VIETNAMESE).assertIsSelected().assertHeightIsAtLeast(48.dp)
        onNodeWithText(Strings.ENGLISH).assertIsNotSelected().performClick()
        onNodeWithText("English: sắp có").assertExists() // explains instead of doing nothing
        onNodeWithText("中文").assertExists() // more languages listed, most used first
        onNodeWithText(Strings.VIETNAMESE).performClick() // choosing closes the menu
        onNodeWithText(Strings.ENGLISH).assertDoesNotExist()
    }

    /** Owner 14-11-2026: the welcome logo must be big (the first-run form is gone in UX-2, ADR-034). */
    @Test
    fun welcomeLogoIsLarge() = runComposeUiTest {
        // Real iPhone density (3x): at density 1 the PNG's pixel height looked big enough and hid the bug.
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f)) {
                Box(Modifier.requiredSize(402.dp, 874.dp)) { com.gowander.onboarding.WelcomeScreen {} }
            }
        }
        onNodeWithContentDescription(Strings.APP_NAME).assertWidthIsAtLeast(300.dp).assertHeightIsAtLeast(190.dp) // 350x233 logo keeps its ratio
    }

    /** UX-2 (ADR-034): five slots on a 320 dp iPhone, labels on one line, tabs expose selection, the centre is an action. */
    @Test
    fun bottomNavFiveSlotsFitOnSmallIphones() = runComposeUiTest {
        var tab by mutableStateOf(com.gowander.nav.Tab.Map)
        var went = 0
        setContent { Box(Modifier.width(320.dp)) { BottomNav(tab, onGo = { went++ }) { tab = it } } }
        for (label in listOf(Strings.TAB_MAP, Strings.TAB_DISCOVER, Strings.TAB_GO, Strings.TAB_JOURNEY, Strings.TAB_PROFILE)) {
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            assertTrue(layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width + 0.5f && layout.lineCount == 1, "$label is cut")
            onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
        onNodeWithText(Strings.TAB_MAP).assertIsSelected()
        onNodeWithText(Strings.TAB_JOURNEY).assertIsNotSelected().performClick()
        assertEquals(com.gowander.nav.Tab.Journey, tab)
        onNodeWithText(Strings.TAB_JOURNEY).assertIsSelected()
        onNodeWithText(Strings.TAB_GO).performClick()
        assertEquals(1, went)
        assertEquals(com.gowander.nav.Tab.Journey, tab) // the centre action does not switch tabs
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
        onNodeWithText(Strings.VIEW_SATELLITE).assertDoesNotExist() // no "coming soon" tile until a provider exists (ADR-034)

        onNodeWithContentDescription(Strings.MAP_VIEW).performClick() // the same button closes it again
        onNodeWithText(Strings.VIEW_2D).assertDoesNotExist()
    }
}
