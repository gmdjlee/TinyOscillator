package com.tinyoscillator.core.scraper

import com.tinyoscillator.domain.model.MarketDepositChartData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Naver Finance 증시 자금 동향 데이터 스크래퍼
 */
class NaverFinanceScraper {

    companion object {
        private const val BASE_URL = "https://finance.naver.com/sise/sise_deposit.naver"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val REQUEST_DELAY_MS = 500L
        private const val TIMEOUT_SECONDS = 15L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * 증시 자금 동향 데이터 스크래핑
     */
    suspend fun scrapeDepositData(numPages: Int = 5): MarketDepositChartData? = withContext(Dispatchers.IO) {
        if (numPages <= 0) return@withContext null

        Timber.d("Scraping deposit data: $numPages pages")
        val allData = mutableListOf<DepositRecord>()

        for (page in 1..numPages) {
            try {
                val pageData = scrapePage(page)
                if (pageData.isNotEmpty()) {
                    allData.addAll(pageData)
                    Timber.d("Page $page: ${pageData.size} records")
                }
                if (page < numPages) delay(REQUEST_DELAY_MS)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Error scraping page $page")
            }
        }

        if (allData.isEmpty()) return@withContext null

        val dedupedData = allData.distinctBy { it.date }.sortedBy { it.date }

        MarketDepositChartData(
            dates = dedupedData.map { it.date },
            depositAmounts = dedupedData.map { it.depositAmount },
            depositChanges = dedupedData.map { it.depositChange },
            creditAmounts = dedupedData.map { it.creditAmount },
            creditChanges = dedupedData.map { it.creditChange }
        )
    }

    /**
     * 최신 데이터 수집 (1페이지만)
     */
    suspend fun getLatestData(): MarketDepositChartData? = scrapeDepositData(1)

    private fun scrapePage(page: Int): List<DepositRecord> {
        val url = "$BASE_URL?page=$page"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .header("Referer", "https://finance.naver.com/")
            .build()

        val html = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("HTTP ${response.code} for page $page")
                    return emptyList()
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            Timber.e(e, "HTTP request failed for page $page")
            return emptyList()
        }

        if (html == null) return emptyList()
        return parseHtml(html)
    }

    private fun parseHtml(html: String): List<DepositRecord> {
        val doc: Document = try {
            Jsoup.parse(html, "EUC-KR")
        } catch (e: Exception) {
            Timber.e(e, "HTML parsing failed")
            return emptyList()
        }

        val table = doc.select("table.type_1").firstOrNull() ?: return emptyList()
        val data = mutableListOf<DepositRecord>()
        val rows = table.select("tr")

        for (i in 2 until rows.size) {
            val cols = rows[i].select("td")
            if (cols.size < 5) continue

            val rawDate = cols[0].text().trim()
            if (rawDate.isEmpty()) continue

            val date = parseDate(rawDate) ?: continue

            data.add(
                DepositRecord(
                    date = date,
                    depositAmount = parseNumber(cols[1].text()),
                    depositChange = parseNumber(cols[2].text()),
                    creditAmount = parseNumber(cols[3].text()),
                    creditChange = parseNumber(cols[4].text())
                )
            )
        }

        return data
    }

    private fun parseDate(s: String): String? {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return null

        // YYYY-MM-DD
        if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') return trimmed

        // YYYY.MM.DD or YY.MM.DD
        if ('.' in trimmed) {
            val parts = trimmed.split('.')
            if (parts.size == 3) {
                var year = parts[0].trim()
                val month = parts[1].trim().padStart(2, '0')
                val day = parts[2].trim().padStart(2, '0')
                if (year.length == 2) year = "20$year"
                return "$year-$month-$day"
            }
        }

        // YYYYMMDD
        if (trimmed.length == 8 && trimmed.all { it.isDigit() }) {
            return "${trimmed.substring(0, 4)}-${trimmed.substring(4, 6)}-${trimmed.substring(6)}"
        }

        // YYYY/MM/DD
        if ('/' in trimmed) {
            val parts = trimmed.split('/')
            if (parts.size == 3) {
                var year = parts[0].trim()
                val month = parts[1].trim().padStart(2, '0')
                val day = parts[2].trim().padStart(2, '0')
                if (year.length == 2) year = "20$year"
                return "$year-$month-$day"
            }
        }

        Timber.w("Unparseable date format: $trimmed")
        return null
    }

    private fun parseNumber(text: String): Double {
        val cleaned = text.trim()
            .replace(",", "")
            .replace("억원", "")
            .replace("억", "")
            .trim()

        return if (cleaned.isEmpty() || cleaned == "-") {
            0.0
        } else {
            cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    private data class DepositRecord(
        val date: String,
        val depositAmount: Double,
        val depositChange: Double,
        val creditAmount: Double,
        val creditChange: Double
    )
}
