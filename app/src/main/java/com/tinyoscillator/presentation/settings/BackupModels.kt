package com.tinyoscillator.presentation.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val backupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

// region API Backup Models

@Serializable
data class KiwoomApiBackup(
    val appKey: String,
    val secretKey: String,
    val mode: String
)

@Serializable
data class KisApiBackup(
    val appKey: String,
    val appSecret: String,
    val mode: String
)

@Serializable
data class KrxApiBackup(
    val id: String,
    val password: String
)

@Serializable
data class AiApiBackup(
    val apiKey: String,
    val provider: String,
    val modelId: String = ""
)

@Serializable
data class DartApiBackup(
    val apiKey: String
)

@Serializable
data class EcosApiBackup(
    val apiKey: String
)

@Serializable
data class ApiBackup(
    val version: Int = 1,
    val type: String, // "kiwoom", "kis", "krx", "ai", "dart", "ecos", "all_api"
    val kiwoom: KiwoomApiBackup? = null,
    val kis: KisApiBackup? = null,
    val krx: KrxApiBackup? = null,
    val ai: AiApiBackup? = null,
    val dart: DartApiBackup? = null,
    val ecos: EcosApiBackup? = null
)

// endregion

// region ETF Backup Models

@Serializable
data class EtfBackupEntry(
    val ticker: String,
    val name: String,
    val isinCode: String,
    val indexName: String? = null,
    val totalFee: Double? = null,
    val updatedAt: Long = 0
)

@Serializable
data class EtfHoldingBackupEntry(
    val etfTicker: String,
    val stockTicker: String,
    val date: String,
    val stockName: String,
    val weight: Double? = null,
    val shares: Long = 0,
    val amount: Long = 0
)

@Serializable
data class EtfDataBackup(
    val version: Int = 1,
    val type: String = "etf_data",
    val startDate: String? = null,
    val endDate: String? = null,
    val etfs: List<EtfBackupEntry>,
    val holdings: List<EtfHoldingBackupEntry>
)

// endregion

// region Portfolio Backup Models

@Serializable
data class PortfolioBackupEntry(
    val name: String,
    val maxWeightPercent: Int,
    val totalAmountLimit: Long? = null
)

@Serializable
data class PortfolioHoldingBackupEntry(
    val ticker: String,
    val stockName: String,
    val market: String,
    val sector: String,
    val lastPrice: Int = 0,
    val priceUpdatedAt: Long = 0,
    val targetPrice: Int = 0
)

@Serializable
data class PortfolioTransactionBackupEntry(
    val date: String,
    val shares: Int,
    val pricePerShare: Int,
    val memo: String = ""
)

@Serializable
data class PortfolioHoldingWithTransactions(
    val holding: PortfolioHoldingBackupEntry,
    val transactions: List<PortfolioTransactionBackupEntry>
)

@Serializable
data class PortfolioDataBackup(
    val version: Int = 1,
    val type: String = "portfolio",
    val portfolio: PortfolioBackupEntry,
    val holdings: List<PortfolioHoldingWithTransactions>
)

// endregion

// region Consensus Report Backup Models

@Serializable
data class ConsensusReportBackupEntry(
    val writeDate: String,
    val category: String,
    val prevOpinion: String,
    val opinion: String,
    val title: String,
    val stockTicker: String,
    val stockName: String = "",
    val author: String,
    val institution: String,
    val targetPrice: Long,
    val currentPrice: Long,
    val divergenceRate: Double
)

@Serializable
data class ConsensusDataBackup(
    val version: Int = 1,
    val type: String = "consensus_reports",
    val reports: List<ConsensusReportBackupEntry>
)

// endregion

// region Fear & Greed Backup Models

@Serializable
data class FearGreedBackupEntry(
    val id: String,
    val market: String,
    val date: String,
    val indexValue: Double,
    val fearGreedValue: Double,
    val oscillator: Double,
    val rsi: Double,
    val momentum: Double,
    val putCallRatio: Double,
    val volatility: Double,
    val spread: Double
)

@Serializable
data class FearGreedBackupData(
    val version: Int = 1,
    val type: String = "fear_greed",
    val entries: List<FearGreedBackupEntry>
)

// endregion
