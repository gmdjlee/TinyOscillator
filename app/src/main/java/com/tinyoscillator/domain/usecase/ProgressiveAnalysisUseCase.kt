package com.tinyoscillator.domain.usecase

import com.tinyoscillator.data.engine.RationaleBuilder
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.data.engine.calibration.SignalScoreExtractor
import com.tinyoscillator.domain.indicator.IndicatorCalculator
import com.tinyoscillator.domain.model.AnalysisStep
import com.tinyoscillator.domain.model.ProgressiveAnalysisState
import com.tinyoscillator.domain.repository.StatisticalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 분석 단계를 순서대로 emit하는 UseCase.
 * 각 단계는 완료되는 즉시 ProgressiveAnalysisState를 업데이트해 emit한다.
 *
 * 아키텍처 원칙:
 * - 1단계(가격): 데이터 로딩
 * - 2단계(기술지표): 1단계 완료 후 계산
 * - 3단계(앙상블): 11개 엔진 병렬 실행 (가장 시간 소모)
 * - 4단계(외부): 3단계 결과에서 DART·기관 데이터 추출 (soft fail)
 */
@Singleton
open class ProgressiveAnalysisUseCase @Inject constructor(
    private val repository: StatisticalRepository,
    private val statisticalEngine: StatisticalAnalysisEngine,
) {
    open operator fun invoke(ticker: String): Flow<ProgressiveAnalysisState> =
        channelFlow {
            var state = ProgressiveAnalysisState(ticker = ticker)

            // ── 초기 빈 상태 (스켈레톤 유지) ─────────────────────
            send(state)

            // ── 1단계: 가격 데이터 ───────────────────────────────
            val prices = repository.getDailyPrices(ticker, 252)
            if (prices.isEmpty()) throw IllegalStateException("가격 데이터 없음: $ticker")

            val latest = prices.last()
            val prev = if (prices.size >= 2) prices[prices.size - 2] else null
            val priceChange = if (prev != null && prev.closePrice > 0)
                (latest.closePrice - prev.closePrice).toFloat() / prev.closePrice
            else 0f

            val priceStep = AnalysisStep.PriceData(
                ticker = ticker,
                currentPrice = latest.closePrice.toLong(),
                priceChange = priceChange,
                volume = latest.volume,
                isLoading = false,
                isComplete = true,
            )
            state = state.copy(steps = state.steps + priceStep)
            send(state)

            // ── 2단계: 기술 지표 ─────────────────────────────────
            val closes = prices.map { it.closePrice.toFloat() }.toFloatArray()
            val ema5 = IndicatorCalculator.ema(closes, 5)
            val ema20 = IndicatorCalculator.ema(closes, 20)
            val ema60 = IndicatorCalculator.ema(closes, 60)
            val macd = IndicatorCalculator.macd(closes)
            val rsi = IndicatorCalculator.rsi(closes, 14)
            val bollinger = IndicatorCalculator.bollinger(closes, 20)

            val lastClose = closes.lastOrNull() ?: 0f
            val bollingerPct = if (bollinger.upper.last().isNaN() || bollinger.lower.last().isNaN()) 0.5f
            else {
                val range = bollinger.upper.last() - bollinger.lower.last()
                if (range > 0f) (lastClose - bollinger.lower.last()) / range else 0.5f
            }

            val techStep = AnalysisStep.TechnicalIndicators(
                ema5 = ema5.lastOrNull { !it.isNaN() } ?: 0f,
                ema20 = ema20.lastOrNull { !it.isNaN() } ?: 0f,
                ema60 = ema60.lastOrNull { !it.isNaN() } ?: 0f,
                macdHistogram = macd.histogram.lastOrNull { !it.isNaN() } ?: 0f,
                rsi = rsi.lastOrNull { !it.isNaN() } ?: 50f,
                bollingerPct = bollingerPct,
                isLoading = false,
                isComplete = true,
            )
            state = state.copy(steps = state.steps + techStep)
            send(state)

            // ── 3단계: 앙상블 신호 ───────────────────────────────
            val result = statisticalEngine.analyze(ticker)
            val ensembleProb = statisticalEngine.getEnsembleProbability(result)
            val algoScores = SignalScoreExtractor.extract(result).associate {
                it.algoName to it.rawScore.toFloat()
            }
            val rationaleMap = try {
                RationaleBuilder.build(result).mapValues { (_, v) -> v.rationale }
            } catch (e: Exception) {
                Timber.w(e, "근거 생성 실패")
                emptyMap()
            }

            val ensembleStep = AnalysisStep.EnsembleSignal(
                algoResults = algoScores,
                ensembleScore = ensembleProb.toFloat(),
                rationale = rationaleMap,
                isLoading = false,
                isComplete = true,
            )
            state = state.copy(steps = state.steps + ensembleStep)
            send(state)

            // ── 4단계: 외부 데이터 (soft fail) ───────────────────
            try {
                val dartEvents = result.dartEventResult?.eventStudies
                    ?.map { "${it.eventType}: CAR ${String.format("%.1f", it.carFinal * 100)}%" }
                    ?: emptyList()

                val recentPrices = prices.takeLast(20)
                val institutionalFlow = if (recentPrices.isNotEmpty())
                    recentPrices.sumOf { it.instNetBuy }.toFloat() / recentPrices.size
                else 0f

                val extStep = AnalysisStep.ExternalData(
                    dartEvents = dartEvents,
                    institutionalFlow = institutionalFlow,
                    consensusTarget = null,
                    isLoading = false,
                    isComplete = true,
                )
                state = state.copy(steps = state.steps + extStep)
            } catch (e: Exception) {
                Timber.w(e, "외부 데이터 수집 실패 — 선택적 데이터이므로 무시")
            }

            state = state.copy(isFullyComplete = true)
            send(state)
        }.flowOn(Dispatchers.IO)
}
