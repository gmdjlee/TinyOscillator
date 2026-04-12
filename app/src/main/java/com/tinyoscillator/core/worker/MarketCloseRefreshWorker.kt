package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import com.tinyoscillator.presentation.settings.loadMarketDepositCollectionPeriod
import com.tinyoscillator.presentation.settings.loadMarketOscillatorCollectionPeriod
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 장 마감 데이터 교체 워커.
 *
 * 매일 19:00에 실행되어 당일 장중 데이터를 삭제하고
 * 장 마감 확정 데이터로 교체합니다.
 *
 * 대상: 종목분석, ETF분석, 시장지표(과매수/과매도, 자금동향)
 */
@HiltWorker
class MarketCloseRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository,
    private val marketIndicatorRepository: MarketIndicatorRepository,
    private val analysisCacheDao: AnalysisCacheDao,
    private val etfDao: EtfDao,
    private val oscillatorDao: MarketOscillatorDao,
    private val depositDao: MarketDepositDao,
    private val apiConfigProvider: ApiConfigProvider
) : BaseCollectionWorker(context, workerParams) {

    override val notificationTitle = "장 마감 데이터 교체"
    override val notificationId = CollectionNotificationHelper.MARKET_CLOSE_REFRESH_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        Timber.d("장 마감 데이터 교체 워커 시작 (attempt: $runAttemptCount)")

        // 평일 체크 (토/일이면 건너뜀)
        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            Timber.d("주말이므로 장 마감 교체 건너뜀: $dayOfWeek")
            return Result.success()
        }

        showInitialNotification("장 마감 데이터 교체 준비 중...")

        val todayStr = today.format(DateFormats.yyyyMMdd)
        val todayIso = today.toString() // yyyy-MM-dd
        val results = mutableListOf<String>()

        // 1. 종목분석 데이터 교체
        updateProgress("종목분석 데이터 교체 중...", STATUS_RUNNING, 0.1f)
        updateNotification("종목분석 데이터 교체 중...", 10)
        val stockResult = refreshStockAnalysis(todayStr)
        results.add(stockResult)

        // 2. ETF분석 데이터 교체
        updateProgress("ETF 데이터 교체 중...", STATUS_RUNNING, 0.3f)
        updateNotification("ETF 데이터 교체 중...", 30)
        val etfResult = refreshEtfHoldings(todayStr)
        results.add(etfResult)

        // 3. 시장지표 - 과매수/과매도 교체
        updateProgress("과매수/과매도 데이터 교체 중...", STATUS_RUNNING, 0.5f)
        updateNotification("과매수/과매도 데이터 교체 중...", 50)
        val oscillatorResult = refreshMarketOscillator(todayIso)
        results.add(oscillatorResult)

        // 4. 시장지표 - 자금동향 교체
        updateProgress("자금 동향 데이터 교체 중...", STATUS_RUNNING, 0.7f)
        updateNotification("자금 동향 데이터 교체 중...", 70)
        val depositResult = refreshMarketDeposit(todayIso)
        results.add(depositResult)

        val summary = results.joinToString(", ")
        val hasError = results.any { it.contains("실패") }
        val status = if (hasError) STATUS_ERROR else STATUS_SUCCESS
        Timber.d("장 마감 데이터 교체 완료: $summary")
        updateProgress("완료: $summary", status, 1f)
        showCompletion("장 마감 교체 완료: $summary", isError = hasError)
        saveLog(LABEL, status, summary)
        return Result.success()
    }

    /**
     * 종목분석: 당일 캐시 삭제 → 쿨다운 초기화 → API 재수집
     */
    private suspend fun refreshStockAnalysis(todayStr: String): String {
        return try {
            val tickers = analysisCacheDao.getTickersForDate(todayStr)
            if (tickers.isEmpty()) {
                Timber.d("종목분석: 당일 데이터 없음, 건너뜀")
                return "종목 0건"
            }

            // 당일 데이터 삭제
            analysisCacheDao.deleteByDate(todayStr)
            stockRepository.clearAllCooldowns()
            Timber.d("종목분석: 당일 데이터 삭제 완료 (${tickers.size}개 종목)")

            // 최대 MAX_STOCK_REFRESH 종목만 API 재수집
            val kiwoomConfig = apiConfigProvider.getKiwoomConfig()
            if (!kiwoomConfig.isValid()) {
                Timber.w("종목분석: Kiwoom API 키 미설정, 삭제만 수행")
                return "종목 ${tickers.size}건 삭제 (API키 없음)"
            }

            val refreshTargets = tickers.take(MAX_STOCK_REFRESH)
            var refreshed = 0
            for (ticker in refreshTargets) {
                try {
                    val startDate = LocalDate.now().minusDays(365).format(DateFormats.yyyyMMdd)
                    stockRepository.getDailyTradingData(ticker, startDate, todayStr, kiwoomConfig)
                    refreshed++
                    Timber.d("종목분석: $ticker 재수집 완료 ($refreshed/${refreshTargets.size})")
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w("종목분석: $ticker 재수집 실패: ${e.message}")
                }
            }

            "종목 ${refreshed}/${tickers.size}건"
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "종목분석 교체 실패")
            "종목 실패"
        }
    }

    /**
     * ETF분석: 당일 holdings 삭제 → KRX 재수집
     */
    private suspend fun refreshEtfHoldings(todayStr: String): String {
        return try {
            val creds = loadKrxCredentials(applicationContext)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                Timber.w("ETF: KRX 자격증명 미설정, 건너뜀")
                return "ETF 건너뜀"
            }

            // 당일 holdings 삭제
            etfDao.deleteHoldingsForDate(todayStr)
            Timber.d("ETF: 당일($todayStr) holdings 삭제 완료")

            // 재수집 (당일만, daysBack=1)
            val keywords = loadEtfKeywordFilter(applicationContext)
            var resultMsg = "ETF 재수집 중"
            etfRepository.updateData(creds, keywords, daysBack = 1).collect { progress ->
                when (progress) {
                    is EtfDataProgress.Success -> {
                        resultMsg = "ETF ${progress.holdingCount}건"
                    }
                    is EtfDataProgress.Error -> {
                        resultMsg = "ETF 실패"
                        Timber.w("ETF 재수집 실패: ${progress.message}")
                    }
                    is EtfDataProgress.Loading -> {
                        Timber.d("ETF 재수집 진행: ${progress.message}")
                    }
                }
            }
            resultMsg
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ETF 교체 실패")
            "ETF 실패"
        }
    }

    /**
     * 시장지표 과매수/과매도: 당일 삭제 → KRX 재수집
     */
    private suspend fun refreshMarketOscillator(todayIso: String): String {
        return try {
            val creds = loadKrxCredentials(applicationContext)
            if (creds.id.isBlank() || creds.password.isBlank()) {
                Timber.w("시장지표: KRX 자격증명 미설정, 건너뜀")
                return "지표 건너뜀"
            }

            // 당일 데이터 삭제
            oscillatorDao.deleteByDate(todayIso)
            Timber.d("시장지표: 당일($todayIso) oscillator 삭제 완료")

            val period = loadMarketOscillatorCollectionPeriod(applicationContext)

            // KOSPI 재수집
            val kospiResult = marketIndicatorRepository.updateMarketData(
                "KOSPI", creds.id, creds.password, period.daysBack
            )
            delay(KRX_RATE_LIMIT_MS)

            // KOSDAQ 재수집
            val kosdaqResult = marketIndicatorRepository.updateMarketData(
                "KOSDAQ", creds.id, creds.password, period.daysBack
            )

            val kospi = kospiResult.getOrNull() ?: 0
            val kosdaq = kosdaqResult.getOrNull() ?: 0
            "지표 KOSPI${kospi}+KOSDAQ${kosdaq}건"
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "시장지표 교체 실패")
            "지표 실패"
        }
    }

    /**
     * 자금 동향: 당일 삭제 → Naver 재스크래핑
     */
    private suspend fun refreshMarketDeposit(todayIso: String): String {
        return try {
            // 당일 데이터 삭제
            depositDao.deleteByDate(todayIso)
            Timber.d("자금동향: 당일($todayIso) deposit 삭제 완료")

            val period = loadMarketDepositCollectionPeriod(applicationContext)
            val chartData = marketIndicatorRepository.getOrUpdateMarketData(
                daysBack = period.daysBack
            )

            if (chartData != null) {
                "자금 ${chartData.dates.size}건"
            } else {
                "자금 실패"
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "자금동향 교체 실패")
            "자금 실패"
        }
    }

    companion object {
        const val WORK_NAME = "market_close_refresh_daily"
        const val MANUAL_WORK_NAME = "market_close_refresh_manual"
        const val TAG = "collection_market_close"
        const val LABEL = "장 마감 교체"
        private const val MAX_STOCK_REFRESH = 10
        private const val KRX_RATE_LIMIT_MS = 5000L
    }
}
