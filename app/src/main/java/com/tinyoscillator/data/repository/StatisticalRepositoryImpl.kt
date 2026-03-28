package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.EtfAmountPoint
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import com.tinyoscillator.domain.repository.SectorEtfReturn
import com.tinyoscillator.domain.repository.StatisticalRepository
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StatisticalRepository 구현체
 *
 * Room DB에서 원시 데이터를 조회하고, 기존 UseCase로 오실레이터/DeMark을 계산하여
 * 통계 엔진에 데이터를 제공한다.
 */
@Singleton
class StatisticalRepositoryImpl @Inject constructor(
    private val analysisCacheDao: AnalysisCacheDao,
    private val stockMasterDao: StockMasterDao,
    private val fundamentalCacheDao: FundamentalCacheDao,
    private val etfDao: EtfDao,
    private val calcOscillatorUseCase: CalcOscillatorUseCase,
    private val calcDemarkTDUseCase: CalcDemarkTDUseCase
) : StatisticalRepository {

    override suspend fun getDailyPrices(ticker: String, limit: Int): List<DailyTrading> {
        val entities = analysisCacheDao.getRecentByTicker(ticker, limit)
        return entities
            .sortedBy { it.date }
            .map { e ->
                DailyTrading(
                    date = e.date,
                    marketCap = e.marketCap,
                    foreignNetBuy = e.foreignNet,
                    instNetBuy = e.instNet,
                    closePrice = e.closePrice
                )
            }
    }

    override suspend fun getStockName(ticker: String): String? {
        return stockMasterDao.getStockName(ticker)
    }

    override suspend fun getOscillatorData(ticker: String, limit: Int): List<OscillatorRow> {
        val prices = getDailyPrices(ticker, limit)
        if (prices.isEmpty()) return emptyList()
        return try {
            calcOscillatorUseCase.execute(prices)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDemarkData(ticker: String, limit: Int): List<DemarkTDRow> {
        val prices = getDailyPrices(ticker, limit)
        if (prices.size < 5) return emptyList()
        return try {
            calcDemarkTDUseCase.execute(prices, DemarkPeriodType.DAILY)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getFundamentalData(ticker: String, limit: Int): List<FundamentalSnapshot> {
        val entities = fundamentalCacheDao.getRecentByTicker(ticker, limit)
        return entities
            .sortedBy { it.date }
            .map { e ->
                FundamentalSnapshot(
                    date = e.date,
                    close = e.close,
                    per = e.per,
                    pbr = e.pbr,
                    eps = e.eps,
                    bps = e.bps,
                    dividendYield = e.dividendYield
                )
            }
    }

    override suspend fun getEtfHoldingCount(ticker: String): Int {
        return etfDao.getEtfCountForStock(ticker)
    }

    override suspend fun getEtfAmountTrend(ticker: String): List<EtfAmountPoint> {
        val trend = etfDao.getStockAggregatedTrend(ticker)
        return trend.map { t ->
            EtfAmountPoint(
                date = t.date,
                totalAmount = t.totalAmount,
                etfCount = t.etfCount
            )
        }
    }

    override suspend fun getSectorEtfReturns(ticker: String, limit: Int): List<SectorEtfReturn> {
        // 섹터 ETF 수익률은 현재 DB에 직접 저장되지 않으므로 빈 리스트 반환
        // 향후 섹터별 ETF 매핑 테이블 추가 시 구현
        return emptyList()
    }
}
