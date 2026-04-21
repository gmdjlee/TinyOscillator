package com.tinyoscillator.presentation.settings

import android.content.Context
import android.net.Uri
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.domain.model.FinancialDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

internal fun formatTimestampInternal(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}

internal fun Any?.toTsvInternal(): String = when {
    this == null -> ""
    this is Number && this.toDouble() == 0.0 -> ""
    this is String && this.isEmpty() -> ""
    else -> toString()
}

internal object BackupExporter {

    suspend fun exportApiBackup(
        context: Context,
        uri: Uri,
        type: String,
        password: String
    ): Result<Int> = withContext(Dispatchers.IO) {
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
            val encryptedBytes = BackupEncryption.encrypt(json, password)
            context.contentResolver.openOutputStream(uri)?.use { it.write(encryptedBytes) }
            Result.success(1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                        w.write("${s.ticker}$t${s.name}$t${s.market}$t${s.sector}$t${formatTimestampInternal(s.lastUpdated)}\n")
                    }
                    totalRecords += stockMasters.size
                    onProgress("stock_master: ${stockMasters.size}건")

                    // analysis_cache
                    w.write("\n## analysis_cache\n")
                    w.write("ticker${t}date${t}marketCap${t}foreignNet${t}instNet${t}close${t}open${t}high${t}low${t}volume\n")
                    for (a in analysisCache) {
                        w.write("${a.ticker}$t${a.date}$t${a.marketCap.toTsvInternal()}$t${a.foreignNet.toTsvInternal()}$t${a.instNet.toTsvInternal()}$t${a.closePrice.toTsvInternal()}$t${a.openPrice.toTsvInternal()}$t${a.highPrice.toTsvInternal()}$t${a.lowPrice.toTsvInternal()}$t${a.volume.toTsvInternal()}\n")
                    }
                    totalRecords += analysisCache.size
                    onProgress("analysis_cache: ${analysisCache.size}건")

                    // analysis_history
                    w.write("\n## analysis_history\n")
                    w.write("ticker${t}name${t}lastAnalyzedAt\n")
                    for (h in analysisHistory) {
                        w.write("${h.ticker}$t${h.name}$t${formatTimestampInternal(h.lastAnalyzedAt)}\n")
                    }
                    totalRecords += analysisHistory.size

                    // fundamental_cache
                    w.write("\n## fundamental_cache\n")
                    w.write("ticker${t}date${t}close${t}eps${t}per${t}bps${t}pbr${t}dps${t}dividendYield\n")
                    for (f in fundamentalCache) {
                        w.write("${f.ticker}$t${f.date}$t${f.close.toTsvInternal()}$t${f.eps.toTsvInternal()}$t${f.per.toTsvInternal()}$t${f.bps.toTsvInternal()}$t${f.pbr.toTsvInternal()}$t${f.dps.toTsvInternal()}$t${f.dividendYield.toTsvInternal()}\n")
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
                            w.write("$ticker$t$name$t${bs.yearMonth}$t${bs.currentAssets.toTsvInternal()}$t${bs.fixedAssets.toTsvInternal()}$t${bs.totalAssets.toTsvInternal()}$t${bs.currentLiabilities.toTsvInternal()}$t${bs.fixedLiabilities.toTsvInternal()}$t${bs.totalLiabilities.toTsvInternal()}$t${bs.capital.toTsvInternal()}$t${bs.capitalSurplus.toTsvInternal()}$t${bs.retainedEarnings.toTsvInternal()}$t${bs.totalEquity.toTsvInternal()}\n")
                            financialRows++
                        }
                    }

                    // financial_income_statement
                    w.write("\n## financial_income_statement\n")
                    w.write("ticker${t}name${t}yearMonth${t}revenue${t}costOfSales${t}grossProfit${t}operatingProfit${t}ordinaryProfit${t}netIncome\n")
                    for ((ticker, name, cache) in parsed) {
                        for (is_ in cache.incomeStatements) {
                            w.write("$ticker$t$name$t${is_.yearMonth}$t${is_.revenue.toTsvInternal()}$t${is_.costOfSales.toTsvInternal()}$t${is_.grossProfit.toTsvInternal()}$t${is_.operatingProfit.toTsvInternal()}$t${is_.ordinaryProfit.toTsvInternal()}$t${is_.netIncome.toTsvInternal()}\n")
                            financialRows++
                        }
                    }

                    // financial_profitability
                    w.write("\n## financial_profitability\n")
                    w.write("ticker${t}name${t}yearMonth${t}operatingMargin${t}netMargin${t}roe${t}roa\n")
                    for ((ticker, name, cache) in parsed) {
                        for (pr in cache.profitabilityRatios) {
                            w.write("$ticker$t$name$t${pr.yearMonth}$t${pr.operatingMargin.toTsvInternal()}$t${pr.netMargin.toTsvInternal()}$t${pr.roe.toTsvInternal()}$t${pr.roa.toTsvInternal()}\n")
                            financialRows++
                        }
                    }

                    // financial_stability
                    w.write("\n## financial_stability\n")
                    w.write("ticker${t}name${t}yearMonth${t}debtRatio${t}currentRatio${t}quickRatio${t}borrowingDependency${t}interestCoverageRatio\n")
                    for ((ticker, name, cache) in parsed) {
                        for (sr in cache.stabilityRatios) {
                            w.write("$ticker$t$name$t${sr.yearMonth}$t${sr.debtRatio.toTsvInternal()}$t${sr.currentRatio.toTsvInternal()}$t${sr.quickRatio.toTsvInternal()}$t${sr.borrowingDependency.toTsvInternal()}$t${sr.interestCoverageRatio.toTsvInternal()}\n")
                            financialRows++
                        }
                    }

                    // financial_growth
                    w.write("\n## financial_growth\n")
                    w.write("ticker${t}name${t}yearMonth${t}revenueGrowth${t}operatingProfitGrowth${t}netIncomeGrowth${t}equityGrowth${t}totalAssetsGrowth\n")
                    for ((ticker, name, cache) in parsed) {
                        for (gr in cache.growthRatios) {
                            w.write("$ticker$t$name$t${gr.yearMonth}$t${gr.revenueGrowth.toTsvInternal()}$t${gr.operatingProfitGrowth.toTsvInternal()}$t${gr.netIncomeGrowth.toTsvInternal()}$t${gr.equityGrowth.toTsvInternal()}$t${gr.totalAssetsGrowth.toTsvInternal()}\n")
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
                        w.write("${e.ticker}$t${e.name}$t${e.isinCode}$t${e.indexName.toTsvInternal()}$t${e.totalFee.toTsvInternal()}\n")
                    }
                    totalRecords += etfs.size

                    // etf_holdings
                    w.write("\n## etf_holdings\n")
                    w.write("etfTicker${t}stockTicker${t}date${t}stockName${t}weight${t}shares${t}amount\n")
                    for (h in etfHoldings) {
                        w.write("${h.etfTicker}$t${h.stockTicker}$t${h.date}$t${h.stockName}$t${h.weight.toTsvInternal()}$t${h.shares.toTsvInternal()}$t${h.amount.toTsvInternal()}\n")
                    }
                    totalRecords += etfHoldings.size
                    onProgress("etf: ${etfs.size}종목, holdings ${etfHoldings.size}건")

                    // portfolios
                    w.write("\n## portfolios\n")
                    w.write("id${t}name${t}maxWeightPercent${t}totalAmountLimit\n")
                    for (p in portfolios) {
                        w.write("${p.id}$t${p.name}$t${p.maxWeightPercent}$t${p.totalAmountLimit.toTsvInternal()}\n")
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
                            w.write("${p.id}$t${h.ticker}$t${h.stockName}$t${h.market}$t${h.sector}$t${h.lastPrice.toTsvInternal()}$t${h.targetPrice.toTsvInternal()}\n")
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
}
