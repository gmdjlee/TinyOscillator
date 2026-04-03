package com.tinyoscillator

import android.app.Application
import androidx.work.Configuration
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.worker.CollectionNotificationHelper
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.data.engine.regime.MarketRegimeClassifier
import com.tinyoscillator.presentation.settings.loadConsensusScheduleTime
import com.tinyoscillator.presentation.settings.loadDepositScheduleTime
import com.tinyoscillator.presentation.settings.loadEtfScheduleTime
import com.tinyoscillator.presentation.settings.loadFearGreedScheduleTime
import com.tinyoscillator.presentation.settings.loadMarketCloseRefreshScheduleTime
import com.tinyoscillator.presentation.settings.loadOscillatorScheduleTime
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class TinyOscillatorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerConfiguration: Configuration

    @Inject
    lateinit var regimeDao: RegimeDao

    @Inject
    lateinit var marketRegimeClassifier: MarketRegimeClassifier

    @Inject
    lateinit var statisticalAnalysisEngine: StatisticalAnalysisEngine

    override val workManagerConfiguration: Configuration
        get() = workerConfiguration

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        CollectionNotificationHelper.createChannel(this)

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val etfSchedule = loadEtfScheduleTime(this@TinyOscillatorApp)
            if (etfSchedule.enabled) {
                WorkManagerHelper.scheduleEtfUpdate(this@TinyOscillatorApp, etfSchedule.hour, etfSchedule.minute)
            }

            val oscSchedule = loadOscillatorScheduleTime(this@TinyOscillatorApp)
            if (oscSchedule.enabled) {
                WorkManagerHelper.scheduleOscillatorUpdate(this@TinyOscillatorApp, oscSchedule.hour, oscSchedule.minute)
            }

            val depositSchedule = loadDepositScheduleTime(this@TinyOscillatorApp)
            if (depositSchedule.enabled) {
                WorkManagerHelper.scheduleDepositUpdate(this@TinyOscillatorApp, depositSchedule.hour, depositSchedule.minute)
            }

            val mcRefreshSchedule = loadMarketCloseRefreshScheduleTime(this@TinyOscillatorApp)
            if (mcRefreshSchedule.enabled) {
                WorkManagerHelper.scheduleMarketCloseRefresh(this@TinyOscillatorApp, mcRefreshSchedule.hour, mcRefreshSchedule.minute)
            }

            val consensusSchedule = loadConsensusScheduleTime(this@TinyOscillatorApp)
            if (consensusSchedule.enabled) {
                WorkManagerHelper.scheduleConsensusUpdate(this@TinyOscillatorApp, consensusSchedule.hour, consensusSchedule.minute)
            }

            val fgSchedule = loadFearGreedScheduleTime(this@TinyOscillatorApp)
            if (fgSchedule.enabled) {
                WorkManagerHelper.scheduleFearGreedUpdate(this@TinyOscillatorApp, fgSchedule.hour, fgSchedule.minute)
            }

            // 시장 레짐 모델 복원 + 주간 업데이트 스케줄
            WorkManagerHelper.scheduleRegimeUpdate(this@TinyOscillatorApp)
            restoreRegimeModel()

            // 매크로 지표 주간 업데이트 (매주 일요일 05:30)
            WorkManagerHelper.scheduleMacroUpdate(this@TinyOscillatorApp)

            // 메타 학습기 주간 재학습 (매주 일요일 06:30)
            WorkManagerHelper.scheduleMetaLearnerRefit(this@TinyOscillatorApp)

            // Feature 캐시 만료 엔트리 정리 (매일 06:00)
            WorkManagerHelper.scheduleFeatureCacheEviction(this@TinyOscillatorApp)
        }
    }

    private suspend fun restoreRegimeModel() {
        try {
            val state = regimeDao.getRegimeState() ?: return
            val stateMap = jsonStringToMap(state.stateJson)
            marketRegimeClassifier.loadModel(stateMap)

            // Predict current regime from cached KOSPI data
            val kospiData = regimeDao.getAllKospiIndex()
            if (kospiData.size > MarketRegimeClassifier.LOOKBACK + 1) {
                val closes = kospiData.map { it.closeValue }.toDoubleArray()
                val result = marketRegimeClassifier.predictRegime(closes)
                statisticalAnalysisEngine.updateRegimeResult(result)
                Timber.d("시장 레짐 모델 복원 완료: %s (신뢰도: %.1f%%)", result.regimeName, result.confidence * 100)
            }
        } catch (e: Exception) {
            Timber.w(e, "시장 레짐 모델 복원 실패 — 다음 학습까지 기본값 사용")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonStringToMap(json: String): Map<String, Any> {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(json)
        return jsonElementToAny(element) as? Map<String, Any> ?: emptyMap()
    }

    private fun jsonElementToAny(element: kotlinx.serialization.json.JsonElement): Any? = when (element) {
        is kotlinx.serialization.json.JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" -> true
            element.content == "false" -> false
            element.content.contains('.') -> element.content.toDoubleOrNull() ?: element.content
            else -> element.content.toLongOrNull() ?: element.content.toDoubleOrNull() ?: element.content
        }
        is kotlinx.serialization.json.JsonArray -> element.map { jsonElementToAny(it) }
        is kotlinx.serialization.json.JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToAny(v)!! }
        is kotlinx.serialization.json.JsonNull -> null
    }
}
