package com.tinyoscillator.presentation.settings

import android.content.Context
import android.net.Uri
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.core.database.entity.FearGreedEntity
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal object BackupImporter {

    suspend fun importApiBackup(
        context: Context,
        uri: Uri,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encryptedBytes = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: return@withContext Result.failure(Exception("파일을 읽을 수 없습니다"))

            val json = try {
                BackupEncryption.decrypt(encryptedBytes, password)
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

    suspend fun importEtfData(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
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
