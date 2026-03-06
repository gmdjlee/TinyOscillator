package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltWorker
class MarketOscillatorUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MarketIndicatorRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("시장지표 업데이트 워커 시작 (attempt: $runAttemptCount)")

        val kospiResult = repository.updateMarketData("KOSPI")
        if (kospiResult.isFailure) {
            Timber.e("KOSPI 업데이트 실패: ${kospiResult.exceptionOrNull()?.message}")
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        delay(KRX_RATE_LIMIT_MS)

        val kosdaqResult = repository.updateMarketData("KOSDAQ")
        if (kosdaqResult.isFailure) {
            Timber.e("KOSDAQ 업데이트 실패: ${kosdaqResult.exceptionOrNull()?.message}")
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val kospiCount = kospiResult.getOrNull() ?: 0
        val kosdaqCount = kosdaqResult.getOrNull() ?: 0
        Timber.d("시장지표 업데이트 완료: KOSPI $kospiCount, KOSDAQ $kosdaqCount")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "market_oscillator_daily_update"
        private const val KRX_RATE_LIMIT_MS = 5000L
    }
}
