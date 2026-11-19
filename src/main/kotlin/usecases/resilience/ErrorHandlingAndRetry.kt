package usecases.resilience

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ResilienceService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(ResilienceService::class.java)

    /**
     * Executes an operation with exponential backoff and retry strategy.
     * CRITICAL: [CancellationException] is never swallowed or retried, preserving structured concurrency.
     */
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 50,
        factor: Double = 2.0,
        maxDelayMs: Long = 1000,
        retryableExceptions: Set<Class<out Throwable>> = setOf(IOException::class.java, IllegalStateException::class.java),
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelayMs
        for (attempt in 1..maxAttempts) {
            try {
                logger.info("Attempt {}/{}", attempt, maxAttempts)
                return block(attempt)
            } catch (e: CancellationException) {
                // NEVER catch/swallow CancellationException in retry loops
                logger.warn("Task was cancelled during attempt {}. Propagating cancellation.", attempt)
                throw e
            } catch (e: Throwable) {
                val isRetryable = retryableExceptions.any { it.isInstance(e) }
                if (attempt == maxAttempts || !isRetryable) {
                    logger.error("Exhausted all {} attempts or encountered non-retryable error: {}", attempt, e.message)
                    throw e
                }
                logger.warn("Attempt {} failed with [{}]. Retrying in {}ms...", attempt, e.message, currentDelay)
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        throw IllegalStateException("Unexpected end of retry loop")
    }

    /**
     * Demonstrates [SupervisorJob] vs standard [Job] in a CoroutineScope.
     * With [SupervisorJob], one failing child task does NOT cancel remaining child tasks.
     */
    suspend fun executeSupervisedTasks(
        taskCount: Int,
        failingTaskIndex: Int
    ): Pair<List<Int>, List<Throwable>> = coroutineScope {
        val supervisor = SupervisorJob(coroutineContext[Job])
        val supervisedScope = CoroutineScope(coroutineContext + supervisor + dispatcher)

        val successfulTasks = CopyOnWriteArrayList<Int>()
        val capturedErrors = CopyOnWriteArrayList<Throwable>()

        val jobs = (1..taskCount).map { id ->
            supervisedScope.launch {
                try {
                    delay(id * 20L)
                    if (id == failingTaskIndex) {
                        logger.warn("Simulated failure in supervised task #{}", id)
                        throw IllegalStateException("Task #$id failed purposefully")
                    }
                    successfulTasks.add(id)
                    logger.info("Supervised task #{} completed successfully", id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    capturedErrors.add(e)
                }
            }
        }

        jobs.joinAll()
        supervisor.cancel() // Clean up scope supervisor job

        successfulTasks.toList() to capturedErrors.toList()
    }
}
