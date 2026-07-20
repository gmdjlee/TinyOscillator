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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * 키별 계산 직렬화 락 — 동일 종목이 동시에 분석될 때 캐시 미스가 겹쳐 compute(11-엔진)가
     * 중복 실행되는 것을 막는다. 선행 코루틴이 계산·캐싱을 끝내면 후행은 double-check 캐시 히트로
     * 즉시 반환한다. (모바일 세션 규모에서 키 수는 소수라 맵 성장은 무시 가능.)
     */
    private val keyLocks = ConcurrentHashMap<String, Mutex>()

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

        // 1. 락 없이 캐시 조회 (fast-path — 대부분의 호출은 여기서 반환)
        loadFresh(keyStr, serializer)?.let { return@withContext it }

        // 2. 동일 키 동시 계산 방지 — per-key Mutex로 중복 compute(11-엔진) 차단
        val lock = keyLocks.getOrPut(keyStr) { Mutex() }
        lock.withLock {
            // 3. double-check — 대기 중 선행 코루틴이 이미 계산·캐싱했을 수 있다
            loadFresh(keyStr, serializer)?.let { return@withLock it }

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
    }

    /**
     * TTL 유효한 캐시 항목을 조회해 역직렬화하여 반환, 미스/만료 시 null.
     * 히트 시 통계 카운터를 증가시킨다.
     */
    private suspend fun <T> loadFresh(keyStr: String, serializer: KSerializer<T>): T? {
        val now = System.currentTimeMillis()
        val cached = dao.get(keyStr)
        if (cached != null && (cached.computedAt + cached.ttlMs) > now) {
            hitCounter.incrementAndGet()
            statsUpdate.value = now
            Timber.d("FeatureStore HIT: %s", keyStr)
            return json.decodeFromString(serializer, cached.value)
        }
        return null
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
