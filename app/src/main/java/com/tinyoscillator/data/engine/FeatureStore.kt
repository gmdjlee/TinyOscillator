package com.tinyoscillator.data.engine

import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.entity.FeatureCacheEntity
import com.tinyoscillator.domain.model.CacheStats
import com.tinyoscillator.domain.model.FeatureKey
import com.tinyoscillator.domain.model.FeatureTtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature Store — 통계 엔진 결과를 Room DB에 TTL 기반 캐싱
 *
 * - getOrCompute: 캐시 히트 시 역직렬화 반환, 미스 시 compute → 직렬화 → upsert
 * - 종목별/전체 무효화
 * - CacheStats Flow로 히트/미스 카운트 노출
 */
@Singleton
class FeatureStore @Inject constructor(
    private val dao: FeatureCacheDao,
    private val json: Json
) {

    private val hitCounter = AtomicLong(0)
    private val missCounter = AtomicLong(0)
    private val statsUpdate = MutableStateFlow(0L)

    /** 실시간 캐시 통계 */
    val cacheStats: Flow<CacheStats> = combine(
        statsUpdate,
        dao.count()
    ) { _, entryCount ->
        CacheStats(
            hitCount = hitCounter.get(),
            missCount = missCounter.get(),
            entryCount = entryCount
        )
    }

    /**
     * 캐시에서 조회하고, 없거나 만료 시 compute()를 실행하여 캐시에 저장 후 반환.
     *
     * @param key 캐시 키 (ticker:feature:date)
     * @param ttl TTL 정책
     * @param serializer kotlinx.serialization KSerializer
     * @param compute 실제 계산 람다 (캐시 미스 시에만 호출)
     */
    suspend fun <T> getOrCompute(
        key: FeatureKey,
        ttl: FeatureTtl,
        serializer: KSerializer<T>,
        compute: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        val keyStr = key.asString()
        val now = System.currentTimeMillis()

        // 캐시 조회
        val cached = dao.get(keyStr)
        if (cached != null && (cached.computedAt + cached.ttlMs) > now) {
            hitCounter.incrementAndGet()
            statsUpdate.value = now
            Timber.d("FeatureStore HIT: %s", keyStr)
            return@withContext json.decodeFromString(serializer, cached.value)
        }

        // 캐시 미스 — 계산 실행
        missCounter.incrementAndGet()
        Timber.d("FeatureStore MISS: %s", keyStr)

        val result = compute()
        val serialized = json.encodeToString(serializer, result)

        dao.upsert(
            FeatureCacheEntity(
                key = keyStr,
                ticker = key.ticker,
                featureName = key.featureName,
                value = serialized,
                computedAt = System.currentTimeMillis(),
                ttlMs = ttl.ms
            )
        )

        statsUpdate.value = System.currentTimeMillis()
        result
    }

    /**
     * 값을 캐시에 직접 저장 (compute 없이).
     * Worker에서 미리 계산된 결과를 캐시할 때 사용.
     */
    suspend fun <T> put(
        key: FeatureKey,
        ttl: FeatureTtl,
        serializer: KSerializer<T>,
        value: T
    ) = withContext(Dispatchers.IO) {
        val keyStr = key.asString()
        val serialized = json.encodeToString(serializer, value)
        dao.upsert(
            FeatureCacheEntity(
                key = keyStr,
                ticker = key.ticker,
                featureName = key.featureName,
                value = serialized,
                computedAt = System.currentTimeMillis(),
                ttlMs = ttl.ms
            )
        )
        Timber.d("FeatureStore PUT: %s", keyStr)
    }

    /** 특정 종목의 모든 캐시 무효화 */
    suspend fun invalidate(ticker: String) {
        dao.evictByTicker(ticker)
        Timber.d("FeatureStore invalidated: ticker=%s", ticker)
    }

    /** 전체 캐시 삭제 */
    suspend fun invalidateAll() {
        dao.evictAll()
        hitCounter.set(0)
        missCounter.set(0)
        statsUpdate.value = System.currentTimeMillis()
        Timber.d("FeatureStore invalidated all")
    }

    /** 만료된 엔트리 정리 */
    suspend fun evictExpired() {
        dao.evictExpired(System.currentTimeMillis())
    }
}
