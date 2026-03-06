package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.AmountRankingRow
import com.tinyoscillator.domain.model.CashDepositRow
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.domain.model.StockChange
import com.tinyoscillator.domain.model.StockInEtfRow
import com.tinyoscillator.domain.model.StockSearchResult
import com.tinyoscillator.presentation.settings.EtfKeywordFilter
import com.tinyoscillator.presentation.settings.KrxCredentials
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EtfRepository(
    private val etfDao: EtfDao,
    private val krxApiClient: KrxApiClient
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

    suspend fun login(id: String, pw: String): Boolean = krxApiClient.login(id, pw)

    fun getAllEtfs(): Flow<List<EtfEntity>> = etfDao.getAllEtfs()

    suspend fun getEtf(ticker: String): EtfEntity? = etfDao.getEtf(ticker)

    suspend fun getHoldings(etfTicker: String, date: String): List<EtfHoldingEntity> =
        etfDao.getHoldings(etfTicker, date)

    suspend fun getLatestDate(): String? = etfDao.getLatestDate()

    suspend fun getAllDates(): List<String> = etfDao.getAllDates()

    fun updateData(
        creds: KrxCredentials,
        keywords: EtfKeywordFilter,
        daysBack: Int = 14
    ): Flow<EtfDataProgress> = flow {
        try {
            emit(EtfDataProgress.Loading("KRX 로그인 중...", 0f))
            val loggedIn = login(creds.id, creds.password)
            if (!loggedIn) {
                emit(EtfDataProgress.Error("KRX 로그인 실패. ID/비밀번호를 확인해주세요."))
                return@flow
            }

            emit(EtfDataProgress.Loading("ETF 목록 수집 중...", 0.1f))

            val dates = getBusinessDates(daysBack)
            if (dates.isEmpty()) {
                emit(EtfDataProgress.Error("수집할 영업일이 없습니다."))
                return@flow
            }

            // Check what we already have
            val latestInDb = etfDao.getLatestDate()
            val datesToFetch = if (latestInDb != null) {
                dates.filter { it > latestInDb }
            } else {
                dates
            }

            if (datesToFetch.isEmpty()) {
                val etfCount = etfDao.getAllEtfs().let { flow ->
                    var count = 0
                    flow.collect { count = it.size; return@collect }
                    count
                }
                emit(EtfDataProgress.Success(etfCount, 0))
                return@flow
            }

            // Fetch ETF list from the most recent date
            val latestDate = datesToFetch.last()
            val allEtfs = krxApiClient.getEtfTickerList(latestDate)
            Timber.d("전체 ETF 수: ${allEtfs.size}")

            // Apply keyword filter (순서: 1.액티브 필터 → 2.제외 키워드 → 3.포함 키워드)
            val step1Active = allEtfs.filter { it.name.contains("액티브") }
            Timber.d("1단계 액티브 ETF: ${step1Active.size}개")

            val step2Excluded = step1Active.filter { etf ->
                keywords.excludeKeywords.none { kw -> etf.name.contains(kw) }
            }
            Timber.d("2단계 제외 키워드 적용: ${step1Active.size} → ${step2Excluded.size}개")

            val filteredEtfs = step2Excluded.filter { etf ->
                keywords.includeKeywords.any { kw -> etf.name.contains(kw) }
            }
            Timber.d("3단계 포함 키워드 적용: ${step2Excluded.size} → ${filteredEtfs.size}개")

            // Save ETF entities
            val etfEntities = filteredEtfs.map { etf ->
                EtfEntity(
                    ticker = etf.ticker,
                    name = etf.name,
                    isinCode = etf.isinCode,
                    indexName = etf.indexName,
                    totalFee = etf.totalFee
                )
            }
            etfDao.insertEtfs(etfEntities)

            emit(EtfDataProgress.Loading("구성종목 수집 중...", 0.3f))

            // Fetch holdings for each ETF × date
            var totalHoldings = 0
            val totalOps = filteredEtfs.size * datesToFetch.size
            var completedOps = 0

            for (date in datesToFetch) {
                for (etf in filteredEtfs) {
                    try {
                        val portfolio = krxApiClient.getPortfolio(date, etf.ticker)
                        if (portfolio.isNotEmpty()) {
                            val holdings = portfolio.map { p ->
                                EtfHoldingEntity(
                                    etfTicker = etf.ticker,
                                    stockTicker = p.ticker,
                                    date = date,
                                    stockName = p.name,
                                    weight = p.weight,
                                    shares = p.shares,
                                    amount = p.valuationAmount
                                )
                            }
                            etfDao.insertHoldings(holdings)
                            totalHoldings += holdings.size
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "구성종목 수집 실패: ${etf.ticker} / $date")
                    }

                    completedOps++
                    val progress = 0.3f + (completedOps.toFloat() / totalOps) * 0.65f
                    emit(EtfDataProgress.Loading(
                        "${etf.name} ($date) 수집 중... ($completedOps/$totalOps)",
                        progress
                    ))

                    delay(500) // Rate limit
                }
            }

            // Cleanup old data (>365 days)
            val cutoffDate = getCutoffDate(365)
            etfDao.deleteHoldingsBeforeDate(cutoffDate)

            emit(EtfDataProgress.Success(filteredEtfs.size, totalHoldings))
        } catch (e: Exception) {
            Timber.e(e, "ETF 데이터 수집 실패")
            emit(EtfDataProgress.Error("데이터 수집 실패: ${e.message}"))
        } finally {
            krxApiClient.close()
        }
    }

    suspend fun getAmountRanking(date: String): List<AmountRankingRow> =
        etfDao.getAmountRanking(date)

    suspend fun getCashDepositTrend(): List<CashDepositRow> =
        etfDao.getCashDepositTrend()

    suspend fun getEtfsHoldingStock(stockTicker: String, date: String): List<StockInEtfRow> =
        etfDao.getEtfsHoldingStock(stockTicker, date)

    suspend fun searchStocksInHoldings(date: String, query: String): List<StockSearchResult> =
        etfDao.searchStocksInHoldings(date, query)

    suspend fun computeStockChanges(date1: String, date2: String): List<StockChange> {
        val oldHoldings = etfDao.getAllHoldingsForDate(date1)
        val newHoldings = etfDao.getAllHoldingsForDate(date2)

        // Build lookup maps: (etfTicker, stockTicker) -> holding
        val oldMap = oldHoldings.associateBy { "${it.etfTicker}|${it.stockTicker}" }
        val newMap = newHoldings.associateBy { "${it.etfTicker}|${it.stockTicker}" }

        // ETF name lookup
        val etfNames = mutableMapOf<String, String>()
        val allEtfTickers = (oldHoldings.map { it.etfTicker } + newHoldings.map { it.etfTicker }).toSet()
        for (ticker in allEtfTickers) {
            etfDao.getEtf(ticker)?.let { etfNames[ticker] = it.name }
        }

        val changes = mutableListOf<StockChange>()
        val allKeys = (oldMap.keys + newMap.keys)

        for (key in allKeys) {
            val old = oldMap[key]
            val new = newMap[key]
            val etfTicker = (old ?: new)!!.etfTicker
            val stockTicker = (old ?: new)!!.stockTicker
            val stockName = (new ?: old)!!.stockName
            val etfName = etfNames[etfTicker] ?: etfTicker

            when {
                old == null && new != null -> changes.add(
                    StockChange(stockTicker, stockName, etfTicker, etfName,
                        null, new.weight, 0, new.amount, ChangeType.NEW)
                )
                old != null && new == null -> changes.add(
                    StockChange(stockTicker, stockName, etfTicker, etfName,
                        old.weight, null, old.amount, 0, ChangeType.REMOVED)
                )
                old != null && new != null -> {
                    val oldWeight = old.weight ?: 0.0
                    val newWeight = new.weight ?: 0.0
                    if (newWeight > oldWeight + 0.001) {
                        changes.add(
                            StockChange(stockTicker, stockName, etfTicker, etfName,
                                old.weight, new.weight, old.amount, new.amount, ChangeType.INCREASED)
                        )
                    } else if (newWeight < oldWeight - 0.001) {
                        changes.add(
                            StockChange(stockTicker, stockName, etfTicker, etfName,
                                old.weight, new.weight, old.amount, new.amount, ChangeType.DECREASED)
                        )
                    }
                }
            }
        }
        return changes
    }

    suspend fun getEnrichedAmountRanking(date: String, comparisonDate: String?): List<AmountRankingItem> {
        val ranking = etfDao.getAmountRanking(date)
        val changes = if (comparisonDate != null) computeStockChanges(comparisonDate, date) else emptyList()

        // Group change counts by stock
        val changeCounts = changes.groupBy { it.stockTicker }

        return ranking.mapIndexed { index, row ->
            val stockChanges = changeCounts[row.stock_ticker] ?: emptyList()
            AmountRankingItem(
                rank = index + 1,
                stockTicker = row.stock_ticker,
                stockName = row.stock_name,
                totalAmountBillion = row.totalAmount / 100_000_000.0,
                etfCount = row.etfCount,
                newCount = stockChanges.count { it.changeType == ChangeType.NEW },
                increasedCount = stockChanges.count { it.changeType == ChangeType.INCREASED },
                decreasedCount = stockChanges.count { it.changeType == ChangeType.DECREASED },
                removedCount = stockChanges.count { it.changeType == ChangeType.REMOVED }
            )
        }
    }

    private fun getBusinessDates(daysBack: Int): List<String> {
        val dates = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        for (i in 0 until daysBack * 2) { // Iterate extra to account for weekends
            if (dates.size >= daysBack) break
            calendar.add(Calendar.DAY_OF_YEAR, if (i == 0) 0 else -1)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                dates.add(dateFormat.format(calendar.time))
            }
        }
        return dates.reversed() // oldest first
    }

    private fun getCutoffDate(daysBack: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
        return dateFormat.format(calendar.time)
    }
}
