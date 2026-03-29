package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.FearGreedRepository
import com.tinyoscillator.presentation.settings.loadFearGreedCollectionPeriod
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class FearGreedUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: FearGreedRepository
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "Fear & Greed 업데이트"
    override val notificationId = CollectionNotificationHelper.FEAR_GREED_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("Fear & Greed 업데이트 워커 시작 (attempt: $runAttemptCount)")

        CollectionNotificationHelper.createChannel(applicationContext)
        setForeground(createForegroundInfo("Fear & Greed 데이터 수집 준비 중..."))

        val creds = loadKrxCredentials(applicationContext)
        if (creds.id.isBlank() || creds.password.isBlank()) {
            Timber.w("KRX 자격증명 미설정, Fear & Greed 업데이트 건너뜀")
            updateProgress("KRX 자격증명 미설정", STATUS_ERROR)
            showCompletion("KRX 자격증명이 설정되지 않았습니다.", isError = true)
            saveLog(LABEL, STATUS_ERROR, "KRX 자격증명 미설정")
            return Result.failure()
        }

        updateProgress("Fear & Greed 데이터 수집 중...", STATUS_RUNNING, 0.1f)
        updateNotification("Fear & Greed 데이터 수집 중...", 20)

        val collectionDays = loadFearGreedCollectionPeriod(applicationContext).daysBack
        val result = repository.updateFearGreed(creds.id, creds.password, collectionDays)
        if (result.isFailure) {
            val msg = "Fear & Greed 업데이트 실패: ${result.exceptionOrNull()?.message}"
            Timber.e(msg)
            updateProgress(msg, STATUS_ERROR)
            showCompletion(msg, isError = true)
            saveLog(LABEL, STATUS_ERROR, msg, result.exceptionOrNull()?.stackTraceToString())
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val count = result.getOrNull() ?: 0
        val msg = "완료: ${count}건 업데이트"
        Timber.d("Fear & Greed 업데이트 완료: $msg")
        updateProgress(msg, STATUS_SUCCESS, 1f)
        showCompletion(msg)
        saveLog(LABEL, STATUS_SUCCESS, msg)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "fear_greed_daily_update"
        const val MANUAL_WORK_NAME = "fear_greed_manual_update"
        const val TAG = "collection_feargreed"
        const val LABEL = "Fear & Greed"
    }
}
