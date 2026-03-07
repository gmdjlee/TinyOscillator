package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class MarketDepositUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MarketIndicatorRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("자금 동향 업데이트 워커 시작 (attempt: $runAttemptCount)")

        val chartData = repository.getOrUpdateMarketData(limit = 500)
        return if (chartData != null) {
            Timber.d("자금 동향 업데이트 완료: ${chartData.dates.size} records")
            Result.success()
        } else if (runAttemptCount < 3) {
            Timber.w("자금 동향 업데이트 실패, 재시도 예정")
            Result.retry()
        } else {
            Timber.e("자금 동향 업데이트 최종 실패")
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "market_deposit_daily_update"
    }
}
