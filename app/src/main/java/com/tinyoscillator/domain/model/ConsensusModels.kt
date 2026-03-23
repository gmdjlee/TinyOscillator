package com.tinyoscillator.domain.model

data class ConsensusReport(
    val writeDate: String,
    val category: String,
    val prevOpinion: String,
    val opinion: String,
    val title: String,
    val stockTicker: String,
    val stockName: String,
    val author: String,
    val institution: String,
    val targetPrice: Long,
    val currentPrice: Long,
    val divergenceRate: Double
)

data class ConsensusFilter(
    val dateRange: Pair<String, String>? = null,
    val category: String? = null,
    val prevOpinion: String? = null,
    val opinion: String? = null,
    val stockTicker: String? = null,
    val author: String? = null,
    val institution: String? = null
)

data class ConsensusFilterOptions(
    val dates: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val prevOpinions: List<String> = emptyList(),
    val opinions: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val institutions: List<String> = emptyList()
)

sealed class ConsensusDataProgress {
    data class Loading(val message: String, val progress: Float = 0f) : ConsensusDataProgress()
    data class Success(val count: Int) : ConsensusDataProgress()
    data class Error(val message: String) : ConsensusDataProgress()
}

data class ConsensusChartData(
    val ticker: String,
    val stockName: String,
    val dates: List<String>,
    val marketCaps: List<Long>,
    val reportDates: List<String>,
    val reportTargetPrices: List<Long>
)
