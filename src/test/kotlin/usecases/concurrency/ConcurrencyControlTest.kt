package usecases.concurrency

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrencyControlTest {

    @Test
    fun `MutexProtectedCounter ensures thread safety under concurrent increments`() = runTest {
        val counter = MutexProtectedCounter()
        val totalOperations = 100

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(totalOperations) {
                    launch {
                        counter.increment()
                    }
                }
            }
        }

        assertEquals(totalOperations, counter.count, "Mutex must prevent race conditions and lost updates")
    }

    @Test
    fun `ConfinedDispatcherCounter ensures thread safety via single-thread confinement`() = runTest {
        val counter = ConfinedDispatcherCounter()
        val totalOperations = 100

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(totalOperations) {
                    launch {
                        counter.increment()
                    }
                }
            }
        }

        assertEquals(totalOperations, counter.count, "Thread confinement must guarantee sequential mutation")
    }

    @Test
    fun `BoundedRateLimiter restricts peak concurrency to specified permits`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val maxPermits = 3
        val limiter = BoundedRateLimiter(maxConcurrentRequests = maxPermits, dispatcher = testDispatcher)
        val totalTasks = 15

        coroutineScope {
            repeat(totalTasks) { id ->
                launch(testDispatcher) {
                    limiter.executeBounded("task-$id") {
                        delay(50)
                        "result-$id"
                    }
                }
            }
        }

        assertTrue(
            limiter.getPeakConcurrency() <= maxPermits,
            "Peak concurrency (${limiter.getPeakConcurrency()}) should not exceed max permits ($maxPermits)"
        )
    }
}
