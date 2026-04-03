package com.tinyoscillator.data.engine

import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.entity.SignalHistoryEntity
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.engine.calibration.SignalCalibrator
import com.tinyoscillator.data.engine.calibration.SignalScoreExtractor
import com.tinyoscillator.data.engine.ensemble.SignalHistoryStore
import com.tinyoscillator.data.engine.ensemble.StackingEnsemble
import com.tinyoscillator.data.engine.incremental.IncrementalModelManager
import com.tinyoscillator.data.engine.macro.MacroRegimeOverlay
import com.tinyoscillator.data.engine.regime.MarketRegimeClassifier
import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import com.tinyoscillator.data.engine.risk.PositionRecommendationEngine
import com.tinyoscillator.domain.model.Korea5FactorResult
import com.tinyoscillator.domain.model.MetaLearnerStatus
import com.tinyoscillator.domain.model.CalibratedScore
import com.tinyoscillator.domain.model.ExecutionMetadata
import com.tinyoscillator.domain.model.FeatureKey
import com.tinyoscillator.domain.model.FeatureTtl
import com.tinyoscillator.domain.model.MacroEnvironment
import com.tinyoscillator.domain.model.MacroSignalResult
import com.tinyoscillator.domain.model.MarketRegimeResult
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.repository.StatisticalRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 통계 분석 오케스트레이터 — 10개 엔진을 coroutine으로 병렬 실행하고 결과를 통합
 *
 * 각 엔진의 실패는 개별 처리 (하나가 실패해도 나머지 결과는 반환).
 * 보정기가 학습된 경우, 원시 점수를 보정된 확률로 변환.
 * 실행 시간 메타데이터 포함.
 */
@Singleton
class StatisticalAnalysisEngine @Inject constructor(
    private val repository: StatisticalRepository,
    private val naiveBayesEngine: NaiveBayesEngine,
    private val logisticScoringEngine: LogisticScoringEngine,
    private val hmmRegimeEngine: HmmRegimeEngine,
    private val patternScanEngine: PatternScanEngine,
    private val signalScoringEngine: SignalScoringEngine,
    private val correlationEngine: CorrelationEngine,
    private val bayesianUpdateEngine: BayesianUpdateEngine,
    private val orderFlowEngine: OrderFlowEngine,
    private val dartEventEngine: DartEventEngine,
    private val korea5FactorEngine: Korea5FactorEngine,
    val signalCalibrator: SignalCalibrator,
    private val calibrationDao: CalibrationDao,
    private val marketRegimeClassifier: MarketRegimeClassifier,
    private val macroRegimeOverlay: MacroRegimeOverlay,
    private val featureStore: FeatureStore,
    private val apiConfigProvider: ApiConfigProvider,
    val signalHistoryStore: SignalHistoryStore
) {

    /** 포지션 사이징 추천 엔진 */
    val positionRecommendationEngine = PositionRecommendationEngine()

    /** 스태킹 앙상블 메타 학습기 */
    val stackingEnsemble = StackingEnsemble(
        baseModelNames = RegimeWeightTable.ALL_ALGOS,
        metaC = 0.5
    )

    /** 점진적 학습 모델 매니저 */
    val incrementalModelManager = IncrementalModelManager(
        algoNames = RegimeWeightTable.ALL_ALGOS
    )

    /** 캐시된 시장 레짐 결과 (주기적으로 갱신) */
    @Volatile
    private var cachedRegimeResult: MarketRegimeResult? = null

    /** 캐시된 매크로 신호 결과 (주간 갱신) */
    @Volatile
    private var cachedMacroSignal: MacroSignalResult? = null

    /**
     * 종합 통계 분석 실행 (FeatureStore 캐시 적용)
     *
     * 동일 종목+날짜에 대해 Daily TTL(4시간) 이내 재호출 시 캐시 반환.
     * 캐시 미스 시 7개 엔진 병렬 실행 후 결과를 캐싱.
     */
    suspend fun analyze(stockCode: String): StatisticalResult {
        val key = FeatureKey(ticker = stockCode, featureName = "StatisticalResult")
        return featureStore.getOrCompute(
            key = key,
            ttl = FeatureTtl.Daily,
            serializer = StatisticalResult.serializer()
        ) {
            analyzeInternal(stockCode)
        }
    }

    /**
     * 특정 종목의 분석 캐시 무효화 (수동 새로고침용)
     */
    suspend fun clearAnalysisCache(ticker: String) {
        featureStore.invalidate(ticker)
    }

    /**
     * 종합 통계 분석 실행 (내부 — 캐시 미스 시 호출)
     *
     * 10개 엔진을 병렬 실행하고 결과를 StatisticalResult로 통합.
     * 각 엔진 실패 시 해당 결과만 null로 표시.
     */
    private suspend fun analyzeInternal(stockCode: String): StatisticalResult = coroutineScope {
        val totalStart = System.currentTimeMillis()
        val timings = mutableMapOf<String, Long>()
        val failedEngines = mutableListOf<String>()

        Timber.d("━━━ 통계 분석 시작: $stockCode ━━━")

        // 공통 데이터 로드
        val stockName = repository.getStockName(stockCode) ?: stockCode
        val prices = repository.getDailyPrices(stockCode)
        val oscillators = repository.getOscillatorData(stockCode)
        val demarkRows = repository.getDemarkData(stockCode)
        val fundamentals = try { repository.getFundamentalData(stockCode) } catch (e: Exception) { null }
        val sectorEtfReturns = try { repository.getSectorEtfReturns(stockCode) } catch (e: Exception) { null }
        val etfAmountTrend = try { repository.getEtfAmountTrend(stockCode) } catch (e: Exception) { null }

        Timber.d("데이터 로드 완료 — prices=%d, osc=%d, demark=%d",
            prices.size, oscillators.size, demarkRows.size)

        // 9개 엔진 병렬 실행
        val bayesDeferred = async {
            timedExecution("NaiveBayes", timings, failedEngines) {
                naiveBayesEngine.analyze(prices, oscillators, demarkRows, fundamentals, etfAmountTrend)
            }
        }

        val logisticDeferred = async {
            timedExecution("Logistic", timings, failedEngines) {
                val lastDemark = demarkRows.lastOrNull()
                logisticScoringEngine.analyze(
                    prices, oscillators, fundamentals,
                    demarkBuySetup = lastDemark?.tdBuyCount ?: 0
                )
            }
        }

        val hmmDeferred = async {
            timedExecution("HMM", timings, failedEngines) {
                hmmRegimeEngine.analyze(prices)
            }
        }

        val patternDeferred = async {
            timedExecution("PatternScan", timings, failedEngines) {
                patternScanEngine.analyze(prices, oscillators, demarkRows, fundamentals)
            }
        }

        val correlationDeferred = async {
            timedExecution("Correlation", timings, failedEngines) {
                correlationEngine.analyze(oscillators, demarkRows, prices, sectorEtfReturns)
            }
        }

        val bayesianUpdateDeferred = async {
            timedExecution("BayesianUpdate", timings, failedEngines) {
                bayesianUpdateEngine.analyze(prices, oscillators, demarkRows, fundamentals, etfAmountTrend)
            }
        }

        val orderFlowDeferred = async {
            timedExecution("OrderFlow", timings, failedEngines) {
                orderFlowEngine.analyze(prices)
            }
        }

        val dartEventDeferred = async {
            timedExecution("DartEvent", timings, failedEngines) {
                val dartApiKey = try { apiConfigProvider.getDartApiKey() } catch (_: Exception) { null }
                dartEventEngine.analyze(dartApiKey, stockCode, prices)
            }
        }

        val korea5FactorDeferred = async {
            timedExecution("Korea5Factor", timings, failedEngines) {
                korea5FactorEngine.analyze(prices, stockCode)
            }
        }

        // 패턴 분석 결과를 기다린 후 SignalScoring에 전달
        val patternResult = patternDeferred.await()

        val signalDeferred = async {
            timedExecution("SignalScoring", timings, failedEngines) {
                signalScoringEngine.analyze(
                    oscillators, demarkRows, prices, fundamentals, patternResult
                )
            }
        }

        // 모든 결과 수집
        val bayesResult = bayesDeferred.await()
        val logisticResult = logisticDeferred.await()
        val hmmResult = hmmDeferred.await()
        val correlationResult = correlationDeferred.await()
        val bayesianUpdateResult = bayesianUpdateDeferred.await()
        val orderFlowResult = orderFlowDeferred.await()
        val dartEventResult = dartEventDeferred.await()
        val korea5FactorResult = korea5FactorDeferred.await()
        val signalResult = signalDeferred.await()

        val totalTime = System.currentTimeMillis() - totalStart

        Timber.d("━━━ 통계 분석 완료: %dms (실패: %s) ━━━",
            totalTime, failedEngines.ifEmpty { listOf("없음") })

        // 시장 레짐 결과 첨부 (캐시에서 가져옴 — 학습은 워커가 담당)
        val regimeResult = cachedRegimeResult
        if (regimeResult != null) {
            Timber.d("  시장 레짐: %s (신뢰도: %.1f%%, %d일 지속)",
                regimeResult.regimeName, regimeResult.confidence * 100, regimeResult.regimeDurationDays)
        }

        // 매크로 신호 결과 첨부 (캐시에서 가져옴 — 업데이트는 워커가 담당)
        val macroSignal = cachedMacroSignal
        if (macroSignal != null) {
            Timber.d("  매크로 환경: %s (금리YoY=%.2fpp, IIP=%.1f%%, CPI=%.1f%%)",
                macroSignal.macroEnv, macroSignal.baseRateYoy, macroSignal.iipYoy, macroSignal.cpiYoy)
        }

        // 포지션 사이징 추천 계산 (앙상블 확률 + 가격 데이터 필요)
        val positionRec = try {
            val tempResult = StatisticalResult(
                ticker = stockCode, stockName = stockName,
                bayesResult = bayesResult, logisticResult = logisticResult,
                hmmResult = hmmResult, patternAnalysis = patternResult,
                signalScoringResult = signalResult, correlationAnalysis = correlationResult,
                bayesianUpdateResult = bayesianUpdateResult, orderFlowResult = orderFlowResult,
                dartEventResult = dartEventResult, korea5FactorResult = korea5FactorResult,
                marketRegimeResult = regimeResult, macroSignalResult = macroSignal
            )
            val ensembleProb = getEnsembleProbability(tempResult)
            positionRecommendationEngine.recommend(
                ticker = stockCode,
                signalProb = ensembleProb,
                prices = prices
            )
        } catch (e: Exception) {
            Timber.w(e, "포지션 사이징 추천 실패")
            null
        }

        val result = StatisticalResult(
            ticker = stockCode,
            stockName = stockName,
            bayesResult = bayesResult,
            logisticResult = logisticResult,
            hmmResult = hmmResult,
            patternAnalysis = patternResult,
            signalScoringResult = signalResult,
            correlationAnalysis = correlationResult,
            bayesianUpdateResult = bayesianUpdateResult,
            orderFlowResult = orderFlowResult,
            dartEventResult = dartEventResult,
            korea5FactorResult = korea5FactorResult,
            marketRegimeResult = regimeResult,
            macroSignalResult = macroSignal,
            positionRecommendation = positionRec,
            executionMetadata = ExecutionMetadata(
                totalTimeMs = totalTime,
                engineTimings = timings.toMap(),
                failedEngines = failedEngines.toList()
            )
        )

        // 신호 이력 저장 (보정 학습 데이터 축적)
        try {
            recordSignalHistory(stockCode, result)
        } catch (e: Exception) {
            Timber.w(e, "신호 이력 저장 실패 — 분석 결과에는 영향 없음")
        }

        // 앙상블 이력 저장 (스태킹 메타 학습기 학습 데이터 축적)
        try {
            recordEnsembleHistory(stockCode, result)
        } catch (e: Exception) {
            Timber.w(e, "앙상블 이력 저장 실패 — 분석 결과에는 영향 없음")
        }

        result
    }

    /**
     * StatisticalResult에서 보정된 점수를 추출.
     * 보정기가 학습되지 않은 알고리즘은 원시 점수를 그대로 반환.
     */
    fun getCalibratedScores(result: StatisticalResult): List<CalibratedScore> {
        return SignalScoreExtractor.extract(result).map { raw ->
            CalibratedScore(
                algoName = raw.algoName,
                rawScore = raw.rawScore,
                calibratedScore = signalCalibrator.transform(raw.algoName, raw.rawScore)
            )
        }
    }

    /**
     * 시장 레짐 결과 캐시 갱신 (RegimeUpdateWorker에서 호출)
     */
    fun updateRegimeResult(result: MarketRegimeResult) {
        val previous = cachedRegimeResult
        cachedRegimeResult = result
        if (previous != null && previous.regimeName != result.regimeName) {
            Timber.i("━━━ 시장 레짐 전환: %s → %s (신뢰도: %.1f%%) ━━━",
                previous.regimeName, result.regimeName, result.confidence * 100)
        }
    }

    /**
     * 현재 레짐 + 매크로 오버레이 적용된 알고리즘 가중치 반환
     */
    fun getRegimeWeights(): Map<String, Double> {
        val regime = cachedRegimeResult ?: return RegimeWeightTable.equalWeights()
        val baseWeights = RegimeWeightTable.getWeights(regime.regimeName)

        // 매크로 오버레이 적용
        val macroSignal = cachedMacroSignal
        if (macroSignal != null && macroSignal.unavailableReason == null) {
            val macroEnv = MacroEnvironment.fromString(macroSignal.macroEnv)
            val adjusted = macroRegimeOverlay.adjustRegimeWeights(baseWeights, macroEnv)
            Timber.d("가중치 조정 — 레짐: %s, 매크로: %s", regime.regimeName, macroEnv.name)
            return adjusted
        }

        return baseWeights
    }

    /**
     * 매크로 신호 캐시 갱신 (MacroUpdateWorker에서 호출)
     */
    fun updateMacroSignal(result: MacroSignalResult) {
        cachedMacroSignal = result
        Timber.d("매크로 신호 갱신: %s (금리YoY=%.2fpp)", result.macroEnv, result.baseRateYoy)
    }

    /**
     * 스태킹 앙상블 확률 예측.
     *
     * 우선순위:
     * 1. 스태킹 메타 학습기 (주간 배치 학습)
     * 2. 점진적 모델 (야간 SGD 갱신)
     * 3. 레짐 가중합 폴백 (cold start)
     *
     * 메타 학습기와 점진적 모델이 모두 학습된 경우, 메타 학습기 70% + 점진적 30% 블렌딩.
     *
     * @param result 9개 엔진의 분석 결과
     * @return 상승 확률 [0, 1]
     */
    fun getEnsembleProbability(result: StatisticalResult): Double {
        val calibratedSignals = getCalibratedSignals(result)

        val stackingProb = if (stackingEnsemble.isFitted) {
            try {
                stackingEnsemble.predictProba(calibratedSignals)
            } catch (e: Exception) {
                Timber.w(e, "메타 학습기 예측 실패")
                null
            }
        } else null

        val incrementalProb = if (incrementalModelManager.naiveBayes.isFitted &&
            incrementalModelManager.logisticRegression.isFitted) {
            try {
                incrementalModelManager.predictProba(calibratedSignals)
            } catch (e: Exception) {
                Timber.w(e, "점진적 모델 예측 실패")
                null
            }
        } else null

        return when {
            // 둘 다 학습된 경우: 블렌딩 (메타 학습기 70%, 점진적 30%)
            stackingProb != null && incrementalProb != null ->
                (0.7 * stackingProb + 0.3 * incrementalProb).coerceIn(0.0, 1.0)
            // 메타 학습기만 학습된 경우
            stackingProb != null -> stackingProb
            // 점진적 모델만 학습된 경우
            incrementalProb != null -> incrementalProb
            // Cold start: 레짐 가중합
            else -> weightedSumFallback(calibratedSignals)
        }
    }

    /**
     * 보정된 신호 추출 (스태킹 입력용).
     */
    private fun getCalibratedSignals(result: StatisticalResult): Map<String, Double> {
        return SignalScoreExtractor.extract(result).associate { raw ->
            raw.algoName to signalCalibrator.transform(raw.algoName, raw.rawScore)
        }
    }

    /**
     * 레짐 가중합 폴백 (cold start 시 사용).
     */
    private fun weightedSumFallback(signals: Map<String, Double>): Double {
        val weights = getRegimeWeights()
        var sum = 0.0
        var weightSum = 0.0
        for ((algo, weight) in weights) {
            val signal = signals[algo] ?: continue
            sum += weight * signal
            weightSum += weight
        }
        return if (weightSum > 0) (sum / weightSum).coerceIn(0.0, 1.0) else 0.5
    }

    /**
     * 메타 학습기 재학습 (MetaLearnerRefitWorker에서 호출).
     */
    fun refitMetaLearner(signals: Array<DoubleArray>, labels: IntArray) {
        stackingEnsemble.fit(signals, labels)
        Timber.i("메타 학습기 재학습 완료: %d 샘플", signals.size)
    }

    /**
     * 메타 학습기 상태 조회 (UI용).
     */
    fun getMetaLearnerStatus(): MetaLearnerStatus {
        return stackingEnsemble.getStatus()
    }

    /**
     * 앙상블 특성 중요도 조회.
     */
    fun getEnsembleFeatureImportance(): Map<String, Float> {
        return stackingEnsemble.featureImportance()
    }

    /**
     * 앙상블 이력 기록 (분석 후 호출, T+0 시점).
     */
    private suspend fun recordEnsembleHistory(stockCode: String, result: StatisticalResult) {
        val calibratedSignals = getCalibratedSignals(result)
        if (calibratedSignals.isEmpty()) return

        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val regimeId = cachedRegimeResult?.regimeName
        signalHistoryStore.append(
            ticker = stockCode,
            date = today,
            signals = calibratedSignals,
            regimeId = regimeId
        )
    }

    private suspend fun recordSignalHistory(stockCode: String, result: StatisticalResult) {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val scores = SignalScoreExtractor.extract(result)
        if (scores.isEmpty()) return

        val entities = scores.map { raw ->
            SignalHistoryEntity(
                ticker = stockCode,
                algoName = raw.algoName,
                rawScore = raw.rawScore,
                date = today
            )
        }
        calibrationDao.insertSignalHistory(entities)
    }

    /**
     * 엔진 실행 시간 측정 + 에러 핸들링
     */
    private inline fun <T> timedExecution(
        engineName: String,
        timings: MutableMap<String, Long>,
        failedEngines: MutableList<String>,
        block: () -> T
    ): T? {
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            timings[engineName] = System.currentTimeMillis() - start
            Timber.d("  ✓ $engineName: ${timings[engineName]}ms")
            result
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            timings[engineName] = System.currentTimeMillis() - start
            failedEngines.add(engineName)
            Timber.e(e, "  ✗ $engineName 실패: ${e.message}")
            null
        }
    }
}
