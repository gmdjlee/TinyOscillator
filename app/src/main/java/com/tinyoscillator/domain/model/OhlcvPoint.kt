package com.tinyoscillator.domain.model

/**
 * OHLCV 캔들 데이터 포인트 — 테스트 픽스처 및 엔진 입력용
 */
data class OhlcvPoint(
    val index: Int,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Long,
    val date: String = ""
)
