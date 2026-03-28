package com.tinyoscillator.data.repository

import android.content.Context
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import com.tinyoscillator.core.scraper.EquityReportScraper
import com.tinyoscillator.core.scraper.FnGuideReportScraper
import com.tinyoscillator.domain.model.ConsensusChartData
import com.tinyoscillator.domain.model.ConsensusDataProgress
import com.tinyoscillator.domain.model.ConsensusFilter
import com.tinyoscillator.domain.model.ConsensusFilterOptions
import com.tinyoscillator.domain.model.ConsensusReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class ConsensusRepository(
    private val dao: ConsensusReportDao,
    private val equityScraper: EquityReportScraper,
    private val fnGuideScraper: FnGuideReportScraper,
    private val analysisCacheDao: AnalysisCacheDao
) {

    /**
     * equity.co.kr + FnGuide에서 리포트를 수집하고 병합 후 DB 저장
     */
    fun collectReports(startDate: String, endDate: String): Flow<ConsensusDataProgress> = flow {
        emit(ConsensusDataProgress.Loading("리포트 수집 시작...", 0f))

        try {
            // 1. equity.co.kr 수집
            emit(ConsensusDataProgress.Loading("equity.co.kr 수집 중...", 0.1f))
            val equityReports = try {
                equityScraper.collectReports(startDate, endDate)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "equity.co.kr 수집 실패, FnGuide만 사용")
                emptyList()
            }
            Timber.d("equity.co.kr: ${equityReports.size}건")

            // 2. FnGuide 수집
            emit(ConsensusDataProgress.Loading("FnGuide 수집 중...", 0.5f))
            val fnGuideReports = try {
                fnGuideScraper.collectReports(startDate, endDate)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "FnGuide 수집 실패, equity만 사용")
                emptyList()
            }
            Timber.d("FnGuide: ${fnGuideReports.size}건")

            // 3. 병합 및 중복 제거
            emit(ConsensusDataProgress.Loading("데이터 병합 중...", 0.9f))
            val merged = mergeReports(equityReports, fnGuideReports)

            if (merged.isEmpty()) {
                emit(ConsensusDataProgress.Success(0))
                return@flow
            }

            // 500건 배치 insert (REPLACE on conflict)
            merged.chunked(500).forEach { batch ->
                dao.insertAll(batch)
            }

            emit(ConsensusDataProgress.Success(merged.size))
            Timber.d("리포트 수집 완료: ${merged.size}건 (equity: ${equityReports.size}, FnGuide: ${fnGuideReports.size}) ($startDate ~ $endDate)")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "리포트 수집 실패")
            emit(ConsensusDataProgress.Error("수집 실패: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 두 소스의 리포트를 병합하고 중복을 제거한다.
     * 중복 기준:
     *  1차: 작성일 + 종목명 + 제목 동일
     *  2차: 작성일 + 종목명 + 작성기관 동일
     */
    private fun mergeReports(
        equityReports: List<ConsensusReportEntity>,
        fnGuideReports: List<ConsensusReportEntity>
    ): List<ConsensusReportEntity> {
        val combined = equityReports + fnGuideReports

        // 1차 중복 제거: 작성일 + 종목명 + 제목
        val afterTitleDedup = combined.distinctBy { report ->
            "${report.writeDate}|${report.stockName}|${report.title}"
        }

        // 2차 중복 제거: 작성일 + 종목명 + 작성기관
        val afterInstitutionDedup = afterTitleDedup.distinctBy { report ->
            "${report.writeDate}|${report.stockName}|${report.institution}"
        }

        val removed = combined.size - afterInstitutionDedup.size
        if (removed > 0) {
            Timber.d("병합 중복 제거: ${combined.size} → ${afterInstitutionDedup.size} (${removed}건 제거)")
        }

        return afterInstitutionDedup
    }

    /**
     * 필터 적용하여 리포트 조회
     */
    suspend fun getReports(filter: ConsensusFilter): List<ConsensusReport> = withContext(Dispatchers.IO) {
        val entities = if (filter.dateRange != null) {
            dao.getByDateRange(filter.dateRange.first, filter.dateRange.second)
        } else {
            dao.getAll()
        }

        entities
            .filter { e ->
                (filter.category == null || e.category == filter.category) &&
                (filter.prevOpinion == null || e.prevOpinion == filter.prevOpinion) &&
                (filter.opinion == null || e.opinion == filter.opinion) &&
                (filter.stockTicker == null || e.stockTicker == filter.stockTicker) &&
                (filter.stockName == null || e.stockName == filter.stockName) &&
                (filter.author == null || e.author == filter.author) &&
                (filter.institution == null || e.institution == filter.institution)
            }
            .map { it.toDomain() }
    }

    /**
     * 특정 종목의 리포트 조회
     */
    suspend fun getReportsByTicker(ticker: String): List<ConsensusReport> = withContext(Dispatchers.IO) {
        dao.getByTicker(ticker).map { it.toDomain() }
    }

    /**
     * 필터 옵션 (distinct 값들)
     */
    suspend fun getFilterOptions(): ConsensusFilterOptions = withContext(Dispatchers.IO) {
        ConsensusFilterOptions(
            dates = dao.getDistinctDates(),
            categories = dao.getDistinctCategories(),
            prevOpinions = dao.getDistinctPrevOpinions(),
            opinions = dao.getDistinctOpinions(),
            stockNames = dao.getDistinctStockNames(),
            authors = dao.getDistinctAuthors(),
            institutions = dao.getDistinctInstitutions()
        )
    }

    suspend fun getLatestDate(): String? = withContext(Dispatchers.IO) { dao.getLatestDate() }
    suspend fun getCount(): Int = withContext(Dispatchers.IO) { dao.getCount() }

    /**
     * 컨센서스 차트 데이터 생성
     * - 시가총액: analysis_cache에서 조회 (날짜 형식: "yyyyMMdd")
     * - 목표가: consensus_reports에서 조회 (날짜 형식: "yyyy-MM-dd")
     */
    suspend fun getConsensusChartData(ticker: String, stockName: String): ConsensusChartData? = withContext(Dispatchers.IO) {
        val reports = dao.getByTicker(ticker)
        if (reports.isEmpty()) return@withContext null

        // 시가총액 데이터 조회
        val earliestReport = reports.minOf { it.writeDate }
        val latestReport = reports.maxOf { it.writeDate }
        // analysis_cache date format: "yyyyMMdd"
        val startAnalysis = earliestReport.replace("-", "")
        val endAnalysis = latestReport.replace("-", "")
        val cacheEntries = analysisCacheDao.getByTickerDateRange(ticker, startAnalysis, endAnalysis)

        // analysis_cache "yyyyMMdd" → "yyyy-MM-dd"
        val dates = cacheEntries.map { e ->
            "${e.date.substring(0, 4)}-${e.date.substring(4, 6)}-${e.date.substring(6, 8)}"
        }
        val marketCaps = cacheEntries.map { it.marketCap }

        // 목표가 scatter 데이터
        val reportDates = reports.filter { it.targetPrice > 0 }.map { it.writeDate }
        val reportTargetPrices = reports.filter { it.targetPrice > 0 }.map { it.targetPrice }

        ConsensusChartData(
            ticker = ticker,
            stockName = stockName,
            dates = dates,
            marketCaps = marketCaps,
            reportDates = reportDates,
            reportTargetPrices = reportTargetPrices
        )
    }

    /**
     * JSON 시드 데이터 → DB (최초 1회)
     */
    suspend fun seedFromJson(context: Context) = withContext(Dispatchers.IO) {
        if (dao.getCount() > 0) {
            Timber.d("컨센서스 데이터 이미 존재, 시딩 건너뜀")
            return@withContext
        }

        try {
            val jsonStr = context.assets.open("equity_reports_seed.json").bufferedReader().use { it.readText() }
            val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray
            val entities = mutableListOf<ConsensusReportEntity>()

            for (element in jsonArray) {
                val obj = element.jsonObject
                val entity = parseJsonReport(obj) ?: continue
                entities.add(entity)
            }

            // 500건 배치 insert
            entities.chunked(500).forEach { batch ->
                dao.insertAll(batch)
            }

            Timber.d("컨센서스 시드 데이터 로드 완료: ${entities.size}건")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "컨센서스 시드 데이터 로드 실패")
        }
    }

    private fun parseJsonReport(obj: JsonObject): ConsensusReportEntity? {
        val rawDate = obj["작성일"]?.jsonPrimitive?.content ?: return null
        val writeDate = parseDate(rawDate) ?: return null
        val rawTitle = obj["제목"]?.jsonPrimitive?.content ?: ""
        val stockTicker = obj["종목코드"]?.jsonPrimitive?.content ?: ""
        if (stockTicker.isBlank()) return null

        // 종목명 추출 및 제목에서 "종목명(종목코드)" 제거
        val codeMatch = Regex("\\((\\d{6})\\)").find(rawTitle)
        val stockName = codeMatch?.let {
            rawTitle.substring(0, it.range.first).trim()
        } ?: ""
        val title = rawTitle.replace(Regex(".*\\(\\d{6}\\)\\s*"), "").trim()

        val targetPrice = parsePrice(obj["목표가"]?.jsonPrimitive?.content ?: "0")
        val currentPrice = parsePrice(obj["현재가"]?.jsonPrimitive?.content ?: "0")
        val divergenceRate = if (currentPrice > 0) {
            (targetPrice - currentPrice).toDouble() / currentPrice * 100.0
        } else {
            0.0
        }

        return ConsensusReportEntity(
            writeDate = writeDate,
            category = obj["분류"]?.jsonPrimitive?.content ?: "",
            prevOpinion = obj["이전의견"]?.jsonPrimitive?.content ?: "",
            opinion = obj["투자의견"]?.jsonPrimitive?.content ?: "",
            title = title,
            stockTicker = stockTicker,
            stockName = stockName,
            author = obj["작성자"]?.jsonPrimitive?.content ?: "",
            institution = obj["작성기관"]?.jsonPrimitive?.content ?: "",
            targetPrice = targetPrice,
            currentPrice = currentPrice,
            divergenceRate = divergenceRate
        )
    }

    private fun parseDate(dateStr: String): String? {
        val parts = dateStr.trim().split("/")
        if (parts.size != 3) return null
        val year = if (parts[0].length == 2) "20${parts[0]}" else parts[0]
        val month = parts[1].padStart(2, '0')
        val day = parts[2].padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun parsePrice(priceStr: String): Long {
        val cleaned = priceStr.replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "0") return 0L
        return cleaned.toLongOrNull() ?: 0L
    }

    private fun ConsensusReportEntity.toDomain() = ConsensusReport(
        writeDate = writeDate,
        category = category,
        prevOpinion = prevOpinion,
        opinion = opinion,
        title = title,
        stockTicker = stockTicker,
        stockName = stockName,
        author = author,
        institution = institution,
        targetPrice = targetPrice,
        currentPrice = currentPrice,
        divergenceRate = divergenceRate
    )
}
