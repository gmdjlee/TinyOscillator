package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.domain.model.HeatmapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 관심 종목(= 최근 분석 종목) 리스트의 일별 앙상블 점수 히트맵 데이터를 구축한다.
 */
@Singleton
class BuildHeatmapUseCase @Inject constructor(
    private val analysisHistoryDao: AnalysisHistoryDao,
    private val calibrationDao: CalibrationDao,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val labelFormatter = DateTimeFormatter.ofPattern("MM.dd")

    /**
     * 최근 [windowDays]일, 분석 이력 종목 전체의 일별 앙상블 평균 점수.
     */
    suspend operator fun invoke(windowDays: Int = 20): HeatmapData =
        withContext(Dispatchers.IO) {
            val history = analysisHistoryDao.getAll()
            val tickers = history.map { it.ticker }
            val tickerNames = history.associate { it.ticker to it.name }

            if (tickers.isEmpty()) {
                return@withContext HeatmapData(
                    tickers = emptyList(),
                    tickerNames = emptyMap(),
                    dates = emptyList(),
                    dateLabels = emptyList(),
                    scores = emptyMap(),
                )
            }

            val today = LocalDate.now()
            val dateStrings = (windowDays - 1 downTo 0).map { d ->
                today.minusDays(d.toLong())
            }
            val dates = dateStrings.map { it.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000L }
            val dateLabels = dateStrings.map { it.format(labelFormatter) }
            val dateKeys = dateStrings.map { it.format(dateFormatter) }

            val scores = tickers.associateWith { ticker ->
                dateKeys.map { dateKey ->
                    val avg = calibrationDao.getAverageScoreForDay(ticker, dateKey)
                    avg?.toFloat() ?: 0.5f
                }
            }

            HeatmapData(
                tickers = tickers,
                tickerNames = tickerNames,
                dates = dates,
                dateLabels = dateLabels,
                scores = scores,
            )
        }
}
