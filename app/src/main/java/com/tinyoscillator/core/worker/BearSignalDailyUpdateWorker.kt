package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * BearSignal(주도주 붕괴 판단 계기판) [A] 등급 자동 지표 일간 업데이트 워커
 * (TASK.md §2 111행, §4 데이터 소스 주기 열, §6 Phase 5-1 신설).
 *
 * 신호2 통계(±3σ/±4σ 카운트)·코스피 2사 비중은 `kotlin_krx` 일별 시세를 원천으로 하므로 §4 주기
 * 열의 "일"에 해당한다. 신용잔고(§3.4 `credit`)도 같은 경로에서 로컬 `market_deposits`(02:00
 * NaverFinance 스크랩)를 읽어 best-effort로 갱신한다 — 02:00 워커가 채운 데이터를 06:30에 소비 — 기존 [BearSignalUpdateWorker](월간→주간 전환)가 3개 UseCase를 월 1회
 * 갱신하던 것에서 [A] 등급만 분리해 매일 갱신한다(§4의 다른 지표는 [BearSignalUpdateWorker]가
 * 계속 담당하므로 갱신 대상이 겹치지 않는다).
 *
 * 단일 UseCase만 실행하므로 [BearSignalUpdateWorker]의 "부분 성공 허용(N계열 중 일부 실패)" 집계
 * 로직이 필요 없다 — 실패 시 즉시 재시도(`runAttemptCount<3`) 또는 최종 실패로 취급한다(기존 캐시
 * 폴백은 [BearSignalRepositoryImpl]가 이미 보장하므로 오프라인 우선 렌더에는 영향이 없다).
 *
 * 기본 스케줄: 매일 06:30 ([WorkManagerHelper.scheduleBearSignalDailyUpdate]) — 06:00
 * FeatureCacheEvictionWorker와 겹치지 않도록 30분 분산 배치한다.
 */
@HiltWorker
class BearSignalDailyUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val refreshAutoInputsUseCase: RefreshAutoInputsUseCase
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "BearSignal 지표 갱신(일간)"
    override val notificationId = CollectionNotificationHelper.BEAR_SIGNAL_DAILY_NOTIFICATION_ID

    override suspend fun doCollectionWork(): Result {
        Timber.d("BearSignal [A] 자동 지표 일간 업데이트 워커 시작 (attempt: $runAttemptCount)")
        showInitialNotification("BearSignal 지표 갱신 준비 중(일간)...")

        try {
            updateProgress("[A] 자동 지표 갱신 중(신호2 통계·코스피 2사 비중·신용잔고)...", STATUS_RUNNING, 0.5f)
            updateNotification("[A] 자동 지표 갱신 중...", 50)

            var failed = false
            refreshAutoInputsUseCase().onFailure {
                Timber.w(it, "BearSignal [A] 자동 지표 갱신 실패(일간)")
                failed = true
            }

            return if (failed) {
                val msg = "BearSignal [A] 자동 지표 갱신 실패(일간)"
                Timber.w(msg)
                saveLog(LABEL, STATUS_ERROR, msg)
                showCompletion(msg, isError = true)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            } else {
                val msg = "완료: [A] 자동 지표(신호2 통계·코스피 2사 비중·신용잔고) 갱신 성공"
                Timber.i(msg)
                updateProgress(msg, STATUS_SUCCESS, 1f)
                showCompletion(msg)
                saveLog(LABEL, STATUS_SUCCESS, msg)
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "BearSignal 지표 갱신 실패(일간): ${e.message}"
            Timber.e(e, msg)
            saveLog(LABEL, STATUS_ERROR, msg, e.stackTraceToString())
            showCompletion(msg, isError = true)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "bear_signal_daily_update"
        const val MANUAL_WORK_NAME = "bear_signal_daily_manual_update"
        const val TAG = "collection_bear_signal_daily"
        const val LABEL = "BearSignal 지표(일간)"
    }
}
