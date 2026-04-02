package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Feature Store 만료 캐시 정리 워커
 *
 * 매일 06:00 KST에 실행하여 TTL이 만료된 feature_cache 엔트리를 삭제.
 */
@HiltWorker
class FeatureCacheEvictionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val featureCacheDao: FeatureCacheDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            featureCacheDao.evictExpired(now)
            Timber.d("Feature 캐시 정리 완료")

            // Worker log via EntryPoint
            try {
                val entryPoint = androidx.hilt.work.HiltWorkerFactory::class.java
                    .let { /* log via BaseCollectionWorker pattern if needed */ }
            } catch (_: Exception) { /* non-critical */ }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Feature 캐시 정리 실패")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "feature_cache_eviction"
        const val MANUAL_WORK_NAME = "feature_cache_eviction_manual"
        const val TAG = "feature_cache_eviction_tag"
    }
}
