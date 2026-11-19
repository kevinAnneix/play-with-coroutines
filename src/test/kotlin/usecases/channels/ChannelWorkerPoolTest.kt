package usecases.channels

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelWorkerPoolTest {

    @Test
    fun `WorkerPipeline processes all tasks through worker pool and collects results`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = WorkerPipeline(workerCount = 3, dispatcher = testDispatcher)

        val tasks = (1..6).map { id ->
            WorkTask(id = id, payload = "payload-$id", complexityMs = 20L * id)
        }

        val results = pipeline.executePipeline(tasks)

        assertEquals(6, results.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), results.map { it.taskId })
        assertTrue(results.all { it.output.startsWith("PROCESSED_PAYLOAD-") })

        val uniqueWorkers = results.map { it.processedByWorker }.toSet()
        assertTrue(uniqueWorkers.size in 1..3)
    }
}
