package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import com.tinyoscillator.presentation.settings.loadMarketOscillatorCollectionPeriod
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltWorker
class MarketOscillatorUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MarketIndicatorRepository
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "과매수/과매도 데이터 수집"
    override val notificationId = CollectionNotificationHelper.OSCILLATOR_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("시장지표 업데이트 워커 시작 (attempt: $runAttemptCount)")

        CollectionNotificationHelper.createChannel(applicationContext)
        setForeground(createForegroundInfo("과매수/과매도 데이터 수집 준비 중..."))

        val creds = loadKrxCredentials(applicationContext)
        if (creds.id.isBlank() || creds.password.isBlank()) {
            Timber.w("KRX 자격증명 미설정, 시장지표 업데이트 건너뜀")
            updateProgress("KRX 자격증명 미설정", STATUS_ERROR)
            showCompletion("KRX 자격증명이 설정되지 않았습니다.", isError = true)
            return Result.failure()
        }

        val period = loadMarketOscillatorCollectionPeriod(applicationContext)
        val days = period.daysBack

        updateProgress("KOSPI 데이터 수집 중...", STATUS_RUNNING, 0.1f)
        updateNotification("KOSPI 데이터 수집 중...", 20)

        val kospiResult = repository.updateMarketData("KOSPI", creds.id, creds.password, days)
        if (kospiResult.isFailure) {
            val msg = "KOSPI 업데이트 실패: ${kospiResult.exceptionOrNull()?.message}"
            Timber.e(msg)
            updateProgress(msg, STATUS_ERROR)
            showCompletion(msg, isError = true)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        updateProgress("KOSDAQ 데이터 수집 대기 중...", STATUS_RUNNING, 0.5f)
        updateNotification("KOSDAQ 데이터 수집 대기 중...", 50)
        delay(KRX_RATE_LIMIT_MS)

        updateProgress("KOSDAQ 데이터 수집 중...", STATUS_RUNNING, 0.6f)
        updateNotification("KOSDAQ 데이터 수집 중...", 60)

        val kosdaqResult = repository.updateMarketData("KOSDAQ", creds.id, creds.password, days)
        if (kosdaqResult.isFailure) {
            val msg = "KOSDAQ 업데이트 실패: ${kosdaqResult.exceptionOrNull()?.message}"
            Timber.e(msg)
            updateProgress(msg, STATUS_ERROR)
            showCompletion(msg, isError = true)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val kospiCount = kospiResult.getOrNull() ?: 0
        val kosdaqCount = kosdaqResult.getOrNull() ?: 0
        val msg = "완료: KOSPI ${kospiCount}건, KOSDAQ ${kosdaqCount}건 (${days}일)"
        Timber.d("시장지표 업데이트 완료: $msg")
        updateProgress(msg, STATUS_SUCCESS, 1f)
        showCompletion(msg)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "market_oscillator_daily_update"
        const val MANUAL_WORK_NAME = "market_oscillator_manual_update"
        const val TAG = "collection_oscillator"
        private const val KRX_RATE_LIMIT_MS = 5000L
    }
}
