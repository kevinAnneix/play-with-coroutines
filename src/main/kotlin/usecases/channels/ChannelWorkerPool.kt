package usecases.channels

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

data class WorkTask(val id: Int, val payload: String, val complexityMs: Long)
data class WorkResult(val taskId: Int, val processedByWorker: String, val output: String)

class WorkerPipeline(
    private val workerCount: Int = 3,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val logger = LoggerFactory.getLogger(WorkerPipeline::class.java)

    /**
     * Demonstrates Fan-out (distributing work from 1 channel to N workers)
     * and Fan-in (aggregating results from N workers into 1 result channel).
     */
    suspend fun executePipeline(tasks: List<WorkTask>): List<WorkResult> = coroutineScope {
        val taskChannel = Channel<WorkTask>(capacity = Channel.BUFFERED)
        val resultChannel = Channel<WorkResult>(capacity = Channel.BUFFERED)
        val collectedResults = CopyOnWriteArrayList<WorkResult>()

        // 1. Launch Producer: pushes tasks into the channel and closes it when done
        launch(dispatcher) {
            logger.info("Producer starting: publishing {} tasks", tasks.size)
            for (task in tasks) {
                taskChannel.send(task)
                logger.debug("Task [{}] sent to queue", task.id)
            }
            taskChannel.close()
            logger.info("Producer finished and channel closed")
        }

        // 2. Launch N Worker Coroutines (Fan-out)
        val workerJobs = (1..workerCount).map { workerId ->
            launch(dispatcher) {
                val workerName = "Worker-$workerId"
                logger.info("[{}] started listening for tasks", workerName)
                for (task in taskChannel) {
                    logger.info("[{}] processing task [{}] (simulated {}ms)", workerName, task.id, task.complexityMs)
                    delay(task.complexityMs)
                    val result = WorkResult(
                        taskId = task.id,
                        processedByWorker = workerName,
                        output = "PROCESSED_${task.payload.uppercase()}"
                    )
                    resultChannel.send(result)
                }
                logger.info("[{}] finished all available work", workerName)
            }
        }

        // 3. Launch Result Collector (Fan-in)
        val collectorJob = launch(dispatcher) {
            for (res in resultChannel) {
                logger.info("Collector received result for task [{}] from {}", res.taskId, res.processedByWorker)
                collectedResults.add(res)
            }
        }

        // 4. Await workers completion, then close the result channel
        workerJobs.joinAll()
        resultChannel.close()
        collectorJob.join()

        logger.info("Pipeline completed. Total results collected: {}", collectedResults.size)
        collectedResults.sortedBy { it.taskId }
    }
}
