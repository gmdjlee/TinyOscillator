package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("ETF 업데이트 워커 시작 (attempt: $runAttemptCount)")

        CollectionNotificationHelper.createChannel(applicationContext)
        setForeground(createForegroundInfo("ETF 데이터 수집 준비 중..."))

        val creds = loadKrxCredentials(applicationContext)
        if (creds.id.isBlank() || creds.password.isBlank()) {
            Timber.w("KRX 자격증명 미설정 → 워커 건너뜀")
            updateProgress("KRX 자격증명 미설정", STATUS_ERROR)
            showCompletion("KRX 자격증명이 설정되지 않았습니다.", isError = true)
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
                    lastResult = Result.success()
                }
                is EtfDataProgress.Error -> {
                    Timber.e("ETF 업데이트 실패: ${progress.message}")
                    updateProgress(progress.message, STATUS_ERROR)
                    showCompletion("오류: ${progress.message}", isError = true)
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

    private suspend fun updateProgress(message: String, status: String, progress: Float = 0f) {
        setProgress(workDataOf(
            KEY_PROGRESS to progress,
            KEY_MESSAGE to message,
            KEY_STATUS to status
        ))
    }

    private fun updateNotification(message: String, progress: Int) {
        val notification = CollectionNotificationHelper.buildProgressNotification(
            applicationContext, "ETF 데이터 수집", message, progress
        )
        CollectionNotificationHelper.showNotification(
            applicationContext, CollectionNotificationHelper.ETF_NOTIFICATION_ID, notification
        )
    }

    private fun showCompletion(message: String, isError: Boolean = false) {
        val notification = CollectionNotificationHelper.buildCompletionNotification(
            applicationContext, "ETF 데이터 수집", message, isError
        )
        CollectionNotificationHelper.showNotification(
            applicationContext, CollectionNotificationHelper.ETF_NOTIFICATION_ID, notification
        )
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = CollectionNotificationHelper.buildProgressNotification(
            applicationContext, "ETF 데이터 수집", message, indeterminate = true
        )
        return ForegroundInfo(
            CollectionNotificationHelper.ETF_NOTIFICATION_ID,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val WORK_NAME = "etf_daily_update"
        const val MANUAL_WORK_NAME = "etf_manual_update"
        const val TAG = "collection_etf"
    }
}
