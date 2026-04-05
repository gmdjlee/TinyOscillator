package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.AlgoResult
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 알고리즘 신호 충돌 감지기.
 *
 * 7~11개 알고리즘 점수의 표준편차를 기반으로
 * 충돌 수준(NONE/LOW/HIGH/CRITICAL)을 판별하고
 * 추천 포지션 배수를 산출한다.
 *
 * 분석 참고용 — 투자 조언이 아님.
 */
object SignalConflictDetector {

    data class ConflictResult(
        /** 알고리즘 점수의 표준편차 */
        val stdDev: Float,
        /** 충돌 수준 */
        val conflictLevel: ConflictLevel,
        /** 강세 알고리즘 수 (score > 0.6) */
        val bullCount: Int,
        /** 약세 알고리즘 수 (score < 0.4) */
        val bearCount: Int,
        /** 중립 알고리즘 수 */
        val neutralCount: Int,
        /** 추천 포지션 배수 [0, 1] */
        val positionMultiplier: Float,
        /** 한국어 경고 메시지 */
        val warningMessage: String,
    )

    enum class ConflictLevel(val threshold: Float, val positionMultiplier: Float) {
        NONE(0f, 1.0f),
        LOW(0.12f, 0.75f),
        HIGH(0.18f, 0.50f),
        CRITICAL(0.25f, 0.25f),
    }

    fun detect(algoResults: Map<String, AlgoResult>): ConflictResult {
        val scores = algoResults.values.map { it.score }
        if (scores.isEmpty()) return emptyResult()

        val mean = scores.average().toFloat()
        val stdDev = sqrt(
            scores.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat()
                / scores.size
        )

        val bullCount = scores.count { it > 0.60f }
        val bearCount = scores.count { it < 0.40f }
        val neutralCount = scores.size - bullCount - bearCount

        val level = when {
            stdDev < ConflictLevel.LOW.threshold      -> ConflictLevel.NONE
            stdDev < ConflictLevel.HIGH.threshold     -> ConflictLevel.LOW
            stdDev < ConflictLevel.CRITICAL.threshold -> ConflictLevel.HIGH
            else                                       -> ConflictLevel.CRITICAL
        }

        val message = buildMessage(level, bullCount, bearCount, stdDev)

        return ConflictResult(
            stdDev = stdDev,
            conflictLevel = level,
            bullCount = bullCount,
            bearCount = bearCount,
            neutralCount = neutralCount,
            positionMultiplier = level.positionMultiplier,
            warningMessage = message,
        )
    }

    private fun buildMessage(
        level: ConflictLevel, bull: Int, bear: Int, std: Float
    ): String {
        val stdPct = (std * 100).roundToInt()
        return when (level) {
            ConflictLevel.NONE ->
                ""
            ConflictLevel.LOW ->
                "알고리즘 의견 경미한 불일치 (σ=${stdPct}%): 강세 ${bull}개 vs 약세 ${bear}개"
            ConflictLevel.HIGH ->
                "알고리즘 의견 충돌 주의 (σ=${stdPct}%): 강세 ${bull}개 vs 약세 ${bear}개 — 추천 포지션 50% 축소"
            ConflictLevel.CRITICAL ->
                "알고리즘 의견 극심한 분열 (σ=${stdPct}%): 분석 신뢰도 낮음 — 포지션 75% 축소 권장"
        }
    }

    private fun emptyResult() = ConflictResult(
        stdDev = 0f,
        conflictLevel = ConflictLevel.NONE,
        bullCount = 0,
        bearCount = 0,
        neutralCount = 0,
        positionMultiplier = 1f,
        warningMessage = "",
    )
}
