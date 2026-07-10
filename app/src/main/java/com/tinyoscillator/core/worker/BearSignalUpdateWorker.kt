package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * BearSignal(주도주 붕괴 판단 계기판) 지표 월간 업데이트 워커 (TASK.md §2, §5.4, §6 Phase 5).
 *
 * [A]/[B] 등급 자동 지표(신호2 통계·코스피 2사 비중·관세청 수출비중·FRED/ECOS 금리·IPO ETF 방향) +
 * 국가별 지수 4기간 수익률(도표48)을 [com.tinyoscillator.feature.bearsignal.presentation.BearSignalViewModel.refresh]
 * 와 동일한 3단계(자동 → 외부 → 국가별 수익률)로 갱신한다.
 *
 * 기본 스케줄: 매월 5일 06:00 ([WorkManagerHelper.scheduleBearSignalUpdate]) — 관세청 무역통계·
 * 한국은행 기준금리 발표 주기(월 1회)에 맞춘다. 각 지표는 이미 [BearSignalRepositoryImpl]가
 * 개별 실패 시 기존 캐시로 폴백하므로(Phase 1/2), 본 워커는 3개 UseCase 실행 결과를 집계해 최종
 * Result만 판단한다 — 3개 전부 실패했을 때만 재시도/실패로 취급하고, 일부만 성공해도 성공으로
 * 간주한다(부분 갱신도 캐시 신선도 개선에 기여, 오프라인 우선 렌더는 이미 Room 캐시가 보장).
 */
@HiltWorker
class BearSignalUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val refreshAutoInputsUseCase: RefreshAutoInputsUseCase,
    private val refreshExternalAutoInputsUseCase: RefreshExternalAutoInputsUseCase,
    private val refreshMarketReturnsUseCase: RefreshMarketReturnsUseCase
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "BearSignal 지표 갱신"
    override val notificationId = CollectionNotificationHelper.BEAR_SIGNAL_NOTIFICATION_ID

    override suspend fun doCollectionWork(): Result {
        Timber.d("BearSignal 지표 월간 업데이트 워커 시작 (attempt: $runAttemptCount)")
        showInitialNotification("BearSignal 지표 갱신 준비 중...")

        val failed = mutableListOf<String>()

        try {
            updateProgress("[A] 자동 지표 갱신 중(신호2 통계·코스피 2사 비중)...", STATUS_RUNNING, 0.2f)
            updateNotification("[A] 자동 지표 갱신 중...", 20)
            refreshAutoInputsUseCase().onFailure {
                Timber.w(it, "BearSignal [A] 자동 지표 갱신 실패")
                failed += "자동 지표"
            }

            updateProgress("[B] 외부 지표 갱신 중(관세청·FRED·ECOS·IPO ETF)...", STATUS_RUNNING, 0.5f)
            updateNotification("[B] 외부 지표 갱신 중...", 50)
            refreshExternalAutoInputsUseCase().onFailure {
                Timber.w(it, "BearSignal [B] 외부 지표 갱신 실패")
                failed += "외부 지표"
            }

            updateProgress("국가별 지수 수익률 갱신 중(도표48)...", STATUS_RUNNING, 0.8f)
            updateNotification("국가별 지수 수익률 갱신 중...", 80)
            refreshMarketReturnsUseCase().onFailure {
                Timber.w(it, "BearSignal 국가별 지수 수익률 갱신 실패")
                failed += "국가별 지수 수익률"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "BearSignal 지표 갱신 실패: ${e.message}"
            Timber.e(e, msg)
            saveLog(LABEL, STATUS_ERROR, msg, e.stackTraceToString())
            showCompletion(msg, isError = true)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        return if (failed.size == 3) {
            val msg = "BearSignal 지표 갱신 전체 실패: ${failed.joinToString()}"
            Timber.w(msg)
            saveLog(LABEL, STATUS_ERROR, msg)
            showCompletion(msg, isError = true)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            val msg = if (failed.isEmpty()) {
                "완료: 자동 지표 · 외부 지표 · 국가별 수익률 3계열 전부 갱신 성공"
            } else {
                "부분 갱신 완료(실패: ${failed.joinToString()})"
            }
            Timber.i(msg)
            updateProgress(msg, STATUS_SUCCESS, 1f)
            showCompletion(msg)
            saveLog(LABEL, STATUS_SUCCESS, msg)
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "bear_signal_monthly_update"
        const val MANUAL_WORK_NAME = "bear_signal_manual_update"
        const val TAG = "collection_bear_signal"
        const val LABEL = "BearSignal 지표"
    }
}
