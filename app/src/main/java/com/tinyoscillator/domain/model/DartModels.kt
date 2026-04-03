package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

// ─── DART 공시 원시 데이터 ───

/** DART OpenAPI에서 조회한 공시 정보 */
data class DartDisclosure(
    val rceptNo: String,        // 접수번호 (PK)
    val rceptDt: String,        // 접수일자 (yyyyMMdd)
    val reportNm: String,       // 보고서명 (한국어)
    val corpName: String,       // 회사명
    val corpCode: String,       // DART 고유번호 (8자리)
    val eventType: String       // 분류된 이벤트 타입
)

/** DART corp_code ↔ stock_code 매핑 */
data class CorpCodeEntry(
    val corpCode: String,       // DART 고유번호 (8자리)
    val corpName: String,       // 회사명
    val stockCode: String       // 종목코드 (6자리, 비상장은 빈 문자열)
)

// ─── DART 이벤트 스터디 결과 ───

/** 이벤트 스터디 CAR (Cumulative Abnormal Return) 결과 */
@Serializable
data class EventStudyResult(
    val beta: Double,
    val carFinal: Double,       // 최종 CAR
    val tStat: Double,          // t-통계량
    val nObs: Int,              // 추정 윈도우 관측 수
    val eventType: String,      // 이벤트 분류
    val eventDate: String,      // 이벤트 발생일 (yyyyMMdd)
    val significant: Boolean    // |t_stat| > 2.0
)

/** DART 이벤트 분석 결과 (9번째 엔진) */
@Serializable
data class DartEventResult(
    /** 종합 이벤트 신호 점수 (0.0~1.0, 0.5=중립) */
    val signalScore: Double,
    /** 최근 가장 큰 영향의 이벤트 타입 */
    val dominantEventType: String,
    /** 가장 최근 이벤트의 CAR */
    val latestCar: Double,
    /** 조회 기간 내 이벤트 수 */
    val nEvents: Int,
    /** 개별 이벤트 스터디 결과 (최신 3건) */
    val eventStudies: List<EventStudyResult> = emptyList(),
    /** 이벤트 타입별 원핫 신호 */
    val eventTypeSignals: Map<String, Double> = emptyMap(),
    /** 데이터 기준일 (yyyyMMdd) */
    val dataDate: String = "",
    /** DART API 사용 불가 시 사유 */
    val unavailableReason: String? = null
)

// ─── 이벤트 타입 상수 ───

/** DART 공시 이벤트 타입 분류 */
object DartEventType {
    const val RIGHTS_OFFERING = "RIGHTS_OFFERING"       // 유상증자
    const val BUYBACK = "BUYBACK"                       // 자사주 매입/소각
    const val OWNERSHIP_CHANGE = "OWNERSHIP_CHANGE"     // 지분 변동
    const val MGMT_CHANGE = "MGMT_CHANGE"               // 경영진 변동
    const val EARNINGS_SURPRISE = "EARNINGS_SURPRISE"   // 실적 서프라이즈
    const val DIVIDEND_CHANGE = "DIVIDEND_CHANGE"       // 배당 변경
    const val OTHER = "OTHER"                           // 기타

    val ALL_TYPES = listOf(
        RIGHTS_OFFERING, BUYBACK, OWNERSHIP_CHANGE,
        MGMT_CHANGE, EARNINGS_SURPRISE, DIVIDEND_CHANGE, OTHER
    )

    /**
     * 한국어 보고서명에서 이벤트 타입을 키워드 매칭으로 분류
     */
    private val KEYWORD_MAP = mapOf(
        RIGHTS_OFFERING to listOf("유상증자", "신주발행", "주주배정", "제3자배정", "일반공모"),
        BUYBACK to listOf("자기주식", "자사주", "자기주식취득", "자기주식처분", "자사주매입", "자사주소각"),
        OWNERSHIP_CHANGE to listOf("주식등의대량보유", "임원ㆍ주요주주", "대량보유", "지분변동", "최대주주변경"),
        MGMT_CHANGE to listOf("대표이사변경", "임원선임", "사외이사", "감사선임", "이사회구성"),
        EARNINGS_SURPRISE to listOf("영업실적", "매출액", "실적공시", "분기보고서", "반기보고서", "사업보고서", "실적"),
        DIVIDEND_CHANGE to listOf("배당", "현금배당", "주식배당", "중간배당", "결산배당")
    )

    fun classify(reportNm: String): String {
        for ((eventType, keywords) in KEYWORD_MAP) {
            if (keywords.any { reportNm.contains(it) }) {
                return eventType
            }
        }
        return OTHER
    }

    fun toKorean(eventType: String): String = when (eventType) {
        RIGHTS_OFFERING -> "유상증자"
        BUYBACK -> "자사주 매입/소각"
        OWNERSHIP_CHANGE -> "지분 변동"
        MGMT_CHANGE -> "경영진 변동"
        EARNINGS_SURPRISE -> "실적 공시"
        DIVIDEND_CHANGE -> "배당 변경"
        OTHER -> "기타"
        else -> eventType
    }
}
