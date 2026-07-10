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
 * Phase 2는 [B] 등급 스칼라 지표([AMP_SEMI]/[AMP_BUFFER]/[GATE_RATE]/[GATE_DIR]/[S3_ETF])를 같은
 * 범용 key-value 캐시 테이블에 키만 추가해 재사용한다(마이그레이션 불필요, Room 버전 그대로 v34).
 * 값이 Boolean/String인 지표는 [BearSignalAutoCacheMapper]가 Double로 인코딩해 저장한다.
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
    AMP_KOSPI2("amp_kospi2"),

    /** §3.5 증폭·집중 — 반도체 수출 비중(%) (관세청 무역통계, Phase 2) */
    AMP_SEMI("amp_semi"),

    /** §3.5 증폭·집중 — 완충산업(자동차/일반기계/석유) 건재 여부. 1.0=true, 0.0=false 인코딩 */
    AMP_BUFFER("amp_buffer"),

    /** §3.4 금리 방아쇠 — 미 연준 목표금리 상단(%) (FRED `DFEDTARU`, Phase 2) */
    GATE_RATE("gate_rate"),

    /** §3.4 금리 방아쇠 — 한국은행 기준금리 방향. -1.0=ease, 0.0=hold, 1.0=hike 인코딩 */
    GATE_DIR("gate_dir"),

    /** §3.3 신호3 — IPO ETF(티커 `IPO`) 방향. -1.0=down, 0.0=flat, 1.0=up 인코딩 */
    S3_ETF("s3_etf");

    companion object {
        fun fromKey(key: String): BearIndicatorKey? = entries.find { it.key == key }
    }
}

/**
 * 자동 수집 결과 — Phase 1 [A] 등급 2지표 + Phase 2 [B] 등급 5지표.
 *
 * [up3]/[down3] → [BearSignalInputs.up]/[BearSignalInputs.down] 매핑(§3.2 `scoreS2` 입력).
 * [up4]/[down4] → 표시용 보조 카운트(§5.2 배지) — 스코어링에는 사용하지 않는다.
 * [kospi2] → [BearSignalInputs.kospi2] 매핑(§3.5 `amplifier` 입력).
 *
 * Phase 2 신규 필드는 모두 nullable — 구버전(5키) 캐시와의 하위 호환을 위해서다. [semi]/[buffer]는
 * §3.5 `amplifier` 입력, [rate]/[dir]는 §3.4 `scoreGate` 입력, [etf]는 §3.3 `scoreS3` 입력에 매핑된다.
 * 개별 지표는 원격 소스가 서로 달라(관세청/FRED/ECOS/시세소스) 독립적으로 실패할 수 있으므로,
 * [com.tinyoscillator.feature.bearsignal.data.repository.BearSignalRepositoryImpl.refreshExternalAutoInputs]는
 * 지표별로 best-effort 수집 후 실패한 지표만 이전 캐시값으로 유지한다.
 */
data class AutoBearSignalInputs(
    val up3: AutoIndicator<Int>,
    val down3: AutoIndicator<Int>,
    val up4: AutoIndicator<Int>,
    val down4: AutoIndicator<Int>,
    val kospi2: AutoIndicator<Double>,
    val semi: AutoIndicator<Double>? = null,
    val buffer: AutoIndicator<Boolean>? = null,
    val rate: AutoIndicator<Double>? = null,
    val dir: AutoIndicator<String>? = null,
    val etf: AutoIndicator<String>? = null
)
