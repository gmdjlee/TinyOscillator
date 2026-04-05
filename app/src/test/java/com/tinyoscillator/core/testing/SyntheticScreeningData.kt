package com.tinyoscillator.core.testing

import com.tinyoscillator.domain.model.MarketType
import com.tinyoscillator.domain.model.ScreenerResultItem
import com.tinyoscillator.domain.model.StockMasterEntry
import com.tinyoscillator.domain.model.WatchlistEntry

/**
 * 스크리닝·탐색 테스트용 공유 픽스처.
 */
object SyntheticScreeningData {

    fun stockMasters(count: Int = 50): List<StockMasterEntry> {
        val sectors = listOf("IT", "금융", "바이오", "소비재", "산업재", "에너지", "부동산")
        return (0 until count).map { i ->
            StockMasterEntry(
                ticker = "${(100000 + i * 1000)}".take(6),
                name = "테스트종목$i",
                sectorCode = sectors[i % sectors.size],
                sectorName = sectors[i % sectors.size],
                marketType = if (i % 2 == 0) MarketType.KOSPI else MarketType.KOSDAQ,
                marketCap = (1000L + i * 500L),
                initialConsonants = "ㅌㅅㅈㅁ$i",
            )
        }
    }

    fun screenerItems(count: Int = 30): List<ScreenerResultItem> =
        (0 until count).map { i ->
            ScreenerResultItem(
                ticker = "${(100000 + i * 1000)}".take(6),
                name = "테스트종목$i",
                signalScore = (0.4f + i * 0.02f).coerceAtMost(0.99f),
                pbr = 0.5f + i * 0.1f,
                marketCapBil = 1000L + i * 200L,
                foreignRatio = 0.10f + i * 0.02f,
                volumeRatio = 0.5f + i * 0.05f,
                sectorName = "IT",
            )
        }

    fun watchlistEntries(count: Int = 10): List<WatchlistEntry> =
        (0 until count).map { i ->
            WatchlistEntry(
                id = i.toLong(),
                ticker = "${(100000 + i * 1000)}".take(6),
                name = "종목$i",
                groupId = if (i < 5) 0L else 1L,
                sortOrder = i,
                signalScore = 0.5f + i * 0.04f,
                currentPrice = 50000L + i * 1000L,
                priceChange = (i % 3 - 1).toFloat() * 0.02f,
            )
        }
}
