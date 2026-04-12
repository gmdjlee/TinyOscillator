package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.FearGreedDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.entity.MarketDepositEntity
import com.tinyoscillator.core.database.entity.MarketOscillatorEntity
import com.tinyoscillator.core.scraper.EquityReportScraper
import com.tinyoscillator.core.scraper.FnGuideReportScraper
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.presentation.settings.loadEtfCollectionPeriod
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import com.tinyoscillator.presentation.settings.loadFearGreedCollectionPeriod
import com.tinyoscillator.presentation.settings.loadMarketDepositCollectionPeriod
import com.tinyoscillator.presentation.settings.loadMarketOscillatorCollectionPeriod
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@HiltWorker
class DataIntegrityCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val etfRepository: EtfRepository,
    private val marketIndicatorRepository: MarketIndicatorRepository,
    private val fearGreedDao: FearGreedDao,
    private val fearGreedRepository: com.tinyoscillator.data.repository.FearGreedRepository,
    private val oscillatorDao: MarketOscillatorDao,
    private val depositDao: MarketDepositDao,
    private val etfDao: EtfDao,
    private val consensusReportDao: ConsensusReportDao,
    private val equityReportScraper: EquityReportScraper,
    private val fnGuideReportScraper: FnGuideReportScraper
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "데이터 무결성 검사"
    override val notificationId = CollectionNotificationHelper.INTEGRITY_CHECK_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("데이터 무결성 검사 시작 (attempt: $runAttemptCount)")

        showInitialNotification("데이터 무결성 검사 준비 중...")

        val creds = loadKrxCredentials(applicationContext)
        val hasKrxCreds = creds.id.isNotBlank() && creds.password.isNotBlank()

        val results = mutableListOf<String>()
        var totalFixed = 0
        var totalChecked = 0

        // 1. ETF 데이터 무결성 검사
        if (hasKrxCreds) {
            updateProgress("ETF 데이터 검사 중...", STATUS_RUNNING, 0.05f)
            updateNotification("ETF 데이터 검사 중...", 5)

            val etfResult = checkEtfIntegrity(creds.id, creds.password)
            totalChecked++
            if (etfResult != null) {
                results.add(etfResult.first)
                totalFixed += etfResult.second
            }
        } else {
            results.add("ETF: KRX 자격증명 미설정 (건너뜀)")
        }

        // 2. Fear & Greed 데이터 무결성 검사
        if (hasKrxCreds) {
            updateProgress("Fear & Greed 데이터 검사 중...", STATUS_RUNNING, 0.20f)
            updateNotification("Fear & Greed 데이터 검사 중...", 20)

            val fgResult = checkFearGreedIntegrity(creds.id, creds.password)
            totalChecked++
            if (fgResult != null) {
                results.add(fgResult.first)
                totalFixed += fgResult.second
            }
        } else {
            results.add("Fear & Greed: KRX 자격증명 미설정 (건너뜀)")
        }

        // 3. 과매수/과매도 데이터 무결성 검사
        if (hasKrxCreds) {
            updateProgress("과매수/과매도 데이터 검사 중...", STATUS_RUNNING, 0.40f)
            updateNotification("과매수/과매도 데이터 검사 중...", 40)

            val oscResult = checkOscillatorIntegrity(creds.id, creds.password)
            totalChecked++
            if (oscResult != null) {
                results.add(oscResult.first)
                totalFixed += oscResult.second
            }
        } else {
            results.add("과매수/과매도: KRX 자격증명 미설정 (건너뜀)")
        }

        // 4. 자금 동향 데이터 무결성 검사
        updateProgress("자금 동향 데이터 검사 중...", STATUS_RUNNING, 0.55f)
        updateNotification("자금 동향 데이터 검사 중...", 55)

        val depositResult = checkDepositIntegrity()
        totalChecked++
        if (depositResult != null) {
            results.add(depositResult.first)
            totalFixed += depositResult.second
        }

        // 5. 리포트 데이터 무결성 검사
        updateProgress("리포트 데이터 검사 중...", STATUS_RUNNING, 0.75f)
        updateNotification("리포트 데이터 검사 중...", 75)

        val reportResult = checkReportIntegrity()
        totalChecked++
        if (reportResult != null) {
            results.add(reportResult.first)
            totalFixed += reportResult.second
        }

        val summary = if (totalFixed > 0) {
            "검사 완료: ${totalFixed}건 수정\n${results.joinToString("\n")}"
        } else {
            "검사 완료: 이상 없음\n${results.joinToString("\n")}"
        }

        Timber.i("데이터 무결성 검사 완료: $summary")
        updateProgress(summary, STATUS_SUCCESS, 1f)
        showCompletion(if (totalFixed > 0) "${totalFixed}건 데이터 수정 완료" else "모든 데이터 정상")
        saveLog(LABEL, STATUS_SUCCESS, summary)
        return Result.success()
    }

    private suspend fun checkEtfIntegrity(krxId: String, krxPassword: String): Pair<String, Int>? {
        return try {
            val keywords = loadEtfKeywordFilter(applicationContext)
            val period = loadEtfCollectionPeriod(applicationContext)
            val creds = com.tinyoscillator.domain.model.KrxCredentials(krxId, krxPassword)

            // 1단계: 불완전 데이터 감지 — holdings 건수가 비정상적으로 적은 pair 삭제
            val dates = getBusinessDates(period.daysBack)
            var purgedCount = 0
            if (dates.isNotEmpty()) {
                val countStrings = etfDao.getHoldingCountsByDates(dates)
                // 형식: "etf_ticker|date|count"
                val pairCounts = countStrings.mapNotNull { str ->
                    val parts = str.split("|")
                    if (parts.size == 3) Triple(parts[0], parts[1], parts[2].toIntOrNull() ?: 0) else null
                }

                // holdings 건수가 MIN_HOLDINGS_THRESHOLD 미만인 pair는 불완전 데이터로 간주
                val incompletePairs = pairCounts.filter { it.third in 1 until MIN_HOLDINGS_THRESHOLD }
                for ((ticker, date, count) in incompletePairs) {
                    Timber.w("ETF 불완전 데이터 감지: $ticker/$date — ${count}건 (최소 ${MIN_HOLDINGS_THRESHOLD}건 필요)")
                    etfDao.deleteHoldingsForEtfAndDate(ticker, date)
                    purgedCount++
                }
                if (purgedCount > 0) {
                    Timber.i("ETF 불완전 데이터 ${purgedCount}건 제거 → 재수집 대상으로 전환")
                }
            }

            // 2단계: updateData로 누락 + 방금 제거한 불완전 데이터 재수집
            var fixedCount = 0
            var errorMsg: String? = null

            etfRepository.updateData(
                creds = creds,
                keywords = keywords,
                daysBack = period.daysBack
            ).collect { progress ->
                when (progress) {
                    is EtfDataProgress.Success -> {
                        fixedCount = progress.holdingCount
                    }
                    is EtfDataProgress.Error -> {
                        errorMsg = progress.message
                    }
                    is EtfDataProgress.Loading -> {
                        updateProgress("ETF: ${progress.message}", STATUS_RUNNING,
                            0.05f + progress.progress * 0.25f)
                    }
                }
            }

            val totalFixed = purgedCount + fixedCount
            if (errorMsg != null) {
                Timber.w("ETF 무결성 검사 실패: $errorMsg")
                Pair("ETF: 검사 실패 ($errorMsg)", purgedCount)
            } else if (totalFixed > 0) {
                Pair("ETF: ${purgedCount}건 불완전 제거 + ${fixedCount}건 재수집", totalFixed)
            } else {
                Pair("ETF: 정상", 0)
            }
        } catch (e: Exception) {
            Timber.e(e, "ETF 무결성 검사 오류")
            Pair("ETF: 오류 (${e.message})", 0)
        }
    }

    private fun getBusinessDates(daysBack: Int): List<String> {
        val dates = mutableListOf<String>()
        var date = java.time.LocalDate.now()
        for (i in 0 until daysBack * 2) {
            if (dates.size >= daysBack) break
            if (i > 0) date = date.minusDays(1)
            val dayOfWeek = date.dayOfWeek
            if (dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                dates.add(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")))
            }
        }
        return dates
    }

    private suspend fun checkOscillatorIntegrity(krxId: String, krxPassword: String): Pair<String, Int>? {
        return try {
            val period = loadMarketOscillatorCollectionPeriod(applicationContext)
            var totalFixed = 0

            for (market in listOf("KOSPI", "KOSDAQ")) {
                // 기존 DB 데이터 조회
                val existingData = oscillatorDao.getRecentData(market, period.daysBack)
                val existingMap = existingData.associateBy { it.date }

                // 최신 데이터 가져오기
                val result = marketIndicatorRepository.updateMarketData(
                    market, krxId, krxPassword, period.daysBack
                )

                if (result.isSuccess) {
                    val updatedCount = result.getOrNull() ?: 0
                    // 새로 가져온 데이터 중 기존과 다른 것 카운트
                    val newData = oscillatorDao.getRecentData(market, period.daysBack)
                    val diffCount = newData.count { newItem ->
                        val oldItem = existingMap[newItem.date]
                        oldItem == null ||
                                abs(oldItem.indexValue - newItem.indexValue) > 0.01 ||
                                abs(oldItem.oscillator - newItem.oscillator) > 0.01
                    }
                    totalFixed += diffCount
                    Timber.d("$market 무결성: ${existingData.size}건 검사, ${diffCount}건 수정")
                } else {
                    Timber.w("$market 무결성 검사 실패: ${result.exceptionOrNull()?.message}")
                }

                if (market == "KOSPI") {
                    delay(KRX_RATE_LIMIT_MS)
                }
            }

            if (totalFixed > 0) {
                Pair("과매수/과매도: ${totalFixed}건 수정", totalFixed)
            } else {
                Pair("과매수/과매도: 정상", 0)
            }
        } catch (e: Exception) {
            Timber.e(e, "과매수/과매도 무결성 검사 오류")
            Pair("과매수/과매도: 오류 (${e.message})", 0)
        }
    }

    private suspend fun checkDepositIntegrity(): Pair<String, Int>? {
        return try {
            val period = loadMarketDepositCollectionPeriod(applicationContext)

            // 기존 DB 데이터 조회
            val existingData = depositDao.getAllList()
            val existingMap = existingData.associateBy { it.date }

            // 최신 데이터 가져오기 (캐시 TTL 무시하고 강제 스크래핑)
            val chartData = marketIndicatorRepository.getOrUpdateMarketData(
                daysBack = period.daysBack
            )

            if (chartData == null) {
                return Pair("자금 동향: 스크래핑 실패", 0)
            }

            // 업데이트 후 데이터 비교
            val updatedData = depositDao.getAllList()
            val diffCount = updatedData.count { newItem ->
                val oldItem = existingMap[newItem.date]
                oldItem == null ||
                        abs(oldItem.depositAmount - newItem.depositAmount) > 0.01 ||
                        abs(oldItem.depositChange - newItem.depositChange) > 0.01 ||
                        abs(oldItem.creditAmount - newItem.creditAmount) > 0.01 ||
                        abs(oldItem.creditChange - newItem.creditChange) > 0.01
            }

            if (diffCount > 0) {
                Pair("자금 동향: ${diffCount}건 수정", diffCount)
            } else {
                Pair("자금 동향: 정상", 0)
            }
        } catch (e: Exception) {
            Timber.e(e, "자금 동향 무결성 검사 오류")
            Pair("자금 동향: 오류 (${e.message})", 0)
        }
    }

    private suspend fun checkReportIntegrity(): Pair<String, Int>? {
        return try {
            val reportCount = consensusReportDao.getCount()
            if (reportCount == 0) {
                return Pair("리포트: 데이터 없음 (건너뜀)", 0)
            }

            val latestDate = consensusReportDao.getLatestDate()
                ?: return Pair("리포트: 최신 날짜 조회 실패", 0)

            // 최근 7일 범위 재수집하여 누락 데이터 확인
            val endDate = latestDate
            val startDate = LocalDate.parse(latestDate, DateTimeFormatter.ISO_LOCAL_DATE)
                .minusDays(REPORT_CHECK_DAYS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 기존 DB 데이터 조회
            val existingReports = consensusReportDao.getByDateRange(startDate, endDate)
            val existingKeys = existingReports.map { report ->
                "${report.stockTicker}|${report.writeDate}|${report.author}|${report.institution}"
            }.toSet()

            // 양쪽 소스에서 재수집
            val equityReports = try {
                equityReportScraper.collectReports(startDate, endDate)
            } catch (e: Exception) {
                Timber.w(e, "equity.co.kr 무결성 검사 수집 실패")
                emptyList()
            }

            val fnGuideReports = try {
                fnGuideReportScraper.collectReports(startDate, endDate)
            } catch (e: Exception) {
                Timber.w(e, "FnGuide 무결성 검사 수집 실패")
                emptyList()
            }

            val scrapedReports = (equityReports + fnGuideReports)
                .distinctBy { "${it.writeDate}|${it.stockName}|${it.institution}" }

            // 누락된 리포트 찾기
            val missingReports = scrapedReports.filter { report ->
                val key = "${report.stockTicker}|${report.writeDate}|${report.author}|${report.institution}"
                key !in existingKeys
            }

            if (missingReports.isNotEmpty()) {
                // 누락 데이터 삽입
                missingReports.chunked(500).forEach { batch ->
                    consensusReportDao.insertAll(batch)
                }
                Pair("리포트: ${missingReports.size}건 누락 데이터 보충", missingReports.size)
            } else {
                Pair("리포트: 정상 (${existingReports.size}건 검사)", 0)
            }
        } catch (e: Exception) {
            Timber.e(e, "리포트 무결성 검사 오류")
            Pair("리포트: 오류 (${e.message})", 0)
        }
    }

    private suspend fun checkFearGreedIntegrity(krxId: String, krxPassword: String): Pair<String, Int>? {
        return try {
            val period = loadFearGreedCollectionPeriod(applicationContext)

            for (market in listOf("KOSPI", "KOSDAQ")) {
                val existingCount = fearGreedDao.getCountByMarket(market)
                if (existingCount == 0) continue

                // 최신 데이터로 업데이트
                val result = fearGreedRepository.updateFearGreed(krxId, krxPassword)
                if (result.isFailure) {
                    Timber.w("Fear & Greed $market 무결성 검사 실패: ${result.exceptionOrNull()?.message}")
                }

                if (market == "KOSPI") delay(KRX_RATE_LIMIT_MS)
            }

            val totalCount = fearGreedDao.getCountByMarket("KOSPI") + fearGreedDao.getCountByMarket("KOSDAQ")
            Pair("Fear & Greed: 정상 (${totalCount}건)", 0)
        } catch (e: Exception) {
            Timber.e(e, "Fear & Greed 무결성 검사 오류")
            Pair("Fear & Greed: 오류 (${e.message})", 0)
        }
    }

    companion object {
        const val WORK_NAME = "data_integrity_check"
        const val TAG = "integrity_check"
        const val LABEL = "무결성 검사"
        private const val KRX_RATE_LIMIT_MS = 5000L
        private const val REPORT_CHECK_DAYS = 7L
        /** ETF 1종목당 최소 구성종목 수 — 이보다 적으면 불완전 데이터로 간주 */
        private const val MIN_HOLDINGS_THRESHOLD = 3
    }
}
