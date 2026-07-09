package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.CustomsTradeItem
import kotlin.math.abs

/**
 * §3.5 증폭·집중 `semi`(반도체 수출 비중)·`buffer`(완충산업 건재 여부) 입력 계산
 * (TASK.md §1.1 각주5, §4 "수출 비중").
 *
 * **품목 매칭 방식**: 관세청 `getNitemtradeList`는 "15대 품목" 단위로 응답하지만 필드가
 * HS 코드/품목명 중 무엇으로 채워지는지 배포 환경마다 다를 수 있어, 품목명 키워드와 HS 코드
 * 접두사를 **OR 조건**으로 매칭한다(둘 중 하나만 일치해도 해당 카테고리로 분류) — 방어적 파싱.
 *
 * **`semi` 산출 방식(구현 결정, SSOT 아님)**: 관세청 API가 "15대 품목"만 반환하고 국가
 * 총수출액을 별도로 제공하지 않아, **15대 품목 합계 대비 반도체 비중**을 근사치로 사용한다.
 * 15대 품목이 통상 총수출의 70~80%대를 구성하는 것으로 알려져 있어 "총수출 대비" 실제값보다
 * 다소 높게 산출될 수 있다(§8 리스크: K-stat 총계 연동은 v2 후보).
 *
 * **`buffer` 산출 방식(구현 결정, SSOT 아님)**: 자동차+일반기계+석유제품 3개 품목 수출 합계의
 * 전년동월대비(YoY) 증감률이 [BUFFER_YOY_COLLAPSE_THRESHOLD_PCT] 이상 급감하지 않으면 "건재"로
 * 판정한다. §3 스코어링 임계치(SSOT)와 무관한 데이터-입력 산출 규칙이며, 리포트 근거가 쌓이면
 * 조정 가능(현재는 v1 캘리브레이션 값).
 *
 * 순수 산술만 수행 — 안드로이드/IO 의존성 0 (JVM 단위테스트 대상).
 */
object CustomsTradeCalculator {

    /** 반도체 카테고리 매칭 키워드/HS 접두사 (관세, HSK 8541·8542) */
    private val SEMI_KEYWORDS = listOf("반도체")
    private val SEMI_HS_PREFIXES = listOf("8541", "8542")

    /** 완충 산업 3개 카테고리 매칭 키워드/HS 접두사 */
    private val AUTO_KEYWORDS = listOf("자동차")
    private val AUTO_HS_PREFIXES = listOf("8703")
    private val MACHINERY_KEYWORDS = listOf("일반기계", "기계류")
    private val MACHINERY_HS_PREFIXES = listOf("84")
    private val PETROLEUM_KEYWORDS = listOf("석유제품", "석유")
    private val PETROLEUM_HS_PREFIXES = listOf("27")

    /**
     * 완충산업 3개 품목 합계 YoY 증감률이 이 값(%) 미만이면 "붕괴"로 판정(buffer=false).
     * 예: -20.0 → 전년동월대비 20% 이상 급감 시에만 완충 산업이 무너진 것으로 본다.
     */
    const val BUFFER_YOY_COLLAPSE_THRESHOLD_PCT = -20.0

    /**
     * §3.5 `semi` 입력 — 반도체 수출액 / 15대 품목 합계 수출액 × 100 (%).
     *
     * @return null — [items]가 비었거나 합계가 0 이하인 경우
     */
    fun computeSemiShare(items: List<CustomsTradeItem>): Double? {
        if (items.isEmpty()) return null
        val total = items.sumOf { it.exportUsdThousand }
        if (total <= 0.0) return null
        val semi = items.filter { matches(it, SEMI_KEYWORDS, SEMI_HS_PREFIXES) }.sumOf { it.exportUsdThousand }
        return semi / total * 100.0
    }

    /**
     * §3.5 `buffer` 입력 — 완충산업(자동차+일반기계+석유제품) 수출 합계의 YoY 증감률로 건재 여부 판정.
     *
     * @param currentItems 최신 조회월 15대 품목 리스트
     * @param priorYearItems 전년 동월 15대 품목 리스트
     * @return null — 두 기간 중 하나라도 완충산업 3개 품목 합계를 계산할 수 없는 경우(데이터 없음)
     */
    fun computeBufferIntact(
        currentItems: List<CustomsTradeItem>,
        priorYearItems: List<CustomsTradeItem>
    ): Boolean? {
        val current = bufferSum(currentItems) ?: return null
        val prior = bufferSum(priorYearItems) ?: return null
        if (prior == 0.0) return null
        val yoyPct = (current - prior) / abs(prior) * 100.0
        return yoyPct >= BUFFER_YOY_COLLAPSE_THRESHOLD_PCT
    }

    private fun bufferSum(items: List<CustomsTradeItem>): Double? {
        if (items.isEmpty()) return null
        return items.filter {
            matches(it, AUTO_KEYWORDS, AUTO_HS_PREFIXES) ||
                matches(it, MACHINERY_KEYWORDS, MACHINERY_HS_PREFIXES) ||
                matches(it, PETROLEUM_KEYWORDS, PETROLEUM_HS_PREFIXES)
        }.sumOf { it.exportUsdThousand }
    }

    private fun matches(item: CustomsTradeItem, keywords: List<String>, hsPrefixes: List<String>): Boolean {
        if (keywords.any { item.statKor.contains(it) }) return true
        return hsPrefixes.any { item.hsCd.startsWith(it) }
    }
}
