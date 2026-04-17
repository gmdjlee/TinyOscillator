package com.tinyoscillator.presentation.settings

import android.content.Context
import android.net.Uri
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import com.tinyoscillator.core.database.entity.FearGreedEntity
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import com.tinyoscillator.domain.model.FinancialDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

private val backupJson = Json {
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

object BackupManager {

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210000
    private const val KEY_BITS = 256

    fun encrypt(plainText: String, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        val aesKey = SecretKeySpec(secretKey.encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return salt + iv + cipherText
    }

    fun decrypt(encrypted: ByteArray, password: String): String {
        val salt = encrypted.copyOfRange(0, SALT_SIZE)
        val iv = encrypted.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val cipherText = encrypted.copyOfRange(SALT_SIZE + IV_SIZE, encrypted.size)
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
        val aesKey = SecretKeySpec(secretKey.encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // region API Backup/Restore

    suspend fun exportApiBackup(context: Context, uri: Uri, type: String, password: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val backup = when (type) {
                    "kiwoom" -> {
                        val config = loadKiwoomConfig(context)
                        ApiBackup(
                            type = "kiwoom",
                            kiwoom = KiwoomApiBackup(config.appKey, config.secretKey, config.investmentMode.name)
                        )
                    }
                    "kis" -> {
                        val config = loadKisConfig(context)
                        ApiBackup(
                            type = "kis",
                            kis = KisApiBackup(config.appKey, config.appSecret, config.investmentMode.name)
                        )
                    }
                    "krx" -> {
                        val creds = loadKrxCredentials(context)
                        ApiBackup(
                            type = "krx",
                            krx = KrxApiBackup(creds.id, creds.password)
                        )
                    }
                    "ai" -> {
                        val aiConfig = loadAiConfig(context)
                        ApiBackup(
                            type = "ai",
                            ai = AiApiBackup(aiConfig.apiKey, aiConfig.provider.name, aiConfig.modelId)
                        )
                    }
                    "dart" -> {
                        val dartKey = loadDartApiKey(context)
                        ApiBackup(
                            type = "dart",
                            dart = DartApiBackup(dartKey)
                        )
                    }
                    "ecos" -> {
                        val ecosKey = loadEcosApiKey(context)
                        ApiBackup(
                            type = "ecos",
                            ecos = EcosApiBackup(ecosKey)
                        )
                    }
                    else -> {
                        val kiwoomConfig = loadKiwoomConfig(context)
                        val kisConfig = loadKisConfig(context)
                        val krxCreds = loadKrxCredentials(context)
                        val aiConfig = loadAiConfig(context)
                        val dartKey = loadDartApiKey(context)
                        val ecosKey = loadEcosApiKey(context)
                        ApiBackup(
                            type = "all_api",
                            kiwoom = KiwoomApiBackup(kiwoomConfig.appKey, kiwoomConfig.secretKey, kiwoomConfig.investmentMode.name),
                            kis = KisApiBackup(kisConfig.appKey, kisConfig.appSecret, kisConfig.investmentMode.name),
                            krx = KrxApiBackup(krxCreds.id, krxCreds.password),
                            ai = AiApiBackup(aiConfig.apiKey, aiConfig.provider.name, aiConfig.modelId),
                            dart = DartApiBackup(dartKey),
                            ecos = EcosApiBackup(ecosKey)
                        )
                    }
                }
                val json = backupJson.encodeToString(backup)
                val encryptedBytes = encrypt(json, password)
                context.contentResolver.openOutputStream(uri)?.use { it.write(encryptedBytes) }
                Result.success(1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importApiBackup(context: Context, uri: Uri, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

                val json = try {
                    decrypt(encryptedBytes, password)
                } catch (_: Exception) {
                    return@withContext Result.failure(Exception("비밀번호가 올바르지 않거나 파일이 손상되었습니다"))
                }

                val backup = backupJson.decodeFromString<ApiBackup>(json)
                val restoredParts = mutableListOf<String>()

                backup.kiwoom?.let { kw ->
                    val mode = com.tinyoscillator.core.api.InvestmentMode.entries.find { it.name == kw.mode }
                        ?: com.tinyoscillator.core.api.InvestmentMode.MOCK
                    val prefs = getEncryptedPrefsForBackup(context)
                    prefs.edit()
                        .putString("kiwoom_app_key", kw.appKey)
                        .putString("kiwoom_secret_key", kw.secretKey)
                        .putString("kiwoom_mode", mode.name)
                        .apply()
                    restoredParts.add("Kiwoom")
                }
                backup.kis?.let { kis ->
                    val mode = com.tinyoscillator.core.api.InvestmentMode.entries.find { it.name == kis.mode }
                        ?: com.tinyoscillator.core.api.InvestmentMode.MOCK
                    val prefs = getEncryptedPrefsForBackup(context)
                    prefs.edit()
                        .putString("kis_app_key", kis.appKey)
                        .putString("kis_app_secret", kis.appSecret)
                        .putString("kis_mode", mode.name)
                        .apply()
                    restoredParts.add("KIS")
                }
                backup.krx?.let { krx ->
                    saveKrxCredentials(context, KrxCredentials(krx.id, krx.password))
                    restoredParts.add("KRX")
                }
                backup.ai?.let { ai ->
                    // 이전 버전 마이그레이션 (CLAUDE_HAIKU 등 → CLAUDE/GEMINI)
                    val provider = when (ai.provider) {
                        "CLAUDE_HAIKU", "CLAUDE_SONNET" -> com.tinyoscillator.domain.model.AiProvider.CLAUDE
                        "GEMINI_FLASH", "GEMINI_2_5_FLASH" -> com.tinyoscillator.domain.model.AiProvider.GEMINI
                        else -> com.tinyoscillator.domain.model.AiProvider.entries.find { it.name == ai.provider }
                            ?: com.tinyoscillator.domain.model.AiProvider.CLAUDE
                    }
                    val prefs = getEncryptedPrefsForBackup(context)
                    prefs.edit()
                        .putString("ai_api_key", ai.apiKey)
                        .putString("ai_provider", provider.name)
                        .putString("ai_model_id", ai.modelId)
                        .apply()
                    restoredParts.add("AI")
                }
                backup.dart?.let { dart ->
                    saveDartApiKey(context, dart.apiKey)
                    restoredParts.add("DART")
                }
                backup.ecos?.let { ecos ->
                    saveEcosApiKey(context, ecos.apiKey)
                    restoredParts.add("ECOS")
                }

                Result.success("${restoredParts.joinToString(", ")} 복원 완료")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // endregion

    // region ETF Data Backup/Restore

    suspend fun exportEtfData(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        startDate: String?,
        endDate: String?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dao = db.etfDao()
            val etfs = dao.getAllEtfsList().map {
                EtfBackupEntry(it.ticker, it.name, it.isinCode, it.indexName, it.totalFee, it.updatedAt)
            }
            val holdings = if (startDate != null && endDate != null) {
                dao.getHoldingsByDateRange(startDate, endDate)
            } else {
                dao.getAllHoldings()
            }.map {
                EtfHoldingBackupEntry(it.etfTicker, it.stockTicker, it.date, it.stockName, it.weight, it.shares, it.amount)
            }
            val backup = EtfDataBackup(
                startDate = startDate,
                endDate = endDate,
                etfs = etfs,
                holdings = holdings
            )
            val json = backupJson.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Result.success(holdings.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importEtfData(context: Context, uri: Uri, db: AppDatabase): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

                val backup = backupJson.decodeFromString<EtfDataBackup>(json)
                val dao = db.etfDao()

                val etfEntities = backup.etfs.map {
                    EtfEntity(it.ticker, it.name, it.isinCode, it.indexName, it.totalFee, it.updatedAt)
                }
                val holdingEntities = backup.holdings.map {
                    EtfHoldingEntity(it.etfTicker, it.stockTicker, it.date, it.stockName, it.weight, it.shares, it.amount)
                }

                if (etfEntities.isNotEmpty()) dao.insertEtfs(etfEntities)
                // Insert in chunks to avoid SQLite variable limit
                holdingEntities.chunked(500).forEach { chunk ->
                    dao.insertHoldings(chunk)
                }

                Result.success("ETF ${etfEntities.size}개, 보유종목 ${holdingEntities.size}건 복원 완료")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // endregion

    // region Portfolio Backup/Restore

    suspend fun exportPortfolioData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dao = db.portfolioDao()
            val portfolios = dao.getAllPortfoliosList()
            if (portfolios.isEmpty()) {
                return@withContext Result.failure(Exception("포트폴리오가 없습니다"))
            }
            val portfolio = portfolios.first()

            val holdings = dao.getHoldingsListForPortfolio(portfolio.id)
            val holdingsWithTx = holdings.map { holding ->
                val transactions = dao.getTransactionsListForHolding(holding.id)
                PortfolioHoldingWithTransactions(
                    holding = PortfolioHoldingBackupEntry(
                        ticker = holding.ticker,
                        stockName = holding.stockName,
                        market = holding.market,
                        sector = holding.sector,
                        lastPrice = holding.lastPrice,
                        priceUpdatedAt = holding.priceUpdatedAt,
                        targetPrice = holding.targetPrice
                    ),
                    transactions = transactions.map { tx ->
                        PortfolioTransactionBackupEntry(
                            date = tx.date,
                            shares = tx.shares,
                            pricePerShare = tx.pricePerShare,
                            memo = tx.memo
                        )
                    }
                )
            }

            val backup = PortfolioDataBackup(
                portfolio = PortfolioBackupEntry(
                    name = portfolio.name,
                    maxWeightPercent = portfolio.maxWeightPercent,
                    totalAmountLimit = portfolio.totalAmountLimit
                ),
                holdings = holdingsWithTx
            )

            val json = backupJson.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            val txCount = holdingsWithTx.sumOf { it.transactions.size }
            Result.success(txCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importPortfolioData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

            val backup = backupJson.decodeFromString<PortfolioDataBackup>(json)
            val dao = db.portfolioDao()

            // Delete existing portfolio data (single portfolio model)
            val existing = dao.getAllPortfoliosList()
            existing.forEach { dao.deletePortfolioWithData(it.id) }

            // Insert portfolio
            val portfolioId = dao.insertPortfolio(
                PortfolioEntity(
                    name = backup.portfolio.name,
                    maxWeightPercent = backup.portfolio.maxWeightPercent,
                    totalAmountLimit = backup.portfolio.totalAmountLimit
                )
            )

            var totalTx = 0
            for (hwt in backup.holdings) {
                val holdingId = dao.insertHolding(
                    PortfolioHoldingEntity(
                        portfolioId = portfolioId,
                        ticker = hwt.holding.ticker,
                        stockName = hwt.holding.stockName,
                        market = hwt.holding.market,
                        sector = hwt.holding.sector,
                        lastPrice = hwt.holding.lastPrice,
                        priceUpdatedAt = hwt.holding.priceUpdatedAt,
                        targetPrice = hwt.holding.targetPrice
                    )
                )
                for (tx in hwt.transactions) {
                    dao.insertTransaction(
                        PortfolioTransactionEntity(
                            holdingId = holdingId,
                            date = tx.date,
                            shares = tx.shares,
                            pricePerShare = tx.pricePerShare,
                            memo = tx.memo
                        )
                    )
                    totalTx++
                }
            }

            Result.success("포트폴리오 복원 완료 (종목 ${backup.holdings.size}개, 거래 ${totalTx}건)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // endregion

    // region Consensus Report Backup/Restore

    suspend fun exportConsensusData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dao = db.consensusReportDao()
            val entities = dao.getAll()
            if (entities.isEmpty()) {
                return@withContext Result.failure(Exception("리포트 데이터가 없습니다"))
            }

            val backup = ConsensusDataBackup(
                reports = entities.map {
                    ConsensusReportBackupEntry(
                        writeDate = it.writeDate,
                        category = it.category,
                        prevOpinion = it.prevOpinion,
                        opinion = it.opinion,
                        title = it.title,
                        stockTicker = it.stockTicker,
                        stockName = it.stockName,
                        author = it.author,
                        institution = it.institution,
                        targetPrice = it.targetPrice,
                        currentPrice = it.currentPrice,
                        divergenceRate = it.divergenceRate
                    )
                }
            )

            val json = backupJson.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Result.success(entities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importConsensusData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

            val backup = backupJson.decodeFromString<ConsensusDataBackup>(json)
            val dao = db.consensusReportDao()

            val entities = backup.reports.map {
                ConsensusReportEntity(
                    writeDate = it.writeDate,
                    category = it.category,
                    prevOpinion = it.prevOpinion,
                    opinion = it.opinion,
                    title = it.title,
                    stockTicker = it.stockTicker,
                    stockName = it.stockName,
                    author = it.author,
                    institution = it.institution,
                    targetPrice = it.targetPrice,
                    currentPrice = it.currentPrice,
                    divergenceRate = it.divergenceRate
                )
            }

            entities.chunked(500).forEach { chunk ->
                dao.insertAll(chunk)
            }

            Result.success("리포트 ${entities.size}건 복원 완료")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // endregion

    // region Fear & Greed Backup/Restore

    suspend fun exportFearGreedData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dao = db.fearGreedDao()
            val entities = dao.getAllList()
            if (entities.isEmpty()) {
                return@withContext Result.failure(Exception("Fear & Greed 데이터가 없습니다"))
            }

            val backup = FearGreedBackupData(
                entries = entities.map {
                    FearGreedBackupEntry(
                        id = it.id,
                        market = it.market,
                        date = it.date,
                        indexValue = it.indexValue,
                        fearGreedValue = it.fearGreedValue,
                        oscillator = it.oscillator,
                        rsi = it.rsi,
                        momentum = it.momentum,
                        putCallRatio = it.putCallRatio,
                        volatility = it.volatility,
                        spread = it.spread
                    )
                }
            )

            val json = backupJson.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Result.success(entities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFearGreedData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

            val backup = backupJson.decodeFromString<FearGreedBackupData>(json)
            val dao = db.fearGreedDao()

            val entities = backup.entries.map {
                FearGreedEntity(
                    id = it.id,
                    market = it.market,
                    date = it.date,
                    indexValue = it.indexValue,
                    fearGreedValue = it.fearGreedValue,
                    oscillator = it.oscillator,
                    rsi = it.rsi,
                    momentum = it.momentum,
                    putCallRatio = it.putCallRatio,
                    volatility = it.volatility,
                    spread = it.spread
                )
            }

            entities.chunked(500).forEach { chunk ->
                dao.insertAll(chunk)
            }

            Result.success("Fear & Greed ${entities.size}건 복원 완료")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // endregion

    // region Data Export for Analysis

    internal fun formatTimestamp(millis: Long): String {
        if (millis == 0L) return ""
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
    }

    internal fun Any?.toTsv(): String = when {
        this == null -> ""
        this is Number && this.toDouble() == 0.0 -> ""
        this is String && this.isEmpty() -> ""
        else -> toString()
    }

    suspend fun exportAllDataForAnalysis(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        onProgress: (String) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val t = "\t"
            var totalRecords = 0

            onProgress("데이터 로딩 중...")
            val stockMasters = db.stockMasterDao().getAll()
            val analysisCache = db.analysisCacheDao().getAll()
            val analysisHistory = db.analysisHistoryDao().getAll()
            val fundamentalCache = db.fundamentalCacheDao().getAll()
            val financialCache = db.financialCacheDao().getAll()
            val oscillators = db.marketOscillatorDao().getAllList()
            val deposits = db.marketDepositDao().getAllList()
            val fearGreedIndices = db.fearGreedDao().getAllList()
            val etfs = db.etfDao().getAllEtfsList()
            val etfHoldings = db.etfDao().getAllHoldings()
            val portfolios = db.portfolioDao().getAllPortfoliosList()
            val consensusReports = db.consensusReportDao().getAll()

            onProgress("파일 쓰기 중...")
            context.contentResolver.openOutputStream(uri)?.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w ->
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

                    // Header
                    w.write("# TinyOscillator Data Export\n")
                    w.write("# Date: $now\n")
                    w.write("# Records: stock_master=${stockMasters.size}, analysis_cache=${analysisCache.size}, ")
                    w.write("analysis_history=${analysisHistory.size}, fundamental_cache=${fundamentalCache.size}, ")
                    w.write("financial_cache=${financialCache.size}, market_oscillator=${oscillators.size}, ")
                    w.write("market_deposits=${deposits.size}, fear_greed=${fearGreedIndices.size}, etfs=${etfs.size}, etf_holdings=${etfHoldings.size}, ")
                    w.write("portfolios=${portfolios.size}, consensus_reports=${consensusReports.size}\n\n")

                    // stock_master
                    w.write("## stock_master\n")
                    w.write("ticker${t}name${t}market${t}sector${t}lastUpdated\n")
                    for (s in stockMasters) {
                        w.write("${s.ticker}$t${s.name}$t${s.market}$t${s.sector}$t${formatTimestamp(s.lastUpdated)}\n")
                    }
                    totalRecords += stockMasters.size
                    onProgress("stock_master: ${stockMasters.size}건")

                    // analysis_cache
                    w.write("\n## analysis_cache\n")
                    w.write("ticker${t}date${t}marketCap${t}foreignNet${t}instNet${t}close${t}open${t}high${t}low${t}volume\n")
                    for (a in analysisCache) {
                        w.write("${a.ticker}$t${a.date}$t${a.marketCap.toTsv()}$t${a.foreignNet.toTsv()}$t${a.instNet.toTsv()}$t${a.closePrice.toTsv()}$t${a.openPrice.toTsv()}$t${a.highPrice.toTsv()}$t${a.lowPrice.toTsv()}$t${a.volume.toTsv()}\n")
                    }
                    totalRecords += analysisCache.size
                    onProgress("analysis_cache: ${analysisCache.size}건")

                    // analysis_history
                    w.write("\n## analysis_history\n")
                    w.write("ticker${t}name${t}lastAnalyzedAt\n")
                    for (h in analysisHistory) {
                        w.write("${h.ticker}$t${h.name}$t${formatTimestamp(h.lastAnalyzedAt)}\n")
                    }
                    totalRecords += analysisHistory.size

                    // fundamental_cache
                    w.write("\n## fundamental_cache\n")
                    w.write("ticker${t}date${t}close${t}eps${t}per${t}bps${t}pbr${t}dps${t}dividendYield\n")
                    for (f in fundamentalCache) {
                        w.write("${f.ticker}$t${f.date}$t${f.close.toTsv()}$t${f.eps.toTsv()}$t${f.per.toTsv()}$t${f.bps.toTsv()}$t${f.pbr.toTsv()}$t${f.dps.toTsv()}$t${f.dividendYield.toTsv()}\n")
                    }
                    totalRecords += fundamentalCache.size
                    onProgress("fundamental_cache: ${fundamentalCache.size}건")

                    // financial_cache → 5 sub-tables
                    val parsed = financialCache.mapNotNull { entity ->
                        try {
                            val cache = backupJson.decodeFromString<FinancialDataCache>(entity.data)
                            Triple(entity.ticker, entity.name, cache)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    // financial_balance_sheet
                    w.write("\n## financial_balance_sheet\n")
                    w.write("ticker${t}name${t}yearMonth${t}currentAssets${t}fixedAssets${t}totalAssets${t}currentLiabilities${t}fixedLiabilities${t}totalLiabilities${t}capital${t}capitalSurplus${t}retainedEarnings${t}totalEquity\n")
                    var financialRows = 0
                    for ((ticker, name, cache) in parsed) {
                        for (bs in cache.balanceSheets) {
                            w.write("$ticker$t$name$t${bs.yearMonth}$t${bs.currentAssets.toTsv()}$t${bs.fixedAssets.toTsv()}$t${bs.totalAssets.toTsv()}$t${bs.currentLiabilities.toTsv()}$t${bs.fixedLiabilities.toTsv()}$t${bs.totalLiabilities.toTsv()}$t${bs.capital.toTsv()}$t${bs.capitalSurplus.toTsv()}$t${bs.retainedEarnings.toTsv()}$t${bs.totalEquity.toTsv()}\n")
                            financialRows++
                        }
                    }

                    // financial_income_statement
                    w.write("\n## financial_income_statement\n")
                    w.write("ticker${t}name${t}yearMonth${t}revenue${t}costOfSales${t}grossProfit${t}operatingProfit${t}ordinaryProfit${t}netIncome\n")
                    for ((ticker, name, cache) in parsed) {
                        for (is_ in cache.incomeStatements) {
                            w.write("$ticker$t$name$t${is_.yearMonth}$t${is_.revenue.toTsv()}$t${is_.costOfSales.toTsv()}$t${is_.grossProfit.toTsv()}$t${is_.operatingProfit.toTsv()}$t${is_.ordinaryProfit.toTsv()}$t${is_.netIncome.toTsv()}\n")
                            financialRows++
                        }
                    }

                    // financial_profitability
                    w.write("\n## financial_profitability\n")
                    w.write("ticker${t}name${t}yearMonth${t}operatingMargin${t}netMargin${t}roe${t}roa\n")
                    for ((ticker, name, cache) in parsed) {
                        for (pr in cache.profitabilityRatios) {
                            w.write("$ticker$t$name$t${pr.yearMonth}$t${pr.operatingMargin.toTsv()}$t${pr.netMargin.toTsv()}$t${pr.roe.toTsv()}$t${pr.roa.toTsv()}\n")
                            financialRows++
                        }
                    }

                    // financial_stability
                    w.write("\n## financial_stability\n")
                    w.write("ticker${t}name${t}yearMonth${t}debtRatio${t}currentRatio${t}quickRatio${t}borrowingDependency${t}interestCoverageRatio\n")
                    for ((ticker, name, cache) in parsed) {
                        for (sr in cache.stabilityRatios) {
                            w.write("$ticker$t$name$t${sr.yearMonth}$t${sr.debtRatio.toTsv()}$t${sr.currentRatio.toTsv()}$t${sr.quickRatio.toTsv()}$t${sr.borrowingDependency.toTsv()}$t${sr.interestCoverageRatio.toTsv()}\n")
                            financialRows++
                        }
                    }

                    // financial_growth
                    w.write("\n## financial_growth\n")
                    w.write("ticker${t}name${t}yearMonth${t}revenueGrowth${t}operatingProfitGrowth${t}netIncomeGrowth${t}equityGrowth${t}totalAssetsGrowth\n")
                    for ((ticker, name, cache) in parsed) {
                        for (gr in cache.growthRatios) {
                            w.write("$ticker$t$name$t${gr.yearMonth}$t${gr.revenueGrowth.toTsv()}$t${gr.operatingProfitGrowth.toTsv()}$t${gr.netIncomeGrowth.toTsv()}$t${gr.equityGrowth.toTsv()}$t${gr.totalAssetsGrowth.toTsv()}\n")
                            financialRows++
                        }
                    }
                    totalRecords += financialRows
                    onProgress("financial: ${parsed.size}종목, ${financialRows}행")

                    // market_oscillator
                    w.write("\n## market_oscillator\n")
                    w.write("market${t}date${t}indexValue${t}oscillator\n")
                    for (o in oscillators) {
                        w.write("${o.market}$t${o.date}$t${o.indexValue}$t${o.oscillator}\n")
                    }
                    totalRecords += oscillators.size
                    onProgress("market_oscillator: ${oscillators.size}건")

                    // market_deposits
                    w.write("\n## market_deposits\n")
                    w.write("date${t}depositAmount${t}depositChange${t}creditAmount${t}creditChange\n")
                    for (d in deposits) {
                        w.write("${d.date}$t${d.depositAmount}$t${d.depositChange}$t${d.creditAmount}$t${d.creditChange}\n")
                    }
                    totalRecords += deposits.size

                    // fear_greed_index
                    w.write("\n## fear_greed_index\n")
                    w.write("id${t}market${t}date${t}indexValue${t}fearGreedValue${t}oscillator${t}rsi${t}momentum${t}putCallRatio${t}volatility${t}spread\n")
                    for (fg in fearGreedIndices) {
                        w.write("${fg.id}$t${fg.market}$t${fg.date}$t${fg.indexValue}$t${fg.fearGreedValue}$t${fg.oscillator}$t${fg.rsi}$t${fg.momentum}$t${fg.putCallRatio}$t${fg.volatility}$t${fg.spread}\n")
                    }
                    totalRecords += fearGreedIndices.size
                    onProgress("fear_greed: ${fearGreedIndices.size}건")

                    // etfs
                    w.write("\n## etfs\n")
                    w.write("ticker${t}name${t}isinCode${t}indexName${t}totalFee\n")
                    for (e in etfs) {
                        w.write("${e.ticker}$t${e.name}$t${e.isinCode}$t${e.indexName.toTsv()}$t${e.totalFee.toTsv()}\n")
                    }
                    totalRecords += etfs.size

                    // etf_holdings
                    w.write("\n## etf_holdings\n")
                    w.write("etfTicker${t}stockTicker${t}date${t}stockName${t}weight${t}shares${t}amount\n")
                    for (h in etfHoldings) {
                        w.write("${h.etfTicker}$t${h.stockTicker}$t${h.date}$t${h.stockName}$t${h.weight.toTsv()}$t${h.shares.toTsv()}$t${h.amount.toTsv()}\n")
                    }
                    totalRecords += etfHoldings.size
                    onProgress("etf: ${etfs.size}종목, holdings ${etfHoldings.size}건")

                    // portfolios
                    w.write("\n## portfolios\n")
                    w.write("id${t}name${t}maxWeightPercent${t}totalAmountLimit\n")
                    for (p in portfolios) {
                        w.write("${p.id}$t${p.name}$t${p.maxWeightPercent}$t${p.totalAmountLimit.toTsv()}\n")
                    }
                    totalRecords += portfolios.size

                    // portfolio_holdings & transactions
                    w.write("\n## portfolio_holdings\n")
                    w.write("portfolioId${t}ticker${t}stockName${t}market${t}sector${t}lastPrice${t}targetPrice\n")
                    var holdingCount = 0
                    var txCount = 0
                    val allTx = mutableListOf<Triple<Long, String, com.tinyoscillator.core.database.entity.PortfolioTransactionEntity>>()
                    for (p in portfolios) {
                        val holdings = db.portfolioDao().getHoldingsListForPortfolio(p.id)
                        for (h in holdings) {
                            w.write("${p.id}$t${h.ticker}$t${h.stockName}$t${h.market}$t${h.sector}$t${h.lastPrice.toTsv()}$t${h.targetPrice.toTsv()}\n")
                            holdingCount++
                            val transactions = db.portfolioDao().getTransactionsListForHolding(h.id)
                            for (tx in transactions) {
                                allTx.add(Triple(h.id, h.ticker, tx))
                            }
                        }
                    }
                    totalRecords += holdingCount

                    w.write("\n## portfolio_transactions\n")
                    w.write("holdingId${t}date${t}shares${t}pricePerShare${t}memo\n")
                    for ((holdingId, _, tx) in allTx) {
                        w.write("$holdingId$t${tx.date}$t${tx.shares}$t${tx.pricePerShare}$t${tx.memo}\n")
                        txCount++
                    }
                    totalRecords += txCount
                    onProgress("portfolio: ${portfolios.size}개, holdings $holdingCount, tx $txCount")

                    // consensus_reports
                    w.write("\n## consensus_reports\n")
                    w.write("writeDate${t}category${t}stockTicker${t}stockName${t}prevOpinion${t}opinion${t}title${t}author${t}institution${t}targetPrice${t}currentPrice${t}divergenceRate\n")
                    for (cr in consensusReports) {
                        w.write("${cr.writeDate}$t${cr.category}$t${cr.stockTicker}$t${cr.stockName}$t${cr.prevOpinion}$t${cr.opinion}$t${cr.title}$t${cr.author}$t${cr.institution}$t${cr.targetPrice}$t${cr.currentPrice}$t${cr.divergenceRate}\n")
                    }
                    totalRecords += consensusReports.size
                    onProgress("consensus_reports: ${consensusReports.size}건")
                }
            }

            onProgress("완료: 총 $totalRecords 레코드")
            Result.success(totalRecords)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // endregion

    private fun getEncryptedPrefsForBackup(context: Context): android.content.SharedPreferences {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context.applicationContext,
            "api_settings_encrypted",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
