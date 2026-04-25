package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.ThemeRepository
import com.tinyoscillator.domain.model.ThemeDataProgress
import com.tinyoscillator.presentation.settings.loadKiwoomConfig
import com.tinyoscillator.presentation.settings.loadThemeExchangeFilter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class ThemeUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val themeRepository: ThemeRepository
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "테마 데이터 수집"
    override val notificationId = CollectionNotificationHelper.THEME_NOTIFICATION_ID

    override val maxDurationMs = 30L * 60 * 1000

    override suspend fun doCollectionWork(): Result {
        Timber.d("테마 업데이트 워커 시작 (attempt: $runAttemptCount)")

        showInitialNotification("테마 데이터 수집 준비 중...")

        val config = loadKiwoomConfig(applicationContext)
        if (!config.isValid()) {
            Timber.w("Kiwoom 자격증명 미설정 → 테마 워커 건너뜀")
            updateProgress("Kiwoom 자격증명 미설정", STATUS_ERROR)
            showCompletion("Kiwoom 자격증명이 설정되지 않았습니다.", isError = true)
            saveLog(LABEL, STATUS_ERROR, "Kiwoom 자격증명 미설정")
            return Result.success()
        }

        val exchange = loadThemeExchangeFilter(applicationContext)

        var lastResult: Result = Result.success()
        themeRepository.updateAll(config, exchange).collect { progress ->
            when (progress) {
                is ThemeDataProgress.Success -> {
                    Timber.d("테마 업데이트 완료: ${progress.themeCount} 테마, ${progress.stockCount} 종목")
                    val msg = "완료: 테마 ${progress.themeCount}개, 종목 ${progress.stockCount}건"
                    updateProgress(msg, STATUS_SUCCESS, 1f)
                    showCompletion(msg)
                    saveLog(LABEL, STATUS_SUCCESS, msg)
                    lastResult = Result.success()
                }
                is ThemeDataProgress.Error -> {
                    Timber.e("테마 업데이트 실패: ${progress.message}")
                    updateProgress(progress.message, STATUS_ERROR)
                    showCompletion("오류: ${progress.message}", isError = true)
                    saveLog(LABEL, STATUS_ERROR, progress.message)
                    lastResult = if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                is ThemeDataProgress.Loading -> {
                    Timber.d("테마 업데이트 진행: ${progress.message}")
                    updateProgress(progress.message, STATUS_RUNNING, progress.progress)
                    updateNotification(progress.message, (progress.progress * 100).toInt())
                }
            }
        }

        return lastResult
    }

    companion object {
        const val WORK_NAME = "theme_daily_update"
        const val MANUAL_WORK_NAME = "theme_manual_update"
        const val TAG = "collection_theme"
        const val LABEL = "테마"
    }
}
