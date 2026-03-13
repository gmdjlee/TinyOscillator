package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.presentation.settings.loadMarketDepositCollectionPeriod
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class MarketDepositUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MarketIndicatorRepository
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "자금 동향 데이터 수집"
    override val notificationId = CollectionNotificationHelper.DEPOSIT_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("자금 동향 업데이트 워커 시작 (attempt: $runAttemptCount)")

        CollectionNotificationHelper.createChannel(applicationContext)
        setForeground(createForegroundInfo("자금 동향 데이터 수집 준비 중..."))

        val period = loadMarketDepositCollectionPeriod(applicationContext)

        updateProgress("자금 동향 데이터 수집 중...", STATUS_RUNNING, 0.1f)
        updateNotification("자금 동향 데이터 수집 중...", 10)

        val chartData = repository.getOrUpdateMarketData(
            daysBack = period.daysBack,
            onProgress = { message, progressPercent ->
                val p = (progressPercent.toFloat() / 100f).coerceIn(0.1f, 0.9f)
                try {
                    updateNotification(message, (p * 100).toInt())
                } catch (_: Exception) { /* ignore notification failures */ }
            }
        )

        return if (chartData != null) {
            val msg = "완료: ${chartData.dates.size}건 데이터 (${period.daysBack}일)"
            Timber.d("자금 동향 업데이트 완료: $msg")
            updateProgress(msg, STATUS_SUCCESS, 1f)
            showCompletion(msg)
            Result.success()
        } else if (runAttemptCount < 3) {
            Timber.w("자금 동향 업데이트 실패, 재시도 예정")
            updateProgress("자금 동향 업데이트 실패, 재시도 예정", STATUS_ERROR)
            showCompletion("자금 동향 업데이트 실패, 재시도합니다.", isError = true)
            Result.retry()
        } else {
            Timber.e("자금 동향 업데이트 최종 실패")
            updateProgress("자금 동향 업데이트 최종 실패", STATUS_ERROR)
            showCompletion("자금 동향 업데이트 최종 실패", isError = true)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "market_deposit_daily_update"
        const val MANUAL_WORK_NAME = "market_deposit_manual_update"
        const val TAG = "collection_deposit"
    }
}
