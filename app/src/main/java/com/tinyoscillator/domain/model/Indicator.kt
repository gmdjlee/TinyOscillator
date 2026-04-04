package com.tinyoscillator.domain.model

enum class OverlayType { PRICE, OSCILLATOR, VOLUME }

data class IndicatorParams(
    val period: Int = 14,
    val fast: Int = 12,
    val slow: Int = 26,
    val signal: Int = 9,
    val multiplier: Float = 2f,
)

enum class Indicator(
    val displayNameKo: String,
    val defaultParams: IndicatorParams,
    val overlayType: OverlayType,
) {
    EMA_SHORT("단기 이동평균 (EMA5)", IndicatorParams(period = 5), OverlayType.PRICE),
    EMA_MID("중기 이동평균 (EMA20)", IndicatorParams(period = 20), OverlayType.PRICE),
    EMA_LONG("장기 이동평균 (EMA60)", IndicatorParams(period = 60), OverlayType.PRICE),
    BOLLINGER("볼린저밴드", IndicatorParams(period = 20, multiplier = 2f), OverlayType.PRICE),
    MACD("MACD", IndicatorParams(fast = 12, slow = 26, signal = 9), OverlayType.OSCILLATOR),
    RSI("RSI", IndicatorParams(period = 14), OverlayType.OSCILLATOR),
    STOCHASTIC("스토캐스틱", IndicatorParams(period = 14), OverlayType.OSCILLATOR),
    VOLUME_PROFILE("거래량 프로파일", IndicatorParams(), OverlayType.PRICE),
}
