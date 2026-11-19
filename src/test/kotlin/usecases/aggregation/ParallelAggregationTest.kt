package usecases.aggregation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ParallelAggregationTest {

    @Test
    fun `aggregateStrict should successfully aggregate all sources in parallel`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = AggregationService(dispatcher = testDispatcher)

        val dashboard = service.aggregateStrict("user-123")

        assertEquals("user-123", dashboard.profile.id)
        assertEquals("Alice Developer", dashboard.profile.name)
        assertEquals(2, dashboard.orders.size)
        assertEquals(2, dashboard.recommendations.size)
        assertEquals("strict", dashboard.source)
    }

    @Test
    fun `aggregateStrict should fail-fast when a single dependency fails`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = object : AggregationService(dispatcher = testDispatcher) {
            override suspend fun fetchRecommendations(userId: String, shouldFail: Boolean): List<Recommendation> {
                throw IllegalStateException("Simulated recommendation outage")
            }
        }

        assertThrows<IllegalStateException> {
            service.aggregateStrict("user-123")
        }
    }

    @Test
    fun `aggregateResilient should provide fallback when recommendation service fails`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = AggregationService(dispatcher = testDispatcher)

        val dashboard = service.aggregateResilient("user-123", shouldFailRecs = true)

        assertEquals("user-123", dashboard.profile.id)
        assertEquals(2, dashboard.orders.size)
        assertEquals(1, dashboard.recommendations.size)
        assertEquals("FALLBACK-DEFAULT", dashboard.recommendations.first().itemId)
        assertEquals("degraded", dashboard.source)
    }
}
