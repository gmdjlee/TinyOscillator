package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * BearSignal(주도주 붕괴 판단 계기판) [B] 등급 자동 지표 · 국가별 지수 수익률 주간 업데이트 워커
 * (TASK.md §2 111행, §4 데이터 소스 주기 열, §6 Phase 5-1).
 *
 * **주간 전환 사유(§6 Phase 5-1)**: v1.0 계획에서는 신호2 통계·코스피 2사 비중([A] 등급, `kotlin_krx`
 * 일별 시세 기반)까지 이 워커가 함께 월 1회 갱신했으나, §4 주기 열상 [A] 등급은 "일" 주기가 맞다.
 * [BearSignalDailyUpdateWorker](신규, 매일)가 [A] 등급을 전담하도록 분리하고, 본 워커는 관세청
 * 무역통계·FRED/ECOS 금리(월·이벤트)·IPO ETF 방향(주)만 남긴다 — 주간 주기가 §4의 "주"(IPO ETF)를
 * 충족하고, 월/이벤트 주기 지표를 더 잦은 빈도로 조회하는 것은 무해하다(각 소스가 기존에 이미 캐시
 * 폴백을 보장하므로 과다 호출로 인한 부작용이 없다 — 실제로는 소스 발표 주기가 그대로 결과를
 * 좌우한다). WORK_NAME은 `bear_signal_monthly_update` → `bear_signal_weekly_update`로 변경한다
 * (미출시 브랜치라 구 이름 마이그레이션 코드가 불필요하다).
 *
 * 기본 스케줄: 매주 월요일 06:00 KST ([WorkManagerHelper.scheduleBearSignalUpdate]) — 주말 동안의
 * 미국 시장 마감 데이터(FRED 등)가 반영된 이후 새 주의 첫 영업일 아침에 갱신한다.
 * 각 지표는 이미 [BearSignalRepositoryImpl]가 개별 실패 시 기존 캐시로 폴백하므로(Phase 1/2), 본
 * 워커는 2개 UseCase 실행 결과를 집계해 최종 Result만 판단한다 — 2개 전부 실패했을 때만 재시도/
 * 실패로 취급하고, 일부만 성공해도 성공으로 간주한다(부분 갱신도 캐시 신선도 개선에 기여, 오프라인
 * 우선 렌더는 이미 Room 캐시가 보장).
 */
@HiltWorker
class BearSignalUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val refreshExternalAutoInputsUseCase: RefreshExternalAutoInputsUseCase,
    private val refreshMarketReturnsUseCase: RefreshMarketReturnsUseCase
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "BearSignal 지표 갱신"
    override val notificationId = CollectionNotificationHelper.BEAR_SIGNAL_NOTIFICATION_ID

    override suspend fun doCollectionWork(): Result {
        Timber.d("BearSignal 지표 주간 업데이트 워커 시작 (attempt: $runAttemptCount)")
        showInitialNotification("BearSignal 지표 갱신 준비 중...")

        val failed = mutableListOf<String>()

        try {
            updateProgress("[B] 외부 지표 갱신 중(관세청·FRED·ECOS·IPO ETF)...", STATUS_RUNNING, 0.4f)
            updateNotification("[B] 외부 지표 갱신 중...", 40)
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

        return if (failed.size == 2) {
            val msg = "BearSignal 지표 갱신 전체 실패: ${failed.joinToString()}"
            Timber.w(msg)
            saveLog(LABEL, STATUS_ERROR, msg)
            showCompletion(msg, isError = true)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            val msg = if (failed.isEmpty()) {
                "완료: 외부 지표 · 국가별 수익률 2계열 전부 갱신 성공"
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
        const val WORK_NAME = "bear_signal_weekly_update"
        const val MANUAL_WORK_NAME = "bear_signal_manual_update"
        const val TAG = "collection_bear_signal"
        const val LABEL = "BearSignal 지표"
    }
}
