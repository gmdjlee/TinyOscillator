package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.*
import javax.inject.Inject

/**
 * 확률분석 결과 로컬 해석기.
 *
 * StatisticalResult의 수치를 규칙 기반으로 해석하여 한국어 텍스트를 생성.
 * 순수 함수, I/O 없음.
 */
class ProbabilityInterpreter @Inject constructor() {

    /** 전체 결과 요약 (1~3줄) */
    fun summarize(result: StatisticalResult): String {
        val parts = mutableListOf<String>()

        // 베이즈 방향성
        result.bayesResult?.let { bayes ->
            val maxProb = maxOf(bayes.upProbability, bayes.downProbability, bayes.sidewaysProbability)
            val direction = when (maxProb) {
                bayes.upProbability -> "상승"
                bayes.downProbability -> "하락"
                else -> "횡보"
            }
            parts += "베이즈 분류: ${direction} ${pct(maxProb)} 우세"
        }

        // 신호 점수
        result.signalScoringResult?.let { ss ->
            val sentiment = when {
                ss.totalScore >= 70 -> "강한 매수"
                ss.totalScore >= 55 -> "약한 매수"
                ss.totalScore <= 30 -> "강한 매도"
                ss.totalScore <= 45 -> "약한 매도"
                else -> "중립"
            }
            parts += "신호 점수: ${ss.totalScore}/100 ($sentiment)"
        }

        // 베이지안 갱신 방향
        result.bayesianUpdateResult?.let { bu ->
            val delta = bu.finalPosterior - bu.priorProbability
            val arrow = if (delta > 0) "상향" else "하향"
            parts += "베이지안 확률: ${pct(bu.priorProbability)} → ${pct(bu.finalPosterior)} ($arrow)"
        }

        // HMM 레짐
        result.hmmResult?.let { hmm ->
            parts += "시장 레짐: ${hmm.regimeDescription}"
        }

        if (parts.isEmpty()) return "분석 결과를 해석할 수 없습니다."

        // 종합 판단
        val overallDirection = assessOverallDirection(result)
        return parts.joinToString("\n") + "\n\n종합: $overallDirection"
    }

    /** 나이브 베이즈 해석 */
    fun interpretBayes(bayes: BayesResult): String {
        val sb = StringBuilder()
        val maxProb = maxOf(bayes.upProbability, bayes.downProbability, bayes.sidewaysProbability)
        val direction = when (maxProb) {
            bayes.upProbability -> "상승"
            bayes.downProbability -> "하락"
            else -> "횡보"
        }

        sb.appendLine("나이브 베이즈 분류기가 ${bayes.sampleCount}개 과거 샘플을 학습하여 현재 조건에서의 방향을 예측했습니다.")
        sb.appendLine()
        sb.appendLine("${direction} 확률이 ${pct(maxProb)}로 가장 높습니다.")

        // 확률 분포 해석
        val spread = maxProb - minOf(bayes.upProbability, bayes.downProbability, bayes.sidewaysProbability)
        if (spread < 0.15) {
            sb.appendLine("세 방향 확률이 고르게 분포되어 방향성이 불명확합니다.")
        } else if (spread > 0.3) {
            sb.appendLine("확률 편중이 뚜렷하여 ${direction} 신호가 강합니다.")
        }

        // 주요 피처
        if (bayes.dominantFeatures.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("주요 영향 요인:")
            bayes.dominantFeatures.take(3).forEach { f ->
                val impact = when {
                    f.likelihoodRatio > 2.0 -> "강한 영향"
                    f.likelihoodRatio > 1.5 -> "보통 영향"
                    else -> "약한 영향"
                }
                sb.appendLine("  - ${f.featureName}: ${String.format("%.2f", f.likelihoodRatio)}x ($impact)")
            }
        }
        return sb.toString()
    }

    /** 로지스틱 회귀 해석 */
    fun interpretLogistic(lr: LogisticResult): String {
        val sb = StringBuilder()
        val sentiment = when {
            lr.score0to100 >= 75 -> "매우 긍정적"
            lr.score0to100 >= 60 -> "긍정적"
            lr.score0to100 >= 40 -> "중립"
            lr.score0to100 >= 25 -> "부정적"
            else -> "매우 부정적"
        }
        sb.appendLine("로지스틱 회귀 모델이 여러 지표를 가중합산하여 ${lr.score0to100}/100 점수를 산출했습니다.")
        sb.appendLine("시장 전망: $sentiment (상승 확률 ${pct(lr.probability)})")
        sb.appendLine()

        // 주요 변수 해석
        val sorted = lr.featureValues.entries.sortedByDescending { kotlin.math.abs(it.value) }
        if (sorted.isNotEmpty()) {
            sb.appendLine("주요 입력 변수:")
            sorted.take(3).forEach { (name, value) ->
                val sign = if (value > 0) "+" else ""
                sb.appendLine("  - $name: $sign${String.format("%.3f", value)}")
            }
        }
        return sb.toString()
    }

    /** HMM 레짐 해석 */
    fun interpretHmm(hmm: HmmResult): String {
        val sb = StringBuilder()
        sb.appendLine("은닉 마르코프 모델(HMM)이 시장을 4개 상태로 분류했습니다.")
        sb.appendLine("현재 레짐: ${hmm.regimeDescription}")
        sb.appendLine()

        // 레짐별 확률
        val regimeNames = listOf("저변동 상승", "저변동 횡보", "고변동 상승", "고변동 하락")
        val maxIdx = hmm.regimeProbabilities.indices.maxByOrNull { hmm.regimeProbabilities[it] } ?: 0
        sb.appendLine("레짐 확률 분포:")
        hmm.regimeProbabilities.forEachIndexed { i, p ->
            val marker = if (i == maxIdx) " ◀" else ""
            sb.appendLine("  - ${regimeNames[i]}: ${pct(p)}$marker")
        }

        // 레짐 전환 해석
        if (hmm.recentRegimePath.size >= 3) {
            val last3 = hmm.recentRegimePath.takeLast(3)
            val transitions = last3.zipWithNext().count { (a, b) -> a != b }
            sb.appendLine()
            if (transitions == 0) {
                sb.appendLine("최근 레짐이 안정적으로 유지되고 있어 현재 추세가 지속될 가능성이 높습니다.")
            } else {
                sb.appendLine("최근 레짐 전환이 감지되어 추세 변화 가능성에 주의가 필요합니다.")
            }
        }

        // 투자 시사점
        sb.appendLine()
        when (hmm.currentRegime) {
            HmmResult.REGIME_LOW_VOL_UP ->
                sb.appendLine("시사점: 안정적 상승 구간으로, 추세 추종 전략이 유리합니다.")
            HmmResult.REGIME_LOW_VOL_SIDEWAYS ->
                sb.appendLine("시사점: 박스권 장세로, 레인지 매매 전략을 고려할 수 있습니다.")
            HmmResult.REGIME_HIGH_VOL_UP ->
                sb.appendLine("시사점: 급등 구간으로, 변동성 확대에 따른 리스크 관리가 중요합니다.")
            HmmResult.REGIME_HIGH_VOL_DOWN ->
                sb.appendLine("시사점: 급락 구간으로, 방어적 포지션 또는 손절 전략이 필요합니다.")
        }
        return sb.toString()
    }

    /** 패턴 분석 해석 */
    fun interpretPattern(pa: PatternAnalysis): String {
        val sb = StringBuilder()
        sb.appendLine("${pa.totalHistoricalDays}일 간의 과거 데이터에서 ${pa.allPatterns.size}개 패턴을 검색했습니다.")

        if (pa.activePatterns.isEmpty()) {
            sb.appendLine("현재 활성화된 패턴이 없어 뚜렷한 기술적 시그널이 부재합니다.")
            return sb.toString()
        }

        sb.appendLine("현재 ${pa.activePatterns.size}개 패턴이 활성 상태입니다.")
        sb.appendLine()

        pa.activePatterns.forEach { p ->
            sb.appendLine("${p.patternDescription}:")
            val reliability = when {
                p.totalOccurrences >= 20 && p.winRate20d > 0.6 -> "높은 신뢰도"
                p.totalOccurrences >= 10 && p.winRate20d > 0.5 -> "보통 신뢰도"
                else -> "낮은 신뢰도 (샘플 부족)"
            }
            sb.appendLine("  과거 ${p.totalOccurrences}회 발생, 20일 승률 ${pct(p.winRate20d)} ($reliability)")
            sb.appendLine("  평균 수익률: 5일 ${pctSigned(p.avgReturn5d)}, 10일 ${pctSigned(p.avgReturn10d)}, 20일 ${pctSigned(p.avgReturn20d)}")

            if (p.avgMdd20d < -0.05) {
                sb.appendLine("  주의: 평균 최대낙폭 ${pctSigned(p.avgMdd20d)}로 하방 리스크 존재")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    /** 신호 점수 해석 */
    fun interpretSignalScoring(ss: SignalScoringResult): String {
        val sb = StringBuilder()
        val sentiment = when {
            ss.totalScore >= 70 -> "강한 매수 신호"
            ss.totalScore >= 55 -> "약한 매수 신호"
            ss.totalScore <= 30 -> "강한 매도 신호"
            ss.totalScore <= 45 -> "약한 매도 신호"
            else -> "중립 신호"
        }
        sb.appendLine("${ss.contributions.size}개 지표를 가중합산한 종합 점수: ${ss.totalScore}/100 ($sentiment)")
        sb.appendLine("지배적 방향: ${ss.dominantDirection}")
        sb.appendLine()

        // 주요 기여 신호
        val active = ss.contributions.filter { it.signal > 0 }
            .sortedByDescending { it.contributionPercent }
        if (active.isNotEmpty()) {
            sb.appendLine("활성 신호:")
            active.take(5).forEach { c ->
                val dir = if (c.direction > 0) "매수" else if (c.direction < 0) "매도" else "중립"
                sb.appendLine("  - ${c.name}: $dir (기여도 ${String.format("%.1f", c.contributionPercent)}%)")
            }
        }

        // 충돌 신호
        if (ss.conflictingSignals.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("주의 - 충돌 신호 ${ss.conflictingSignals.size}개:")
            ss.conflictingSignals.forEach { c ->
                sb.appendLine("  - ${c.description}")
            }
            sb.appendLine("충돌 신호가 있으면 단일 방향 베팅의 리스크가 높아집니다.")
        }
        return sb.toString()
    }

    /** 상관 분석 해석 */
    fun interpretCorrelation(ca: CorrelationAnalysis): String {
        val sb = StringBuilder()
        if (ca.correlations.isEmpty()) {
            return "상관 분석 데이터가 부족합니다."
        }

        sb.appendLine("${ca.correlations.size}쌍의 지표 간 상관관계를 분석했습니다.")
        sb.appendLine()

        // 강한 상관관계
        val strong = ca.correlations.filter {
            it.strength == CorrelationStrength.STRONG_POSITIVE ||
                    it.strength == CorrelationStrength.STRONG_NEGATIVE
        }
        if (strong.isNotEmpty()) {
            sb.appendLine("주목할 강한 상관관계:")
            strong.forEach { c ->
                sb.appendLine("  - ${c.indicator1} ↔ ${c.indicator2}: r=${String.format("%.2f", c.pearsonR)} (${c.strength.label})")
            }
            sb.appendLine()
        }

        // 선행-후행 관계
        if (ca.leadLagResults.isNotEmpty()) {
            sb.appendLine("선행-후행 관계:")
            ca.leadLagResults.forEach { ll ->
                sb.appendLine("  - ${ll.interpretation}")
                if (kotlin.math.abs(ll.rAtOptimalLag) > 0.5) {
                    sb.appendLine("    → 예측력이 높은 선행지표로 활용 가능")
                }
            }
        }
        return sb.toString()
    }

    /** 베이지안 갱신 해석 */
    fun interpretBayesianUpdate(bu: BayesianUpdateResult): String {
        val sb = StringBuilder()
        val delta = bu.finalPosterior - bu.priorProbability
        val direction = if (delta > 0) "상향" else "하향"
        val magnitude = when {
            kotlin.math.abs(delta) > 0.15 -> "대폭"
            kotlin.math.abs(delta) > 0.05 -> "소폭"
            else -> "미미하게"
        }

        sb.appendLine("사전 확률 ${pct(bu.priorProbability)}에서 ${bu.updateHistory.size}개 증거를 순차 반영하여")
        sb.appendLine("최종 사후 확률 ${pct(bu.finalPosterior)}로 $magnitude $direction 조정되었습니다.")
        sb.appendLine()

        if (bu.updateHistory.isNotEmpty()) {
            sb.appendLine("갱신 과정:")
            bu.updateHistory.forEach { u ->
                val arrow = if (u.deltaProb > 0) "↑" else "↓"
                val impact = when {
                    kotlin.math.abs(u.deltaProb) > 0.05 -> "큰 영향"
                    kotlin.math.abs(u.deltaProb) > 0.02 -> "보통 영향"
                    else -> "미미한 영향"
                }
                sb.appendLine("  - ${u.signalName}: $arrow${pct(kotlin.math.abs(u.deltaProb))} ($impact)")
            }

            // 가장 큰 영향을 준 신호
            val maxImpact = bu.updateHistory.maxByOrNull { kotlin.math.abs(it.deltaProb) }
            if (maxImpact != null) {
                sb.appendLine()
                sb.appendLine("가장 큰 영향: ${maxImpact.signalName} (${pctSigned(maxImpact.deltaProb)} 변동)")
            }
        }
        return sb.toString()
    }

    /** AI 해석용 프롬프트 생성 */
    fun buildPromptForAi(result: StatisticalResult): String {
        val sb = StringBuilder()
        sb.appendLine("다음은 ${result.stockName}(${result.ticker})의 7개 통계 알고리즘 확률분석 결과입니다.")
        sb.appendLine("각 결과를 종합하여 투자자에게 유용한 해석을 제공해주세요.")
        sb.appendLine()

        result.bayesResult?.let { b ->
            sb.appendLine("[나이브 베이즈] 상승=${pct(b.upProbability)} 하락=${pct(b.downProbability)} 횡보=${pct(b.sidewaysProbability)} 샘플=${b.sampleCount}")
            if (b.dominantFeatures.isNotEmpty()) {
                sb.appendLine("  주요피처: ${b.dominantFeatures.take(3).joinToString { "${it.featureName}(${String.format("%.2f", it.likelihoodRatio)}x)" }}")
            }
        }

        result.logisticResult?.let { l ->
            sb.appendLine("[로지스틱] 점수=${l.score0to100}/100 확률=${pct(l.probability)}")
        }

        result.hmmResult?.let { h ->
            sb.appendLine("[HMM] 레짐=${h.regimeDescription} 확률=[${h.regimeProbabilities.joinToString { pct(it) }}]")
        }

        result.patternAnalysis?.let { p ->
            sb.appendLine("[패턴] 활성=${p.activePatterns.size}/${p.allPatterns.size}")
            p.activePatterns.forEach { pm ->
                sb.appendLine("  ${pm.patternDescription}: 승률=${pct(pm.winRate20d)} 수익=${pctSigned(pm.avgReturn20d)} 횟수=${pm.totalOccurrences}")
            }
        }

        result.signalScoringResult?.let { s ->
            sb.appendLine("[신호점수] ${s.totalScore}/100 방향=${s.dominantDirection} 충돌=${s.conflictingSignals.size}")
        }

        result.correlationAnalysis?.let { c ->
            if (c.correlations.isNotEmpty()) {
                sb.appendLine("[상관분석] ${c.correlations.size}쌍")
                c.correlations.filter { it.strength == CorrelationStrength.STRONG_POSITIVE || it.strength == CorrelationStrength.STRONG_NEGATIVE }
                    .forEach { cr -> sb.appendLine("  ${cr.indicator1}↔${cr.indicator2}: r=${String.format("%.2f", cr.pearsonR)}") }
            }
        }

        result.bayesianUpdateResult?.let { b ->
            sb.appendLine("[베이지안] 사전=${pct(b.priorProbability)} → 사후=${pct(b.finalPosterior)}")
        }

        return sb.toString()
    }

    // --- 내부 유틸 ---

    private fun assessOverallDirection(result: StatisticalResult): String {
        var bullScore = 0
        var bearScore = 0
        var total = 0

        result.bayesResult?.let {
            total++
            if (it.upProbability > it.downProbability) bullScore++ else bearScore++
        }
        result.logisticResult?.let {
            total++
            if (it.score0to100 >= 55) bullScore++ else if (it.score0to100 <= 45) bearScore++
        }
        result.hmmResult?.let {
            total++
            when (it.currentRegime) {
                HmmResult.REGIME_LOW_VOL_UP, HmmResult.REGIME_HIGH_VOL_UP -> bullScore++
                HmmResult.REGIME_HIGH_VOL_DOWN -> bearScore++
            }
        }
        result.signalScoringResult?.let {
            total++
            if (it.totalScore >= 55) bullScore++ else if (it.totalScore <= 45) bearScore++
        }
        result.bayesianUpdateResult?.let {
            total++
            if (it.finalPosterior > 0.55) bullScore++ else if (it.finalPosterior < 0.45) bearScore++
        }

        if (total == 0) return "판단 불가"

        val bullRatio = bullScore.toDouble() / total
        val bearRatio = bearScore.toDouble() / total

        return when {
            bullRatio >= 0.7 -> "다수 지표가 상승을 시사합니다. 다만 시장 환경 변화에 유의하세요."
            bearRatio >= 0.7 -> "다수 지표가 하락을 시사합니다. 리스크 관리에 유의하세요."
            bullRatio > bearRatio -> "소폭 상승 우위이나 의견이 엇갈려 방향성이 불확실합니다."
            bearRatio > bullRatio -> "소폭 하락 우위이나 의견이 엇갈려 방향성이 불확실합니다."
            else -> "지표 간 의견이 팽팽히 대립하여 뚜렷한 방향성을 판단하기 어렵습니다."
        }
    }

    private fun pct(v: Double) = "${String.format("%.1f", v * 100)}%"
    private fun pctSigned(v: Double): String {
        val s = String.format("%.1f", v * 100)
        return if (v >= 0) "+${s}%" else "${s}%"
    }
}
