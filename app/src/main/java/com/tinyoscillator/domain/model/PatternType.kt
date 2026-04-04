package com.tinyoscillator.domain.model

enum class PatternSentiment { BULLISH, BEARISH, NEUTRAL }

/**
 * marker/color 규칙:
 * - 도형: 패턴별 고유 유니코드 심볼로 즉시 식별
 * - 색상: 0xAARRGGBB (Long). 상승=난색 계열, 하락=한색 계열, 중립=회색
 */
enum class PatternType(
    val labelKo: String,
    val sentiment: PatternSentiment,
    val candleCount: Int,
    val marker: String,
    val colorArgb: Long,
) {
    // 1-candle
    DOJI("도지", PatternSentiment.NEUTRAL, 1, "+", 0xFF9E9E9E),
    HAMMER("망치형", PatternSentiment.BULLISH, 1, "H", 0xFFE65100),
    INVERTED_HAMMER("역망치형", PatternSentiment.BULLISH, 1, "h", 0xFFFF9800),
    SHOOTING_STAR("유성형", PatternSentiment.BEARISH, 1, "S", 0xFF1565C0),
    // 2-candle
    HANGING_MAN("교수형", PatternSentiment.BEARISH, 2, "X", 0xFF7B1FA2),
    BULLISH_ENGULFING("상승 장악형", PatternSentiment.BULLISH, 2, "BE", 0xFFD84315),
    BEARISH_ENGULFING("하락 장악형", PatternSentiment.BEARISH, 2, "be", 0xFF0277BD),
    PIERCING_LINE("관통형", PatternSentiment.BULLISH, 2, "P", 0xFFC62828),
    DARK_CLOUD_COVER("먹구름형", PatternSentiment.BEARISH, 2, "D", 0xFF1A237E),
    // 3-candle
    MORNING_STAR("샛별형", PatternSentiment.BULLISH, 3, "MS", 0xFFFF6D00),
    EVENING_STAR("저녁별형", PatternSentiment.BEARISH, 3, "ES", 0xFF283593),
    THREE_WHITE_SOLDIERS("세 백색병사", PatternSentiment.BULLISH, 3, "3W", 0xFFBF360C),
    THREE_BLACK_CROWS("세 흑색까마귀", PatternSentiment.BEARISH, 3, "3B", 0xFF0D47A1),
}

data class PatternResult(
    val index: Int,
    val type: PatternType,
    val strength: Float,
)
