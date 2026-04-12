package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.presentation.settings.loadEtfCollectionPeriod
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class EtfUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val etfRepository: EtfRepository
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "ETF 데이터 수집"
    override val notificationId = CollectionNotificationHelper.ETF_NOTIFICATION_ID

    override val maxDurationMs = 45L * 60 * 1000  // ETF 수집은 대량이므로 45분

    override suspend fun doCollectionWork(): Result {
        Timber.d("ETF 업데이트 워커 시작 (attempt: $runAttemptCount)")

        showInitialNotification("ETF 데이터 수집 준비 중...")

        val creds = loadKrxCredentials(applicationContext)
        if (creds.id.isBlank() || creds.password.isBlank()) {
            Timber.w("KRX 자격증명 미설정 → 워커 건너뜀")
            updateProgress("KRX 자격증명 미설정", STATUS_ERROR)
            showCompletion("KRX 자격증명이 설정되지 않았습니다.", isError = true)
            saveLog(LABEL, STATUS_ERROR, "KRX 자격증명 미설정")
            return Result.success()
        }

        val keywords = loadEtfKeywordFilter(applicationContext)
        val period = loadEtfCollectionPeriod(applicationContext)

        var lastResult: Result = Result.success()
        etfRepository.updateData(creds, keywords, daysBack = period.daysBack).collect { progress ->
            when (progress) {
                is EtfDataProgress.Success -> {
                    Timber.d("ETF 업데이트 완료: ${progress.etfCount} ETFs, ${progress.holdingCount} holdings")
                    val msg = "완료: ETF ${progress.etfCount}개, 보유종목 ${progress.holdingCount}건"
                    updateProgress(msg, STATUS_SUCCESS, 1f)
                    showCompletion(msg)
                    saveLog(LABEL, STATUS_SUCCESS, msg)
                    lastResult = Result.success()
                }
                is EtfDataProgress.Error -> {
                    Timber.e("ETF 업데이트 실패: ${progress.message}")
                    updateProgress(progress.message, STATUS_ERROR)
                    showCompletion("오류: ${progress.message}", isError = true)
                    saveLog(LABEL, STATUS_ERROR, progress.message)
                    lastResult = if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                is EtfDataProgress.Loading -> {
                    Timber.d("ETF 업데이트 진행: ${progress.message}")
                    updateProgress(progress.message, STATUS_RUNNING, progress.progress)
                    updateNotification(progress.message, (progress.progress * 100).toInt())
                }
            }
        }

        return lastResult
    }

    companion object {
        const val WORK_NAME = "etf_daily_update"
        const val MANUAL_WORK_NAME = "etf_manual_update"
        const val TAG = "collection_etf"
        const val LABEL = "ETF"
    }
}
