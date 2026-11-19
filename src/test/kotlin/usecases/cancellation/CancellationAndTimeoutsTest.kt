package usecases.cancellation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CancellationAndTimeoutsTest {

    @Test
    fun `processItemsCooperative should complete all items normally`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val processor = BatchProcessor(dispatcher = testDispatcher)

        val report = processor.processItemsCooperative(totalItems = 50, itemDelayMs = 10)

        assertEquals(50, report.processedItems)
        assertTrue(report.completedNormally)
    }

    @Test
    fun `processItemsCooperative should cancel and run NonCancellable cleanup callback`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val processor = BatchProcessor(dispatcher = testDispatcher)
        val cleanupCalled = AtomicBoolean(false)

        val job = launch(testDispatcher) {
            processor.processItemsCooperative(
                totalItems = 100,
                itemDelayMs = 10,
                onCleanupCallback = {
                    cleanupCalled.set(true)
                }
            )
        }

        // Advance partway
        advanceTimeBy(100)
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(cleanupCalled.get(), "NonCancellable cleanup block should have executed successfully")
    }

    @Test
    fun `executeWithStrictTimeout throws TimeoutCancellationException on expiry`() = runTest {
        val processor = BatchProcessor()

        assertThrows<TimeoutCancellationException> {
            processor.executeWithStrictTimeout(timeoutMs = 100, workDurationMs = 500)
        }
    }

    @Test
    fun `executeWithGracefulTimeout returns null on timeout expiry and value on completion`() = runTest {
        val processor = BatchProcessor()

        val timedOutResult = processor.executeWithGracefulTimeout(timeoutMs = 100, workDurationMs = 500)
        assertNull(timedOutResult)

        val successResult = processor.executeWithGracefulTimeout(timeoutMs = 500, workDurationMs = 100)
        assertEquals("Success", successResult)
    }
}
