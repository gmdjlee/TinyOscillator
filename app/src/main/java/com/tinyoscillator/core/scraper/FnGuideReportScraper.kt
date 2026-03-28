package com.tinyoscillator.core.scraper

import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * FnGuide 리포트 요약 스크래퍼
 * - comp.fnguide.com에서 리포트 요약 데이터를 수집
 * - 세션 획득 후 날짜 범위를 월 단위로 분할하여 수집
 * - 투자의견 한국어 통일 및 제공처/작성자 자동 분리
 */
class FnGuideReportScraper(
    httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val MAIN_URL =
            "https://comp.fnguide.com/SVO2/ASP/SVD_Report_Summary.asp"
        private const val DATA_URL =
            "https://comp.fnguide.com/SVO2/ASP/SVD_Report_Summary_Data.asp"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val TIMEOUT_SECONDS = 15L
        private const val MIN_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 5_000L
        private const val DEFAULT_GICODE = "A005930" // 삼성전자 (세션 획득용)

        /** 투자의견 통일 매핑 */
        private val OPINION_MAP = mapOf(
            "BUY" to "매수", "Buy" to "매수", "buy" to "매수", "매수유지" to "매수",
            "HOLD" to "보유", "Hold" to "보유", "hold" to "보유",
            "NEUTRAL" to "중립", "Neutral" to "중립", "neutral" to "중립",
            "TRADING BUY" to "Trading BUY", "Trading Buy" to "Trading BUY",
            "OUTPERFORM" to "Outperform", "Outperform" to "Outperform",
            "MARKETPERFORM" to "Market Perform", "MarketPerform" to "Market Perform",
            "Not Rated" to "Not Rated", "NOT RATED" to "Not Rated",
            "not rated" to "Not Rated", "NR" to "Not Rated"
        )

        /** 제공처/작성자 분리용 패턴 */
        private val PROVIDER_RE = Regex(
            "^(.+(?:증권|큐더스|파인더|자산운용|연구소|리서치|협의회|인사이트))(.+)$"
        )
    }

    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(InMemoryCookieJar())
        .build()

    /**
     * 날짜 범위 내 리포트를 수집하여 반환.
     * @param startDate "yyyy-MM-dd" 형식
     * @param endDate "yyyy-MM-dd" 형식
     */
    suspend fun collectReports(startDate: String, endDate: String): List<ConsensusReportEntity> =
        withContext(Dispatchers.IO) {
            val allReports = mutableListOf<ConsensusReportEntity>()

            // 1. 세션 획득 (메인 페이지 방문)
            if (!initSession()) {
                Timber.w("FnGuide 세션 획득 실패")
                return@withContext emptyList()
            }

            delay(randomDelay())

            // 2. 월 단위로 분할하여 수집
            val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE
            val compactFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
            val start = LocalDate.parse(startDate, isoFmt)
            val end = LocalDate.parse(endDate, isoFmt)
            val monthlyRanges = generateMonthlyRanges(start, end)

            for ((idx, range) in monthlyRanges.withIndex()) {
                val fromStr = range.first.format(compactFmt)
                val toStr = range.second.format(compactFmt)

                Timber.d("FnGuide 수집 중: $fromStr ~ $toStr [${idx + 1}/${monthlyRanges.size}]")

                val html = fetchData(fromStr, toStr)
                if (html != null) {
                    val chunk = parseHtml(html)
                    allReports.addAll(chunk)
                    Timber.d("FnGuide: ${chunk.size}건 수집 ($fromStr ~ $toStr)")
                }

                // 마지막 청크가 아니면 딜레이
                if (idx < monthlyRanges.size - 1) {
                    delay(randomDelay())
                }
            }

            Timber.d("FnGuide 총 ${allReports.size}건 수집 ($startDate ~ $endDate)")
            allReports
        }

    private fun initSession(): Boolean {
        return try {
            val url = "$MAIN_URL?pGB=1&gicode=$DEFAULT_GICODE&cID=&MenuYn=Y&ReportGB=&NewMenuID=901&stkGb=701"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            Timber.e(e, "FnGuide 세션 획득 실패")
            false
        }
    }

    private fun fetchData(fromDate: String, toDate: String): String? {
        return try {
            val url = "$DATA_URL?fr_dt=$fromDate&to_dt=$toDate&stext=&check=all&sortOrd=5&sortAD=A"
            val mainReferer =
                "$MAIN_URL?pGB=1&gicode=$DEFAULT_GICODE&cID=&MenuYn=Y&ReportGB=&NewMenuID=901&stkGb=701"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", mainReferer)
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("FnGuide 데이터 조회 실패: ${response.code}")
                    return null
                }
                val html = response.body?.string()
                if (html.isNullOrBlank() || html.contains("오류")) {
                    Timber.w("FnGuide 빈 응답 또는 서버 오류")
                    return null
                }
                html
            }
        } catch (e: IOException) {
            Timber.e(e, "FnGuide 데이터 요청 실패")
            null
        }
    }

    internal fun parseHtml(html: String): List<ConsensusReportEntity> {
        val doc = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            Timber.e(e, "FnGuide HTML 파싱 실패")
            return emptyList()
        }

        val rows = doc.select("tr")
        val results = mutableListOf<ConsensusReportEntity>()

        for (row in rows) {
            val tds = row.select("td")
            if (tds.size < 6) continue

            // 일자
            val rawDate = tds[0].text().trim()
            val writeDate = normalizeDate(rawDate) ?: continue

            // 종목명 - 리포트 제목
            val cell1 = tds[1]
            val dl = cell1.selectFirst("dl")
            var stockName = ""
            var stockCode = ""
            var reportTitle = ""

            if (dl != null) {
                val dt = dl.selectFirst("dt")
                if (dt != null) {
                    val link = dt.selectFirst("a")
                    if (link != null) {
                        val linkText = link.text().trim()
                        val codeSpan = link.selectFirst("span.txt1")
                        if (codeSpan != null) {
                            stockCode = codeSpan.text().trim()
                            stockName = linkText.replace(stockCode, "").trim()
                        }
                    }
                    val titleSpan = dt.selectFirst("span.txt2")
                    if (titleSpan != null) {
                        reportTitle = titleSpan.text().trim().removePrefix("- ").trim()
                    }
                }
            } else {
                stockName = cell1.text().trim()
            }

            // 종목코드: A 접두사 제거, 6자리 zero-pad
            stockCode = normalizeCode(stockCode)
            if (stockCode.isBlank()) continue

            // 투자의견 통일
            val opinion = normalizeOpinion(tds[2].text().trim())

            // 목표주가 → 목표가
            val targetPrice = parsePrice(tds[3].text().trim())

            // 전일종가 → 현재가
            val currentPrice = parsePrice(tds[4].text().trim())

            // 제공처/작성자 분리
            val providerText = tds[5].text().trim()
            val (institution, author) = splitProviderWriter(providerText)

            val divergenceRate = if (currentPrice > 0) {
                (targetPrice - currentPrice).toDouble() / currentPrice * 100.0
            } else {
                0.0
            }

            results.add(
                ConsensusReportEntity(
                    writeDate = writeDate,
                    category = "",
                    prevOpinion = "",
                    opinion = opinion,
                    title = reportTitle,
                    stockTicker = stockCode,
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
     * 날짜 범위를 월 단위로 분할.
     */
    private fun generateMonthlyRanges(
        start: LocalDate,
        end: LocalDate
    ): List<Pair<LocalDate, LocalDate>> {
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
        var current = start

        while (!current.isAfter(end)) {
            val monthEnd = current.with(TemporalAdjusters.lastDayOfMonth())
            val chunkEnd = if (monthEnd.isBefore(end)) monthEnd else end
            ranges.add(current to chunkEnd)
            current = chunkEnd.plusDays(1)
        }

        return ranges
    }

    /**
     * 날짜를 yyyy-MM-dd 형식으로 통일.
     * 입력: "2026/03/23", "2026-03-23", "26/03/23" 등
     */
    internal fun normalizeDate(dateStr: String): String? {
        if (dateStr.isBlank()) return null
        val s = dateStr.trim().replace("-", "/").replace(".", "/")
        val parts = s.split("/")
        if (parts.size != 3) return null

        val year = if (parts[0].length == 2) "20${parts[0]}" else parts[0]
        val month = parts[1].padStart(2, '0')
        val day = parts[2].padStart(2, '0')
        return "$year-$month-$day"
    }

    /**
     * 종목코드를 6자리 zero-pad (A 접두사 제거).
     */
    internal fun normalizeCode(code: String): String {
        if (code.isBlank()) return ""
        val s = code.trim().removePrefix("A")
            .replace(Regex("[^0-9A-Za-z]"), "")
        return if (s.all { it.isDigit() }) s.padStart(6, '0') else s
    }

    internal fun normalizeOpinion(opinion: String): String {
        if (opinion.isBlank()) return ""
        return OPINION_MAP[opinion.trim()] ?: opinion.trim()
    }

    /**
     * '제공처/작성자' 문자열을 작성기관과 작성자로 분리.
     */
    internal fun splitProviderWriter(text: String): Pair<String, String> {
        if (text.isBlank()) return "" to ""
        val match = PROVIDER_RE.find(text)
        return if (match != null) {
            match.groupValues[1].trim() to match.groupValues[2].trim()
        } else {
            text to ""
        }
    }

    internal fun parsePrice(priceStr: String): Long {
        val cleaned = priceStr.replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "0") return 0L
        return cleaned.toLongOrNull() ?: 0L
    }

    private fun randomDelay(): Long {
        return MIN_DELAY_MS + Random.nextLong(MAX_DELAY_MS - MIN_DELAY_MS)
    }
}
