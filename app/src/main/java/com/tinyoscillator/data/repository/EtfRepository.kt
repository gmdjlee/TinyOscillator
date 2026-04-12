package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.AmountRankingRow
import com.tinyoscillator.domain.model.CashDepositRow
import com.tinyoscillator.domain.model.ChangeType
import com.tinyoscillator.domain.model.WeightTrend
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.domain.model.HoldingTimeSeries
import com.tinyoscillator.domain.model.StockAggregatedTimePoint
import com.tinyoscillator.domain.model.StockChange
import com.tinyoscillator.domain.model.StockInEtfRow
import com.tinyoscillator.domain.model.StockSearchResult
import com.tinyoscillator.domain.model.EtfKeywordFilter
import com.tinyoscillator.domain.model.KrxCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.tinyoscillator.core.util.DateFormats
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate

class EtfRepository(
    private val etfDao: EtfDao,
    private val krxApiClient: KrxApiClient
) {
    private val dateFormat = DateFormats.yyyyMMdd

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
        require(daysBack in 1..365) { "daysBack must be 1-365, got $daysBack" }
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

            // Fetch ETF list from the most recent date
            val latestDate = dates.last()
            val allEtfs = krxApiClient.getEtfTickerList(latestDate).getOrElse { e ->
                emit(EtfDataProgress.Error("ETF 목록 조회 실패: ${e.message ?: e.javaClass.simpleName}"))
                return@flow
            }
            Timber.d("전체 ETF 수: ${allEtfs.size}")

            // Apply keyword filter (순서: 1.액티브 ETF 기본 수집 → 2.제외 키워드 → 3.포함 키워드)
            val step1Active = allEtfs.filter { it.name.contains("액티브") }
            Timber.d("1단계 액티브 ETF: ${step1Active.size}개")

            val step2Excluded = step1Active.filter { etf ->
                keywords.excludeKeywords.none { kw -> etf.name.contains(kw) }
            }
            Timber.d("2단계 제외 키워드 적용: ${step1Active.size} → ${step2Excluded.size}개")

            val filteredEtfs = if (keywords.includeKeywords.isEmpty()) {
                step2Excluded
            } else {
                step2Excluded.filter { etf ->
                    keywords.includeKeywords.any { kw -> etf.name.contains(kw) }
                }
            }
            Timber.d("3단계 포함 키워드 적용: ${step2Excluded.size} → ${filteredEtfs.size}개")

            // Save ETF entities (upsert, no delete)
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

            // Pair-based incremental logic:
            // Check which (etf_ticker, date) pairs already exist in DB (scoped to target dates)
            val existingPairs = etfDao.getExistingPairsForDates(dates).toSet()

            data class WorkItem(val ticker: String, val name: String, val date: String)
            val workItems = mutableListOf<WorkItem>()
            for (etf in filteredEtfs) {
                for (date in dates) {
                    if ("${etf.ticker}|$date" !in existingPairs) {
                        workItems.add(WorkItem(etf.ticker, etf.name, date))
                    }
                }
            }

            val skippedCount = (filteredEtfs.size * dates.size) - workItems.size
            Timber.i("수집 대상: ${workItems.size}건, 스킵: ${skippedCount}건 (이미 수집됨)")
            if (workItems.isNotEmpty()) {
                val byEtf = workItems.groupBy { it.ticker }
                for ((ticker, items) in byEtf.entries.take(5)) {
                    Timber.d("  $ticker: ${items.size}/${dates.size}일 수집 필요")
                }
            }

            if (workItems.isEmpty()) {
                Timber.d("수집 대상 없음 — 모든 데이터 완결")
                emit(EtfDataProgress.Success(filteredEtfs.size, 0))
                return@flow
            }

            emit(EtfDataProgress.Loading("구성종목 수집 중...", 0.3f))

            // Fetch holdings for each work item
            var totalHoldings = 0
            var failedOps = 0
            val totalOps = workItems.size
            var completedOps = 0

            for (item in workItems) {
                try {
                    val portfolio = krxApiClient.getPortfolio(item.date, item.ticker).getOrThrow()
                    if (portfolio.isNotEmpty()) {
                        val holdings = portfolio.map { p ->
                            EtfHoldingEntity(
                                etfTicker = item.ticker,
                                stockTicker = p.ticker,
                                date = item.date,
                                stockName = p.name,
                                weight = p.weight,
                                shares = p.shares,
                                amount = p.valuationAmount
                            )
                        }
                        // 트랜잭션: 기존 부분 데이터 삭제 → 전체 삽입 (원자적)
                        etfDao.replaceHoldingsForEtfAndDate(item.ticker, item.date, holdings)
                        totalHoldings += holdings.size
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failedOps++
                    Timber.w(e, "구성종목 수집 실패: ${item.ticker} / ${item.date}")
                }

                completedOps++
                val progress = 0.3f + (completedOps.toFloat() / totalOps) * 0.65f
                emit(EtfDataProgress.Loading(
                    "${item.name} (${item.date}) 수집 중... ($completedOps/$totalOps)",
                    progress
                ))

                delay(500) // Rate limit
            }

            if (failedOps > 0) {
                Timber.w("수집 완료: 성공=${completedOps - failedOps}, 실패=$failedOps, 총 holdings=$totalHoldings (다음 실행 시 실패 항목 재수집)")
            } else {
                Timber.i("수집 완료: 전체 ${completedOps}건 성공, 총 holdings=$totalHoldings")
            }

            // Cleanup old data (>365 days)
            val cutoffDate = getCutoffDate(365)
            etfDao.deleteHoldingsBeforeDate(cutoffDate)

            emit(EtfDataProgress.Success(filteredEtfs.size, totalHoldings))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ETF 데이터 수집 실패")
            emit(EtfDataProgress.Error("데이터 수집 실패: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            try {
                krxApiClient.close()
            } catch (e: Exception) {
                Timber.w(e, "KRX 클라이언트 close 실패")
            }
        }
    }

    suspend fun getExcludedTickers(excludeKeywords: List<String>): List<String> {
        if (excludeKeywords.isEmpty()) return emptyList()
        return etfDao.getAllEtfsList().filter { etf ->
            excludeKeywords.any { kw -> etf.name.contains(kw) }
        }.map { it.ticker }
    }

    suspend fun getAmountRanking(date: String, excludedTickers: List<String> = emptyList()): List<AmountRankingRow> =
        if (excludedTickers.isEmpty()) etfDao.getAmountRanking(date)
        else etfDao.getAmountRankingExcluding(date, excludedTickers)

    suspend fun getCashDepositTrend(excludedTickers: List<String> = emptyList()): List<CashDepositRow> =
        if (excludedTickers.isEmpty()) etfDao.getCashDepositTrend()
        else etfDao.getCashDepositTrendExcluding(excludedTickers)

    suspend fun getEtfsHoldingStock(stockTicker: String, date: String, excludedTickers: List<String> = emptyList()): List<StockInEtfRow> =
        if (excludedTickers.isEmpty()) etfDao.getEtfsHoldingStock(stockTicker, date)
        else etfDao.getEtfsHoldingStockExcluding(stockTicker, date, excludedTickers)

    suspend fun searchStocksInHoldings(date: String, query: String, excludedTickers: List<String> = emptyList()): List<StockSearchResult> =
        if (excludedTickers.isEmpty()) etfDao.searchStocksInHoldings(date, query)
        else etfDao.searchStocksInHoldingsExcluding(date, query, excludedTickers)

    suspend fun computeStockChanges(date1: String, date2: String, excludedTickers: List<String> = emptyList()): List<StockChange> {
        val oldHoldings = if (excludedTickers.isEmpty()) etfDao.getAllHoldingsForDate(date1) else etfDao.getAllHoldingsForDateExcluding(date1, excludedTickers)
        val newHoldings = if (excludedTickers.isEmpty()) etfDao.getAllHoldingsForDate(date2) else etfDao.getAllHoldingsForDateExcluding(date2, excludedTickers)

        // Build lookup maps: (etfTicker, stockTicker) -> holding
        val oldMap = oldHoldings.associateBy { "${it.etfTicker}|${it.stockTicker}" }
        val newMap = newHoldings.associateBy { "${it.etfTicker}|${it.stockTicker}" }

        // ETF name lookup (batch query)
        val allEtfTickers = (oldHoldings.map { it.etfTicker } + newHoldings.map { it.etfTicker }).toSet()
        val etfNames = etfDao.getEtfsByTickers(allEtfTickers.toList())
            .associate { it.ticker to it.name }

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

    suspend fun getEnrichedAmountRanking(date: String, comparisonDate: String?, excludedTickers: List<String> = emptyList()): List<AmountRankingItem> {
        val ranking = getAmountRanking(date, excludedTickers)
        val changes = if (comparisonDate != null) computeStockChanges(comparisonDate, date, excludedTickers) else emptyList()

        // Group change counts by stock
        val changeCounts = changes.groupBy { it.stockTicker }

        // Compute comparison-date weights per stock for trend calculation
        val comparisonMaxWeights: Map<String, Double>
        val comparisonAvgWeights: Map<String, Double>
        if (comparisonDate != null) {
            val compHoldings = if (excludedTickers.isEmpty()) etfDao.getAllHoldingsForDate(comparisonDate)
            else etfDao.getAllHoldingsForDateExcluding(comparisonDate, excludedTickers)
            val grouped = compHoldings.groupBy { it.stockTicker }
            comparisonMaxWeights = buildMap {
                for ((ticker, holdings) in grouped) {
                    holdings.mapNotNull { it.weight }.maxOrNull()?.let { put(ticker, it) }
                }
            }
            comparisonAvgWeights = buildMap {
                for ((ticker, holdings) in grouped) {
                    val weights = holdings.mapNotNull { it.weight }
                    if (weights.isNotEmpty()) put(ticker, weights.average())
                }
            }
        } else {
            comparisonMaxWeights = emptyMap()
            comparisonAvgWeights = emptyMap()
        }

        return ranking.mapIndexed { index, row ->
            val stockChanges = changeCounts[row.stock_ticker] ?: emptyList()
            val currentMax = row.maxWeight
            val compMax = comparisonMaxWeights[row.stock_ticker]
            val maxTrend = when {
                currentMax == null || comparisonDate == null -> WeightTrend.NONE
                compMax == null -> WeightTrend.NONE
                currentMax > compMax + 0.001 -> WeightTrend.UP
                currentMax < compMax - 0.001 -> WeightTrend.DOWN
                else -> WeightTrend.FLAT
            }
            val currentAvg = row.avgWeight
            val compAvg = comparisonAvgWeights[row.stock_ticker]
            val avgTrend = when {
                currentAvg == null || comparisonDate == null -> WeightTrend.NONE
                compAvg == null -> WeightTrend.NONE
                currentAvg > compAvg + 0.001 -> WeightTrend.UP
                currentAvg < compAvg - 0.001 -> WeightTrend.DOWN
                else -> WeightTrend.FLAT
            }
            AmountRankingItem(
                rank = index + 1,
                stockTicker = row.stock_ticker,
                stockName = row.stock_name,
                totalAmountBillion = row.totalAmount / 100_000_000.0,
                etfCount = row.etfCount,
                newCount = stockChanges.count { it.changeType == ChangeType.NEW },
                increasedCount = stockChanges.count { it.changeType == ChangeType.INCREASED },
                decreasedCount = stockChanges.count { it.changeType == ChangeType.DECREASED },
                removedCount = stockChanges.count { it.changeType == ChangeType.REMOVED },
                maxWeight = currentMax,
                maxWeightTrend = maxTrend,
                avgWeight = currentAvg,
                avgWeightTrend = avgTrend
            )
        }
    }

    suspend fun getStockTrendInEtf(etfTicker: String, stockTicker: String): List<HoldingTimeSeries> =
        etfDao.getStockTrendInEtf(etfTicker, stockTicker)

    suspend fun getStockAggregatedTrend(stockTicker: String, excludedTickers: List<String> = emptyList()): List<StockAggregatedTimePoint> =
        if (excludedTickers.isEmpty()) etfDao.getStockAggregatedTrend(stockTicker)
        else etfDao.getStockAggregatedTrendExcluding(stockTicker, excludedTickers)

    suspend fun getStockName(stockTicker: String): String? = etfDao.getStockName(stockTicker)

    private fun getBusinessDates(daysBack: Int): List<String> {
        require(daysBack > 0) { "daysBack must be > 0" }
        val dates = mutableListOf<String>()
        var date = LocalDate.now()
        for (i in 0 until daysBack * 2) { // Iterate extra to account for weekends
            if (dates.size >= daysBack) break
            if (i > 0) date = date.minusDays(1)
            val dayOfWeek = date.dayOfWeek
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                dates.add(date.format(dateFormat))
            }
        }
        return dates.reversed() // oldest first
    }

    private fun getCutoffDate(daysBack: Int): String {
        return LocalDate.now().minusDays(daysBack.toLong()).format(dateFormat)
    }
}
