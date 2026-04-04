package com.tinyoscillator.domain.model

/**
 * 캔들스틱 패턴 유형 — SyntheticData 픽스처에서 패턴별 테스트 데이터 생성에 사용
 */
enum class PatternType {
    DOJI,
    HAMMER,
    BULLISH_ENGULFING,
    MORNING_STAR,
    SHOOTING_STAR,
    BEARISH_ENGULFING,
    EVENING_STAR,
    GENERIC
}
