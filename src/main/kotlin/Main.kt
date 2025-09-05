import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main() = runBlocking {
    val logger: Logger = LoggerFactory.getLogger("CoroutinesPlayground")
    logger.info("Coroutines example")
    
    // Launch a coroutine in the background
    val job = launch {
        delay(1000L)
        logger.info("World!")
    }
    
    // Continue execution in the main coroutine
    logger.info("Hello, ")
    
    // Wait for the background coroutine to complete
    job.join()

    logger.info("Done!")
}

/**
 * Example function that demonstrates async/await pattern with coroutines
 */
suspend fun fetchUserData(): String {
    delay(500L) // Simulate network delay
    return "User data"
}

/**
 * Example function that demonstrates parallel execution with coroutines
 */
suspend fun fetchDataInParallel() = coroutineScope {
    val result1 = async { fetchUserData() }
    val result2 = async { 
        delay(300L)
        "Additional data" 
    }
    
    // Wait for both results
    "${result1.await()} and ${result2.await()}"
}