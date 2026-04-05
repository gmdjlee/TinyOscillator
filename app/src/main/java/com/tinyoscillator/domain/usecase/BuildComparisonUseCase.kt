package com.tinyoscillator.domain.usecase

import android.graphics.Color
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.domain.model.ComparisonData
import com.tinyoscillator.domain.model.ComparisonPeriod
import com.tinyoscillator.domain.model.ComparisonSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * 종목 수익률·신호 강도를 섹터 평균, KOSPI 대비 오버레이 비교 데이터를 구축한다.
 *
 * 기존 Room 캐시(analysis_cache, kospi_index, signal_history)만 사용하므로
 * 네트워크 호출 없이 즉시 응답한다.
 */
class BuildComparisonUseCase @Inject constructor(
    private val analysisCacheDao: AnalysisCacheDao,
    private val regimeDao: RegimeDao,
    private val calibrationDao: CalibrationDao,
    private val stockMasterDao: StockMasterDao,
) {
    private val fmt = DateFormats.yyyyMMdd

    suspend operator fun invoke(
        ticker: String,
        period: ComparisonPeriod,
        startDate: Long? = null,
        endDate: Long? = null,
    ): ComparisonData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fromMs = when (period) {
            ComparisonPeriod.CUSTOM -> startDate ?: (now - 365 * 86_400_000L)
            else -> now - period.days * 86_400_000L
        }
        val toMs = endDate ?: now

        val fromDate = LocalDate.ofEpochDay(fromMs / 86_400_000L).format(fmt)
        val toDate = LocalDate.ofEpochDay(toMs / 86_400_000L).format(fmt)

        coroutineScope {
            // 병렬 데이터 수집
            val targetDeferred = async {
                analysisCacheDao.getByTickerDateRange(ticker, fromDate, toDate)
                    .filter { it.closePrice > 0 }
                    .map { it.date to it.closePrice.toFloat() }
            }
            val kospiDeferred = async {
                regimeDao.getKospiIndexByDateRange(fromDate, toDate)
                    .map { it.date to it.closeValue.toFloat() }
            }
            val sectorDeferred = async {
                val sector = stockMasterDao.getSector(ticker)
                if (sector.isNullOrBlank()) return@async null
                val sectorTickers = stockMasterDao.getTickersBySector(sector, 20)
                if (sectorTickers.size < 2) return@async sector to emptyList<Pair<String, Float>>()

                // 섹터 평균: 각 날짜별로 종목 종가 평균
                val allData = sectorTickers.map { t ->
                    analysisCacheDao.getByTickerDateRange(t, fromDate, toDate)
                        .filter { it.closePrice > 0 }
                        .associate { it.date to it.closePrice.toFloat() }
                }
                // 공통 날짜 집합
                val commonDates = allData.fold(allData.firstOrNull()?.keys ?: emptySet()) { acc, m ->
                    acc.intersect(m.keys)
                }.sorted()
                val avgPrices = commonDates.map { date ->
                    val avg = allData.mapNotNull { it[date] }.average().toFloat()
                    date to avg
                }
                sector to avgPrices
            }
            val signalDeferred = async {
                val sinceDate = fromDate
                val dates = calibrationDao.getDistinctDates(ticker, sinceDate)
                dates.map { date ->
                    val score = calibrationDao.getAverageScoreForDay(ticker, date) ?: 0.5
                    dateToEpochMs(date) to score.toFloat()
                }
            }

            val targetPrices = targetDeferred.await()
            val kospiPrices = kospiDeferred.await()
            val sectorResult = sectorDeferred.await()
            val signalHistory = signalDeferred.await()

            val targetReturns = normalizeReturns(targetPrices.map { it.second })
            val kospiReturns = normalizeReturns(kospiPrices.map { it.second })

            val beta = estimateBeta(targetReturns, kospiReturns)
            val alpha = (targetReturns.lastOrNull() ?: 0f) - (kospiReturns.lastOrNull() ?: 0f)

            val sectorSeries = sectorResult?.let { (sectorName, sectorPrices) ->
                if (sectorPrices.isEmpty()) return@let null
                ComparisonSeries(
                    label = sectorName,
                    color = Color.parseColor("#888780"),
                    returns = normalizeReturns(sectorPrices.map { it.second }),
                    dates = sectorPrices.map { dateToEpochMs(it.first) },
                )
            }

            ComparisonData(
                targetSeries = ComparisonSeries(
                    label = ticker,
                    color = Color.parseColor("#D85A30"),
                    returns = targetReturns,
                    dates = targetPrices.map { dateToEpochMs(it.first) },
                ),
                sectorSeries = sectorSeries,
                kospiSeries = ComparisonSeries(
                    label = "KOSPI",
                    color = Color.parseColor("#378ADD"),
                    returns = kospiReturns,
                    dates = kospiPrices.map { dateToEpochMs(it.first) },
                ),
                signalHistory = signalHistory,
                alphaFinal = alpha,
                betaEstimate = beta,
            )
        }
    }

    companion object {
        fun normalizeReturns(prices: List<Float>): List<Float> {
            if (prices.isEmpty()) return emptyList()
            val base = prices.first().coerceAtLeast(0.01f)
            return prices.map { it / base - 1f }
        }

        fun estimateBeta(y: List<Float>, x: List<Float>): Float {
            val n = minOf(y.size, x.size)
            if (n < 10) return 1.0f
            val yS = y.take(n); val xS = x.take(n)
            val xMean = xS.average().toFloat()
            val yMean = yS.average().toFloat()
            val cov = (0 until n).sumOf { i ->
                ((xS[i] - xMean) * (yS[i] - yMean)).toDouble()
            }.toFloat() / n
            val varX = (0 until n).sumOf { i ->
                ((xS[i] - xMean) * (xS[i] - xMean)).toDouble()
            }.toFloat() / n
            return if (varX < 1e-8f) 1.0f else cov / varX
        }

        private val yyyyMMdd = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")

        fun dateToEpochMs(dateStr: String): Long {
            return try {
                LocalDate.parse(dateStr, yyyyMMdd)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                    .toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
