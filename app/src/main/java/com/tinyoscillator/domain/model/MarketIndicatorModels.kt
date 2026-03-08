package com.tinyoscillator.domain.model

import java.time.LocalDate

/**
 * 시장 과매수/과매도 Oscillator 도메인 모델
 */
data class MarketOscillator(
    val id: String,
    val market: String,
    val date: String,
    val indexValue: Double,
    val oscillator: Double,
    val lastUpdated: Long
) {
    fun getStatusKorean(): String = when {
        oscillator >= 80 -> "극도과매수"
        oscillator >= 50 -> "과매수"
        oscillator <= -80 -> "극도과매도"
        oscillator <= -50 -> "과매도"
        else -> "중립"
    }
}

/**
 * 증시 자금 동향 도메인 모델
 */
data class MarketDeposit(
    val date: String,
    val depositAmount: Double,
    val depositChange: Double,
    val creditAmount: Double,
    val creditChange: Double,
    val lastUpdated: Long
)

/**
 * 증시 자금 차트 데이터 (병렬 리스트)
 */
data class MarketDepositChartData(
    val dates: List<String>,
    val depositAmounts: List<Double>,
    val depositChanges: List<Double>,
    val creditAmounts: List<Double>,
    val creditChanges: List<Double>
) {
    companion object {
        fun empty() = MarketDepositChartData(
            dates = emptyList(),
            depositAmounts = emptyList(),
            depositChanges = emptyList(),
            creditAmounts = emptyList(),
            creditChanges = emptyList()
        )
    }
}

/**
 * 과매수/과매도 전용 날짜 범위 옵션
 */
enum class OscillatorRangeOption(val days: Int, val label: String) {
    ONE_WEEK(7, "1주"),
    TWO_WEEKS(14, "2주"),
    THREE_WEEKS(21, "3주"),
    FOUR_WEEKS(28, "4주");

    companion object {
        val DEFAULT = FOUR_WEEKS

        fun calculateDateRange(option: OscillatorRangeOption): Pair<String, String> {
            val endDate = LocalDate.now().toString()
            val startDate = LocalDate.now().minusDays(option.days.toLong()).toString()
            return Pair(startDate, endDate)
        }
    }
}

/**
 * 자금 동향 날짜 범위 옵션
 */
enum class DateRangeOption(val days: Int, val label: String) {
    WEEK(7, "1주"),
    MONTH(30, "1개월"),
    THREE_MONTHS(90, "3개월"),
    SIX_MONTHS(180, "6개월"),
    YEAR(365, "1년"),
    ALL(-1, "전체");

    companion object {
        val DEFAULT = YEAR

        /**
         * 날짜 범위 계산 (startDate, endDate)
         */
        fun calculateDateRange(option: DateRangeOption): Pair<String, String> {
            val endDate = LocalDate.now().toString()
            val startDate = if (option.days > 0) {
                LocalDate.now().minusDays(option.days.toLong()).toString()
            } else {
                "2000-01-01"
            }
            return Pair(startDate, endDate)
        }
    }
}

/**
 * 시장 과매수/과매도 화면 상태
 */
sealed class MarketOscillatorState {
    data object Loading : MarketOscillatorState()
    data class Idle(val hasData: Boolean, val latestDate: String?) : MarketOscillatorState()
    data class Initializing(val message: String, val progress: Int) : MarketOscillatorState()
    data class Updating(val message: String) : MarketOscillatorState()
    data class Success(val message: String) : MarketOscillatorState()
    data class Error(val message: String) : MarketOscillatorState()
}

/**
 * 자금 동향 화면 상태
 */
sealed class MarketDepositState {
    data object Idle : MarketDepositState()
    data class Loading(val message: String = "", val progress: Int = -1) : MarketDepositState()
    data class Success(val message: String) : MarketDepositState()
    data class Error(val message: String) : MarketDepositState()
}
