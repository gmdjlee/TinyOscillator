package com.tinyoscillator.data.engine

import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.MacroDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.FactorBetas
import com.tinyoscillator.domain.model.Korea5FactorResult
import com.tinyoscillator.domain.model.MonthlyFactorRow
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 한국형 5팩터 모델 엔진 (10번째 통계 엔진)
 *
 * 한국 시장에 맞춘 간이 Fama-French 5팩터 모델:
 * 1. MKT_excess: KOSPI 월간수익률 − BOK 기준금리(연율→월율)
 * 2. SMB: 시가총액 기반 소형주 프리미엄 (종목 특성 프록시)
 * 3. HML: PBR 역수 기반 가치주 프리미엄 (종목 특성 프록시)
 * 4. RMW: 수익성 (EPS/BPS → ROE 프록시)
 * 5. CMA: 투자보수성 (자산 성장률 프록시)
 *
 * OLS 회귀로 rolling alpha(팩터 조정 초과 수익률)를 산출하고,
 * alpha의 z-score를 시그모이드 변환하여 [0,1] 신호를 생성.
 */
@Singleton
class Korea5FactorEngine @Inject constructor(
    private val regimeDao: RegimeDao,
    private val macroDao: MacroDao,
    private val fundamentalCacheDao: FundamentalCacheDao
) {

    companion object {
        /** 최소 회귀 관측치 (이보다 적으면 결과 생성 불가) */
        const val MIN_OBS = 24
        /** 기본 롤링 윈도우 (개월) */
        const val DEFAULT_WINDOW = 36
        /** 롤링 스텝 (개월) */
        const val STEP = 3
        /** z-score 산출용 최소 alpha 이력 */
        const val MIN_ALPHA_HISTORY = 6
        /** z-score 시그모이드 스케일링 팩터 */
        private const val SIGMOID_SCALE = 2.0

        private val YM_FMT = DateTimeFormatter.ofPattern("yyyyMM")
        private val DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE
    }

    /**
     * 5팩터 분석 실행
     *
     * @param prices 종목 일간 거래 데이터 (최소 252*3일 권장)
     * @param stockCode 종목 코드
     * @return Korea5FactorResult (데이터 부족 시 unavailableReason 포함)
     */
    suspend fun analyze(
        prices: List<DailyTrading>,
        stockCode: String
    ): Korea5FactorResult {
        // 1. 종목 월간 수익률 산출
        val stockMonthlyRet = computeMonthlyReturns(prices)
        if (stockMonthlyRet.size < MIN_OBS) {
            return unavailable("종목 월간 수익률 ${stockMonthlyRet.size}개 < 최소 $MIN_OBS 개월")
        }

        // 2. 팩터 데이터 구축
        val factorRows = buildFactorData(prices, stockCode)
        if (factorRows.size < MIN_OBS) {
            return unavailable("팩터 데이터 ${factorRows.size}개 < 최소 $MIN_OBS 개월")
        }

        // 3. 무위험 월간 수익률 (BOK 기준금리)
        val rfMonthly = getRiskFreeMonthly()

        // 4. 공통 기간 정렬
        val commonMonths = stockMonthlyRet.keys
            .intersect(factorRows.associate { it.yearMonth to it }.keys)
            .sorted()

        if (commonMonths.size < MIN_OBS) {
            return unavailable("공통 기간 ${commonMonths.size}개 < 최소 $MIN_OBS 개월")
        }

        val factorMap = factorRows.associateBy { it.yearMonth }

        // 5. 롤링 알파 산출
        val alphaHistory = rollingAlpha(
            stockMonthlyRet = stockMonthlyRet,
            factorMap = factorMap,
            rfMonthly = rfMonthly,
            sortedMonths = commonMonths.toList(),
            window = DEFAULT_WINDOW,
            step = STEP
        )

        if (alphaHistory.isEmpty()) {
            return unavailable("롤링 알파 산출 불가 (관측치 부족)")
        }

        // 6. 최종 윈도우 OLS (최근 alpha + betas)
        val lastWindowMonths = commonMonths.toList().takeLast(DEFAULT_WINDOW.coerceAtMost(commonMonths.size))
        val finalFit = estimateBetas(
            stockMonthlyRet = stockMonthlyRet,
            factorMap = factorMap,
            rfMonthly = rfMonthly,
            months = lastWindowMonths
        ) ?: return unavailable("OLS 회귀 실패 (관측치 부족 또는 특이행렬)")
        val (betas, _, rSq) = finalFit

        // 7. z-score & signal
        val latestAlpha = alphaHistory.last().second
        val alphaZscore = if (alphaHistory.size >= MIN_ALPHA_HISTORY) {
            val recent = alphaHistory.takeLast(12.coerceAtMost(alphaHistory.size))
            val mean = recent.map { it.second }.average()
            val std = stddev(recent.map { it.second })
            if (std > 1e-10) (latestAlpha - mean) / std else 0.0
        } else {
            0.0
        }

        val signalScore = sigmoid(alphaZscore * SIGMOID_SCALE)

        return Korea5FactorResult(
            alphaRaw = latestAlpha,
            alphaZscore = alphaZscore,
            signalScore = signalScore,
            nObs = lastWindowMonths.size,
            lastDate = commonMonths.last(),
            betas = betas,
            rSquared = rSq,
            windowMonths = DEFAULT_WINDOW
        )
    }

    // ─── 팩터 구축 ───

    /**
     * 종목 데이터 + KOSPI + 매크로에서 월별 팩터 수익률 구축
     *
     * 실제 크로스섹션 팩터 포트폴리오 대신, 종목 특성 기반 프록시를 사용:
     * - MKT_excess: KOSPI 월간수익률 − RF
     * - SMB: 시가총액 역순위 기반 프록시 (소형주일수록 양수)
     * - HML: 1/PBR 변동 기반 가치 프록시
     * - RMW: EPS/BPS (ROE 프록시) 변동
     * - CMA: BPS 성장률 역수 (보수적 투자일수록 양수)
     */
    private suspend fun buildFactorData(
        prices: List<DailyTrading>,
        stockCode: String
    ): List<MonthlyFactorRow> {
        // KOSPI 월간 수익률
        val kospiDaily = regimeDao.getAllKospiIndex()
        if (kospiDaily.isEmpty()) return emptyList()

        val kospiMonthly = computeMonthlyReturnsFromKospi(kospiDaily)

        // RF 월간
        val rfMonthly = getRiskFreeMonthly()

        // 펀더멘탈 데이터 (PBR, EPS, BPS) — 최근 48개월치
        val fundamentals = fundamentalCacheDao.getRecentByTicker(stockCode, 48)
        val fundMap = fundamentals.associateBy { it.date.take(6) } // yyyyMM key

        // 종목 시가총액 월간
        val mcapMonthly = computeMonthlyMarketCap(prices)

        val rows = mutableListOf<MonthlyFactorRow>()

        for ((ym, kospiRet) in kospiMonthly) {
            val rf = rfMonthly[ym] ?: rfMonthly.values.lastOrNull() ?: 0.0
            val mktExcess = kospiRet - rf

            // SMB: 시가총액 역수 표준화 프록시 (소형주 프리미엄)
            val mcap = mcapMonthly[ym]
            val smb = if (mcap != null && mcap > 0) {
                // 로그 시가총액 역순: 작을수록 양수 (중간값 대비)
                val logMcap = kotlin.math.ln(mcap.toDouble())
                // 한국 시장 평균 로그 시가총액 ~27 (1조원 수준)
                ((27.0 - logMcap) / 10.0).coerceIn(-0.05, 0.05)
            } else 0.0

            // HML: PBR 역수 기반 가치 프록시
            val fund = fundMap[ym]
            val prevYm = YearMonth.parse(ym, YM_FMT).minusMonths(1).format(YM_FMT)
            val prevFund = fundMap[prevYm]
            val hml = if (fund != null && fund.pbr > 0) {
                val bookToPrice = 1.0 / fund.pbr
                val prevBtp = if (prevFund != null && prevFund.pbr > 0) 1.0 / prevFund.pbr else bookToPrice
                ((bookToPrice - prevBtp) / prevBtp.coerceAtLeast(0.01)).coerceIn(-0.05, 0.05)
            } else 0.0

            // RMW: ROE 프록시 (EPS / BPS)
            val rmw = if (fund != null && fund.bps > 0) {
                val roe = fund.eps.toDouble() / fund.bps.toDouble()
                val prevRoe = if (prevFund != null && prevFund.bps > 0) {
                    prevFund.eps.toDouble() / prevFund.bps.toDouble()
                } else roe
                ((roe - prevRoe) * 10.0).coerceIn(-0.05, 0.05)  // 변동 스케일링
            } else 0.0

            // CMA: BPS 성장률 역수 (보수적 투자 = 낮은 자산 성장)
            val cma = if (fund != null && prevFund != null && prevFund.bps > 0) {
                val assetGrowth = (fund.bps.toDouble() - prevFund.bps.toDouble()) / prevFund.bps.toDouble()
                (-assetGrowth).coerceIn(-0.05, 0.05)  // 보수적 투자일수록 양수
            } else 0.0

            rows.add(MonthlyFactorRow(
                yearMonth = ym,
                mktExcess = mktExcess,
                smb = smb,
                hml = hml,
                rmw = rmw,
                cma = cma
            ))
        }

        return rows.sortedBy { it.yearMonth }
    }

    // ─── OLS 회귀 ───

    /**
     * OLS 회귀: stock_excess = alpha + b1*MKT + b2*SMB + b3*HML + b4*RMW + b5*CMA + eps
     *
     * @return Triple(betas, alpha, rSquared) — 관측치 부족 또는 특이행렬이면 null
     */
    internal fun estimateBetas(
        stockMonthlyRet: Map<String, Double>,
        factorMap: Map<String, MonthlyFactorRow>,
        rfMonthly: Map<String, Double>,
        months: List<String>
    ): Triple<FactorBetas, Double, Double>? {
        // 유효한 관측치만 필터링 (factor 또는 stock return 누락 시 제외)
        val k = 6  // intercept + 5 factors
        val yList = mutableListOf<Double>()
        val xList = mutableListOf<DoubleArray>()

        for (ym in months) {
            val rf = rfMonthly[ym] ?: 0.0
            val stockRet = stockMonthlyRet[ym] ?: continue
            val factor = factorMap[ym] ?: continue

            yList.add(stockRet - rf)
            xList.add(doubleArrayOf(
                1.0, factor.mktExcess, factor.smb,
                factor.hml, factor.rmw, factor.cma
            ))
        }

        val n = yList.size
        if (n < MIN_OBS) {
            Timber.d("Korea5Factor estimateBetas: 관측치 %d < 최소 %d", n, MIN_OBS)
            return null
        }

        val y = yList.toDoubleArray()
        val x = xList.toTypedArray()

        // OLS: beta = (X'X)^(-1) X'y
        val xtx = Array(k) { DoubleArray(k) }
        val xty = DoubleArray(k)

        for (i in 0 until n) {
            for (j in 0 until k) {
                xty[j] += x[i][j] * y[i]
                for (l in 0 until k) {
                    xtx[j][l] += x[i][j] * x[i][l]
                }
            }
        }

        val beta = solveLinearSystem(xtx, xty) ?: run {
            Timber.d("Korea5Factor estimateBetas: 특이행렬 (n=%d)", n)
            return null
        }

        // R²
        val yMean = y.average()
        var ssTot = 0.0
        var ssRes = 0.0
        for (i in 0 until n) {
            var yHat = 0.0
            for (j in 0 until k) {
                yHat += beta[j] * x[i][j]
            }
            ssRes += (y[i] - yHat) * (y[i] - yHat)
            ssTot += (y[i] - yMean) * (y[i] - yMean)
        }
        val rSq = if (ssTot > 1e-12) 1.0 - (ssRes / ssTot) else 0.0

        val betas = FactorBetas(
            mkt = beta[1],
            smb = beta[2],
            hml = beta[3],
            rmw = beta[4],
            cma = beta[5]
        )

        return Triple(betas, beta[0], rSq.coerceIn(0.0, 1.0))
    }

    /**
     * 롤링 알파 산출
     *
     * @param window 롤링 윈도우 크기 (개월)
     * @param step 슬라이딩 스텝 (개월)
     * @return List<Pair<endYearMonth, alpha>>
     */
    internal fun rollingAlpha(
        stockMonthlyRet: Map<String, Double>,
        factorMap: Map<String, MonthlyFactorRow>,
        rfMonthly: Map<String, Double>,
        sortedMonths: List<String>,
        window: Int = DEFAULT_WINDOW,
        step: Int = STEP
    ): List<Pair<String, Double>> {
        val result = mutableListOf<Pair<String, Double>>()

        var start = 0
        while (start + window <= sortedMonths.size) {
            val windowMonths = sortedMonths.subList(start, start + window)
            val fit = estimateBetas(
                stockMonthlyRet, factorMap, rfMonthly, windowMonths
            )
            // OLS 실패(특이행렬/관측치 부족) 윈도우는 스킵 — 통계적 무의미한 0 알파 기록 방지
            if (fit != null) {
                result.add(windowMonths.last() to fit.second)
            }
            start += step
        }

        return result
    }

    // ─── 월간 수익률 계산 ───

    /**
     * 종목 일간 종가에서 월간 수익률 산출
     */
    internal fun computeMonthlyReturns(prices: List<DailyTrading>): Map<String, Double> {
        if (prices.size < 2) return emptyMap()

        // 월말 종가 추출
        val sorted = prices.sortedBy { it.date }
        val monthlyClose = mutableMapOf<String, Int>()

        for (p in sorted) {
            if (p.closePrice <= 0) continue
            val ym = p.date.take(6)
            monthlyClose[ym] = p.closePrice  // 덮어쓰기 → 월말 값
        }

        // 월간 수익률 (전월 대비)
        val yms = monthlyClose.keys.sorted()
        val returns = mutableMapOf<String, Double>()
        for (i in 1 until yms.size) {
            val prev = monthlyClose[yms[i - 1]]!!
            val curr = monthlyClose[yms[i]]!!
            if (prev > 0) {
                returns[yms[i]] = (curr.toDouble() - prev.toDouble()) / prev.toDouble()
            }
        }
        return returns
    }

    /**
     * KOSPI 일간 데이터에서 월간 수익률 산출
     */
    private fun computeMonthlyReturnsFromKospi(
        kospiData: List<com.tinyoscillator.core.database.entity.KospiIndexEntity>
    ): Map<String, Double> {
        if (kospiData.size < 2) return emptyMap()

        val sorted = kospiData.sortedBy { it.date }
        val monthlyClose = mutableMapOf<String, Double>()

        for (k in sorted) {
            if (k.closeValue <= 0) continue
            monthlyClose[k.date.take(6)] = k.closeValue
        }

        val yms = monthlyClose.keys.sorted()
        val returns = mutableMapOf<String, Double>()
        for (i in 1 until yms.size) {
            val prev = monthlyClose[yms[i - 1]]!!
            val curr = monthlyClose[yms[i]]!!
            if (prev > 0) {
                returns[yms[i]] = (curr - prev) / prev
            }
        }
        return returns
    }

    /**
     * 종목 일간 데이터에서 월간 시가총액 추출
     */
    private fun computeMonthlyMarketCap(prices: List<DailyTrading>): Map<String, Long> {
        val sorted = prices.sortedBy { it.date }
        val result = mutableMapOf<String, Long>()
        for (p in sorted) {
            if (p.marketCap > 0) {
                result[p.date.take(6)] = p.marketCap
            }
        }
        return result
    }

    // ─── 무위험 수익률 ───

    /**
     * BOK 기준금리를 월간 무위험 수익률로 변환
     * 기준금리는 연율(%) → 월간 수익률 (소수)
     */
    private suspend fun getRiskFreeMonthly(): Map<String, Double> {
        val macroData = macroDao.getByIndicator("base_rate", 60)
        val result = mutableMapOf<String, Double>()
        for (m in macroData) {
            // raw_value는 % 단위 (예: 3.5 = 3.5%)
            val annualRate = m.rawValue / 100.0
            val monthlyRate = annualRate / 12.0
            result[m.yearMonth] = monthlyRate
        }
        return result
    }

    // ─── 수학 유틸리티 ───

    /**
     * 가우스 소거법 (피봇팅 포함)으로 선형방정식 Ax = b 풀기
     */
    internal fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = a.size
        // Augmented matrix
        val aug = Array(n) { i -> DoubleArray(n + 1) { j ->
            if (j < n) a[i][j] else b[i]
        }}

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            var maxRow = col
            var maxVal = abs(aug[col][col])
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }
            if (maxVal < 1e-12) return null  // Singular

            // Swap rows
            val temp = aug[col]
            aug[col] = aug[maxRow]
            aug[maxRow] = temp

            // Eliminate below
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col until n + 1) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            x[i] = aug[i][n]
            for (j in i + 1 until n) {
                x[i] -= aug[i][j] * x[j]
            }
            if (abs(aug[i][i]) < 1e-12) return null
            x[i] /= aug[i][i]
        }
        return x
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    private fun stddev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.sum() / (values.size - 1)
        return sqrt(variance)
    }

    private fun unavailable(reason: String): Korea5FactorResult {
        Timber.d("Korea5Factor 사용 불가: $reason")
        return Korea5FactorResult(
            alphaRaw = 0.0,
            alphaZscore = 0.0,
            signalScore = 0.5,
            nObs = 0,
            lastDate = "",
            betas = FactorBetas(),
            unavailableReason = reason
        )
    }
}
