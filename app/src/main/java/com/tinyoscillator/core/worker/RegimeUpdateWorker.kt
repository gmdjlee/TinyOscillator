package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.entity.KospiIndexEntity
import com.tinyoscillator.core.database.entity.RegimeStateEntity
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.data.engine.regime.MarketRegimeClassifier
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 시장 레짐 모델 주간 재학습 워커
 *
 * KOSPI 종합지수 504일(2년) 이력을 가져와 HMM 레짐 분류기를 재학습.
 * 학습된 모델 파라미터를 Room DB에 영속화.
 * 기본 스케줄: 매주 일요일 05:00 (WorkManagerHelper에서 설정)
 */
@HiltWorker
class RegimeUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val regimeDao: RegimeDao,
    private val krxApiClient: KrxApiClient,
    private val marketRegimeClassifier: MarketRegimeClassifier,
    private val statisticalAnalysisEngine: StatisticalAnalysisEngine
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "시장 레짐 업데이트"
    override val notificationId = REGIME_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("시장 레짐 업데이트 워커 시작 (attempt: $runAttemptCount)")

        CollectionNotificationHelper.createChannel(applicationContext)
        setForeground(createForegroundInfo("시장 레짐 모델 학습 준비 중..."))

        val creds = loadKrxCredentials(applicationContext)
        if (creds.id.isBlank() || creds.password.isBlank()) {
            Timber.w("KRX 자격증명 미설정, 레짐 업데이트 건너뜀")
            updateProgress("KRX 자격증명 미설정", STATUS_ERROR)
            saveLog(LABEL, STATUS_ERROR, "KRX 자격증명 미설정")
            return Result.failure()
        }

        try {
            // Step 1: Login to KRX
            updateProgress("KRX 로그인 중...", STATUS_RUNNING, 0.1f)
            val loggedIn = krxApiClient.login(creds.id, creds.password)
            if (!loggedIn) {
                Timber.e("KRX 로그인 실패")
                saveLog(LABEL, STATUS_ERROR, "KRX 로그인 실패")
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // Step 2: Fetch KOSPI index data (504 days)
            updateProgress("KOSPI 지수 데이터 수집 중...", STATUS_RUNNING, 0.3f)
            updateNotification("KOSPI 지수 데이터 수집 중...", 30)

            val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
            val endDate = LocalDate.now().format(fmt)
            val startDate = LocalDate.now().minusDays(KOSPI_HISTORY_DAYS.toLong()).format(fmt)

            val krxIndex = krxApiClient.getKrxIndex()
            if (krxIndex == null) {
                Timber.e("KrxIndex 객체 없음")
                saveLog(LABEL, STATUS_ERROR, "KrxIndex 객체 없음")
                return Result.failure()
            }

            val indexData = krxIndex.getKospi(startDate, endDate)
            if (indexData.size < MarketRegimeClassifier.LOOKBACK + 10) {
                val msg = "KOSPI 데이터 부족: ${indexData.size}일"
                Timber.e(msg)
                saveLog(LABEL, STATUS_ERROR, msg)
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // Step 3: Cache KOSPI closes in Room
            updateProgress("KOSPI 데이터 캐싱 중...", STATUS_RUNNING, 0.5f)
            val entities = indexData.map { ohlcv ->
                KospiIndexEntity(
                    date = ohlcv.date,
                    closeValue = ohlcv.close
                )
            }
            regimeDao.insertKospiIndex(entities)

            // Clean up old data (keep only KOSPI_HISTORY_DAYS)
            val cutoffDate = LocalDate.now().minusDays(KOSPI_HISTORY_DAYS.toLong() + 30).format(fmt)
            regimeDao.deleteOldKospiIndex(cutoffDate)

            // Step 4: Train regime classifier
            updateProgress("레짐 모델 학습 중...", STATUS_RUNNING, 0.7f)
            updateNotification("레짐 모델 학습 중...", 70)

            val closes = indexData.map { it.close }.toDoubleArray()
            marketRegimeClassifier.fit(closes)

            // Step 5: Predict current regime
            val regimeResult = marketRegimeClassifier.predictRegime(closes)
            statisticalAnalysisEngine.updateRegimeResult(regimeResult)

            // Step 6: Persist model state
            updateProgress("모델 저장 중...", STATUS_RUNNING, 0.9f)
            val modelState = marketRegimeClassifier.saveModel()
            val stateJson = mapToJsonString(modelState)

            regimeDao.insertRegimeState(
                RegimeStateEntity(
                    stateJson = stateJson,
                    regimeName = regimeResult.regimeName,
                    confidence = regimeResult.confidence
                )
            )

            val msg = "완료: ${regimeResult.regimeName} (신뢰도: ${String.format("%.1f", regimeResult.confidence * 100)}%, ${indexData.size}일 데이터)"
            Timber.d("시장 레짐 업데이트 완료: $msg")
            updateProgress(msg, STATUS_SUCCESS, 1f)
            showCompletion(msg)
            saveLog(LABEL, STATUS_SUCCESS, msg)
            return Result.success()

        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "레짐 업데이트 실패: ${e.message}"
            Timber.e(e, msg)
            saveLog(LABEL, STATUS_ERROR, msg, e.stackTraceToString())
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            krxApiClient.close()
        }
    }

    companion object {
        const val WORK_NAME = "regime_update"
        const val MANUAL_WORK_NAME = "regime_update_manual"
        const val TAG = "regime_update_tag"
        const val LABEL = "RegimeUpdate"
        const val REGIME_NOTIFICATION_ID = 1008
        const val KOSPI_HISTORY_DAYS = 504
    }
}

// ─── Simple JSON serialization for nested Map<String, Any> ───

private fun mapToJsonString(map: Map<String, Any>): String {
    val jsonElement = anyToJsonElement(map)
    return Json.encodeToString(jsonElement)
}

private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) ->
        k.toString() to anyToJsonElement(v)
    })
    else -> JsonPrimitive(value.toString())
}
