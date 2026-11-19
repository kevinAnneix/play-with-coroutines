import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import org.slf4j.LoggerFactory
import usecases.aggregation.AggregationService
import usecases.cancellation.BatchProcessor
import usecases.channels.WorkTask
import usecases.channels.WorkerPipeline
import usecases.concurrency.BoundedRateLimiter
import usecases.concurrency.ConfinedDispatcherCounter
import usecases.concurrency.MutexProtectedCounter
import usecases.concurrency.UnsafeCounter
import usecases.flows.MarketViewModel
import usecases.flows.ReactiveMarketService
import usecases.flows.UIState
import usecases.resilience.ResilienceService

fun main(): Unit = runBlocking {
    val logger = LoggerFactory.getLogger("Main")
    println("================================================================================")
    println("           KOTLIN COROUTINES PLAYGROUND & PRACTICAL USE CASES                   ")
    println("================================================================================")

    // -------------------------------------------------------------------------
    // Use Case 1: Parallel Data Aggregation & Fault Isolation
    // -------------------------------------------------------------------------
    println("\n>>> [1/6] USE CASE: Parallel Data Aggregation (async / await & supervisorScope)")
    val aggregationService = AggregationService()

    println("--> Running strict parallel aggregation...")
    val strictResult = aggregationService.aggregateStrict("usr_1001")
    println("Strict Dashboard Result: Profile=${strictResult.profile.name}, Orders=${strictResult.orders.size}, Recs=${strictResult.recommendations.size}, Source=${strictResult.source}")

    println("--> Running resilient aggregation with simulated recommendation service outage...")
    val resilientResult = aggregationService.aggregateResilient("usr_1001", shouldFailRecs = true)
    println("Resilient Dashboard Result: Profile=${resilientResult.profile.name}, Recs=${resilientResult.recommendations.map { it.itemId }}, Source=${resilientResult.source}")

    // -------------------------------------------------------------------------
    // Use Case 2: Cooperative Cancellation, Timeouts & Safe Cleanup
    // -------------------------------------------------------------------------
    println("\n>>> [2/6] USE CASE: Cooperative Cancellation, Timeouts & NonCancellable Cleanup")
    val batchProcessor = BatchProcessor()

    println("--> Testing timeout handling (withTimeoutOrNull)...")
    val timedOut = batchProcessor.executeWithGracefulTimeout(timeoutMs = 50, workDurationMs = 200)
    println("Result with short timeout (50ms limit, 200ms task): $timedOut")

    val completedInTime = batchProcessor.executeWithGracefulTimeout(timeoutMs = 200, workDurationMs = 50)
    println("Result within timeout (200ms limit, 50ms task): $completedInTime")

    println("--> Testing cooperative cancellation with guaranteed NonCancellable cleanup...")
    val cancellableJob = launch {
        try {
            batchProcessor.processItemsCooperative(
                totalItems = 200,
                itemDelayMs = 5,
                onCleanupCallback = {
                    println("  [Cleanup Callback] Released allocated buffer resources safely.")
                }
            )
        } catch (e: CancellationException) {
            println("  [Job Handler] Coroutine received cancellation successfully.")
        }
    }
    delay(30)
    println("--> Cancelling batch processor job now...")
    cancellableJob.cancelAndJoin()

    // -------------------------------------------------------------------------
    // Use Case 3: Concurrency Control (Mutex, Dispatcher Confinement, Semaphore)
    // -------------------------------------------------------------------------
    println("\n>>> [3/6] USE CASE: Concurrency Control (Mutex, Thread Confinement, Semaphores)")
    val unsafeCounter = UnsafeCounter()
    val mutexCounter = MutexProtectedCounter()
    val confinedCounter = ConfinedDispatcherCounter()
    val iterations = 500

    coroutineScope {
        repeat(iterations) {
            launch(Dispatchers.Default) { unsafeCounter.increment() }
            launch(Dispatchers.Default) { mutexCounter.increment() }
            launch(Dispatchers.Default) { confinedCounter.increment() }
        }
    }
    println("Concurrent Increment Results ($iterations expected):")
    println("  - Unsafe Counter (Race Condition): ${unsafeCounter.count}")
    println("  - Mutex-Protected Counter:         ${mutexCounter.count}")
    println("  - Confined Dispatcher Counter:     ${confinedCounter.count}")

    println("--> Bounded Concurrency via Semaphore (max 2 concurrent out of 6 tasks)...")
    val rateLimiter = BoundedRateLimiter(maxConcurrentRequests = 2)
    coroutineScope {
        (1..6).forEach { taskId ->
            launch {
                rateLimiter.executeBounded("Request-$taskId") {
                    delay(30)
                }
            }
        }
    }
    println("Peak concurrent tasks observed: ${rateLimiter.getPeakConcurrency()} (max allowed: 2)")

    // -------------------------------------------------------------------------
    // Use Case 4: Channels & Producer-Consumer Worker Pool (Fan-out / Fan-in)
    // -------------------------------------------------------------------------
    println("\n>>> [4/6] USE CASE: Producer-Consumer Worker Pool with Channels")
    val pipeline = WorkerPipeline(workerCount = 3)
    val tasks = (1..6).map { id ->
        WorkTask(id = id, payload = "image_chunk_$id", complexityMs = 25L)
    }
    val pipelineResults = pipeline.executePipeline(tasks)
    println("Pipeline Processed Results (${pipelineResults.size} total):")
    pipelineResults.forEach { res ->
        println("  - Task #${res.taskId} handled by ${res.processedByWorker} -> ${res.output}")
    }

    // -------------------------------------------------------------------------
    // Use Case 5: Reactive Flows & State Management (Cold Flow, StateFlow, SharedFlow)
    // -------------------------------------------------------------------------
    println("\n>>> [5/6] USE CASE: Reactive Streams with Cold Flow, StateFlow & SharedFlow")
    val marketService = ReactiveMarketService()
    val viewModel = MarketViewModel(scope = this, marketService = marketService)

    println("--> Collecting cold stock stream with backpressure conflation...")
    val conflatedQuotes = marketService.collectWithConflation("NVDA", count = 6, slowConsumerDelayMs = 40)
    println("Conflated quotes received by slow consumer: ${conflatedQuotes.map { it.price }}")

    println("--> Monitoring stock in ViewModel and observing StateFlow updates...")
    viewModel.monitorStock("AAPL", tickCount = 3)
    delay(100)
    when (val state = viewModel.uiState.value) {
        is UIState.Success -> println("ViewModel StateFlow: Success with ${state.quotes.size} quotes")
        is UIState.Loading -> println("ViewModel StateFlow: Loading")
        is UIState.Error -> println("ViewModel StateFlow: Error(${state.message})")
    }

    // -------------------------------------------------------------------------
    // Use Case 6: Structured Resilience & Retry with Exponential Backoff
    // -------------------------------------------------------------------------
    println("\n>>> [6/6] USE CASE: Structured Resilience & Retry with Exponential Backoff")
    val resilienceService = ResilienceService()

    println("--> Running retry with backoff for transient network operation...")
    var retryAttempts = 0
    val retryData = resilienceService.retryWithBackoff(maxAttempts = 3, initialDelayMs = 20, factor = 2.0) { attempt ->
        retryAttempts++
        if (attempt < 3) {
            throw java.io.IOException("Simulated transient connection timeout on attempt $attempt")
        }
        "PAYMENT_CONFIRMATION_TX_998"
    }
    println("Operation succeeded on attempt $retryAttempts with result: $retryData")

    println("--> Demonstrating Supervisor isolation (one failing task among 4 supervised tasks)...")
    val (succeeded, errors) = resilienceService.executeSupervisedTasks(taskCount = 4, failingTaskIndex = 2)
    println("Supervised tasks completed: $succeeded (Errors isolated: ${errors.map { it.message }})")

    println("\n================================================================================")
    println("                   ALL USE CASES COMPLETED SUCCESSFULLY!                        ")
    println("================================================================================")
}
