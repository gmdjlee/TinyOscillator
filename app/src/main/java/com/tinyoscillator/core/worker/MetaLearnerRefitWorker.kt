package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.data.engine.ensemble.SignalHistoryStore
import com.tinyoscillator.data.engine.ensemble.StackingEnsemble
import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * 메타 학습기 재학습 WorkManager 워커.
 *
 * 주간 실행 또는 이력 20건 이상 축적 시 트리거.
 * 전체 앙상블 이력으로 StackingEnsemble.fit() 실행.
 */
@HiltWorker
class MetaLearnerRefitWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val signalHistoryStore: SignalHistoryStore,
    private val statisticalAnalysisEngine: StatisticalAnalysisEngine
) : BaseCollectionWorker(context, params) {

    override val notificationTitle = "메타 학습기"
    override val notificationId = CollectionNotificationHelper.META_LEARNER_NOTIFICATION_ID

    companion object {
        const val WORK_NAME = "meta_learner_refit"
        const val MANUAL_WORK_NAME = "meta_learner_refit_manual"
        const val TAG = "meta_learner"
        const val LABEL = "MetaLearnerRefit"
    }

    override suspend fun doWork(): Result {
        Timber.i("━━━ MetaLearnerRefitWorker 시작 ━━━")
        return try {
            setForeground(createForegroundInfo("메타 학습기 재학습 준비 중..."))

            val algoNames = RegimeWeightTable.ALL_ALGOS
            val trainingData = signalHistoryStore.toTrainingData(algoNames)

            if (trainingData == null) {
                Timber.d("메타 학습기 재학습 건너뜀: 학습 데이터 부족 (최소 %d 필요)",
                    StackingEnsemble.MIN_SAMPLES)
                saveLog(LABEL, STATUS_SUCCESS, "학습 데이터 부족으로 건너뜀")
                return Result.success()
            }

            val (signals, labels) = trainingData
            updateProgress("재학습 중...", STATUS_RUNNING, 0.5f)
            updateNotification("${signals.size}개 샘플로 학습 중...", 50)

            statisticalAnalysisEngine.refitMetaLearner(signals, labels)

            showCompletion("${signals.size}개 샘플로 재학습 완료")
            saveLog(LABEL, STATUS_SUCCESS, "${signals.size}개 샘플 재학습 완료")

            Timber.i("━━━ MetaLearnerRefitWorker 완료: %d 샘플로 재학습 ━━━", signals.size)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "MetaLearnerRefitWorker 실패")
            showCompletion("재학습 실패: ${e.message}", isError = true)
            saveLog(LABEL, STATUS_ERROR, "재학습 실패", e.stackTraceToString())
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
