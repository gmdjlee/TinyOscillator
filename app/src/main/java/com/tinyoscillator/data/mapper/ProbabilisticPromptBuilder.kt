package com.tinyoscillator.data.mapper

import com.tinyoscillator.domain.model.*

/**
 * StatisticalResult → LLM 프롬프트 변환
 *
 * 핵심 원칙:
 * 1. 모든 숫자는 "이미 계산됨" 강조 — LLM이 재계산하지 않도록
 * 2. null 결과는 스킵 (토큰 절약)
 * 3. 전체 입력 < 4,000 tokens 목표
 * 4. ChatML 포맷 (<|im_start|>) 지원
 */
class ProbabilisticPromptBuilder {

    companion object {
        private const val MAX_WORDS = 300
    }

    /**
     * ChatML 포맷 프롬프트 생성
     */
    fun build(result: StatisticalResult): String {
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(result)

        return """<|im_start|>system
$systemPrompt<|im_end|>
<|im_start|>user
$userPrompt<|im_end|>
<|im_start|>assistant
"""
    }

    /**
     * 시스템 프롬프트만 반환 (API 사용 시)
     */
    fun buildSystemPrompt(): String = """You are a Korean stock analyst. All numbers below are PRE-COMPUTED by statistical engines. NEVER recalculate any number. Your job is to INTERPRET and SYNTHESIZE the results.

분석 가이드라인:
1. 10개 알고리즘 결과를 2-레벨 스태킹 앙상블로 종합하여 투자 의견을 제시
2. 상충 신호가 있으면 과거 승률이 높은 쪽에 가중
3. 레짐(HMM)에 따라 신호의 신뢰도를 조정
4. 확률이 0.6 이상이면 유의미, 0.7 이상이면 강한 신호로 해석
5. 포지션 가이드(Kelly+CVaR)가 있으면 추천 비중을 참고하여 행동 권고에 반영

JSON 출력 스키마:
{
  "overall_assessment": "매수/매도/관망 중 하나 + 한 줄 근거",
  "confidence": 0.0~1.0,
  "insights": [{"algorithm": "이름", "interpretation": "해석", "significance": "높음/보통/낮음"}],
  "conflicts": ["상충 신호 설명"],
  "risks": ["리스크 요인"],
  "action": "구체적 행동 권고"
}

언어: 한국어. 최대 ${MAX_WORDS}단어. JSON만 출력."""

    /**
     * 사용자 프롬프트 생성 (결과 데이터 포함)
     */
    fun buildUserPrompt(result: StatisticalResult): String {
        val sb = StringBuilder()
        sb.appendLine("[종목: ${result.stockName} (${result.ticker})]")
        sb.appendLine()

        // Bayes 결과
        result.bayesResult?.let { bayes ->
            sb.appendLine("[나이브 베이즈 분류]")
            sb.appendLine("- 상승확률: ${pct(bayes.upProbability)}, 하락: ${pct(bayes.downProbability)}, 횡보: ${pct(bayes.sidewaysProbability)}")
            sb.appendLine("- 샘플수: ${bayes.sampleCount}건")
            if (bayes.dominantFeatures.isNotEmpty()) {
                val topFeatures = bayes.dominantFeatures.take(3)
                    .joinToString(", ") { "${it.featureName}(${fmt2(it.likelihoodRatio)})" }
                sb.appendLine("- 주요 기여: $topFeatures")
            }
            sb.appendLine()
        }

        // Logistic 결과
        result.logisticResult?.let { logistic ->
            sb.appendLine("[로지스틱 회귀]")
            sb.appendLine("- 상승확률: ${pct(logistic.probability)}, 점수: ${logistic.score0to100}/100")
            sb.appendLine()
        }

        // HMM 결과
        result.hmmResult?.let { hmm ->
            sb.appendLine("[HMM 레짐]")
            sb.appendLine("- 현재: ${hmm.regimeDescription}")
            sb.appendLine("- 확률: R0=${pct(hmm.regimeProbabilities[0])}, R1=${pct(hmm.regimeProbabilities[1])}, R2=${pct(hmm.regimeProbabilities[2])}, R3=${pct(hmm.regimeProbabilities[3])}")
            sb.appendLine()
        }

        // 패턴 분석
        result.patternAnalysis?.let { patterns ->
            sb.appendLine("[패턴 분석]")
            sb.appendLine("- 활성 패턴: ${patterns.activePatterns.size}/${patterns.allPatterns.size}개")
            for (p in patterns.activePatterns.take(3)) {
                sb.appendLine("  · ${p.patternDescription}: 20일승률 ${pct(p.winRate20d)}, 평균수익 ${pct(p.avgReturn20d)}")
            }
            sb.appendLine()
        }

        // 신호 점수
        result.signalScoringResult?.let { signal ->
            sb.appendLine("[신호 점수]")
            sb.appendLine("- 종합: ${signal.totalScore}/100 (${signal.dominantDirection})")
            if (signal.conflictingSignals.isNotEmpty()) {
                sb.appendLine("- 충돌: ${signal.conflictingSignals.joinToString("; ") { it.description }}")
            }
            sb.appendLine()
        }

        // 상관 분석
        result.correlationAnalysis?.let { corr ->
            if (corr.correlations.isNotEmpty()) {
                sb.appendLine("[상관 분석]")
                for (c in corr.correlations.take(3)) {
                    sb.appendLine("  · ${c.indicator1}↔${c.indicator2}: r=${fmt2(c.pearsonR)} (${c.strength.label})")
                }
                sb.appendLine()
            }
        }

        // 베이지안 갱신
        result.bayesianUpdateResult?.let { bu ->
            sb.appendLine("[베이지안 갱신]")
            sb.appendLine("- 사전확률: ${pct(bu.priorProbability)} → 사후확률: ${pct(bu.finalPosterior)}")
            if (bu.updateHistory.isNotEmpty()) {
                val topUpdates = bu.updateHistory
                    .sortedByDescending { kotlin.math.abs(it.deltaProb) }
                    .take(3)
                    .joinToString(", ") { "${it.signalName}(${if (it.deltaProb > 0) "+" else ""}${pct(it.deltaProb)})" }
                sb.appendLine("- 주요 변화: $topUpdates")
            }
            sb.appendLine()
        }

        // 투자자 자금흐름
        result.orderFlowResult?.let { of ->
            sb.appendLine("[투자자 자금흐름]")
            sb.appendLine("- 방향: ${of.flowDirection} (${of.flowStrength})")
            sb.appendLine("- 종합점수: ${pct(of.buyerDominanceScore)}, OFI(5d): ${fmt2(of.ofi5d)}, OFI(20d): ${fmt2(of.ofi20d)}")
            sb.appendLine("- 기관-외국인 괴리: ${pct(of.institutionalDivergence)}, 외국인 압력: ${fmt2(of.foreignBuyPressure)}")
            sb.appendLine("- 추세정렬: ${pct(of.trendAlignment)}, 평균회귀: ${pct(of.meanReversionSignal)}")
            sb.appendLine()
        }

        // DART 공시 이벤트
        result.dartEventResult?.let { de ->
            if (de.nEvents > 0) {
                sb.appendLine("[DART 공시 이벤트]")
                sb.appendLine("- 신호점수: ${pct(de.signalScore)}, 이벤트수: ${de.nEvents}")
                sb.appendLine("- 주요유형: ${DartEventType.toKorean(de.dominantEventType)}, 최근CAR: ${pct(de.latestCar)}")
                de.eventStudies.take(3).forEach { es ->
                    sb.appendLine("  · ${es.eventDate} ${DartEventType.toKorean(es.eventType)}: CAR=${pct(es.carFinal)} t=${fmt2(es.tStat)}")
                }
                sb.appendLine()
            }
        }

        // 5팩터 모델
        result.korea5FactorResult?.let { k5 ->
            if (k5.unavailableReason == null) {
                sb.appendLine("[한국형 5팩터 모델]")
                sb.appendLine("- 신호점수: ${pct(k5.signalScore)}, alpha: ${String.format("%+.4f", k5.alphaRaw)}, z-score: ${String.format("%+.2f", k5.alphaZscore)}")
                sb.appendLine("- 베타: MKT=${fmt2(k5.betas.mkt)}, SMB=${fmt2(k5.betas.smb)}, HML=${fmt2(k5.betas.hml)}, RMW=${fmt2(k5.betas.rmw)}, CMA=${fmt2(k5.betas.cma)}")
                sb.appendLine("- R²=${String.format("%.1f%%", k5.rSquared * 100)}, 관측치=${k5.nObs}개월")
                sb.appendLine()
            }
        }

        // 매크로 환경
        result.macroSignalResult?.let { m ->
            if (m.unavailableReason == null) {
                sb.appendLine("[매크로 환경]")
                sb.appendLine("- 분류: ${m.macroEnv}")
                sb.appendLine("- 기준금리 YoY: ${String.format("%+.2f", m.baseRateYoy)}pp")
                sb.appendLine("- M2 통화량 YoY: ${String.format("%+.1f", m.m2Yoy)}%")
                sb.appendLine("- 산업생산 YoY: ${String.format("%+.1f", m.iipYoy)}%")
                sb.appendLine("- USD/KRW YoY: ${String.format("%+.1f", m.usdKrwYoy)}%")
                sb.appendLine("- CPI YoY: ${String.format("%+.1f", m.cpiYoy)}%")
                sb.appendLine()
            }
        }

        // 포지션 가이드
        result.positionRecommendation?.let { pr ->
            if (pr.unavailableReason == null) {
                sb.appendLine("[포지션 가이드 (Kelly+CVaR)]")
                sb.appendLine("- 추천 비중: ${String.format("%.1f%%", pr.recommendedPct * 100)}")
                sb.appendLine("- 신호 우위: ${String.format("%+.1f%%p", pr.signalEdge * 100)}")
                sb.appendLine("- Kelly(분율): ${String.format("%.1f%%", pr.kellyFractional * 100)}")
                sb.appendLine("- CVaR(95%,1d): ${String.format("%.2f%%", pr.cvar1d * 100)}, 한도: ${String.format("%.1f%%", pr.cvarLimit * 100)}")
                sb.appendLine("- 제한사유: ${pr.sizeReasonCode.label}")
                sb.appendLine("※ 분석 참고용이며 투자 조언이 아닙니다.")
                sb.appendLine()
            }
        }

        sb.appendLine("위 데이터를 종합하여 JSON으로 응답하세요.")

        return sb.toString()
    }

    /**
     * 프롬프트 토큰 수 추정 (한국어: ~2 chars/token, 영어: ~4 chars/token)
     */
    fun estimateTokenCount(prompt: String): Int {
        val koreanChars = prompt.count { it.code in 0xAC00..0xD7A3 || it.code in 0x3131..0x318E }
        val otherChars = prompt.length - koreanChars
        return (koreanChars / 1.5 + otherChars / 4.0).toInt()
    }

    private fun pct(value: Double): String = "${(value * 100).let { String.format("%.1f", it) }}%"
    private fun fmt2(value: Double): String = String.format("%.2f", value)
}
