package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusDataProgress
import com.tinyoscillator.presentation.settings.loadConsensusCollectionPeriod
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@HiltWorker
class ConsensusUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val consensusRepository: ConsensusRepository
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "리포트 데이터 수집"
    override val notificationId = CollectionNotificationHelper.CONSENSUS_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("리포트 업데이트 워커 시작 (attempt: $runAttemptCount)")

        CollectionNotificationHelper.createChannel(applicationContext)
        setForeground(createForegroundInfo("리포트 데이터 수집 준비 중..."))

        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 사용자 설정 수집 기간 로드
        val period = loadConsensusCollectionPeriod(applicationContext)
        val backfillDays = period.daysBack.toLong()

        // DB의 마지막 수집일 다음날부터 오늘까지 수집
        val latestDate = consensusRepository.getLatestDate()
        val startDate = if (latestDate != null) {
            val lastDate = LocalDate.parse(latestDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val nextDay = lastDate.plusDays(1)
            val maxPast = today.minusDays(backfillDays)
            if (nextDay.isBefore(maxPast)) maxPast.format(DateTimeFormatter.ISO_LOCAL_DATE)
            else nextDay.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            // 데이터가 없으면 설정된 기간만큼 수집
            today.minusDays(backfillDays).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }

        if (startDate > todayStr) {
            Timber.d("리포트 이미 최신 상태 (마지막: $latestDate)")
            saveLog(LABEL, STATUS_SUCCESS, "이미 최신 상태 (마지막: $latestDate)")
            return Result.success()
        }

        Timber.d("리포트 수집 범위: $startDate ~ $todayStr")

        var lastResult: Result = Result.success()
        consensusRepository.collectReports(startDate, todayStr).collect { progress ->
            when (progress) {
                is ConsensusDataProgress.Success -> {
                    val msg = "완료: 리포트 ${progress.count}건 수집"
                    Timber.d("리포트 업데이트 완료: ${progress.count}건")
                    updateProgress(msg, STATUS_SUCCESS, 1f)
                    showCompletion(msg)
                    saveLog(LABEL, STATUS_SUCCESS, msg)
                    lastResult = Result.success()
                }
                is ConsensusDataProgress.Error -> {
                    Timber.e("리포트 업데이트 실패: ${progress.message}")
                    updateProgress(progress.message, STATUS_ERROR)
                    showCompletion("오류: ${progress.message}", isError = true)
                    saveLog(LABEL, STATUS_ERROR, progress.message)
                    lastResult = if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                is ConsensusDataProgress.Loading -> {
                    Timber.d("리포트 업데이트 진행: ${progress.message}")
                    updateProgress(progress.message, STATUS_RUNNING, progress.progress)
                    updateNotification(progress.message, (progress.progress * 100).toInt())
                }
            }
        }

        return lastResult
    }

    companion object {
        const val WORK_NAME = "consensus_daily_update"
        const val MANUAL_WORK_NAME = "consensus_manual_update"
        const val TAG = "collection_consensus"
        const val LABEL = "리포트"
    }
}
