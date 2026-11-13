package com.gowander.discover

import com.gowander.api.ApiError
import com.gowander.api.FakeApi
import com.gowander.api.Fixtures
import com.gowander.api.PlacePage
import com.gowander.api.apiJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val api = FakeApi()
    private val page = apiJson.decodeFromString<PlacePage>(Fixtures.PLACES_PAGE)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startLoadsCategoriesAndFirstPage() = runTest {
        val vm = DiscoverViewModel(api)
        val s = vm.state.value
        assertEquals("museum", s.categories.first().slug)
        assertEquals(page.places, s.places)
        assertEquals(page.nextCursor, s.nextCursor)
        assertIs<ListPhase.Ready>(s.phase)
        assertEquals(Triple(null, null, null), api.placeCalls.single())
    }

    @Test
    fun categoryAndQueryFilterAndResetPaging() = runTest {
        val vm = DiscoverViewModel(api)
        vm.selectCategory("museum")
        assertEquals(Triple("museum", null, null), api.placeCalls.last())
        vm.submitQuery("  b  ") // under 2 characters: not sent to the API
        assertEquals(Triple("museum", null, null), api.placeCalls.last())
        vm.submitQuery("  ben thanh ")
        assertEquals(Triple("museum", "ben thanh", null), api.placeCalls.last())
        vm.selectCategory(null)
        assertEquals(Triple(null, "ben thanh", null), api.placeCalls.last())
    }

    @Test
    fun loadMoreAppendsWithCursorUntilLastPage() = runTest {
        val vm = DiscoverViewModel(api)
        api.placesResult = { _, _, _ -> PlacePage(places = page.places.take(1), nextCursor = null) }
        vm.loadMore()
        assertEquals(page.nextCursor, api.placeCalls.last().third)
        assertEquals(page.places.size + 1, vm.state.value.places.size)
        assertNull(vm.state.value.nextCursor)
        val calls = api.placeCalls.size
        vm.loadMore() // last page: nothing to load
        assertEquals(calls, api.placeCalls.size)
    }

    @Test
    fun listFailureThenRetry() = runTest {
        api.placesResult = { _, _, _ -> throw ApiError.Server("DATA_STORE_UNAVAILABLE", "Dữ liệu tạm thời không truy cập được.", 503) }
        val vm = DiscoverViewModel(api)
        val failed = assertIs<ListPhase.Failed>(vm.state.value.phase)
        assertEquals("Dữ liệu tạm thời không truy cập được.", failed.message)
        api.placesResult = { _, _, _ -> page }
        vm.retry()
        assertIs<ListPhase.Ready>(vm.state.value.phase)
        assertEquals(page.places, vm.state.value.places)
    }

    @Test
    fun placeDetailOpensFailsAndCloses() = runTest {
        val vm = DiscoverViewModel(api)
        vm.openPlace("01a1d0db-2bcf-7fb7-b314-f42c1e9c7156")
        assertEquals("Bảo tàng Chiến dịch Hồ Chí Minh", assertIs<DetailState.Loaded>(vm.state.value.detail).place.name)
        api.detailResult = { throw ApiError.Server("PLACE_NOT_FOUND", "Không tìm thấy địa điểm.", 404) }
        vm.openPlace("missing")
        assertTrue(assertIs<DetailState.Failed>(vm.state.value.detail).message.startsWith("Không tìm thấy"))
        vm.closePlace()
        assertIs<DetailState.None>(vm.state.value.detail)
    }
}
