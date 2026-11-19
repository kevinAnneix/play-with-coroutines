package usecases.concurrency

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class UnsafeCounter {
    var count = 0
        private set

    fun increment() {
        count++
    }
}

class MutexProtectedCounter {
    private val mutex = Mutex()
    var count = 0
        private set

    suspend fun increment() = mutex.withLock {
        val current = count
        delay(1) // Introduce suspension point to simulate state evaluation
        count = current + 1
    }
}

/**
 * Demonstrates thread confinement via a single-threaded dispatcher (limitedParallelism = 1).
 * NOTE: Thread confinement protects against parallel thread access. However, if a suspension point
 * (such as delay) occurs between read and write, other coroutines on the same thread can interleave.
 * Therefore, state mutations within confined contexts should be atomic / non-suspending blocks,
 * or use [Mutex] if holding exclusion across suspension points is required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfinedDispatcherCounter(
    private val confinedDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {
    var count = 0
        private set

    suspend fun increment() = withContext(confinedDispatcher) {
        count++
    }
}

class BoundedRateLimiter(
    maxConcurrentRequests: Int = 3,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(BoundedRateLimiter::class.java)
    private val semaphore = Semaphore(permits = maxConcurrentRequests)
    private val activeRequests = AtomicInteger(0)
    private val peakConcurrency = AtomicInteger(0)

    suspend fun <T> executeBounded(taskId: String, block: suspend () -> T): T = withContext(dispatcher) {
        semaphore.withPermit {
            val currentActive = activeRequests.incrementAndGet()
            peakConcurrency.updateAndGet { currentPeak -> maxOf(currentPeak, currentActive) }
            logger.info("Task [{}] acquired permit. Active concurrent tasks: {}", taskId, currentActive)
            try {
                block()
            } finally {
                val remaining = activeRequests.decrementAndGet()
                logger.info("Task [{}] released permit. Active concurrent tasks: {}", taskId, remaining)
            }
        }
    }

    fun getPeakConcurrency(): Int = peakConcurrency.get()
}
