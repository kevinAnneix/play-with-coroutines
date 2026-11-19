package usecases.flows

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory

data class StockQuote(val symbol: String, val price: Double, val timestamp: Long = System.currentTimeMillis())
data class MarketAlert(val symbol: String, val message: String, val severity: String)

sealed interface UIState {
    data object Loading : UIState
    data class Success(val quotes: List<StockQuote>, val lastUpdated: Long) : UIState
    data class Error(val message: String) : UIState
}

open class ReactiveMarketService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(ReactiveMarketService::class.java)

    /**
     * Cold Flow: Emits simulated streaming stock price ticks.
     * Demonstrates [flowOn] for offloading emission to a background dispatcher
     * while preserving downstream collection context.
     */
    open fun stockPriceStream(symbol: String, count: Int, intervalMs: Long = 20): Flow<StockQuote> = flow {
        logger.info("Starting cold stock stream for {}", symbol)
        var price = 100.0
        for (i in 1..count) {
            delay(intervalMs)
            price += ((-2..2).random() * 0.5)
            val quote = StockQuote(symbol, price)
            logger.debug("Emitting quote: {}", quote)
            emit(quote)
        }
        logger.info("Cold stock stream for {} finished", symbol)
    }.flowOn(ioDispatcher)

    /**
     * Demonstrates flow combining and backpressure handling with [conflate].
     * Fast producer + slow consumer: conflate skips intermediate emissions
     * when consumer is busy, ensuring consumer always processes the latest quote.
     */
    suspend fun collectWithConflation(symbol: String, count: Int, slowConsumerDelayMs: Long = 60): List<StockQuote> {
        val collected = mutableListOf<StockQuote>()
        stockPriceStream(symbol, count, intervalMs = 15)
            .conflate()
            .collect { quote ->
                logger.info("Slow consumer received quote: price={}", quote.price)
                delay(slowConsumerDelayMs)
                collected.add(quote)
            }
        return collected
    }

    /**
     * Demonstrates combining two separate cold streams in parallel.
     */
    fun combinedStockStream(
        symbolA: String,
        symbolB: String,
        count: Int
    ): Flow<Pair<StockQuote, StockQuote>> {
        val streamA = stockPriceStream(symbolA, count, 20)
        val streamB = stockPriceStream(symbolB, count, 25)
        return streamA.zip(streamB) { a, b -> a to b }
    }
}

/**
 * State and Event management using Hot Flows ([StateFlow] and [SharedFlow]).
 */
class MarketViewModel(
    private val scope: CoroutineScope,
    private val marketService: ReactiveMarketService
) {
    private val logger = LoggerFactory.getLogger(MarketViewModel::class.java)

    // StateFlow: Holds current UI state, survives configuration changes, replays current value
    private val _uiState = MutableStateFlow<UIState>(UIState.Loading)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    // SharedFlow: One-time events (notifications / alerts) for multiple subscribers
    private val _eventFlow = MutableSharedFlow<MarketAlert>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val eventFlow: SharedFlow<MarketAlert> = _eventFlow.asSharedFlow()

    fun monitorStock(symbol: String, tickCount: Int = 5) {
        scope.launch {
            _uiState.value = UIState.Loading
            try {
                val quotes = mutableListOf<StockQuote>()
                marketService.stockPriceStream(symbol, tickCount, intervalMs = 20)
                    .collect { quote ->
                        quotes.add(quote)
                        _uiState.update {
                            UIState.Success(quotes = quotes.toList(), lastUpdated = System.currentTimeMillis())
                        }
                        if (quote.price > 101.0) {
                            _eventFlow.emit(MarketAlert(symbol, "Price surge above 101: ${quote.price}", "HIGH"))
                        }
                    }
            } catch (e: Exception) {
                logger.error("Error monitoring stock", e)
                _uiState.update { UIState.Error(e.message ?: "Unknown error") }
            }
        }
    }
}
