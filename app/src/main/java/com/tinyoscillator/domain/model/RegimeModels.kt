package com.tinyoscillator.domain.model

/**
 * 시장 레짐 탐지 결과 도메인 모델
 */

/** 시장 레짐 예측 결과 */
data class MarketRegimeResult(
    val regimeId: Int,
    val regimeName: String,
    val regimeDescription: String,
    val confidence: Double,
    val probaVec: List<Double>,
    val regimeDurationDays: Int
)
