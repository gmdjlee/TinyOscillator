package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.ExecutionMetadata
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.repository.StatisticalRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 통계 분석 오케스트레이터 — 7개 엔진을 coroutine으로 병렬 실행하고 결과를 통합
 *
 * 각 엔진의 실패는 개별 처리 (하나가 실패해도 나머지 결과는 반환).
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
    private val bayesianUpdateEngine: BayesianUpdateEngine
) {

    /**
     * 종합 통계 분석 실행
     *
     * 7개 엔진을 병렬 실행하고 결과를 StatisticalResult로 통합.
     * 각 엔진 실패 시 해당 결과만 null로 표시.
     */
    suspend fun analyze(stockCode: String): StatisticalResult = coroutineScope {
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

        // 7개 엔진 병렬 실행
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
        val signalResult = signalDeferred.await()

        val totalTime = System.currentTimeMillis() - totalStart

        Timber.d("━━━ 통계 분석 완료: %dms (실패: %s) ━━━",
            totalTime, failedEngines.ifEmpty { listOf("없음") })

        StatisticalResult(
            ticker = stockCode,
            stockName = stockName,
            bayesResult = bayesResult,
            logisticResult = logisticResult,
            hmmResult = hmmResult,
            patternAnalysis = patternResult,
            signalScoringResult = signalResult,
            correlationAnalysis = correlationResult,
            bayesianUpdateResult = bayesianUpdateResult,
            executionMetadata = ExecutionMetadata(
                totalTimeMs = totalTime,
                engineTimings = timings.toMap(),
                failedEngines = failedEngines.toList()
            )
        )
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
        } catch (e: Exception) {
            timings[engineName] = System.currentTimeMillis() - start
            failedEngines.add(engineName)
            Timber.e(e, "  ✗ $engineName 실패: ${e.message}")
            null
        }
    }
}
