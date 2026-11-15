package com.gowander.home

import com.gowander.mock.MOCK_PLANS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStoreTest {
    @Test
    fun plansAndSavedPlacesSurviveARestart() {
        var disk: String? = null
        val first = AppStore(disk, persist = { disk = it })
        assertEquals(MOCK_PLANS.map { it.id }, first.plans.map { it.id }) // first run: the samples
        assertNull(disk) // nothing written until the user changes something
        val mine = first.newPlan().copy(name = "Chủ nhật đi Quận 5", items = MOCK_PLANS.first().items.take(2))
        first.put(mine)
        first.put(mine.copy(start = "09:30"))
        first.toggleSaved(SavedPlace("a", "Chợ Bến Thành", "Chợ"))
        first.toggleSaved(SavedPlace("b", "Quán cũ", null))
        first.toggleSaved(SavedPlace("b", "Quán cũ", null)) // un-save

        val second = AppStore(disk) // the app starts again
        assertEquals(first.plans.toList(), second.plans.toList())
        assertEquals("09:30", second.plan(mine.id)?.start)
        assertEquals(listOf(SavedPlace("a", "Chợ Bến Thành", "Chợ")), second.saved.toList())
    }

    @Test
    fun unreadableOrOlderDataDoesNotCrash() {
        var kept: String? = null
        assertEquals(MOCK_PLANS.map { it.id }, AppStore("not json", keepUnreadable = { kept = it }).plans.map { it.id })
        assertEquals("not json", kept) // the unreadable data is kept before anything can overwrite it
        val older = AppStore("""{"plans":[],"saved":[{"id":"a","name":"Chợ","category":null,"addedIn":"a later version"}]}""")
        assertTrue(older.plans.isEmpty()) // the user deleted nothing here: an empty stored list stays empty, no samples forced back
        assertEquals("Chợ", older.saved.single().name)
    }
}
