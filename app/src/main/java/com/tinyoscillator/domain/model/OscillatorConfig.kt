package com.tinyoscillator.domain.model

/**
 * 오실레이터 설정 상수
 *
 * 엑셀 EMA 파라미터 대응:
 * - 시기외12: α = 2/(12+1) = 2/13 ≈ 0.1538
 * - 시기외26: α = 2/(26+1) = 2/27 ≈ 0.0741
 * - 시그널:   α = 2/(9+1)  = 2/10 = 0.2
 */
data class OscillatorConfig(
    val emaFast: Int = EMA_FAST,
    val emaSlow: Int = EMA_SLOW,
    val emaSignal: Int = EMA_SIGNAL,
    val rollingWindow: Int = ROLLING_WINDOW
) {
    /** EMA Fast α = 2 / (emaFast + 1) */
    val alphaFast: Double get() = 2.0 / (emaFast + 1)

    /** EMA Slow α = 2 / (emaSlow + 1) */
    val alphaSlow: Double get() = 2.0 / (emaSlow + 1)

    /** Signal α = 2 / (emaSignal + 1) */
    val alphaSignal: Double get() = 2.0 / (emaSignal + 1)

    companion object {
        const val EMA_FAST = 12
        const val EMA_SLOW = 26
        const val EMA_SIGNAL = 9
        const val ROLLING_WINDOW = 5
        const val MARKET_CAP_DIVISOR = 10_0000_0000_0000.0  // 조 단위 변환 (원 → 조)
        const val DEFAULT_ANALYSIS_DAYS = 365
        const val DEFAULT_DISPLAY_DAYS = 60   // 차트 표시 기간 (거래일 기준)
    }
}
