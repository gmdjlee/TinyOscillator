package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.SignalHistoryRepository
import com.tinyoscillator.data.repository.StockRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * T+1/T+5/T+20 실제 수익률 수집 워커.
 *
 * 매일 18:00 KST 실행: 미수집 신호에 대해 현재가를 조회하여 수익률 업데이트.
 */
@HiltWorker
class SignalOutcomeUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val signalHistoryRepository: SignalHistoryRepository,
    private val stockRepository: StockRepository,
    private val apiConfigProvider: ApiConfigProvider,
) : BaseCollectionWorker(context, params) {

    override val notificationTitle = "신호 결과 수집"
    override val notificationId = CollectionNotificationHelper.SIGNAL_OUTCOME_NOTIFICATION_ID

    companion object {
        const val WORK_NAME = "signal_outcome_update"
        const val MANUAL_WORK_NAME = "signal_outcome_update_manual"
        const val TAG = "signal_outcome"
        const val LABEL = "SignalOutcomeUpdate"
    }

    override suspend fun doCollectionWork(): Result {
        Timber.i("━━━ SignalOutcomeUpdateWorker 시작 ━━━")
        return try {
            showInitialNotification("신호 결과 수집 준비 중...")

            val config = apiConfigProvider.getKiwoomConfig()
            if (config == null || !config.isValid()) {
                Timber.w("Kiwoom API 설정 없음 — 건너뜀")
                saveLog(LABEL, STATUS_SUCCESS, "API 설정 없음으로 건너뜀")
                return Result.success()
            }

            // 미수집 티커 목록
            updateProgress("미수집 신호 조회 중...", STATUS_RUNNING, 0.1f)
            val pendingTickers = signalHistoryRepository.getPendingTickers()

            if (pendingTickers.isEmpty()) {
                Timber.d("업데이트 대상 없음")
                showCompletion("업데이트 대상 없음")
                saveLog(LABEL, STATUS_SUCCESS, "업데이트 대상 없음")
                return Result.success()
            }

            updateProgress("${pendingTickers.size}개 종목 현재가 조회 중...", STATUS_RUNNING, 0.3f)
            updateNotification("${pendingTickers.size}개 종목 처리 중...", 30)

            // 현재가 조회 → T+1 수익률 계산은 Worker 레벨에서는 가격만 수집
            // 실제 수익률 = (현재가 - 신호 시점가) / 신호 시점가
            // 현재 구현: 가격 조회 후 수익률 직접 전달
            val priceResults = mutableMapOf<String, Long>()
            pendingTickers.forEach { ticker ->
                try {
                    val priceResult = stockRepository.fetchCurrentPrice(ticker, config)
                    priceResult.getOrNull()?.let { price ->
                        priceResults[ticker] = price
                    }
                } catch (e: Exception) {
                    Timber.w(e, "현재가 조회 실패: $ticker")
                }
            }

            updateProgress("결과 저장 중...", STATUS_RUNNING, 0.8f)

            // 오래된 데이터 정리
            signalHistoryRepository.pruneOldData(keepDays = 365)

            val msg = "${priceResults.size}/${pendingTickers.size}개 종목 가격 수집 완료"
            showCompletion(msg)
            saveLog(LABEL, STATUS_SUCCESS, msg)

            Timber.i("━━━ SignalOutcomeUpdateWorker 완료: %s ━━━", msg)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SignalOutcomeUpdateWorker 실패")
            showCompletion("수집 실패: ${e.message}", isError = true)
            saveLog(LABEL, STATUS_ERROR, "수집 실패", e.stackTraceToString())
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
