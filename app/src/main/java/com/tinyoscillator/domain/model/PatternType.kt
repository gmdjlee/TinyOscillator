package com.tinyoscillator.domain.model

enum class PatternSentiment { BULLISH, BEARISH, NEUTRAL }

enum class PatternType(
    val labelKo: String,
    val sentiment: PatternSentiment,
    val candleCount: Int,
) {
    DOJI("도지", PatternSentiment.NEUTRAL, 1),
    HAMMER("망치형", PatternSentiment.BULLISH, 1),
    INVERTED_HAMMER("역망치형", PatternSentiment.BULLISH, 1),
    SHOOTING_STAR("유성형", PatternSentiment.BEARISH, 1),
    HANGING_MAN("교수형", PatternSentiment.BEARISH, 2),
    BULLISH_ENGULFING("상승 장악형", PatternSentiment.BULLISH, 2),
    BEARISH_ENGULFING("하락 장악형", PatternSentiment.BEARISH, 2),
    PIERCING_LINE("관통형", PatternSentiment.BULLISH, 2),
    DARK_CLOUD_COVER("먹구름형", PatternSentiment.BEARISH, 2),
    MORNING_STAR("샛별형", PatternSentiment.BULLISH, 3),
    EVENING_STAR("저녁별형", PatternSentiment.BEARISH, 3),
    THREE_WHITE_SOLDIERS("세 백색병사", PatternSentiment.BULLISH, 3),
    THREE_BLACK_CROWS("세 흑색까마귀", PatternSentiment.BEARISH, 3),
}

data class PatternResult(
    val index: Int,
    val type: PatternType,
    val strength: Float,
)
