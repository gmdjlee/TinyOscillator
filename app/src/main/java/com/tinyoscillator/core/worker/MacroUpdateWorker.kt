package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.MacroDao
import com.tinyoscillator.core.database.entity.MacroIndicatorEntity
import com.tinyoscillator.data.engine.FeatureStore
import com.tinyoscillator.data.engine.macro.BokEcosCollector
import com.tinyoscillator.data.engine.macro.MacroRegimeOverlay
import com.tinyoscillator.domain.model.FeatureKey
import com.tinyoscillator.domain.model.FeatureTtl
import com.tinyoscillator.domain.model.MacroSignalResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * BOK ECOS 매크로 지표 주간 업데이트 워커
 *
 * 5개 매크로 지표를 수집, YoY 변화율 계산, Room DB 캐시, FeatureStore 캐시.
 * 기본 스케줄: 매주 일요일 05:30 (WorkManagerHelper에서 설정)
 */
@HiltWorker
class MacroUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bokEcosCollector: BokEcosCollector,
    private val macroRegimeOverlay: MacroRegimeOverlay,
    private val macroDao: MacroDao,
    private val featureStore: FeatureStore,
    private val apiConfigProvider: ApiConfigProvider
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "매크로 지표 업데이트"
    override val notificationId = MACRO_NOTIFICATION_ID

    override suspend fun doCollectionWork(): Result {
        Timber.d("매크로 지표 업데이트 워커 시작 (attempt: $runAttemptCount)")

        showInitialNotification("매크로 지표 수집 준비 중...")

        val ecosApiKey = try { apiConfigProvider.getEcosApiKey() } catch (_: Exception) { null }
        if (ecosApiKey.isNullOrBlank()) {
            Timber.w("ECOS API 키 미설정, 매크로 업데이트 건너뜀")
            updateProgress("ECOS API 키 미설정", STATUS_ERROR)
            saveLog(LABEL, STATUS_ERROR, "ECOS API 키 미설정")
            return Result.failure()
        }

        try {
            // Step 1: Fetch macro signal
            updateProgress("매크로 지표 수집 중...", STATUS_RUNNING, 0.3f)
            updateNotification("BOK ECOS 데이터 수집 중...", 30)

            val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            val macroSignal = bokEcosCollector.macroSignalVector(ecosApiKey, today)

            if (macroSignal.unavailableReason != null) {
                val msg = "매크로 수집 실패: ${macroSignal.unavailableReason}"
                Timber.w(msg)
                saveLog(LABEL, STATUS_ERROR, msg)
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // Step 2: Classify macro environment
            updateProgress("매크로 환경 분류 중...", STATUS_RUNNING, 0.6f)
            val classifiedSignal = macroRegimeOverlay.applyClassification(macroSignal)

            // Step 3: Cache in Room DB
            updateProgress("데이터 캐싱 중...", STATUS_RUNNING, 0.8f)
            cacheMacroData(classifiedSignal)

            // Step 4: Cache in FeatureStore
            val key = FeatureKey(ticker = "MACRO", featureName = "MacroSignal")
            featureStore.put(key, FeatureTtl.Weekly, MacroSignalResult.serializer(), classifiedSignal)

            // Step 5: Cleanup old data (keep 36 months)
            val cutoff = YearMonth.now().minusMonths(36).format(DateTimeFormatter.ofPattern("yyyyMM"))
            macroDao.deleteOlderThan(cutoff)

            val msg = "완료: ${classifiedSignal.macroEnv} (금리YoY=${String.format("%.2f", classifiedSignal.baseRateYoy)}pp)"
            Timber.d("매크로 지표 업데이트 완료: $msg")
            updateProgress(msg, STATUS_SUCCESS, 1f)
            showCompletion(msg)
            saveLog(LABEL, STATUS_SUCCESS, msg)
            return Result.success()

        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "매크로 업데이트 실패: ${e.message}"
            Timber.e(e, msg)
            saveLog(LABEL, STATUS_ERROR, msg, e.stackTraceToString())
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun cacheMacroData(signal: MacroSignalResult) {
        val ym = signal.referenceMonth
        val entities = listOf(
            MacroIndicatorEntity(id = "base_rate_$ym", indicatorKey = "base_rate", yearMonth = ym, rawValue = signal.baseRateYoy, yoyChange = signal.baseRateYoy),
            MacroIndicatorEntity(id = "m2_$ym", indicatorKey = "m2", yearMonth = ym, rawValue = signal.m2Yoy, yoyChange = signal.m2Yoy),
            MacroIndicatorEntity(id = "iip_$ym", indicatorKey = "iip", yearMonth = ym, rawValue = signal.iipYoy, yoyChange = signal.iipYoy),
            MacroIndicatorEntity(id = "usd_krw_$ym", indicatorKey = "usd_krw", yearMonth = ym, rawValue = signal.usdKrwYoy, yoyChange = signal.usdKrwYoy),
            MacroIndicatorEntity(id = "cpi_$ym", indicatorKey = "cpi", yearMonth = ym, rawValue = signal.cpiYoy, yoyChange = signal.cpiYoy)
        )
        macroDao.insertAll(entities)
    }

    companion object {
        const val WORK_NAME = "macro_update"
        const val MANUAL_WORK_NAME = "macro_update_manual"
        const val TAG = "macro_update_tag"
        const val LABEL = "MacroUpdate"
        const val MACRO_NOTIFICATION_ID = 1009
    }
}
