package com.tinyoscillator.domain.model

/**
 * 수급 오실레이터 도메인 모델
 *
 * 엑셀 로직 매핑:
 * - 시가총액 시트 → marketCap
 * - 외국인매수데이터 시트 → foreign5dSum (5일 누적)
 * - 기관매수데이터 시트 → inst5dSum (5일 누적)
 * - 시기외 시트 → supplyRatio = (foreign5dSum + inst5dSum) / marketCap
 * - 시기외12/26 시트 → ema12, ema26
 * - MACD 시트 → macd = ema12 - ema26
 * - 시그널 시트 → signal = EMA(macd, 9)
 * - 오실 시트 → oscillator = macd - signal
 */

/** 일별 투자자 거래 원시 데이터 */
data class DailyTrading(
    val date: String,           // "yyyyMMdd"
    val marketCap: Long,        // 시가총액 (원)
    val foreignNetBuy: Long,    // 외국인 순매수 (원)
    val instNetBuy: Long,       // 기관합계 순매수 (원)
    val closePrice: Int = 0,    // 종가 (원)
    val openPrice: Int = 0,     // 시가 (원)
    val highPrice: Int = 0,     // 고가 (원)
    val lowPrice: Int = 0,      // 저가 (원)
    val volume: Long = 0L,      // 거래량
)

/** 오실레이터 계산 중간 + 최종 결과 (엑셀 각 시트에 대응) */
data class OscillatorRow(
    val date: String,           // 날짜
    val marketCap: Long,        // 시가총액 (원)
    val marketCapTril: Double,  // 시가총액 (조)
    val foreign5d: Long,        // 외국인 5일 누적
    val inst5d: Long,           // 기관 5일 누적
    val supplyRatio: Double,    // 수급 비율
    val ema12: Double,          // EMA 12일
    val ema26: Double,          // EMA 26일
    val macd: Double,           // MACD
    val signal: Double,         // 시그널
    val oscillator: Double      // 오실레이터
)

/** 차트 표시용 최종 데이터 */
data class ChartData(
    val stockName: String,
    val ticker: String,
    val rows: List<OscillatorRow>
)

/** 매매 신호 분석 결과 */
data class SignalAnalysis(
    val date: String,
    val marketCapTril: Double,
    val oscillator: Double,
    val macd: Double,
    val signal: Double,
    val trend: Trend,
    val crossSignal: CrossSignal?
)

enum class Trend { BULLISH, BEARISH, NEUTRAL }

enum class CrossSignal {
    GOLDEN_CROSS,   // 오실레이터 음→양 전환 (매수)
    DEAD_CROSS      // 오실레이터 양→음 전환 (매도)
}
