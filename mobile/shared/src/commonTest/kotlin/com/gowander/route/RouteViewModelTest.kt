package com.gowander.route

import com.gowander.api.ApiError
import com.gowander.api.FakeApi
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModelTest {
    private val api = FakeApi()
    private val benThanh = Endpoint(10.7725, 106.6990, "Vị trí của bạn")
    private val thuDuc = Endpoint(10.8480, 106.7730, "Thủ Đức")

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = RouteViewModel(api)

    @Test
    fun searchNeedsBothEndpoints() = runTest {
        val vm = vm()
        vm.search()
        vm.setOrigin(benThanh)
        vm.search()
        assertEquals(0, api.planCalls.size)
        assertIs<RoutePhase.Idle>(vm.state.value.phase)
    }

    @Test
    fun successShowsRoutesFirstSelectedAndDemoLabel() = runTest {
        val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); search() }
        val phase = assertIs<RoutePhase.Results>(vm.state.value.phase)
        assertEquals(0, phase.selected)
        assertTrue(phase.assumedData)
        assertEquals(listOf("WALK", "SUBWAY", "WALK"), phase.routes.first().legs.map { it.mode })
        assertEquals(benThanh.lat, api.planCalls.single().origin.lat)
        vm.selectRoute(5) // out of range: ignored
        assertEquals(0, assertIs<RoutePhase.Results>(vm.state.value.phase).selected)
    }

    @Test
    fun failureKeepsEndpointsAndRetryUsesThem() = runTest {
        api.plan = { throw ApiError.Server("ROUTING_TIMEOUT", "Tìm tuyến quá lâu, vui lòng thử lại.", 504) }
        val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); search() }
        val failed = assertIs<RoutePhase.Failed>(vm.state.value.phase)
        assertEquals("Tìm tuyến quá lâu, vui lòng thử lại.", failed.message)
        assertTrue(failed.retryable)
        assertEquals(benThanh, vm.state.value.origin)
        assertEquals(thuDuc, vm.state.value.destination)

        api.plan = FakeApi().plan
        vm.retry()
        assertIs<RoutePhase.Results>(vm.state.value.phase)
        assertEquals(2, api.planCalls.size)
        assertEquals(api.planCalls[0], api.planCalls[1])
    }

    @Test
    fun userFixableErrorsAreNotRetryable() = runTest {
        for (code in listOf("NO_ROUTE_FOUND", "OUT_OF_COVERAGE", "DATA_UNAVAILABLE", "INVALID_REQUEST")) {
            api.plan = { throw ApiError.Server(code, "msg", 422) }
            val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); search() }
            assertEquals(false, assertIs<RoutePhase.Failed>(vm.state.value.phase).retryable, code)
        }
        api.plan = { throw ApiError.Network(RuntimeException("offline")) }
        val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); search() }
        val failed = assertIs<RoutePhase.Failed>(vm.state.value.phase)
        assertTrue(failed.retryable)
        assertTrue(failed.message.isNotBlank())
    }

    @Test
    fun changingAnEndpointClearsStaleResults() = runTest {
        val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); search() }
        vm.setDestination(benThanh.copy(label = "Nơi khác"))
        assertIs<RoutePhase.Idle>(vm.state.value.phase)
    }

    @Test
    fun newSearchWinsOverASlowerOlderOne() = runTest {
        val slow = CompletableDeferred<Unit>()
        api.plan = { slow.await(); throw ApiError.Server("ROUTING_UNAVAILABLE", "old", 503) }
        val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); search() }
        assertIs<RoutePhase.Loading>(vm.state.value.phase)
        api.plan = FakeApi().plan
        vm.search()
        slow.complete(Unit)
        assertIs<RoutePhase.Results>(vm.state.value.phase)
    }

    @Test
    fun swapExchangesEndpoints() = runTest {
        val vm = vm().apply { setOrigin(benThanh); setDestination(thuDuc); swap() }
        assertEquals(thuDuc, vm.state.value.origin)
        assertEquals(benThanh, vm.state.value.destination)
    }
}
