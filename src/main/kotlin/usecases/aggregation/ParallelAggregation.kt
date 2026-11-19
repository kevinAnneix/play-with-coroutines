package usecases.aggregation

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

data class UserProfile(val id: String, val name: String, val email: String)
data class Order(val orderId: String, val item: String, val amount: Double)
data class Recommendation(val itemId: String, val score: Double)

data class DashboardData(
    val profile: UserProfile,
    val orders: List<Order>,
    val recommendations: List<Recommendation>,
    val source: String = "complete"
)

open class AggregationService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val logger = LoggerFactory.getLogger(AggregationService::class.java)

    open suspend fun fetchUserProfile(userId: String): UserProfile = withContext(dispatcher) {
        logger.info("Fetching profile for user: {}", userId)
        delay(120) // Simulated I/O latency
        UserProfile(id = userId, name = "Alice Developer", email = "alice@example.com")
    }

    open suspend fun fetchOrders(userId: String): List<Order> = withContext(dispatcher) {
        logger.info("Fetching orders for user: {}", userId)
        delay(180)
        listOf(
            Order("ORD-101", "Kotlin in Action", 39.99),
            Order("ORD-102", "Mechanical Keyboard", 129.50)
        )
    }

    open suspend fun fetchRecommendations(userId: String, shouldFail: Boolean = false): List<Recommendation> = withContext(dispatcher) {
        logger.info("Fetching recommendations for user: {} (shouldFail={})", userId, shouldFail)
        delay(90)
        if (shouldFail) {
            throw IllegalStateException("Recommendation service temporarily unavailable")
        }
        listOf(
            Recommendation("ITEM-901", 0.98),
            Recommendation("ITEM-902", 0.85)
        )
    }

    /**
     * Strict aggregation using [coroutineScope]:
     * All asynchronous tasks are launched concurrently. If any task fails, the entire scope fails
     * and cancels all other sibling jobs immediately.
     */
    suspend fun aggregateStrict(userId: String): DashboardData = coroutineScope {
        val profileDeferred = async { fetchUserProfile(userId) }
        val ordersDeferred = async { fetchOrders(userId) }
        val recsDeferred = async { fetchRecommendations(userId) }

        DashboardData(
            profile = profileDeferred.await(),
            orders = ordersDeferred.await(),
            recommendations = recsDeferred.await(),
            source = "strict"
        )
    }

    /**
     * Resilient aggregation using [supervisorScope]:
     * Non-critical failures (such as recommendation service) are isolated and gracefully degraded
     * with fallback values without cancelling the core user profile or order fetching tasks.
     */
    suspend fun aggregateResilient(userId: String, shouldFailRecs: Boolean = false): DashboardData = supervisorScope {
        val profileDeferred = async { fetchUserProfile(userId) }
        val ordersDeferred = async { fetchOrders(userId) }
        val recsDeferred = async {
            try {
                fetchRecommendations(userId, shouldFail = shouldFailRecs)
            } catch (e: Exception) {
                logger.warn("Non-critical recommendation service failed (${e.message}), providing fallback defaults")
                listOf(Recommendation("FALLBACK-DEFAULT", 0.0))
            }
        }

        DashboardData(
            profile = profileDeferred.await(),
            orders = ordersDeferred.await(),
            recommendations = recsDeferred.await(),
            source = if (shouldFailRecs) "degraded" else "complete"
        )
    }
}
