package com.tinyoscillator.domain.model

/**
 * 종목 마스터 데이터 (도메인 레이어).
 * Room Entity(StockMasterEntity)와 분리된 순수 도메인 모델.
 */
data class StockMasterEntry(
    val ticker: String,
    val name: String,
    val nameEng: String = "",
    val sectorCode: String = "",
    val sectorName: String = "",
    val marketType: MarketType = MarketType.KOSPI,
    val marketCap: Long = 0L,
    val initialConsonants: String = "",
)

enum class MarketType { KOSPI, KOSDAQ, KONEX }

/**
 * 스크리너 결과 항목 — 필터/정렬 후 표시용.
 */
data class ScreenerResultItem(
    val ticker: String,
    val name: String,
    val signalScore: Float,
    val pbr: Float,
    val marketCapBil: Long,
    val foreignRatio: Float,
    val volumeRatio: Float,
    val sectorName: String,
)

/**
 * 관심종목(워치리스트) 항목.
 */
data class WatchlistEntry(
    val id: Long,
    val ticker: String,
    val name: String,
    val groupId: Long = 0L,
    val sortOrder: Int = 0,
    val signalScore: Float = 0f,
    val currentPrice: Long = 0L,
    val priceChange: Float = 0f,
)
