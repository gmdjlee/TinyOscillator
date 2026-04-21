package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 만료 캐시 통합 정리 워커 (일일 06:00 KST).
 *
 * 원래 feature_cache 전용이었으나 AnalysisCache/FinancialCache 만료 row가 축적되는 문제로
 * 3종 TTL 캐시를 통합 정리한다 (WORK_NAME은 기존 스케줄 호환을 위해 유지).
 *
 * 정리 대상:
 * - feature_cache: entry별 computed_at + ttl_ms < now
 * - financial_cache: cachedAt < now - 7일 (FinancialRepository의 24h TTL × 7)
 * - analysis_cache: date < now - 730일 (StockRepository.CACHE_RETENTION_DAYS와 일치)
 */
@HiltWorker
class FeatureCacheEvictionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val featureCacheDao: FeatureCacheDao,
    private val financialCacheDao: FinancialCacheDao,
    private val analysisCacheDao: AnalysisCacheDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()

            featureCacheDao.evictExpired(now)

            val financialCutoff = now - FINANCIAL_CACHE_RETENTION_MS
            financialCacheDao.deleteExpired(financialCutoff)

            val analysisCutoff = LocalDate.now()
                .minusDays(ANALYSIS_CACHE_RETENTION_DAYS)
                .format(DateTimeFormatter.ISO_DATE)
            val analysisDeleted = analysisCacheDao.deleteAllOlderThan(analysisCutoff)

            Timber.d(
                "만료 캐시 정리 완료 (feature TTL, financial <%d days, analysis <%s, %d rows)",
                FINANCIAL_CACHE_RETENTION_MS / (24 * 60 * 60 * 1000L),
                analysisCutoff,
                analysisDeleted,
            )
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "만료 캐시 정리 실패")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val FINANCIAL_CACHE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000L
        private const val ANALYSIS_CACHE_RETENTION_DAYS = 730L

        const val WORK_NAME = "feature_cache_eviction"
        const val MANUAL_WORK_NAME = "feature_cache_eviction_manual"
        const val TAG = "feature_cache_eviction_tag"
    }
}
