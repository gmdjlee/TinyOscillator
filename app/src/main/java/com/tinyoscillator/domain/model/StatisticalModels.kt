package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

/**
 * 확률적 기대값 분석 엔진 — 결과 데이터 클래스
 *
 * 7개 통계 알고리즘의 결과를 불변 data class로 정의.
 * 모든 확률값은 Double (0.0~1.0), 수익률은 Double (소수점 표현, e.g., 0.034 = 3.4%).
 */

// ─── 최상위 통합 결과 ───

/** 7개 알고리즘 결과를 묶는 최상위 래퍼 */
@Serializable
data class StatisticalResult(
    val ticker: String,
    val stockName: String,
    val analyzedAt: Long = System.currentTimeMillis(),
    val bayesResult: BayesResult? = null,
    val logisticResult: LogisticResult? = null,
    val hmmResult: HmmResult? = null,
    val patternAnalysis: PatternAnalysis? = null,
    val signalScoringResult: SignalScoringResult? = null,
    val correlationAnalysis: CorrelationAnalysis? = null,
    val bayesianUpdateResult: BayesianUpdateResult? = null,
    val orderFlowResult: OrderFlowResult? = null,
    val dartEventResult: DartEventResult? = null,
    val korea5FactorResult: Korea5FactorResult? = null,
    val sectorCorrelationResult: SectorCorrelationResult? = null,
    val marketRegimeResult: MarketRegimeResult? = null,
    val macroSignalResult: MacroSignalResult? = null,
    val positionRecommendation: PositionRecommendation? = null,
    val executionMetadata: ExecutionMetadata = ExecutionMetadata()
)

/** 각 엔진의 실행 시간 메타데이터 */
@Serializable
data class ExecutionMetadata(
    val totalTimeMs: Long = 0,
    val engineTimings: Map<String, Long> = emptyMap(),
    val failedEngines: List<String> = emptyList()
)

// ─── 2.1 Naive Bayes ───

/** 나이브 베이즈 분류 결과 */
@Serializable
data class BayesResult(
    val upProbability: Double,
    val downProbability: Double,
    val sidewaysProbability: Double,
    val dominantFeatures: List<FeatureContribution>,
    val sampleCount: Int
)

/** 각 지표의 확률 기여도 */
@Serializable
data class FeatureContribution(
    val featureName: String,
    val likelihoodRatio: Double
)

// ─── 2.2 Logistic Scoring ───

/** 로지스틱 회귀 스코어링 결과 */
@Serializable
data class LogisticResult(
    val probability: Double,
    val weights: Map<String, Double>,
    val featureValues: Map<String, Double>,
    val score0to100: Int
)

// ─── 2.3 HMM Regime ───

/** Hidden Markov Model 레짐 탐지 결과 */
@Serializable
data class HmmResult(
    val currentRegime: Int,
    val regimeProbabilities: DoubleArray,
    val transitionProbabilities: Map<String, Double>,
    val recentRegimePath: List<Int>,
    val regimeDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HmmResult) return false
        return currentRegime == other.currentRegime &&
                regimeProbabilities.contentEquals(other.regimeProbabilities) &&
                transitionProbabilities == other.transitionProbabilities &&
                recentRegimePath == other.recentRegimePath &&
                regimeDescription == other.regimeDescription
    }

    override fun hashCode(): Int {
        var result = currentRegime
        result = 31 * result + regimeProbabilities.contentHashCode()
        result = 31 * result + transitionProbabilities.hashCode()
        result = 31 * result + recentRegimePath.hashCode()
        result = 31 * result + regimeDescription.hashCode()
        return result
    }

    companion object {
        const val REGIME_LOW_VOL_UP = 0
        const val REGIME_LOW_VOL_SIDEWAYS = 1
        const val REGIME_HIGH_VOL_UP = 2
        const val REGIME_HIGH_VOL_DOWN = 3

        fun regimeToDescription(regime: Int): String = when (regime) {
            REGIME_LOW_VOL_UP -> "저변동 상승 (안정적 상승 추세)"
            REGIME_LOW_VOL_SIDEWAYS -> "저변동 횡보 (박스권)"
            REGIME_HIGH_VOL_UP -> "고변동 상승 (급등 구간)"
            REGIME_HIGH_VOL_DOWN -> "고변동 하락 (급락 구간)"
            else -> "알 수 없는 레짐"
        }
    }
}

// ─── 2.4 Pattern Scan ───

/** 패턴 분석 전체 결과 */
@Serializable
data class PatternAnalysis(
    val allPatterns: List<PatternMatch>,
    val activePatterns: List<PatternMatch>,
    val totalHistoricalDays: Int
)

/** 개별 패턴 매칭 결과 */
@Serializable
data class PatternMatch(
    val patternName: String,
    val patternDescription: String,
    val isActive: Boolean,
    val occurrences: List<PatternOccurrence>,
    val winRate5d: Double,
    val winRate10d: Double,
    val winRate20d: Double,
    val avgReturn5d: Double,
    val avgReturn10d: Double,
    val avgReturn20d: Double,
    val avgMdd20d: Double,
    val totalOccurrences: Int
)

/** 패턴 발생 이력 */
@Serializable
data class PatternOccurrence(
    val date: String,
    val return5d: Double,
    val return10d: Double,
    val return20d: Double,
    val mdd20d: Double
)

// ─── 2.5 Signal Scoring ───

/** 가중 신호 앙상블 점수 결과 */
@Serializable
data class SignalScoringResult(
    val totalScore: Int,
    val contributions: List<SignalContribution>,
    val conflictingSignals: List<SignalConflict>,
    val dominantDirection: String
)

/** 개별 신호 기여도 */
@Serializable
data class SignalContribution(
    val name: String,
    val weight: Double,
    val signal: Int,
    val direction: Int,
    val contributionPercent: Double
)

/** 신호 충돌 */
@Serializable
data class SignalConflict(
    val signal1: String,
    val signal2: String,
    val direction1: String,
    val direction2: String,
    val description: String
)

// ─── 2.6 Correlation ───

/** 상관 분석 전체 결과 */
@Serializable
data class CorrelationAnalysis(
    val correlations: List<CorrelationResult>,
    val leadLagResults: List<LeadLagResult>
)

/** 상관 계수 결과 */
@Serializable
data class CorrelationResult(
    val indicator1: String,
    val indicator2: String,
    val pearsonR: Double,
    val strength: CorrelationStrength
)

/** 상관 강도 */
@Serializable
enum class CorrelationStrength(val label: String) {
    STRONG_POSITIVE("강한 양의 상관"),
    MODERATE_POSITIVE("보통 양의 상관"),
    WEAK("약한 상관"),
    MODERATE_NEGATIVE("보통 음의 상관"),
    STRONG_NEGATIVE("강한 음의 상관");

    companion object {
        fun fromR(r: Double): CorrelationStrength = when {
            r >= 0.7 -> STRONG_POSITIVE
            r >= 0.3 -> MODERATE_POSITIVE
            r > -0.3 -> WEAK
            r > -0.7 -> MODERATE_NEGATIVE
            else -> STRONG_NEGATIVE
        }
    }
}

/** 선행-후행 분석 결과 */
@Serializable
data class LeadLagResult(
    val indicator1: String,
    val indicator2: String,
    val optimalLag: Int,
    val rAtOptimalLag: Double,
    val interpretation: String
)

// ─── 2.7 Bayesian Update ───

/** 베이지안 갱신 결과 */
@Serializable
data class BayesianUpdateResult(
    val finalPosterior: Double,
    val priorProbability: Double,
    val updateHistory: List<ProbabilityUpdate>
)

/** 확률 갱신 이력 */
@Serializable
data class ProbabilityUpdate(
    val signalName: String,
    val beforeProb: Double,
    val afterProb: Double,
    val deltaProb: Double,
    val likelihoodRatio: Double
)

// ─── 2.8 Order Flow (투자자 자금흐름) ───

/** 투자자 자금흐름 분석 결과 */
@Serializable
data class OrderFlowResult(
    /** 종합 매수 우위 점수 (0.0~1.0, 0.5=중립) */
    val buyerDominanceScore: Double,
    /** 5일 Order Flow Imbalance (-1.0~1.0) */
    val ofi5d: Double,
    /** 20일 Order Flow Imbalance (-1.0~1.0) */
    val ofi20d: Double,
    /** 기관-외국인 괴리도 (0.0~1.0, 높을수록 불일치) */
    val institutionalDivergence: Double,
    /** 외국인 매수 압력 (-1.0~1.0) */
    val foreignBuyPressure: Double,
    /** 시그모이드 변환 신호 점수 (0.0~1.0) */
    val signalScore: Double,
    /** 자금흐름 방향: BUY / SELL / NEUTRAL */
    val flowDirection: String,
    /** 자금흐름 강도: STRONG / MODERATE / WEAK */
    val flowStrength: String,
    /** 추세 정렬도 (0.0~1.0, 1.0=자금흐름과 가격 추세 완전 일치) */
    val trendAlignment: Double,
    /** 평균회귀 신호 (0.0~1.0, 높을수록 극단치 → 반전 가능) */
    val meanReversionSignal: Double,
    /** 상세 메트릭 */
    val analysisDetails: Map<String, Double> = emptyMap(),
    /** 데이터 기준일 (yyyyMMdd) */
    val dataDate: String = ""
)

// ─── 2.9 Expected Value (확률적 기대값) ───

/** 확률적 기대값 분석 — 시나리오 기반 기대 수익률 */
data class ExpectedValueAnalysis(
    val expectedReturn: Double,
    val scenarios: List<Scenario>,
    val historicalOutcomes: List<HistoricalOutcome>
)

/** 개별 시나리오 */
data class Scenario(
    val name: String,
    val probability: Double,
    val expectedReturn: Double,
    val description: String
)

/** 과거 유사 상황 결과 */
data class HistoricalOutcome(
    val date: String,
    val similarity: Double,
    val actualReturn5d: Double,
    val actualReturn20d: Double
)

// ─── DeMark 분석 (기존 DemarkTDRow 기반 확장) ───

/** DeMark 분석 결과 (통계 엔진용) */
data class DemarkAnalysis(
    val currentState: DemarkState,
    val historicalResults: List<DemarkHistoricalResult>
)

/** 현재 DeMark 상태 */
data class DemarkState(
    val buySetupCount: Int,
    val sellSetupCount: Int,
    val isSetup9Buy: Boolean,
    val isSetup9Sell: Boolean,
    val isCountdown13: Boolean
)

/** DeMark 과거 결과 */
data class DemarkHistoricalResult(
    val date: String,
    val setupType: String,
    val setupCount: Int,
    val returnAfter5d: Double,
    val returnAfter10d: Double,
    val returnAfter20d: Double
)

// ─── ETF 컨텍스트 ───

/** ETF 보유 컨텍스트 (해당 종목을 보유한 ETF 정보) */
data class EtfContext(
    val totalEtfCount: Int,
    val totalHoldingAmount: Long,
    val topEtfs: List<EtfHoldingInfo>,
    val recentAmountTrend: List<EtfAmountSnapshot>
)

/** ETF 보유 정보 */
data class EtfHoldingInfo(
    val etfTicker: String,
    val etfName: String,
    val weight: Double?,
    val amount: Long
)

/** ETF 보유 금액 스냅샷 (시계열) */
data class EtfAmountSnapshot(
    val date: String,
    val totalAmount: Long,
    val etfCount: Int
)

// ─── LLM 최종 출력 ───

/** LLM 종합 분석 결과 */
data class StockAnalysis(
    val overallAssessment: String,
    val confidence: Double,
    val insights: List<AlgorithmInsight>,
    val conflicts: List<String>,
    val risks: List<String>,
    val action: String,
    val summary: String
)

/** 알고리즘별 인사이트 */
data class AlgorithmInsight(
    val algorithmName: String,
    val interpretation: String,
    val significance: String
)

// ─── 분석 파이프라인 상태 ───

/** 분석 파이프라인 진행 상태 */
sealed class AnalysisState {
    data object Loading : AnalysisState()
    data class Computing(val message: String, val progress: Float = 0f) : AnalysisState()
    data class LlmProcessing(val message: String) : AnalysisState()
    data class Streaming(val partialText: String) : AnalysisState()
    data class Success(val result: StockAnalysis, val statisticalResult: StatisticalResult) : AnalysisState()
    data class Error(val message: String, val cause: Throwable? = null) : AnalysisState()
}
