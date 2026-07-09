package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 자동/수동 입력값 래퍼 — 값 + 출처(AUTO/MANUAL) + 최신 갱신시각 (TASK.md §1.2 하이브리드 데이터 아키텍처).
 *
 * Phase 1은 [InputSource.AUTO]만 채운다. Phase 3에서 수동 오버라이드가 같은 래퍼를 재사용한다.
 */
data class AutoIndicator<T>(
    val value: T,
    val source: InputSource,
    val updatedAt: Long
)

/**
 * 자동/수동 입력 캐시 키 — data 계층 Entity의 primary key로도 사용한다(TASK.md §2 `AutoCacheEntity`).
 *
 * Phase 1은 [S2_UP3]/[S2_DOWN3]/[S2_UP4]/[S2_DOWN4](신호2 통계)와 [AMP_KOSPI2](증폭·집중)만 채운다.
 * Phase 2+에서 [B] 등급 지표(해외지수·IPO ETF·금리 등) 키를 같은 enum에 추가해 동일 캐시 테이블을
 * 재사용한다.
 */
enum class BearIndicatorKey(val key: String) {
    /** §3.2 신호2 — 직전 6M ±3σ 초과 상승일 수 */
    S2_UP3("s2_up3"),

    /** §3.2 신호2 — 직전 6M ±3σ 초과 하락일 수 */
    S2_DOWN3("s2_down3"),

    /** ±4σ 초과 상승일 수 (표시용 보조 카운트, 스코어링 미사용) */
    S2_UP4("s2_up4"),

    /** ±4σ 초과 하락일 수 (표시용 보조 카운트, 스코어링 미사용) */
    S2_DOWN4("s2_down4"),

    /** §3.5 증폭·집중 — 삼성전자+SK하이닉스 코스피 시가총액 비중(%) */
    AMP_KOSPI2("amp_kospi2");

    companion object {
        fun fromKey(key: String): BearIndicatorKey? = entries.find { it.key == key }
    }
}

/**
 * Phase 1 자동 수집 결과 — [A] 등급 2지표(신호2 변동성 무게중심 통계 + 코스피 2사 집중 비중).
 *
 * [up3]/[down3] → [BearSignalInputs.up]/[BearSignalInputs.down] 매핑(§3.2 `scoreS2` 입력).
 * [up4]/[down4] → 표시용 보조 카운트(§5.2 배지) — 스코어링에는 사용하지 않는다.
 * [kospi2] → [BearSignalInputs.kospi2] 매핑(§3.5 `amplifier` 입력).
 */
data class AutoBearSignalInputs(
    val up3: AutoIndicator<Int>,
    val down3: AutoIndicator<Int>,
    val up4: AutoIndicator<Int>,
    val down4: AutoIndicator<Int>,
    val kospi2: AutoIndicator<Double>
)
