package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 「주도주 붕괴 판단 계기판」 도메인 모델 (TASK.md §2·§3·부록 A).
 *
 * 스코어링 임계치의 SSOT는 TASK.md §3 — 프로토타입 bear_signal_dashboard.jsx와 1:1.
 * 순수 Kotlin, 안드로이드 의존성 0.
 */

/** 신호 레벨 (0 안전 · 1 주의 · 2 경고 · 3 위험) */
enum class SignalLevel(val label: String) {
    SAFE("안전"),
    CAUTION("주의"),
    WARN("경고"),
    DANGER("위험")
}

/** 금리 방아쇠 GATE 상태 라벨 (§3.4) */
enum class GateState(val label: String) {
    NORMALIZING("정상화 구간"),
    NEARING("경계 접근"),
    CRITICAL("임계 접근"),
    TIGHTENING("긴축 돌입")
}

/** 종합 국면 신호등 (§3.6 상태 기계) */
enum class BearPhase { GREEN, AMBER, ORANGE, RED }

/** 신규 이탈 낙폭 심도 (§3.1) */
enum class Depth { SHALLOW, DEEPENING, DEEP }

/** 입력값 출처 — 자동 수집 / 수동 오버라이드 (§1.2 하이브리드 아키텍처) */
enum class InputSource { AUTO, MANUAL }

/**
 * 국가별 지수 수익률 (도표48 한 행).
 *
 * @param r 누적수익률 [−12M, −6M, −3M, −1M] (%). 미수집 기간은 null.
 * @param lead 주도 지수 여부 (코스피 = true)
 */
data class MarketReturns(
    val name: String,
    val r: List<Double?>,
    val lead: Boolean = false
)

/**
 * 신호1 마켓 분석 결과 (§3.1 analyzeMarkets).
 *
 * @param neg 선택 기간 수익률 음수 지수 수
 * @param worstNew 신규 이탈(12M>0 && 해당 기간<0) 지수 중 최저 수익률 (없으면 0.0)
 * @param depth worstNew 기반 낙폭 심도
 */
data class MarketAnalysis(
    val neg: Int,
    val worstNew: Double,
    val depth: Depth
)

/**
 * 스코어링 입력 (부록 A 스켈레톤과 동일 필드·값 의미).
 *
 * @param periodIdx 기간 인덱스 0=12M, 1=6M, 2=3M, 3=1M (기본 1M)
 * @param up 직전 6M ±3σ 초과 상승일 수
 * @param down 직전 6M ±3σ 초과 하락일 수
 * @param deepening 신호1 낙폭 심화 여부 (신호2 보조 판단)
 * @param loss 적자 상장 비중 (%)
 * @param etf IPO ETF 방향 — "up" | "flat" | "down"
 * @param big 대어 공모 소화 — "smooth" | "pending" | "failed"
 * @param rate 기준금리 상단 (%)
 * @param dir 정책 방향 — "ease" | "hold" | "hike"
 * @param credit 신용거래융자 잔고 (조원)
 * @param margin 반대매매 임박 여부
 * @param semi 반도체 수출 비중 (%)
 * @param kospi2 삼성전자+SK하이닉스 코스피 비중 (%)
 * @param buffer 완충 산업(자동차/기계/석유) 건재 여부
 */
data class BearSignalInputs(
    val markets: List<MarketReturns>,
    val periodIdx: Int = 3,
    val up: Int,
    val down: Int,
    val deepening: Boolean,
    val loss: Double,
    val etf: String,
    val big: String,
    val rate: Double,
    val dir: String,
    val credit: Double,
    val margin: Boolean,
    val semi: Double,
    val kospi2: Double,
    val buffer: Boolean
)

/**
 * 종합 판정 결과 (§3.6 composite).
 *
 * @param lead 선행 신호 합 s1+s2+s3 (0..9)
 * @param leadPct round(lead/9*100)
 * @param warn 경고 이상(≥2) 선행 신호 수
 * @param amp 증폭 계수 (×1.0~1.6)
 */
data class BearSignalResult(
    val s1: Int,
    val s2: Int,
    val s3: Int,
    val gate: Int,
    val amp: Double,
    val lead: Int,
    val leadPct: Int,
    val warn: Int,
    val phase: BearPhase,
    val ma: MarketAnalysis
)

/** [BearType.recoveryLabel] 회복 가능성 등급 — 프로토타입 `recoveryC`(red/amber/accent) 색상 힌트와 대응(UI에서 색 매핑) */
enum class RecoveryOutlook { LOWEST, MEDIUM, PATIENCE }

/**
 * 약세장 3유형 (§3.7 정적 참조 — 유형별 회복 가능성, 프로토타입 `TYPES` 1:1, Phase 4에서 채움).
 *
 * @param index 0-based 유형 인덱스(0=유형1 경쟁·역전, 1=유형2 전방수요·사이클, 2=유형3 밸류·금리).
 * 유형3(index=2)이 `gate>=1`일 때 "현재 활성 방아쇠"로 하이라이트된다
 * (프로토타입 `i === 2 && r.gate >= 1`, [com.tinyoscillator.feature.bearsignal.domain.model.BearSignalStaticContent.ACTIVE_TYPE_INDEX]).
 */
data class BearType(
    val index: Int,
    val title: String,
    val axis: String,
    val recoveryLabel: String,
    val recoveryOutlook: RecoveryOutlook,
    val theory: String,
    val cases: String,
    val why: String,
    val monitor: List<String>
)

/** 유형별 모니터링 체크리스트 항목 UI 상태 (§3.7 — Phase 4 화면 조립, persistence 없는 로컬 토글) */
data class MonitorItem(
    val label: String,
    val checked: Boolean = false
)
