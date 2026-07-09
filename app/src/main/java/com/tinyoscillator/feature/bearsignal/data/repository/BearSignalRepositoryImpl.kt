package com.tinyoscillator.feature.bearsignal.data.repository

import com.krxkt.model.Market
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalAutoCacheMapper
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.Kospi2Calculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.VolatilityStatsCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * BearSignal [A] 등급 자동 지표 Repository 구현 — KRX 수집 → 순수 계산 → Room 캐시(오프라인 우선
 * 폴백). TASK.md §4 데이터 소스 연동 명세, §1.2 하이브리드 데이터 아키텍처.
 *
 * 기존 [com.tinyoscillator.data.repository.FearGreedRepository] 패턴(로그인 → 조회 → 계산 →
 * 캐시 저장 → finally에서 close)을 따른다.
 */
class BearSignalRepositoryImpl(
    private val bearSignalDao: BearSignalDao,
    private val krxApiClient: KrxApiClient,
    private val apiConfigProvider: ApiConfigProvider
) : BearSignalRepository {

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")

        /** ~6개월 영업일(130) 확보를 위한 달력일 버퍼(주말·공휴일 포함) */
        private const val LOOKBACK_CALENDAR_DAYS = 200L

        /** 종가 131건 → 수익률 130건 (§3.2 "직전 6M") */
        private const val TARGET_TRADING_DAYS = 131

        /** 기존 KRX 연동 관례(500ms rate limit) */
        private const val KRX_CALL_DELAY_MS = 500L
    }

    override fun observeAutoInputs(): Flow<AutoBearSignalInputs?> =
        bearSignalDao.observeAutoCache().map { BearSignalAutoCacheMapper.toDomain(it) }

    override suspend fun getCachedAutoInputs(): AutoBearSignalInputs? =
        BearSignalAutoCacheMapper.toDomain(bearSignalDao.getAutoCache())

    override suspend fun refreshAutoInputs(): Result<AutoBearSignalInputs> = withContext(Dispatchers.IO) {
        try {
            val creds = apiConfigProvider.getKrxCredentials()
            if (creds.id.isBlank() || creds.password.isBlank()) {
                return@withContext fallbackOrFailure(IllegalStateException("KRX 계정 정보가 설정되지 않았습니다"))
            }

            val loggedIn = krxApiClient.login(creds.id, creds.password)
            if (!loggedIn) {
                return@withContext fallbackOrFailure(IllegalStateException("KRX 로그인 실패"))
            }

            val krxIndex = krxApiClient.getKrxIndex()
                ?: return@withContext fallbackOrFailure(IllegalStateException("KRX 인덱스 클라이언트 없음"))
            val krxStock = krxApiClient.getKrxStock()
                ?: return@withContext fallbackOrFailure(IllegalStateException("KRX 종목 클라이언트 없음"))

            val endDate = LocalDate.now().format(DATE_FMT)
            val startDate = LocalDate.now().minusDays(LOOKBACK_CALENDAR_DAYS).format(DATE_FMT)

            // 신호2: 코스피(ticker "1001") 일별 종가 → ±3σ/±4σ 통계 (§3.2)
            val kospiOhlcv = krxIndex.getKospi(startDate, endDate)
            val closes = kospiOhlcv.sortedBy { it.date }.map { it.close }
            val windowedCloses = if (closes.size > TARGET_TRADING_DAYS) closes.takeLast(TARGET_TRADING_DAYS) else closes

            val stats = VolatilityStatsCalculator.compute(windowedCloses)
                ?: return@withContext fallbackOrFailure(
                    IllegalStateException("코스피 데이터 부족(${windowedCloses.size}건) — ±σ 계산 불가")
                )

            delay(KRX_CALL_DELAY_MS)

            // kospi2: 코스피 전종목 시가총액 → 삼성전자+SK하이닉스 비중 (§3.5)
            val marketCaps = krxStock.getMarketCap(endDate, Market.KOSPI)
            val marketCapByTicker = marketCaps.associate { it.ticker to it.marketCap }
            val kospi2 = Kospi2Calculator.compute(marketCapByTicker)
                ?: return@withContext fallbackOrFailure(
                    IllegalStateException("코스피 2사(삼성전자/SK하이닉스) 시가총액 데이터 없음")
                )

            val now = System.currentTimeMillis()
            val inputs = AutoBearSignalInputs(
                up3 = AutoIndicator(stats.up3, InputSource.AUTO, now),
                down3 = AutoIndicator(stats.down3, InputSource.AUTO, now),
                up4 = AutoIndicator(stats.up4, InputSource.AUTO, now),
                down4 = AutoIndicator(stats.down4, InputSource.AUTO, now),
                kospi2 = AutoIndicator(kospi2, InputSource.AUTO, now)
            )

            bearSignalDao.upsertAll(BearSignalAutoCacheMapper.toEntities(inputs))
            Timber.i(
                "BearSignal 자동 지표 수집 완료: up3=${stats.up3} down3=${stats.down3} " +
                    "up4=${stats.up4} down4=${stats.down4} kospi2=$kospi2"
            )
            Result.success(inputs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "BearSignal 자동 지표 수집 실패 — 캐시 폴백 시도")
            fallbackOrFailure(e)
        } finally {
            try {
                krxApiClient.close()
            } catch (e: Exception) {
                Timber.w(e, "KRX 클라이언트 close 실패")
            }
        }
    }

    /** 수집 실패 시 기존 캐시가 있으면 성공으로 폴백(오프라인 우선), 없으면 실패를 그대로 전달 */
    private suspend fun fallbackOrFailure(cause: Exception): Result<AutoBearSignalInputs> {
        val cached = getCachedAutoInputs()
        return if (cached != null) {
            Timber.w("BearSignal 자동 지표 캐시로 폴백: ${cause.message}")
            Result.success(cached)
        } else {
            Result.failure(cause)
        }
    }
}
