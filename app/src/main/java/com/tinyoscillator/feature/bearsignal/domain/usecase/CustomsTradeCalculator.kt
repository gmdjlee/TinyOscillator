package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.CustomsTradeItem
import kotlin.math.abs

/**
 * §3.5 증폭·집중 `semi`(반도체 수출 비중)·`buffer`(완충산업 건재 여부) 입력 계산
 * (TASK.md §1.1 각주5, §4 "수출 비중").
 *
 * **품목 매칭 방식**: 관세청 `getItemtradeList`(15101609)는 HS 10단위 전 품목을 반환하므로
 * HS 코드 접두사 매칭이 1차 기준이다. 품목명 키워드는 OR 조건 보조 매칭으로 유지한다
 * (둘 중 하나만 일치해도 해당 카테고리로 분류 — 방어적 파싱).
 *
 * **`semi` 산출 방식(구현 결정, SSOT 아님)**: 응답이 HS 전 품목을 포함하므로 분모가
 * **국가 총수출액 실측**이다 — 반도체(HS 8541·8542) 수출액 / 전 품목 수출 합계 × 100.
 * (구버전 "15대 품목 합계 대비 근사"는 실제보다 높게 산출되는 리스크가 있었고,
 * 2026-07-16 엔드포인트 교체로 해소 — 임계치 캘리브레이션 시 총수출 대비 기준 사용.)
 *
 * **기저(basis) 주의 — 2026-07-16 실측 캘리브레이션**: 임계치 `amp.semiExport=20.0`의 출처인
 * 리포트/언론 인용 "반도체 수출 비중"은 MTI 831 기준(모듈류 포함)이다. 본 HS(8541·8542) 기준은
 * MTI 대비 약 -8%p 낮게 나온다(2026-05 실측: MTI 42.3% vs HS 34.0%). 한편 statKor 키워드
 * "반도체" OR 매칭이 HS 8486(제조장비)·검사기기 등을 +0.7%p가량 추가한다. 현 국면(34%)은 임계
 * 20을 크게 웃돌아 판정에 영향 없고, SSOT 조정은 리포트 근거 없이 불가하므로 임계 20.0 유지
 * — MTI 20~25% 경계 구간에서 발화가 다소 보수적(지연)일 수 있음을 기록한다.
 *
 * **`buffer` 산출 방식(구현 결정, SSOT 아님)**: 완충산업(자동차 HS 8703 + 일반기계 HS 84류 +
 * 석유제품 HS 27류) 수출 합계의 전년동월대비(YoY) 증감률이
 * [BUFFER_YOY_COLLAPSE_THRESHOLD_PCT] 이상 급감하지 않으면 "건재"로 판정한다. HS 류 단위
 * 근사(84류는 컴퓨터 등 포함, 27류는 석탄·가스 포함)이나 현재/전년 동일 기준이라 YoY 비교는
 * 일관적이다. §3 스코어링 임계치(SSOT)와 무관한 데이터-입력 산출 규칙이며, 리포트 근거가
 * 쌓이면 조정 가능(현재는 v1 캘리브레이션 값).
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
     * §3.5 `semi` 입력 — 반도체 수출액 / 전 품목 수출 합계(총수출) × 100 (%).
     *
     * @return null — [items]가 비었거나 합계가 0 이하인 경우
     */
    fun computeSemiShare(items: List<CustomsTradeItem>): Double? {
        if (items.isEmpty()) return null
        val total = items.sumOf { it.exportUsd }
        if (total <= 0.0) return null
        val semi = items.filter { matches(it, SEMI_KEYWORDS, SEMI_HS_PREFIXES) }.sumOf { it.exportUsd }
        return semi / total * 100.0
    }

    /**
     * §3.5 `buffer` 입력 — 완충산업(자동차+일반기계+석유제품) 수출 합계의 YoY 증감률로 건재 여부 판정.
     *
     * @param currentItems 최신 조회월 품목 리스트
     * @param priorYearItems 전년 동월 품목 리스트
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
        }.sumOf { it.exportUsd }
    }

    private fun matches(item: CustomsTradeItem, keywords: List<String>, hsPrefixes: List<String>): Boolean {
        if (keywords.any { item.statKor.contains(it) }) return true
        return hsPrefixes.any { item.hsCd.startsWith(it) }
    }
}
