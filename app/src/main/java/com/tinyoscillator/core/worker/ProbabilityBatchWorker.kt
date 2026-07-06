package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.domain.usecase.ProbabilityAnalysisUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import kotlin.math.abs

/**
 * 포트폴리오 보유 종목 야간 확률분석 배치.
 *
 * 로컬 통계 엔진만 사용(AI API 호출 없음 = 무료). 결과를 스냅샷으로 저장해
 * 아침에 앱을 열면 캐시된 분석을 즉시 조회할 수 있다.
 * 앙상블 점수가 임계값을 돌파하거나 급변하면 신호 알림을 발송한다.
 */
@HiltWorker
class ProbabilityBatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val probabilityAnalysisUseCase: ProbabilityAnalysisUseCase,
    private val portfolioDao: PortfolioDao,
    private val analysisSnapshotDao: AnalysisSnapshotDao
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "확률분석 배치"
    override val notificationId = CollectionNotificationHelper.PROBABILITY_BATCH_NOTIFICATION_ID

    override suspend fun doCollectionWork(): Result {
        Timber.d("확률분석 배치 워커 시작 (attempt: $runAttemptCount)")

        showInitialNotification("포트폴리오 종목 확률분석 준비 중...")

        val stocks = portfolioDao.getDistinctHoldingStocks()
        if (stocks.isEmpty()) {
            Timber.d("포트폴리오 보유 종목 없음, 배치 건너뜀")
            updateProgress("보유 종목 없음", STATUS_SUCCESS, 1f)
            showCompletion("포트폴리오 보유 종목이 없습니다.")
            saveLog(LABEL, STATUS_SUCCESS, "보유 종목 없음")
            return Result.success()
        }

        val alerts = mutableListOf<String>()
        var succeeded = 0
        var failed = 0

        stocks.forEachIndexed { index, stock ->
            val progress = (index + 1).toFloat() / stocks.size
            updateProgress("${stock.stockName} 분석 중 (${index + 1}/${stocks.size})", STATUS_RUNNING, progress)
            updateNotification("${stock.stockName} (${index + 1}/${stocks.size})", (progress * 100).toInt())

            try {
                val previous = analysisSnapshotDao.getRecentByTicker(stock.ticker, 1).firstOrNull()
                val result = probabilityAnalysisUseCase.analyze(stock.ticker)
                val snapshot = probabilityAnalysisUseCase.buildSnapshot(stock.ticker, stock.stockName, result)
                analysisSnapshotDao.insert(snapshot)
                analysisSnapshotDao.deleteOldSnapshots(stock.ticker, 20)
                succeeded++

                buildAlertLine(stock.stockName, previous?.ensembleScore, snapshot.ensembleScore)
                    ?.let(alerts::add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                Timber.w(e, "%s(%s) 배치 분석 실패", stock.stockName, stock.ticker)
            }
        }

        if (alerts.isNotEmpty()) {
            CollectionNotificationHelper.showSignalAlert(applicationContext, alerts)
        }

        val msg = "완료: ${succeeded}종목 분석" +
            (if (failed > 0) ", ${failed}종목 실패" else "") +
            (if (alerts.isNotEmpty()) ", 신호 ${alerts.size}건" else "")
        Timber.d("확률분석 배치 완료: $msg")
        updateProgress(msg, STATUS_SUCCESS, 1f)
        showCompletion(msg)
        saveLog(LABEL, STATUS_SUCCESS, msg)
        return Result.success()
    }

    /**
     * 임계 돌파/급변 시 알림 한 줄 생성. 이전 스냅샷이 없으면(첫 분석) 알리지 않는다 —
     * 첫 배치에서 전 종목이 한꺼번에 알림되는 것을 방지.
     */
    private fun buildAlertLine(name: String, previousScore: Double?, currentScore: Double): String? {
        if (previousScore == null) return null
        val prev = (previousScore * 100).toInt()
        val cur = (currentScore * 100).toInt()
        return when {
            currentScore >= BUY_THRESHOLD && previousScore < BUY_THRESHOLD ->
                "$name: 상승 신호 진입 ($prev% → $cur%)"
            currentScore <= SELL_THRESHOLD && previousScore > SELL_THRESHOLD ->
                "$name: 하락 신호 진입 ($prev% → $cur%)"
            abs(currentScore - previousScore) >= DELTA_THRESHOLD ->
                "$name: 점수 급변 ($prev% → $cur%)"
            else -> null
        }
    }

    companion object {
        const val WORK_NAME = "probability_batch_daily"
        const val MANUAL_WORK_NAME = "probability_batch_manual"
        const val TAG = "collection_probability_batch"
        const val LABEL = "확률분석 배치"

        private const val BUY_THRESHOLD = 0.65
        private const val SELL_THRESHOLD = 0.35
        private const val DELTA_THRESHOLD = 0.15
    }
}
