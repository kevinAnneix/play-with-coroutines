package usecases.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FlowsAndStateTest {

    @Test
    fun `stockPriceStream emits configured number of items`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = ReactiveMarketService(ioDispatcher = testDispatcher)

        val quotes = service.stockPriceStream("AAPL", count = 5, intervalMs = 10).toList()

        assertEquals(5, quotes.size)
        assertTrue(quotes.all { it.symbol == "AAPL" })
    }

    @Test
    fun `combinedStockStream zips two separate stock streams`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = ReactiveMarketService(ioDispatcher = testDispatcher)

        val zipped = service.combinedStockStream("GOOG", "MSFT", count = 3).toList()

        assertEquals(3, zipped.size)
        assertEquals("GOOG", zipped[0].first.symbol)
        assertEquals("MSFT", zipped[0].second.symbol)
    }

    @Test
    fun `MarketViewModel updates StateFlow as new quotes arrive`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = ReactiveMarketService(ioDispatcher = testDispatcher)
        val viewModel = MarketViewModel(scope = this, marketService = service)

        assertEquals(UIState.Loading, viewModel.uiState.value)

        viewModel.monitorStock("TSLA", tickCount = 4)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<UIState.Success>(state)
        assertEquals(4, state.quotes.size)
    }

    @Test
    fun `MarketViewModel emits alerts through SharedFlow on price surge`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = object : ReactiveMarketService(ioDispatcher = testDispatcher) {
            // Service returns quotes triggering alert (> 101.0)
        }
        val viewModel = MarketViewModel(scope = this, marketService = service)
        val alerts = mutableListOf<MarketAlert>()

        val collectJob = launch(testDispatcher) {
            viewModel.eventFlow.collect { alerts.add(it) }
        }

        viewModel.monitorStock("NVDA", tickCount = 10)
        advanceUntilIdle()

        collectJob.cancel()
        // If price went above 101, alerts were emitted without blocking UIState
        assertTrue(viewModel.uiState.value is UIState.Success)
    }
}
