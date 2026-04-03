package com.tinyoscillator.data.engine.macro

import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.domain.model.EcosDataPoint
import com.tinyoscillator.domain.model.MacroSignalResult
import timber.log.Timber
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BOK ECOS 매크로 지표 수집기
 *
 * 5개 지표를 수집하고 YoY 변화율을 계산:
 * - base_rate: 기준금리 (절대 변화, pp)
 * - m2: M2 광의통화 (YoY %)
 * - iip: 전산업생산지수 (YoY %)
 * - usd_krw: USD/KRW 환율 (YoY %)
 * - cpi: 소비자물가지수 (YoY %)
 *
 * ECOS 데이터는 1~2개월 래그 → referenceDate 기준 2개월 전까지만 사용
 */
@Singleton
class BokEcosCollector @Inject constructor(
    private val bokEcosApiClient: BokEcosApiClient
) {

    companion object {
        /** YoY 계산에 필요한 최소 기간 (13개월: 당월 + 12개월 전) */
        const val MIN_MONTHS_FOR_YOY = 13

        /** 데이터 수집 기간 (24개월) */
        const val FETCH_MONTHS = 24

        /** ECOS 데이터 래그 보정 (2개월) */
        const val DATA_LAG_MONTHS = 2L

        /** ffill 최대 허용 갭 (3개월) */
        const val MAX_FFILL_GAP = 3

        private val YYYYMM_FMT = DateTimeFormatter.ofPattern("yyyyMM")
    }

    /**
     * referenceDate 기준으로 24개월 데이터를 수집하고 YoY 변화율 계산 후
     * 가장 최근 월의 매크로 신호 벡터를 반환.
     *
     * @param apiKey BOK ECOS API 인증키
     * @param referenceDate 기준일 (yyyyMMdd 또는 yyyyMM)
     * @return 매크로 신호 결과 (실패 시 unavailableReason 포함)
     */
    suspend fun macroSignalVector(apiKey: String, referenceDate: String): MacroSignalResult {
        if (apiKey.isBlank()) {
            return MacroSignalResult(
                baseRateYoy = 0.0, m2Yoy = 0.0, iipYoy = 0.0,
                usdKrwYoy = 0.0, cpiYoy = 0.0,
                macroEnv = "NEUTRAL",
                unavailableReason = "ECOS API 키 미설정"
            )
        }

        try {
            // referenceDate에서 2개월 래그 보정
            val refMonth = parseReferenceMonth(referenceDate)
                .minusMonths(DATA_LAG_MONTHS)
            val startMonth = refMonth.minusMonths(FETCH_MONTHS.toLong())

            val startYm = startMonth.format(YYYYMM_FMT)
            val endYm = refMonth.format(YYYYMM_FMT)

            Timber.d("ECOS 매크로 데이터 수집: %s ~ %s", startYm, endYm)

            // 5개 지표 전체 수집
            val allData = bokEcosApiClient.fetchAll(apiKey, startYm, endYm)

            // 각 지표별 시계열 → 월별 맵으로 변환
            val seriesMap = mutableMapOf<String, Map<String, Double>>()
            for ((key, points) in allData) {
                if (points.isEmpty()) {
                    Timber.w("ECOS 지표 '%s' 데이터 없음", key)
                    continue
                }
                seriesMap[key] = pointsToMonthMap(points)
            }

            if (seriesMap.size < 3) {
                return MacroSignalResult(
                    baseRateYoy = 0.0, m2Yoy = 0.0, iipYoy = 0.0,
                    usdKrwYoy = 0.0, cpiYoy = 0.0,
                    macroEnv = "NEUTRAL",
                    unavailableReason = "ECOS 데이터 부족 (수집 성공: ${seriesMap.size}/5)"
                )
            }

            // YoY 변화율 계산
            val refYm = refMonth.format(YYYYMM_FMT)
            val yoyValues = computeYoyForMonth(seriesMap, refYm)

            val baseRateYoy = yoyValues["base_rate"] ?: 0.0
            val m2Yoy = yoyValues["m2"] ?: 0.0
            val iipYoy = yoyValues["iip"] ?: 0.0
            val usdKrwYoy = yoyValues["usd_krw"] ?: 0.0
            val cpiYoy = yoyValues["cpi"] ?: 0.0

            Timber.d("ECOS YoY — 금리: %.2fpp, M2: %.1f%%, IIP: %.1f%%, 환율: %.1f%%, CPI: %.1f%%",
                baseRateYoy, m2Yoy, iipYoy, usdKrwYoy, cpiYoy)

            return MacroSignalResult(
                baseRateYoy = baseRateYoy,
                m2Yoy = m2Yoy,
                iipYoy = iipYoy,
                usdKrwYoy = usdKrwYoy,
                cpiYoy = cpiYoy,
                macroEnv = "", // MacroRegimeOverlay에서 분류
                referenceMonth = refYm
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ECOS 매크로 신호 수집 실패")
            return MacroSignalResult(
                baseRateYoy = 0.0, m2Yoy = 0.0, iipYoy = 0.0,
                usdKrwYoy = 0.0, cpiYoy = 0.0,
                macroEnv = "NEUTRAL",
                unavailableReason = "ECOS 수집 실패: ${e.message}"
            )
        }
    }

    /**
     * 시계열 데이터를 월별 맵으로 변환 (ffill 적용)
     */
    internal fun pointsToMonthMap(points: List<EcosDataPoint>): Map<String, Double> {
        val sorted = points.sortedBy { it.time }
        val result = mutableMapOf<String, Double>()

        if (sorted.isEmpty()) return result

        // 기본 데이터 맵
        for (p in sorted) {
            result[p.time] = p.value
        }

        // ffill: 빈 월을 이전 값으로 채움 (최대 MAX_FFILL_GAP개월)
        val firstMonth = YearMonth.parse(sorted.first().time, YYYYMM_FMT)
        val lastMonth = YearMonth.parse(sorted.last().time, YYYYMM_FMT)

        var current = firstMonth
        var lastValue: Double? = null
        var gapCount = 0

        while (!current.isAfter(lastMonth)) {
            val key = current.format(YYYYMM_FMT)
            if (result.containsKey(key)) {
                lastValue = result[key]
                gapCount = 0
            } else if (lastValue != null && gapCount < MAX_FFILL_GAP) {
                result[key] = lastValue
                gapCount++
            }
            current = current.plusMonths(1)
        }

        return result
    }

    /**
     * 특정 월의 YoY 변화율 계산
     *
     * - base_rate: 절대 변화 (pp) — 금리는 %p 차이가 의미 있음
     * - 나머지: (현재값 - 12개월전) / 12개월전 * 100 (%)
     */
    internal fun computeYoyForMonth(
        seriesMap: Map<String, Map<String, Double>>,
        targetYm: String
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val targetMonth = YearMonth.parse(targetYm, YYYYMM_FMT)
        val prevYear = targetMonth.minusMonths(12).format(YYYYMM_FMT)

        for ((key, monthMap) in seriesMap) {
            val currentVal = findClosestValue(monthMap, targetYm, targetMonth)
            val prevVal = findClosestValue(monthMap, prevYear, targetMonth.minusMonths(12))

            if (currentVal != null && prevVal != null) {
                result[key] = if (key == "base_rate") {
                    // 기준금리: 절대 변화 (pp)
                    currentVal - prevVal
                } else {
                    // 나머지: 퍼센트 변화율
                    if (prevVal != 0.0) {
                        (currentVal - prevVal) / prevVal * 100.0
                    } else {
                        0.0
                    }
                }
            }
        }

        return result
    }

    /**
     * 정확한 월 데이터가 없으면 최대 2개월 이전까지 탐색
     */
    private fun findClosestValue(
        monthMap: Map<String, Double>,
        targetYm: String,
        targetMonth: YearMonth
    ): Double? {
        monthMap[targetYm]?.let { return it }
        // 1개월 전
        val prev1 = targetMonth.minusMonths(1).format(YYYYMM_FMT)
        monthMap[prev1]?.let { return it }
        // 2개월 전
        val prev2 = targetMonth.minusMonths(2).format(YYYYMM_FMT)
        return monthMap[prev2]
    }

    private fun parseReferenceMonth(referenceDate: String): YearMonth {
        return when (referenceDate.length) {
            6 -> YearMonth.parse(referenceDate, YYYYMM_FMT)
            8 -> {
                val ym = referenceDate.substring(0, 6)
                YearMonth.parse(ym, YYYYMM_FMT)
            }
            else -> YearMonth.now()
        }
    }
}
