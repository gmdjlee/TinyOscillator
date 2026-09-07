package com.tinyoscillator.domain.model

import java.time.LocalDate

/**
 * 주체별 매물대(Investor Volume Profile) 도메인 모델.
 * 명세: docs/TASK_investor_volume_profile.md §4.
 *
 * 기존 [VolumeProfile]/[VolumeBucket](캔들 방향 기준 매물대 오버레이)과는 별개 기능이다.
 * 이름 충돌을 피하기 위해 이 기능의 타입은 전부 `Investor` 접두를 쓴다.
 */

/** 투자 주체. 표시 순서는 선언 순서(개인 → 외국인 → 기관)로 고정한다. */
enum class InvestorType(val label: String) {
    INDIVIDUAL("개인"),
    FOREIGN("외국인"),
    INSTITUTION("기관")
}

/** 산출 기준. §5.4 — 매수 누적은 상각 없음, 잔존 추정은 비례상각. */
enum class ProfileBasis(val label: String) {
    CUMULATIVE_BUY("매수 누적"),
    RESIDUAL("잔존 추정")
}

/** 차트 표시 방식(표시 필터일 뿐 재계산 대상이 아니다). */
enum class ProfileDisplayMode(val label: String) {
    GROUPED("분리"),
    STACKED("누적")
}

/**
 * 기간. [days]가 null인 항목은 유스케이스가 창을 해소한다(§5 서두, §5.6).
 * ALL은 최근 3년 상한(§0.3-C).
 */
enum class ProfilePeriod(val label: String, val days: Long?) {
    ALL("전체", 365L * 3),
    Y1("1년", 365),
    M6("6개월", 182),
    M3("3개월", 91),
    M1("1개월", 30),
    SINCE_SWING_HIGH("스윙고점 이후", null),
    SINCE_SWING_LOW("스윙저점 이후", null),
    CUSTOM("사용자 지정", null)
}

/**
 * 한 주체의 하루치 매수·매도.
 * amount 단위는 원(KIS 응답의 백만원을 파싱 직후 ×1_000_000L로 승격한 값).
 * [sellAmount]는 원천 보존용이며 현재 소비처가 없다.
 */
data class InvestorLeg(
    val buyVolume: Long,
    val buyAmount: Long,
    val sellVolume: Long,
    val sellAmount: Long
) {
    /** 매수 평균단가(원). 배분 중심을 고정하는 유일한 관측 앵커(§5.1). */
    val buyVwap: Double get() = if (buyVolume > 0) buyAmount.toDouble() / buyVolume else 0.0
}

/**
 * 하루치 원천 데이터. 가격은 전부 수정주가 미반영 원주가, 수량은 원수량.
 * 시가·거래대금은 소비처가 없어 담지 않는다.
 */
data class DailyInvestorFlow(
    val date: LocalDate,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
    val flows: Map<InvestorType, InvestorLeg>
)

/** 재계산을 유발하는 질의. 주체 선택·표시 방식은 여기에 포함하지 않는다(표시 필터). */
data class InvestorProfileQuery(
    val period: ProfilePeriod = ProfilePeriod.M6,
    /** null이면 §5.4 자동 규칙(창 내 거래일 60 미만 → 매수 누적, 이상 → 잔존 추정). */
    val basis: ProfileBasis? = null,
    /** 10 / 15 / 20 / 25 */
    val binCount: Int = 20,
    /** period == CUSTOM일 때만 사용 */
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null
)

/** 가격 구간 [lower, upper). 마지막 구간은 upper 포함(§5.3). */
data class InvestorPriceBin(val lower: Double, val upper: Double) {
    val center: Double get() = (lower + upper) / 2
}

/**
 * 파생 지표(§5.8). 주체 합계 S_i = Σ_type byInvestor[type][i] 기준.
 * 존(zone)은 최대 구간에서 50% 이상인 이웃으로 확장한 연속 인덱스 범위다.
 */
data class InvestorProfileMetrics(
    /** 전체 거래량 배분의 최다 구간 중심(원) */
    val poc: Double,
    /** 전체 거래량 70% 밸류 에어리어 */
    val valueAreaHigh: Double,
    val valueAreaLow: Double,
    /** center > basePrice 구간 합계(주체별) */
    val upperSupply: Map<InvestorType, Double>,
    /** center <= basePrice 구간 합계(주체별) */
    val lowerSupply: Map<InvestorType, Double>,
    /** 상방 캡 존. 상방에 물량이 없으면 null */
    val capZone: IntRange?,
    /** 캡 존 안 주체 합계(주) */
    val capZoneVolume: Double,
    /** 하방 지지 존 */
    val supportZone: IntRange?,
    val supportZoneVolume: Double,
    /** 주체 합계 < 평균의 20%인 구간 수 */
    val lowVolumeNodeCount: Int,
    /** 상방 주체 합계 / 창 내 일평균 거래량 */
    val absorptionDays: Double,
    /** 창 내 일평균 거래량(존 물량의 일수 환산용) */
    val averageDailyVolume: Double
)

/**
 * 산출 결과. [byInvestor]의 값은 전부 0 이상이어야 한다(§4 금지 사항).
 * 전체 거래량 배분은 유스케이스 내부 변수로만 존재한다.
 */
data class InvestorVolumeProfile(
    val bins: List<InvestorPriceBin>,
    /** bins와 같은 길이. 전부 >= 0 */
    val byInvestor: Map<InvestorType, DoubleArray>,
    /** 창 내 최근 종가. UI 라벨은 "기준가(최근 종가)" */
    val basePrice: Long,
    val basis: ProfileBasis,
    val autoBasis: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val tradingDays: Int,
    val binWidth: Double,
    val metrics: InvestorProfileMetrics,
    /** §5.7 절단 발생 시 절단 시작일 */
    val truncatedAt: LocalDate?,
    /** §5.6 전환점 미검출 폴백 여부 */
    val swingFallback: Boolean,
    /** 스윙 기간일 때 전환점 날짜(창 첫 행), 그 외 null */
    val anchorDate: LocalDate?,
    /** 스윙 고점이면 그 날 high, 저점이면 low */
    val anchorPrice: Long?
)
