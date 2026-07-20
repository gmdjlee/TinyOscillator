package com.tinyoscillator.feature.bearsignal.data.repository

import com.krxkt.model.Market
import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalAutoCacheMapper
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalCountryReturnMapper
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalManualCountryReturnMapper
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalManualInputMapper
import com.tinyoscillator.feature.bearsignal.data.remote.CustomsTradeApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.FredApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.IndexDailyBar
import com.tinyoscillator.feature.bearsignal.data.remote.StooqCsvClient
import com.tinyoscillator.feature.bearsignal.data.remote.YahooChartApiClient
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.AutoMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexSource
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexSpec
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.model.ManualIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.CustomsTradeCalculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.GlobalIndexReturnCalculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.IpoEtfDirectionCalculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.Kospi2Calculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.RateGateInputCalculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.VolatilityStatsCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * BearSignal 자동 지표 Repository 구현 — KRX/관세청/FRED/ECOS/시세소스(Yahoo·Stooq) 수집 →
 * 순수 계산 → Room 캐시(오프라인 우선 폴백). TASK.md §4 데이터 소스 연동 명세, §1.2 하이브리드
 * 데이터 아키텍처.
 *
 * Phase 1: [refreshAutoInputs] — [A] 등급(KRX 신호2 통계 + 코스피 2사 비중), 전체 실패 시 캐시 폴백.
 * 신용잔고(`GATE_CREDIT`)도 이 경로에서 로컬 `market_deposits` 테이블(NaverFinance 02:00 일간
 * 스크랩, KOFIA 원천)을 읽어 best-effort로 upsert한다 — KRX 성패와 무관하게 먼저 수행.
 * Phase 2: [refreshExternalAutoInputs] — [B] 등급 스칼라(관세청 수출비중, FRED/ECOS 금리, IPO ETF
 * 방향)를 지표별 best-effort로 수집(개별 실패는 해당 지표만 캐시 유지). [refreshMarketReturns] —
 * 코스피(KRX) + 해외지수의 4기간 수익률을 지수별 best-effort로 수집.
 *
 * 해외지수·IPO ETF 시세는 [indexSourceProvider]가 돌려주는 사용자 선택 소스([GlobalIndexSource],
 * 기본 Yahoo)를 우선 조회하고, 실패(빈 응답·예외) 시 나머지 소스로 자동 폴백한다 — Stooq 안티봇
 * 차단(2026-07 QA) 대응.
 *
 * 기존 [com.tinyoscillator.data.repository.FearGreedRepository] 패턴(로그인 → 조회 → 계산 →
 * 캐시 저장 → finally에서 close)을 따른다.
 */
class BearSignalRepositoryImpl(
    private val bearSignalDao: BearSignalDao,
    private val krxApiClient: KrxApiClient,
    private val apiConfigProvider: ApiConfigProvider,
    private val customsTradeApiClient: CustomsTradeApiClient,
    private val fredApiClient: FredApiClient,
    private val bokEcosApiClient: BokEcosApiClient,
    private val stooqCsvClient: StooqCsvClient,
    private val yahooChartApiClient: YahooChartApiClient,
    private val marketDepositDao: MarketDepositDao,
    private val indexSourceProvider: suspend () -> GlobalIndexSource = { GlobalIndexSource.DEFAULT }
) : BearSignalRepository {

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val YM_FMT = DateTimeFormatter.ofPattern("yyyyMM")

        /** ~6개월 영업일(130) 확보를 위한 달력일 버퍼(주말·공휴일 포함) — 신호2 통계용 */
        private const val LOOKBACK_CALENDAR_DAYS = 200L

        /** 종가 131건 → 수익률 130건 (§3.2 "직전 6M") */
        private const val TARGET_TRADING_DAYS = 131

        /** ~13개월 영업일(252+α) 확보를 위한 달력일 버퍼 — 코스피 4기간(도표48) 수익률용 */
        private const val MARKET_RETURN_LOOKBACK_CALENDAR_DAYS = 400L

        /** 기존 KRX 연동 관례(500ms rate limit) */
        private const val KRX_CALL_DELAY_MS = 500L

        /** Renaissance IPO ETF — §3.3 `etf` 입력의 시세 소스별 티커 */
        private val IPO_ETF_TICKERS = mapOf(
            GlobalIndexSource.YAHOO to "IPO",
            GlobalIndexSource.STOOQ to "ipo.us"
        )

        /** ECOS 기준금리 방향 산출 — 최근 몇 개월을 조회할지(래그·결측 보정 여유분) */
        private const val ECOS_RATE_LOOKBACK_MONTHS = 3L

        /** 관세청 데이터 발표 랙 보정(전월 기준 조회) */
        private const val CUSTOMS_DATA_LAG_MONTHS = 1L

        /** ECOS 데이터 발표 랙 보정(전월 기준 조회, [com.tinyoscillator.data.engine.macro.BokEcosCollector]와 동일 관례) */
        private const val ECOS_DATA_LAG_MONTHS = 1L

        /**
         * 신용잔고 로컬 데이터([MarketDepositDao]) 허용 연령 — KOFIA 주간 발표 주기(7일) + 여유.
         * §4.5 `SuggestionField.CREDIT.maxAgeDays`(웹 제안 신선도)와 같은 근거지만 별개 파라미터
         * (§3 임계치 아님 — `bear_thresholds.json` 무관).
         */
        private const val CREDIT_DEPOSIT_MAX_AGE_DAYS = 10L

        /** 신용잔고 단위 변환 — `market_deposits.credit_amount`는 억원, §3.4 `credit` 입력은 조원 */
        private const val EOK_PER_JO = 10_000.0
    }

    // ── Phase 1: [A] 등급 자동 지표 ──────────────────────────────────────

    override fun observeAutoInputs(): Flow<AutoBearSignalInputs?> =
        bearSignalDao.observeAutoCache().map { BearSignalAutoCacheMapper.toDomain(it) }

    override suspend fun getCachedAutoInputs(): AutoBearSignalInputs? =
        BearSignalAutoCacheMapper.toDomain(bearSignalDao.getAutoCache())

    override suspend fun refreshAutoInputs(): Result<AutoBearSignalInputs> = withContext(Dispatchers.IO) {
        // 신용잔고는 로컬 테이블 조회뿐이라 KRX 성패와 무관 — 먼저 best-effort로 갱신한다.
        collectCreditFromDeposits(System.currentTimeMillis())
        // login→use→close 시퀀스를 client-level mutex로 직렬화 — 공유 싱글턴 KrxApiClient에서
        // 동시 워커(일/주간)·당겨새로고침의 close()가 사용 중 세션을 무효화하는 것을 방지(Phase 3-6).
        krxApiClient.sessionMutex.withLock {
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
            val previous = getCachedAutoInputs()
            val inputs = AutoBearSignalInputs(
                up3 = AutoIndicator(stats.up3, InputSource.AUTO, now),
                down3 = AutoIndicator(stats.down3, InputSource.AUTO, now),
                up4 = AutoIndicator(stats.up4, InputSource.AUTO, now),
                down4 = AutoIndicator(stats.down4, InputSource.AUTO, now),
                kospi2 = AutoIndicator(kospi2, InputSource.AUTO, now),
                // Phase 2 필드는 이 경로가 다루지 않는 범위 — 기존 캐시값을 그대로 보존한다.
                semi = previous?.semi,
                buffer = previous?.buffer,
                rate = previous?.rate,
                dir = previous?.dir,
                etf = previous?.etf
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
    }

    /**
     * 신용잔고 자동 수집 — 로컬 `market_deposits` 최신 행(NaverFinance 일간 스크랩, KOFIA 원천)을
     * 읽어 억원→조원 변환 후 `GATE_CREDIT` 캐시에 upsert한다(§3.4 `credit` 입력, §4 표 "v2 배치").
     *
     * - 최신 행이 없거나 날짜가 [CREDIT_DEPOSIT_MAX_AGE_DAYS]보다 오래되면 건너뛴다(기존 캐시 유지)
     *   — 스크랩 워커 미실행·장기 휴장 시 낡은 값이 §3.4 `scoreGate`에 흘러드는 것을 방지.
     * - §4.5 웹/LLM 제안 승인값과 같은 키를 쓰므로 일간 갱신이 승인값을 덮어쓸 수 있다 — 둘 다
     *   `source=AUTO`이고 로컬 데이터가 더 최신이므로 의도된 동작. MANUAL 오버라이드는 별도 키
     *   공간이라 영향 없다(§4.6 "MANUAL 불패",
     *   [com.tinyoscillator.feature.bearsignal.domain.usecase.MergeBearSignalInputsUseCase]).
     * - 실패는 이 지표만 건너뛴다(best-effort) — [refreshAutoInputs]의 KRX 수집과 격리.
     */
    private suspend fun collectCreditFromDeposits(now: Long) {
        try {
            val latest = marketDepositDao.getLatestDeposit()
            if (latest == null) {
                Timber.w("신용잔고 자동 수집 건너뜀 — market_deposits 데이터 없음(예탁금 워커 미실행?)")
                return
            }
            val depositDate = LocalDate.parse(latest.date)
            if (depositDate.isBefore(LocalDate.now().minusDays(CREDIT_DEPOSIT_MAX_AGE_DAYS))) {
                Timber.w("신용잔고 자동 수집 건너뜀 — 최신 데이터(${latest.date})가 허용 연령(${CREDIT_DEPOSIT_MAX_AGE_DAYS}일) 초과")
                return
            }
            val creditJo = latest.creditAmount / EOK_PER_JO
            bearSignalDao.upsertAll(listOf(BearSignalAutoCacheMapper.creditEntity(creditJo, now)))
            Timber.i("BearSignal 신용잔고 자동 수집 완료: ${latest.creditAmount}억(${latest.date}) → ${"%.2f".format(creditJo)}조")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "신용잔고 자동 수집 실패 — 기존 캐시 유지")
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

    // ── Phase 2: [B] 등급 스칼라 자동 지표 ────────────────────────────────

    override suspend fun refreshExternalAutoInputs(): Result<AutoBearSignalInputs> = withContext(Dispatchers.IO) {
        val base = getCachedAutoInputs()
            ?: return@withContext Result.failure(
                IllegalStateException("BearSignal [A] 등급 자동 지표가 아직 수집되지 않았습니다 — refreshAutoInputs 선행 필요")
            )

        val now = System.currentTimeMillis()
        // 각 collectX는 실제 수집 성공 시에만 non-null — 실패·키 미설정은 null(기존 캐시 유지)
        val (semi, buffer) = collectCustomsInputs(now)
        val rate = collectRateInput(now)
        val dir = collectDirInput(now)
        val etf = collectEtfInput(now)

        // 수집 성공한 B등급 키만 개별 upsert(Phase 3-7). 전체 엔티티 read-modify-write는 수집 중(30~60s
        // 창) 도착한 §4.5 승인값·워커 기록을 시작 시점 base의 stale 값으로 되덮는다. 개별 upsert로
        // 미수집 키를 건드리지 않아 그 사이 갱신을 보존한다(MANUAL은 별도 테이블이라 불변).
        val freshEntities = BearSignalAutoCacheMapper.externalEntities(semi, buffer, rate, dir, etf)
        if (freshEntities.isNotEmpty()) {
            bearSignalDao.upsertAll(freshEntities)
        }

        val merged = base.copy(
            semi = semi ?: base.semi,
            buffer = buffer ?: base.buffer,
            rate = rate ?: base.rate,
            dir = dir ?: base.dir,
            etf = etf ?: base.etf
        )
        Timber.i(
            "BearSignal [B] 외부 지표 수집 완료(신규만 저장): semi=${semi?.value} buffer=${buffer?.value} " +
                "rate=${rate?.value} dir=${dir?.value} etf=${etf?.value}"
        )
        Result.success(merged)
    }

    /** 관세청 무역통계 — semi(반도체 수출비중)·buffer(완충산업 건재) 동시 수집. 미수집 시 null(기존 캐시 유지) */
    private suspend fun collectCustomsInputs(
        now: Long
    ): Pair<AutoIndicator<Double>?, AutoIndicator<Boolean>?> {
        val apiKey = apiConfigProvider.getCustomsTradeApiKey()
        if (apiKey == null) {
            Timber.w("관세청 무역통계 API 키 미설정 — 기존 캐시 유지")
            return null to null
        }
        return try {
            // 관세청은 매월 15일경 전월 자료를 현행화 — 월초에는 전월 데이터가 아직 없을 수
            // 있어 한 달 더 이전으로 1회 폴백한다(빈 응답 기준, 최대 2회 시도).
            var endYm = YearMonth.now().minusMonths(CUSTOMS_DATA_LAG_MONTHS)
            var currentItems = customsTradeApiClient.fetchItemTrade(apiKey, endYm.format(YM_FMT), endYm.format(YM_FMT))
            if (currentItems.isEmpty()) {
                endYm = endYm.minusMonths(1)
                currentItems = customsTradeApiClient.fetchItemTrade(apiKey, endYm.format(YM_FMT), endYm.format(YM_FMT))
            }

            val priorYmStr = endYm.minusMonths(12).format(YM_FMT)
            val priorItems = customsTradeApiClient.fetchItemTrade(apiKey, priorYmStr, priorYmStr)

            val semiShare = CustomsTradeCalculator.computeSemiShare(currentItems)
            val bufferIntact = CustomsTradeCalculator.computeBufferIntact(currentItems, priorItems)

            val semiIndicator = semiShare?.let { AutoIndicator(it, InputSource.AUTO, now) }
            val bufferIndicator = bufferIntact?.let { AutoIndicator(it, InputSource.AUTO, now) }
            semiIndicator to bufferIndicator
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "관세청 무역통계 수집 실패 — 기존 캐시 유지")
            null to null
        }
    }

    /** FRED 연방기금금리 목표 상단 — §3.4 `rate` 입력. 미수집 시 null(기존 캐시 유지) */
    private suspend fun collectRateInput(now: Long): AutoIndicator<Double>? {
        val apiKey = apiConfigProvider.getFredApiKey()
        if (apiKey == null) {
            Timber.w("FRED API 키 미설정 — 기존 캐시 유지")
            return null
        }
        return try {
            val observation = fredApiClient.fetchLatestObservation(apiKey)
            observation?.let { AutoIndicator(it.value, InputSource.AUTO, now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "FRED 금리 수집 실패 — 기존 캐시 유지")
            null
        }
    }

    /** 한국은행 기준금리 방향(ECOS 재사용) — §3.4 `dir` 입력. 미수집 시 null(기존 캐시 유지) */
    private suspend fun collectDirInput(now: Long): AutoIndicator<String>? {
        val apiKey = apiConfigProvider.getEcosApiKey()
        if (apiKey == null) {
            Timber.w("ECOS API 키 미설정 — 기준금리 방향 기존 캐시 유지")
            return null
        }
        return try {
            val endYm = YearMonth.now().minusMonths(ECOS_DATA_LAG_MONTHS)
            val startYm = endYm.minusMonths(ECOS_RATE_LOOKBACK_MONTHS)
            val points = bokEcosApiClient.fetchSeries(apiKey, "base_rate", startYm.format(YM_FMT), endYm.format(YM_FMT))
            val sorted = points.sortedBy { it.time }
            if (sorted.size < 2) return null
            val latest = sorted.last().value
            val previous = sorted[sorted.size - 2].value
            val dirValue = RateGateInputCalculator.computeDirection(latest, previous)
            AutoIndicator(dirValue, InputSource.AUTO, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ECOS 기준금리 방향 수집 실패 — 기존 캐시 유지")
            null
        }
    }

    /** Renaissance IPO ETF(Yahoo·Stooq 폴백) 방향 — §3.3 `etf` 입력. 미수집 시 null(기존 캐시 유지) */
    private suspend fun collectEtfInput(now: Long): AutoIndicator<String>? {
        return try {
            val bars = fetchDailyClosesWithFallback("IPO ETF") { IPO_ETF_TICKERS[it] }
            val direction = IpoEtfDirectionCalculator.computeDirection(bars.map { it.close })
            direction?.let { AutoIndicator(it, InputSource.AUTO, now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IPO ETF 방향 수집 실패 — 기존 캐시 유지")
            null
        }
    }

    /**
     * 사용자 선택 소스([indexSourceProvider]) 우선으로 일별 종가를 조회하고, 실패(빈 응답·예외) 시
     * 나머지 소스로 자동 폴백한다. 모든 소스가 실패하면 빈 리스트.
     */
    private suspend fun fetchDailyClosesWithFallback(
        label: String,
        tickerFor: (GlobalIndexSource) -> String?
    ): List<IndexDailyBar> {
        val preferred = indexSourceProvider()
        val order = listOf(preferred) + GlobalIndexSource.entries.filter { it != preferred }
        for (source in order) {
            val ticker = tickerFor(source) ?: continue
            val bars = try {
                when (source) {
                    GlobalIndexSource.YAHOO -> yahooChartApiClient.fetchDailyCloses(ticker)
                    GlobalIndexSource.STOOQ -> stooqCsvClient.fetchDailyCloses(ticker)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "%s 시세 조회 실패 (%s:%s) — 다음 소스 폴백", label, source.name, ticker)
                emptyList()
            }
            if (bars.isNotEmpty()) return bars
            Timber.w("%s 시세 응답 없음 (%s:%s) — 다음 소스 폴백", label, source.name, ticker)
        }
        return emptyList()
    }

    // ── Phase 2: 국가별 지수 수익률(도표48) ────────────────────────────────

    override fun observeMarketReturns(): Flow<MarketReturnsSnapshot?> =
        bearSignalDao.observeCountryReturns().map { BearSignalCountryReturnMapper.toDomain(it) }

    override suspend fun getCachedMarketReturns(): MarketReturnsSnapshot? =
        BearSignalCountryReturnMapper.toDomain(bearSignalDao.getCountryReturns())

    override suspend fun refreshMarketReturns(): Result<MarketReturnsSnapshot> = withContext(Dispatchers.IO) {
        val cachedSnapshot = getCachedMarketReturns()
        val now = System.currentTimeMillis()

        // login→use→close 시퀀스를 client-level mutex로 직렬화 — 공유 싱글턴에서 동시 워커/새로고침의
        // close()가 사용 중 세션을 무효화하는 것을 방지(Phase 3-6, refreshAutoInputs와 동일 관례).
        val kospiMarket = krxApiClient.sessionMutex.withLock {
            try {
            val creds = apiConfigProvider.getKrxCredentials()
            if (creds.id.isBlank() || creds.password.isBlank()) {
                throw IllegalStateException("KRX 계정 정보가 설정되지 않았습니다")
            }
            val loggedIn = krxApiClient.login(creds.id, creds.password)
            if (!loggedIn) throw IllegalStateException("KRX 로그인 실패")
            val krxIndex = krxApiClient.getKrxIndex() ?: throw IllegalStateException("KRX 인덱스 클라이언트 없음")

            val endDate = LocalDate.now().format(DATE_FMT)
            val startDate = LocalDate.now().minusDays(MARKET_RETURN_LOOKBACK_CALENDAR_DAYS).format(DATE_FMT)
            val ohlcv = krxIndex.getKospi(startDate, endDate)
            val closes = ohlcv.sortedBy { it.date }.map { it.close }
            val returns = GlobalIndexReturnCalculator.computeReturns(closes)
            if (returns.all { it == null }) {
                throw IllegalStateException("코스피 데이터 부족(${closes.size}건) — 4기간 수익률 계산 불가")
            }
            AutoMarketReturn(
                name = GlobalIndexRegistry.KOSPI_NAME,
                r = returns,
                lead = true,
                coverage = MarketCoverage.AUTO,
                updatedAt = now
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "코스피 4기간 수익률 수집 실패 — 기존 캐시 유지")
            cachedSnapshot?.markets?.find { it.name == GlobalIndexRegistry.KOSPI_NAME }
                ?: return@withContext Result.failure(e)
        } finally {
            try {
                krxApiClient.close()
            } catch (e: Exception) {
                Timber.w(e, "KRX 클라이언트 close 실패")
            }
        }
        }

        val overseasMarkets = GlobalIndexRegistry.OVERSEAS_MARKETS.map { spec ->
            collectOverseasMarket(spec, cachedSnapshot, now)
        }

        val snapshot = MarketReturnsSnapshot(markets = listOf(kospiMarket) + overseasMarkets)
        bearSignalDao.upsertCountryReturns(BearSignalCountryReturnMapper.toEntities(snapshot))
        Timber.i("BearSignal 국가별 수익률 수집 완료: manualRequired=${snapshot.manualRequiredNames}")
        Result.success(snapshot)
    }

    private suspend fun collectOverseasMarket(
        spec: GlobalIndexSpec,
        cachedSnapshot: MarketReturnsSnapshot?,
        now: Long
    ): AutoMarketReturn {
        if (!spec.autoCovered) {
            return AutoMarketReturn(
                spec.name, listOf(null, null, null, null), spec.lead, MarketCoverage.MANUAL_REQUIRED, now
            )
        }
        return try {
            val bars = fetchDailyClosesWithFallback(spec.name) { spec.tickerFor(it) }
            val returns = GlobalIndexReturnCalculator.computeReturns(bars.map { it.close })
            if (returns.all { it == null }) {
                throw IllegalStateException("${spec.name} 데이터 부족(${bars.size}건) — 4기간 수익률 계산 불가")
            }
            AutoMarketReturn(spec.name, returns, spec.lead, MarketCoverage.AUTO, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 유효한 캐시(값이 하나라도 있는 행)가 없으면 MANUAL_REQUIRED로 강등 —
            // coverage=AUTO+r=null이면 UI가 "수동 필요" 배지를 못 띄워 사용자가 수동 입력을
            // 안내받지 못한다(§5.3 MANUAL 요청 플래그).
            Timber.w(e, "해외지수(%s) 수집 실패 — 기존 캐시 유지, 유효 캐시 없으면 수동 강등", spec.name)
            cachedSnapshot?.markets?.find { it.name == spec.name }?.takeIf { m -> m.r.any { it != null } }
                ?: AutoMarketReturn(spec.name, listOf(null, null, null, null), spec.lead, MarketCoverage.MANUAL_REQUIRED, now)
        }
    }

    // ── Phase 3: 수동 오버라이드([C]/[D] 등급) ──────────────────────────

    override fun observeManualInputs(): Flow<ManualBearSignalInputs> =
        bearSignalDao.observeManualInputs().map { BearSignalManualInputMapper.toDomain(it) }

    override suspend fun getManualInputs(): ManualBearSignalInputs =
        BearSignalManualInputMapper.toDomain(bearSignalDao.getManualInputs())

    override suspend fun updateManualInput(update: ManualFieldUpdate) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        when (update) {
            is ManualFieldUpdate.Loss -> upsertManualScalar(ManualIndicatorKey.LOSS, update.value, now)
            is ManualFieldUpdate.Big -> upsertManualScalar(
                ManualIndicatorKey.BIG,
                BearSignalManualInputMapper.encodeBig(update.value),
                now
            )
            is ManualFieldUpdate.IssueRatio -> upsertManualScalar(ManualIndicatorKey.ISSUE_RATIO, update.value, now)
            is ManualFieldUpdate.Credit -> upsertManualScalar(ManualIndicatorKey.CREDIT, update.value, now)
            is ManualFieldUpdate.Margin -> upsertManualScalar(
                ManualIndicatorKey.MARGIN,
                BearSignalManualInputMapper.encodeBoolean(update.value),
                now
            )
            is ManualFieldUpdate.Dir -> upsertManualScalar(
                ManualIndicatorKey.DIR,
                BearSignalManualInputMapper.encodeDir(update.value),
                now
            )
            is ManualFieldUpdate.MarketReturn -> bearSignalDao.upsertManualCountryReturn(
                BearSignalManualCountryReturnMapper.toEntity(ManualMarketReturn(update.name, update.r, now))
            )
        }
        Timber.i("BearSignal 수동 입력 반영: $update")
    }

    private suspend fun upsertManualScalar(key: ManualIndicatorKey, value: Double, updatedAt: Long) {
        bearSignalDao.upsertManualInput(BearSignalManualInputMapper.toEntity(key, value, updatedAt))
    }

    override fun observeManualMarketReturns(): Flow<List<ManualMarketReturn>> =
        bearSignalDao.observeManualCountryReturns().map { BearSignalManualCountryReturnMapper.toDomain(it) }

    override suspend fun getManualMarketReturns(): List<ManualMarketReturn> =
        BearSignalManualCountryReturnMapper.toDomain(bearSignalDao.getManualCountryReturns())

    override suspend fun resetToReportBaseline() = withContext(Dispatchers.IO) {
        bearSignalDao.clearManualInputs()
        bearSignalDao.clearManualCountryReturns()
        Timber.i("BearSignal 수동 오버라이드 리셋 완료 — 리포트 기준값(${BearSignalReportBaseline.REPORT_DATE})으로 복귀")
    }

    // ── Phase 4: §4.5 웹/LLM 제안 승인 ──────────────────────────────────

    override suspend fun applySuggestion(
        field: SuggestionField,
        value: String,
        updatedAt: Long
    ) = withContext(Dispatchers.IO) {
        val entity = BearSignalAutoCacheMapper.suggestionEntity(field, value, updatedAt)
        bearSignalDao.upsertAll(listOf(entity))
        Timber.i("BearSignal §4.5 제안 승인 반영: field=${field.name} value=$value")
    }
}
