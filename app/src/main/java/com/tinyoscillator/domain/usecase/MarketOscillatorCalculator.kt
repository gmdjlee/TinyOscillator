package com.tinyoscillator.domain.usecase

import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.tinyoscillator.core.api.KrxApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 시장 과매수/과매도 지표 계산기 (pykrx _calc() 직접 이식)
 *
 * 알고리즘:
 * 1. KOSPI200("1028")/KOSDAQ150("2203") 구성종목만 필터링
 * 2. vol_ratio = upVol / (upVol + downVol)
 * 3. pts_ratio = gained / (gained + lost)
 * 4. avg = (vol_ratio + pts_ratio) / 2
 * 5. oscillator = if (avg > 0.5) avg else (avg - 1.0)  ← 핵심 비선형 변환
 * 6. ×100 → 출력 범위: [-100, -50] ∪ (50, 100]
 */
class MarketOscillatorCalculator(
    private val krxApiClient: KrxApiClient
) {

    companion object {
        private const val PER_REQUEST_DELAY_MS = 500L
    }

    /**
     * 시장 oscillator 분석 실행
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @param startDate 시작일 (yyyyMMdd)
     * @param endDate 종료일 (yyyyMMdd)
     */
    suspend fun analyze(market: String, startDate: String, endDate: String): OscillatorResult? = withContext(Dispatchers.IO) {
        try {
            val krxIndex = krxApiClient.getKrxIndex()
            val krxStock = krxApiClient.getKrxStock()
            if (krxIndex == null || krxStock == null) {
                Timber.e("KRX 로그인이 필요합니다 (krxIndex=${krxIndex != null}, krxStock=${krxStock != null})")
                return@withContext null
            }

            Timber.d("Analyzing $market oscillator: $startDate ~ $endDate")

            val indexData = getIndexData(krxIndex, market, startDate, endDate)
            if (indexData.isEmpty()) {
                Timber.e("No index data for $market")
                return@withContext null
            }

            val latestDate = indexData.last().date
            val componentSet = getComponentSet(krxIndex, market, latestDate)
            if (componentSet.isEmpty()) {
                Timber.e("No component tickers for $market (date=$latestDate)")
                return@withContext null
            }

            Timber.d("Component set: ${componentSet.size} tickers for $market")

            val indexDates = indexData.map { it.date }
            val (oscillatorValues, validDates) = computeOscillatorValues(krxStock, indexDates, market, componentSet)
            if (oscillatorValues.isEmpty()) {
                Timber.e("No oscillator data computed for $market")
                return@withContext null
            }

            val indexMap = indexData.associateBy { it.date }
            val indexValues = validDates.map { date -> indexMap[date]?.close ?: 0.0 }

            OscillatorResult(
                market = market,
                dates = validDates,
                indexValues = indexValues,
                oscillator = oscillatorValues,
                stats = OscillatorStats(
                    mean = oscillatorValues.average(),
                    max = oscillatorValues.maxOrNull() ?: 0.0,
                    min = oscillatorValues.minOrNull() ?: 0.0,
                    latest = oscillatorValues.lastOrNull() ?: 0.0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Oscillator analysis failed for $market")
            null
        }
    }

    private suspend fun getIndexData(krxIndex: KrxIndex, market: String, startDate: String, endDate: String): List<IndexOhlcv> {
        return try {
            when (market.uppercase()) {
                "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                else -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Index data fetch failed for $market")
            emptyList()
        }
    }

    internal suspend fun getComponentSet(krxIndex: KrxIndex, market: String, date: String): Set<String> {
        val indexTicker = when (market.uppercase()) {
            "KOSPI" -> KrxIndex.TICKER_KOSPI_200
            "KOSDAQ" -> KrxIndex.TICKER_KOSDAQ_150
            else -> return emptySet()
        }
        return try {
            krxIndex.getIndexPortfolioTickers(date, indexTicker).toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get component tickers for $market ($indexTicker)")
            emptySet()
        }
    }

    private suspend fun computeOscillatorValues(
        krxStock: KrxStock,
        indexDates: List<String>,
        market: String,
        componentSet: Set<String>
    ): Pair<List<Double>, List<String>> {
        val marketEnum = when (market.uppercase()) {
            "KOSPI" -> Market.KOSPI
            "KOSDAQ" -> Market.KOSDAQ
            else -> Market.ALL
        }

        val oscillatorValues = mutableListOf<Double>()
        val validDates = mutableListOf<String>()

        for ((idx, date) in indexDates.withIndex()) {
            if (idx > 0) delay(PER_REQUEST_DELAY_MS)

            try {
                val ohlcvList = krxStock.getMarketOhlcv(date, marketEnum)
                if (ohlcvList.isEmpty()) continue

                val components = ohlcvList.filter { it.ticker in componentSet }
                if (components.isEmpty()) continue

                var upVol = 0L
                var downVol = 0L
                var gained = 0.0
                var lost = 0.0

                for (stock in components) {
                    when {
                        stock.changeRate > 0 -> {
                            upVol += stock.volume
                            gained += stock.changeRate
                        }
                        stock.changeRate < 0 -> {
                            downVol += stock.volume
                            lost += -stock.changeRate
                        }
                    }
                }

                val totalVol = upVol + downVol
                val totalPts = gained + lost

                val volRatio = if (totalVol > 0L) upVol.toDouble() / totalVol else 0.5
                val ptsRatio = if (totalPts > 0.0) gained / totalPts else 0.5

                val avg = (volRatio + ptsRatio) / 2.0

                // 핵심 비선형 변환: np.where(avg > 0.5, avg, avg - 1)
                val oscillatorRaw = if (avg > 0.5) avg else (avg - 1.0)

                oscillatorValues.add(oscillatorRaw * 100.0)
                validDates.add(date)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Failed to fetch market OHLCV for $date: ${e.message}")
            }
        }

        return Pair(oscillatorValues, validDates)
    }

    data class OscillatorResult(
        val market: String,
        val dates: List<String>,
        val indexValues: List<Double>,
        val oscillator: List<Double>,
        val stats: OscillatorStats
    )

    data class OscillatorStats(
        val mean: Double,
        val max: Double,
        val min: Double,
        val latest: Double
    )
}
