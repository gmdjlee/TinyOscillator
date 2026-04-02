package com.tinyoscillator.data.engine

import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.entity.FeatureCacheEntity
import com.tinyoscillator.domain.model.CacheStats
import com.tinyoscillator.domain.model.FeatureKey
import com.tinyoscillator.domain.model.FeatureTtl
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FeatureStoreTest {

    private lateinit var dao: FeatureCacheDao
    private lateinit var featureStore: FeatureStore
    private val json = Json { ignoreUnknownKeys = true }
    private val countFlow = MutableStateFlow(0)

    @Serializable
    data class TestResult(val score: Double, val label: String)

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        every { dao.count() } returns countFlow
        featureStore = FeatureStore(dao, json)
    }

    @Test
    fun `getOrCompute calls compute on cache miss`() = runTest {
        val key = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 2))
        coEvery { dao.get(key.asString()) } returns null

        var computeCount = 0
        val result = featureStore.getOrCompute(
            key = key,
            ttl = FeatureTtl.Daily,
            serializer = TestResult.serializer()
        ) {
            computeCount++
            TestResult(0.75, "UP")
        }

        assertEquals(1, computeCount)
        assertEquals(0.75, result.score, 0.001)
        assertEquals("UP", result.label)
        coVerify { dao.upsert(any()) }
    }

    @Test
    fun `getOrCompute returns cached value without calling compute`() = runTest {
        val key = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 2))
        val serialized = json.encodeToString(TestResult.serializer(), TestResult(0.8, "DOWN"))
        val now = System.currentTimeMillis()

        coEvery { dao.get(key.asString()) } returns FeatureCacheEntity(
            key = key.asString(),
            ticker = "005930",
            featureName = "NaiveBayes",
            value = serialized,
            computedAt = now - 1000, // 1 second ago
            ttlMs = FeatureTtl.Daily.ms // 4 hours
        )

        var computeCount = 0
        val result = featureStore.getOrCompute(
            key = key,
            ttl = FeatureTtl.Daily,
            serializer = TestResult.serializer()
        ) {
            computeCount++
            TestResult(0.99, "SHOULD_NOT_REACH")
        }

        assertEquals(0, computeCount)
        assertEquals(0.8, result.score, 0.001)
        assertEquals("DOWN", result.label)
    }

    @Test
    fun `getOrCompute recomputes after TTL expiry`() = runTest {
        val key = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 2))
        val serialized = json.encodeToString(TestResult.serializer(), TestResult(0.5, "OLD"))
        val now = System.currentTimeMillis()

        // Expired entry (computed 5 hours ago, TTL is 4 hours)
        coEvery { dao.get(key.asString()) } returns FeatureCacheEntity(
            key = key.asString(),
            ticker = "005930",
            featureName = "NaiveBayes",
            value = serialized,
            computedAt = now - 5 * 60 * 60 * 1000, // 5 hours ago
            ttlMs = FeatureTtl.Daily.ms // 4 hours
        )

        var computeCount = 0
        val result = featureStore.getOrCompute(
            key = key,
            ttl = FeatureTtl.Daily,
            serializer = TestResult.serializer()
        ) {
            computeCount++
            TestResult(0.9, "FRESH")
        }

        assertEquals(1, computeCount)
        assertEquals(0.9, result.score, 0.001)
        assertEquals("FRESH", result.label)
        coVerify { dao.upsert(any()) }
    }

    @Test
    fun `same key within TTL returns cached without recompute`() = runTest {
        val key = FeatureKey("005930", "Logistic", LocalDate.of(2026, 4, 2))

        // First call: cache miss
        coEvery { dao.get(key.asString()) } returns null

        val result1 = featureStore.getOrCompute(
            key = key,
            ttl = FeatureTtl.Daily,
            serializer = TestResult.serializer()
        ) {
            TestResult(0.65, "FIRST")
        }

        // Capture what was upserted
        val slot = slot<FeatureCacheEntity>()
        coVerify { dao.upsert(capture(slot)) }

        // Second call: return the cached entity
        coEvery { dao.get(key.asString()) } returns slot.captured

        var secondComputeCount = 0
        val result2 = featureStore.getOrCompute(
            key = key,
            ttl = FeatureTtl.Daily,
            serializer = TestResult.serializer()
        ) {
            secondComputeCount++
            TestResult(0.99, "SECOND")
        }

        assertEquals(0, secondComputeCount)
        assertEquals("FIRST", result2.label)
    }

    @Test
    fun `invalidate evicts by ticker`() = runTest {
        featureStore.invalidate("005930")
        coVerify { dao.evictByTicker("005930") }
    }

    @Test
    fun `invalidateAll clears everything`() = runTest {
        featureStore.invalidateAll()
        coVerify { dao.evictAll() }
    }

    @Test
    fun `evictExpired delegates to DAO`() = runTest {
        featureStore.evictExpired()
        coVerify { dao.evictExpired(any()) }
    }

    @Test
    fun `FeatureKey asString format is deterministic`() {
        val key = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 2))
        assertEquals("005930:NaiveBayes:20260402", key.asString())
    }

    @Test
    fun `FeatureKey with different dates produces different keys`() {
        val key1 = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 1))
        val key2 = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 2))
        assertNotEquals(key1.asString(), key2.asString())
    }

    @Test
    fun `FeatureKey with different tickers produces different keys`() {
        val key1 = FeatureKey("005930", "NaiveBayes", LocalDate.of(2026, 4, 2))
        val key2 = FeatureKey("000660", "NaiveBayes", LocalDate.of(2026, 4, 2))
        assertNotEquals(key1.asString(), key2.asString())
    }

    @Test
    fun `FeatureTtl constants have correct values`() {
        assertEquals(15 * 60 * 1000L, FeatureTtl.Intraday.ms)
        assertEquals(4 * 60 * 60 * 1000L, FeatureTtl.Daily.ms)
        assertEquals(24 * 60 * 60 * 1000L, FeatureTtl.Weekly.ms)
        assertEquals(123456L, FeatureTtl.Custom(123456L).ms)
    }

    @Test
    fun `cacheStats emits correct hit and miss counts`() = runTest {
        val key = FeatureKey("005930", "Test", LocalDate.of(2026, 4, 2))
        val serialized = json.encodeToString(TestResult.serializer(), TestResult(0.5, "X"))
        val now = System.currentTimeMillis()

        // Miss
        coEvery { dao.get(any()) } returns null
        featureStore.getOrCompute(key, FeatureTtl.Daily, TestResult.serializer()) {
            TestResult(0.5, "X")
        }

        // Hit
        coEvery { dao.get(any()) } returns FeatureCacheEntity(
            key = key.asString(), ticker = "005930", featureName = "Test",
            value = serialized, computedAt = now, ttlMs = FeatureTtl.Daily.ms
        )
        featureStore.getOrCompute(key, FeatureTtl.Daily, TestResult.serializer()) {
            TestResult(0.5, "X")
        }

        countFlow.value = 1
        val stats = featureStore.cacheStats.first()
        assertEquals(1L, stats.hitCount)
        assertEquals(1L, stats.missCount)
        assertEquals(1, stats.entryCount)
    }

    @Test
    fun `upserted entity has correct key and TTL`() = runTest {
        val key = FeatureKey("005930", "Correlation", LocalDate.of(2026, 4, 2))
        coEvery { dao.get(key.asString()) } returns null

        featureStore.getOrCompute(key, FeatureTtl.Weekly, TestResult.serializer()) {
            TestResult(0.3, "WEAK")
        }

        val slot = slot<FeatureCacheEntity>()
        coVerify { dao.upsert(capture(slot)) }

        assertEquals("005930:Correlation:20260402", slot.captured.key)
        assertEquals("005930", slot.captured.ticker)
        assertEquals("Correlation", slot.captured.featureName)
        assertEquals(FeatureTtl.Weekly.ms, slot.captured.ttlMs)

        // Verify JSON round-trip
        val deserialized = json.decodeFromString(TestResult.serializer(), slot.captured.value)
        assertEquals(0.3, deserialized.score, 0.001)
        assertEquals("WEAK", deserialized.label)
    }
}
