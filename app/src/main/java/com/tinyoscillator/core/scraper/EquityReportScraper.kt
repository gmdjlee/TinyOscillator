package com.tinyoscillator.core.scraper

import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import com.tinyoscillator.domain.model.ConsensusDataProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.random.Random

/**
 * equity.co.kr 종목 리포트 스크래퍼
 * - 날짜 기반 수집 (startDate ~ endDate)
 * - 페이지를 순회하며 날짜 범위 내 데이터만 수집
 * - 8~16초 랜덤 딜레이로 서버 부하 최소화
 */
class EquityReportScraper(
    httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val BASE_URL = "https://www.equity.co.kr/research/researchList.do"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val REFERER = "https://www.equity.co.kr/research/researchMain.do"
        private const val MIN_DELAY_MS = 8_000L
        private const val MAX_DELAY_MS = 16_000L
        private const val TIMEOUT_SECONDS = 20L
        private const val MAX_DATE_RANGE_DAYS = 30
        private const val MAX_RETRY = 3
    }

    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * 날짜 범위 내 리포트 수집.
     * @param startDate "YYYY-MM-DD" 형식
     * @param endDate "YYYY-MM-DD" 형식
     */
    fun scrapeReports(startDate: String, endDate: String): Flow<ConsensusDataProgress> = flow {
        require(startDate <= endDate) { "startDate must be <= endDate" }

        Timber.d("리포트 수집 시작: $startDate ~ $endDate")
        emit(ConsensusDataProgress.Loading("리포트 수집 준비 중...", 0f))

        val allReports = mutableListOf<ConsensusReportEntity>()
        var pageNo = 1
        var reachedBeforeStart = false

        while (!reachedBeforeStart) {
            val html = fetchPageWithRetry(pageNo)
            if (html == null) {
                Timber.w("페이지 $pageNo 수집 실패, 중단")
                break
            }

            val pageReports = parsePage(html)
            if (pageReports.isEmpty()) {
                Timber.d("페이지 $pageNo: 데이터 없음, 수집 종료")
                break
            }

            var allBeforeStart = true
            for (report in pageReports) {
                val reportDate = report.writeDate
                if (reportDate < startDate) {
                    // 시작일 이전 데이터 → 이 레코드는 건너뜀
                    continue
                }
                if (reportDate > endDate) {
                    // 종료일 이후 데이터 → 건너뜀 (더 최신 페이지)
                    allBeforeStart = false
                    continue
                }
                // 범위 내 데이터
                allBeforeStart = false
                allReports.add(report)
            }

            if (allBeforeStart) {
                reachedBeforeStart = true
                Timber.d("페이지 $pageNo: 모든 데이터가 시작일 이전, 수집 종료")
            }

            emit(ConsensusDataProgress.Loading(
                "페이지 $pageNo 수집 완료 (${allReports.size}건)",
                0f // 총 페이지 수를 모르므로 indeterminate
            ))

            if (!reachedBeforeStart) {
                pageNo++
                val delayMs = randomDelay()
                Timber.d("다음 페이지 대기: ${delayMs}ms")
                delay(delayMs)
            }
        }

        if (allReports.isEmpty()) {
            emit(ConsensusDataProgress.Success(0))
        } else {
            val deduped = allReports.distinctBy {
                Triple(it.stockTicker, it.writeDate, "${it.author}|${it.institution}")
            }
            emit(ConsensusDataProgress.Success(deduped.size))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 수집된 원시 데이터를 반환 (Repository에서 DB 저장에 사용)
     */
    suspend fun collectReports(startDate: String, endDate: String): List<ConsensusReportEntity> {
        val allReports = mutableListOf<ConsensusReportEntity>()
        var pageNo = 1

        while (true) {
            val html = fetchPageWithRetry(pageNo) ?: break
            val pageReports = parsePage(html)
            if (pageReports.isEmpty()) break

            var allBeforeStart = true
            for (report in pageReports) {
                when {
                    report.writeDate < startDate -> continue
                    report.writeDate > endDate -> { allBeforeStart = false; continue }
                    else -> { allBeforeStart = false; allReports.add(report) }
                }
            }

            if (allBeforeStart) break
            pageNo++
            delay(randomDelay())
        }

        return allReports.distinctBy {
            Triple(it.stockTicker, it.writeDate, "${it.author}|${it.institution}")
        }
    }

    private suspend fun fetchPageWithRetry(pageNo: Int): String? {
        for (attempt in 1..MAX_RETRY) {
            try {
                val body = FormBody.Builder()
                    .add("curPageNo", pageNo.toString())
                    .build()

                val request = Request.Builder()
                    .url(BASE_URL)
                    .post(body)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.e("HTTP ${response.code} for page $pageNo (attempt $attempt)")
                        if (attempt < MAX_RETRY) delay(3000)
                        return@use
                    }
                    val html = response.body?.string()
                    if (!html.isNullOrBlank()) return html
                }
            } catch (e: IOException) {
                Timber.e(e, "HTTP request failed for page $pageNo (attempt $attempt)")
                if (attempt < MAX_RETRY) delay(5000)
            }
        }
        return null
    }

    internal fun parsePage(html: String): List<ConsensusReportEntity> {
        val doc = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            Timber.e(e, "HTML parsing failed")
            return emptyList()
        }

        val table = doc.select("table.list").firstOrNull() ?: return emptyList()
        val tbody = table.select("tbody").firstOrNull() ?: return emptyList()
        val rows = tbody.select("tr")
        val results = mutableListOf<ConsensusReportEntity>()

        for (tr in rows) {
            val tds = tr.select("td")
            if (tds.size < 12) continue

            val rawDate = tds[0].text().trim()
            val writeDate = parseDate(rawDate) ?: continue

            val category = tds[1].text().trim()
            val prevOpinion = tds[2].text().trim()
            val opinion = tds[4].text().trim()

            val titleLink = tds[5].select("a").firstOrNull()
            val title = titleLink?.text()?.trim() ?: tds[5].text().trim()

            val codeMatch = Regex("\\((\\d{6})\\)").find(title)
            val stockTicker = codeMatch?.groupValues?.get(1) ?: ""
            if (stockTicker.isBlank()) continue

            // 종목명 추출 및 제목에서 "종목명(종목코드)" 제거
            val stockName = title.substring(0, codeMatch!!.range.first).trim()
            val cleanTitle = title.replace(Regex(".*\\(\\d{6}\\)\\s*"), "").trim()

            val author = tds[6].text().trim()
            val institution = tds[7].text().trim()
            val targetPrice = parsePrice(tds[9].text().trim())
            val currentPrice = parsePrice(tds[10].text().trim())

            val divergenceRate = if (currentPrice > 0) {
                (targetPrice - currentPrice).toDouble() / currentPrice * 100.0
            } else {
                0.0
            }

            results.add(
                ConsensusReportEntity(
                    writeDate = writeDate,
                    category = category,
                    prevOpinion = prevOpinion,
                    opinion = opinion,
                    title = cleanTitle,
                    stockTicker = stockTicker,
                    stockName = stockName,
                    author = author,
                    institution = institution,
                    targetPrice = targetPrice,
                    currentPrice = currentPrice,
                    divergenceRate = divergenceRate
                )
            )
        }

        return results
    }

    /**
     * "26/03/23" → "2026-03-23"
     */
    internal fun parseDate(dateStr: String): String? {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split("/")
        if (parts.size != 3) return null

        val year = parts[0].trim()
        val month = parts[1].trim().padStart(2, '0')
        val day = parts[2].trim().padStart(2, '0')

        val fullYear = if (year.length == 2) "20$year" else year
        return "$fullYear-$month-$day"
    }

    /**
     * "300,000" → 300000L, "0" or "" → 0L
     */
    internal fun parsePrice(priceStr: String): Long {
        val cleaned = priceStr.replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "0") return 0L
        return cleaned.toLongOrNull() ?: 0L
    }

    /**
     * 감마 분포 기반 랜덤 딜레이 (8~16초 범위)
     */
    private fun randomDelay(): Long {
        // 간단한 감마 분포 근사: 지수 분포 2개의 합
        val u1 = -ln(1.0 - Random.nextDouble())
        val u2 = -ln(1.0 - Random.nextDouble())
        val gamma = u1 + u2 // shape=2, scale=1

        // 0~1 범위로 정규화 후 MIN~MAX 사이로 매핑
        val normalized = (gamma / 6.0).coerceIn(0.0, 1.0)
        return MIN_DELAY_MS + ((MAX_DELAY_MS - MIN_DELAY_MS) * normalized).toLong()
    }
}
