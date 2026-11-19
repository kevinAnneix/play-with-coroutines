package usecases.cancellation

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

data class ProcessingReport(
    val processedItems: Int,
    val completedNormally: Boolean,
    val cancelled: Boolean,
    val cleanedUp: Boolean
)

class BatchProcessor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val logger = LoggerFactory.getLogger(BatchProcessor::class.java)

    /**
     * Cooperatively processes a sequence of items.
     * Demonstrates:
     * 1. Checking [CoroutineScope.isActive] / [ensureActive] in CPU-intensive or iterative work.
     * 2. Yielding execution with [yield] to allow other coroutines on the same dispatcher to progress.
     * 3. Safe cleanup in finally block using `withContext(NonCancellable)` so closing actions can suspend.
     */
    suspend fun processItemsCooperative(
        totalItems: Int,
        itemDelayMs: Long = 10,
        onCleanupCallback: suspend () -> Unit = {}
    ): ProcessingReport = withContext(dispatcher) {
        val processedCount = AtomicInteger(0)
        var wasCancelled = false
        var cleanedUp = false

        try {
            logger.info("Starting batch processing of {} items", totalItems)
            for (i in 1..totalItems) {
                // Ensure cooperative cancellation checkpoint
                ensureActive()

                // Simulate work
                delay(itemDelayMs)
                processedCount.incrementAndGet()

                if (i % 20 == 0) {
                    logger.debug("Processed {}/{} items", i, totalItems)
                }
            }
            logger.info("Batch processing completed successfully for {} items", totalItems)
            ProcessingReport(
                processedItems = processedCount.get(),
                completedNormally = true,
                cancelled = false,
                cleanedUp = false
            )
        } catch (e: CancellationException) {
            wasCancelled = true
            logger.warn("Batch processing cancelled after {} items: {}", processedCount.get(), e.message)
            throw e // Always rethrow CancellationException to maintain structured cancellation
        } finally {
            // If the coroutine is cancelled, normal suspend functions inside finally would throw CancellationException immediately.
            // Wrapping cleanup in withContext(NonCancellable) guarantees cleanup suspension runs to completion.
            withContext(NonCancellable) {
                logger.info("Running critical cleanup logic (NonCancellable)...")
                delay(15) // Simulating async resource release / commit rollback
                onCleanupCallback()
                cleanedUp = true
                logger.info("Critical cleanup completed.")
            }
        }
    }

    /**
     * Executes work bounded by [withTimeout], which throws [TimeoutCancellationException] upon expiry.
     */
    suspend fun executeWithStrictTimeout(timeoutMs: Long, workDurationMs: Long): String {
        return withTimeout(timeoutMs) {
            logger.info("Executing task with timeout {}ms (will take {}ms)", timeoutMs, workDurationMs)
            delay(workDurationMs)
            "Completed in time"
        }
    }

    /**
     * Executes work bounded by [withTimeoutOrNull], returning null when timeout occurs instead of throwing.
     */
    suspend fun executeWithGracefulTimeout(timeoutMs: Long, workDurationMs: Long): String? {
        return withTimeoutOrNull(timeoutMs) {
            logger.info("Executing task with graceful timeout {}ms (will take {}ms)", timeoutMs, workDurationMs)
            delay(workDurationMs)
            "Success"
        }
    }
}
