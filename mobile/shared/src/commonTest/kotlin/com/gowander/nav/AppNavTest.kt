package com.gowander.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UX-2 P1 (ADR-034): one back stack per tab, shared by Android and iOS. */
class AppNavTest {
    @Test
    fun pushAndBackStayInTheCurrentTab() {
        val nav = AppNav().push(Screen.Place("p1")).push(Screen.Results)
        assertEquals(Screen.Results, nav.top)
        assertTrue(nav.canPop)
        val back = nav.pop()
        assertEquals(Screen.Place("p1"), back.top)
        assertNull(back.pop().top)
        assertFalse(back.pop().canPop)
    }

    @Test
    fun switchingTabsKeepsEachTabsStack() {
        val nav = AppNav().push(Screen.Place("p1")).select(Tab.Discover).push(Screen.Place("p2")).select(Tab.Map)
        assertEquals(Tab.Map, nav.tab)
        assertEquals(Screen.Place("p1"), nav.top) // the map tab is where the user left it
        assertEquals(Screen.Place("p2"), nav.select(Tab.Discover).top)
    }

    @Test
    fun tappingTheActiveTabAgainGoesToItsRoot() {
        val nav = AppNav().push(Screen.Place("p1")).push(Screen.Results).select(Tab.Map)
        assertNull(nav.top)
    }

    @Test
    fun showResultsReplacesTheFormScreensSoBackReturnsWhereTheTripStarted() {
        // Place page -> Directions: Results sits on the place, Back returns to the place.
        assertEquals(listOf(Screen.Place("p1"), Screen.Results), AppNav().push(Screen.Place("p1")).showResults().stack)
        // "Đi đâu?" -> search -> pick: the search screen is replaced, Back returns to the map.
        assertEquals(listOf(Screen.Results), AppNav().push(Screen.Search).showResults().stack)
        // Editing the trip from the results: back on the same results, not a second copy.
        val edited = AppNav().push(Screen.Results).push(Screen.Plan).push(Screen.Search).showResults()
        assertEquals(listOf(Screen.Results), edited.stack)
        // Picking on the map from the search screen.
        assertEquals(listOf(Screen.Results), AppNav().push(Screen.Search).push(Screen.PickDestination).showResults().stack)
    }

    @Test
    fun openPlaceReturnsToAnOpenCopyInsteadOfStackingDuplicates() {
        val nav = AppNav().push(Screen.Place("p1")).push(Screen.Results).openPlace("p1")
        assertEquals(listOf(Screen.Place("p1")), nav.stack)
        assertEquals(listOf(Screen.Results, Screen.Place("p2")), AppNav().push(Screen.Results).openPlace("p2").stack)
    }

    @Test
    fun popToGoesBackToTheNearestMatchingScreen() {
        val nav = AppNav().push(Screen.Results).push(Screen.Detail).push(Screen.Navigation).push(Screen.Steps).push(Screen.Preview(3))
        assertEquals(Screen.Navigation, nav.popTo { it == Screen.Navigation }.top)
        assertEquals(nav, nav.popTo { it is Screen.Place }) // nothing to go back to: unchanged
    }

    @Test
    fun dropRouteScreensLeavesPlacePagesAlone() {
        val nav = AppNav().push(Screen.Place("p1")).push(Screen.Results).push(Screen.Detail).select(Tab.Journey).push(Screen.Results)
        val cleared = nav.dropRouteScreens()
        assertEquals(listOf<Screen>(), cleared.stack)
        assertEquals(listOf<Screen>(Screen.Place("p1")), cleared.select(Tab.Map).stack)
    }

    @Test
    fun saveAndRestoreRoundTrip() {
        val nav = AppNav().push(Screen.Place("osm:n/12:3")).push(Screen.Results).push(Screen.Preview(4)).select(Tab.Profile).push(Screen.Search)
        assertEquals(nav, AppNav.restore(nav.save()))
        // Anything unreadable (an older app version, a corrupted bundle) starts clean instead of crashing.
        assertEquals(AppNav(), AppNav.restore(listOf("Nope", "Map|results|unknown")))
        assertEquals(AppNav(), AppNav.restore(emptyList()))
    }
}
