package usecases.resilience

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorHandlingAndRetryTest {

    @Test
    fun `retryWithBackoff succeeds after transient failures`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val resilienceService = ResilienceService(dispatcher = testDispatcher)
        val attempts = AtomicInteger(0)

        val result = resilienceService.retryWithBackoff(
            maxAttempts = 3,
            initialDelayMs = 10,
            factor = 2.0
        ) { attempt ->
            val current = attempts.incrementAndGet()
            if (current < 3) {
                throw IOException("Temporary network glitch")
            }
            "SUCCESS_DATA"
        }

        assertEquals("SUCCESS_DATA", result)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `retryWithBackoff throws exception when max attempts exhausted`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val resilienceService = ResilienceService(dispatcher = testDispatcher)
        val attempts = AtomicInteger(0)

        assertThrows<IOException> {
            resilienceService.retryWithBackoff(
                maxAttempts = 2,
                initialDelayMs = 10
            ) {
                attempts.incrementAndGet()
                throw IOException("Persistent failure")
            }
        }

        assertEquals(2, attempts.get())
    }

    @Test
    fun `retryWithBackoff immediately propagates CancellationException without retrying`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val resilienceService = ResilienceService(dispatcher = testDispatcher)
        val attempts = AtomicInteger(0)

        assertThrows<CancellationException> {
            resilienceService.retryWithBackoff(
                maxAttempts = 5,
                initialDelayMs = 10
            ) {
                attempts.incrementAndGet()
                throw CancellationException("Cancelled from caller")
            }
        }

        assertEquals(1, attempts.get(), "CancellationException must not be caught as retryable error")
    }

    @Test
    fun `executeSupervisedTasks isolates child task failure`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val resilienceService = ResilienceService(dispatcher = testDispatcher)

        val (succeeded, errors) = resilienceService.executeSupervisedTasks(taskCount = 4, failingTaskIndex = 2)

        assertEquals(3, succeeded.size)
        assertTrue(succeeded.containsAll(listOf(1, 3, 4)))
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalStateException)
    }
}
