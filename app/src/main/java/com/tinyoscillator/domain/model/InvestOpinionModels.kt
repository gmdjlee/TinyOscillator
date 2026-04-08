package com.tinyoscillator.domain.model

data class InvestOpinion(
    val date: String,
    val firmName: String,
    val opinion: String,
    val opinionCode: String,
    val targetPrice: Long?,
    val currentPrice: Long?,
    val changeSign: String,
    val changeAmount: Long?,
)

data class InvestOpinionSummary(
    val ticker: String,
    val stockName: String,
    val opinions: List<InvestOpinion>,
    val buyCount: Int,
    val holdCount: Int,
    val sellCount: Int,
    val avgTargetPrice: Long?,
    val currentPrice: Long?,
) {
    val totalCount: Int get() = buyCount + holdCount + sellCount

    val upsidePct: Double?
        get() {
            val avg = avgTargetPrice ?: return null
            val cur = currentPrice ?: return null
            if (cur == 0L) return null
            return (avg - cur).toDouble() / cur * 100.0
        }
}
