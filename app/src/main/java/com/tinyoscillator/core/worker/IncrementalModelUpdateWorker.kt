package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.database.dao.IncrementalModelDao
import com.tinyoscillator.core.database.entity.IncrementalModelStateEntity
import com.tinyoscillator.core.database.entity.ModelDriftAlertEntity
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.data.engine.incremental.IncrementalModelManager
import com.tinyoscillator.domain.model.IncrementalModelManagerState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 점진적 모델 야간 업데이트 WorkManager 워커.
 *
 * 매일 19:00 KST 실행:
 * 1. 모델 상태 로드
 * 2. 어제의 결과 + 오늘의 특성 벡터 조회
 * 3. IncrementalModelManager.dailyUpdate() 실행
 * 4. 드리프트 검사 + 알림 기록
 * 5. 갱신된 모델 상태 저장
 */
@HiltWorker
class IncrementalModelUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val statisticalAnalysisEngine: StatisticalAnalysisEngine,
    private val incrementalModelDao: IncrementalModelDao
) : BaseCollectionWorker(context, params) {

    override val notificationTitle = "점진적 모델 업데이트"
    override val notificationId = CollectionNotificationHelper.INCREMENTAL_MODEL_NOTIFICATION_ID

    companion object {
        const val WORK_NAME = "incremental_model_update"
        const val MANUAL_WORK_NAME = "incremental_model_update_manual"
        const val TAG = "incremental_model"
        const val LABEL = "IncrementalModelUpdate"
        private const val STATE_KEY = "incremental_model_manager"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override suspend fun doWork(): Result {
        Timber.i("━━━ IncrementalModelUpdateWorker 시작 ━━━")
        return try {
            setForeground(createForegroundInfo("점진적 모델 업데이트 준비 중..."))

            val manager = statisticalAnalysisEngine.incrementalModelManager

            // 1. 모델 상태 로드
            updateProgress("모델 상태 로드 중...", STATUS_RUNNING, 0.1f)
            loadManagerState(manager)

            // 2. Cold start if needed
            updateProgress("cold start 확인 중...", STATUS_RUNNING, 0.2f)
            val wasStarted = manager.coldStartIfNeeded(
                statisticalAnalysisEngine.signalHistoryStore
            )
            if (wasStarted) {
                Timber.i("Cold start 수행됨")
            }

            // 3. 최근 완료된 이력에서 데이터 추출
            updateProgress("학습 데이터 수집 중...", STATUS_RUNNING, 0.3f)
            val signalHistoryStore = statisticalAnalysisEngine.signalHistoryStore
            val pendingEntries = signalHistoryStore.getPendingOutcomes(
                cutoffDate = java.time.LocalDate.now().minusDays(1)
                    .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
            )

            if (pendingEntries.isEmpty()) {
                Timber.d("점진적 업데이트 건너뜀: 새 학습 데이터 없음")
                saveManagerState(manager)
                showCompletion("새 학습 데이터 없음 — 건너뜀")
                saveLog(LABEL, STATUS_SUCCESS, "새 학습 데이터 없음")
                return Result.success()
            }

            // 4. 완료된 이력의 최근 항목으로 daily_update 실행
            updateProgress("모델 갱신 중...", STATUS_RUNNING, 0.5f)
            updateNotification("${pendingEntries.size}개 샘플 처리 중...", 50)

            val history = signalHistoryStore.getHistory(
                minSamples = IncrementalModelManager.MIN_SAMPLES_FOR_UPDATE
            )
            if (history == null) {
                Timber.d("점진적 업데이트 건너뜀: 완료된 이력 %d개 미만",
                    IncrementalModelManager.MIN_SAMPLES_FOR_UPDATE)
                saveManagerState(manager)
                showCompletion("학습 이력 부족 — 건너뜀")
                saveLog(LABEL, STATUS_SUCCESS, "학습 이력 부족으로 건너뜀")
                return Result.success()
            }

            // 마지막 항목으로 업데이트
            val lastEntry = history.last()
            val summary = manager.dailyUpdate(lastEntry.signals, lastEntry.actualOutcome)

            // 5. 드리프트 알림 기록
            for (alert in summary.driftAlerts) {
                incrementalModelDao.insertDriftAlert(
                    ModelDriftAlertEntity(
                        modelName = alert.modelName,
                        brierScore = alert.currentBrier,
                        baselineBrier = alert.baselineBrier,
                        degradation = alert.degradation
                    )
                )
                Timber.w("드리프트 알림 저장: %s (열화=%.4f)", alert.modelName, alert.degradation)
            }

            // 6. 모델 상태 저장
            updateProgress("상태 저장 중...", STATUS_RUNNING, 0.9f)
            saveManagerState(manager)

            val msg = "${summary.updatedModels.size}개 모델 갱신, " +
                    "${summary.samplesSeen} 샘플, ${summary.trainingMs}ms"
            showCompletion(msg)
            saveLog(LABEL, STATUS_SUCCESS, msg)

            Timber.i("━━━ IncrementalModelUpdateWorker 완료: %s ━━━", msg)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "IncrementalModelUpdateWorker 실패")
            showCompletion("갱신 실패: ${e.message}", isError = true)
            saveLog(LABEL, STATUS_ERROR, "갱신 실패", e.stackTraceToString())
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun loadManagerState(manager: IncrementalModelManager) {
        try {
            val entity = incrementalModelDao.getModelState(STATE_KEY) ?: return
            val state = json.decodeFromString<IncrementalModelManagerState>(entity.stateJson)
            manager.loadAll(state)
            Timber.d("점진적 모델 상태 로드: %d 샘플", entity.samplesSeen)
        } catch (e: Exception) {
            Timber.w(e, "점진적 모델 상태 로드 실패 — 새로 시작")
        }
    }

    private suspend fun saveManagerState(manager: IncrementalModelManager) {
        val state = manager.saveAll()
        val stateJson = json.encodeToString(state)
        incrementalModelDao.saveModelState(
            IncrementalModelStateEntity(
                modelName = STATE_KEY,
                stateJson = stateJson,
                samplesSeen = state.naiveBayesState?.totalSamples ?: 0
            )
        )
    }
}
