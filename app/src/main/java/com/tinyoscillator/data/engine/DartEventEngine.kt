package com.tinyoscillator.data.engine

import com.tinyoscillator.core.api.DartApiClient
import com.tinyoscillator.core.database.dao.DartDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.entity.DartCorpCodeEntity
import com.tinyoscillator.domain.model.DartDisclosure
import com.tinyoscillator.domain.model.DartEventResult
import com.tinyoscillator.domain.model.DartEventType
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.EventStudyResult
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * DART 공시 이벤트 스터디 엔진 (9번째 통계 엔진)
 *
 * 주요 기능:
 * 1. DART OpenAPI에서 최근 공시 조회
 * 2. 한국어 보고서명을 7가지 이벤트 타입으로 분류
 * 3. OLS 베타 추정 (KOSPI 시장 수익률 대비)
 * 4. CAR (Cumulative Abnormal Return) 산출
 * 5. 이벤트 기반 신호 점수 생성
 *
 * DART API 키가 없으면 graceful하게 빈 결과를 반환.
 */
@Singleton
class DartEventEngine @Inject constructor(
    private val dartApiClient: DartApiClient,
    private val dartDao: DartDao,
    private val regimeDao: RegimeDao
) {

    companion object {
        private const val ESTIMATION_WINDOW = 120    // 베타 추정 기간 (거래일)
        private const val MIN_ESTIMATION_OBS = 60    // 최소 관측치
        private const val PRE_EVENT_DAYS = 5         // 이벤트 전 기간
        private const val POST_EVENT_DAYS = 20       // 이벤트 후 기간
        private const val MIN_POST_DAYS = 10         // 최소 이벤트 후 거래일
        private const val CORP_CODE_CACHE_DAYS = 30L // corp_code 캐시 유효 기간
        private const val LOOKBACK_DAYS = 30         // 공시 조회 기간
    }

    /**
     * DART 이벤트 분석 실행
     *
     * @param dartApiKey DART API 인증키 (없으면 null → 빈 결과 반환)
     * @param stockCode 종목코드 (6자리)
     * @param prices 일별 거래 데이터 (날짜 오름차순, closePrice 포함)
     * @return DartEventResult (API 키 없거나 공시 없으면 nEvents=0)
     */
    suspend fun analyze(
        dartApiKey: String?,
        stockCode: String,
        prices: List<DailyTrading>
    ): DartEventResult {
        if (dartApiKey.isNullOrBlank()) {
            return emptyResult("DART API 키 미설정")
        }

        // 1. corp_code 조회
        val corpCode = resolveCorpCode(dartApiKey, stockCode)
        if (corpCode == null) {
            Timber.d("DART: 종목 %s의 corp_code를 찾을 수 없음", stockCode)
            return emptyResult("corp_code 매핑 없음")
        }

        // 2. 최근 공시 조회
        val disclosures = try {
            dartApiClient.fetchRecentDisclosures(dartApiKey, corpCode, LOOKBACK_DAYS)
        } catch (e: Exception) {
            Timber.w(e, "DART 공시 조회 실패: %s", stockCode)
            return emptyResult("공시 조회 실패: ${e.message}")
        }

        if (disclosures.isEmpty()) {
            return emptyResult(unavailableReason = null)  // 공시 없음은 정상
        }

        // 3. KOSPI 일별 종가 로드 (시장 수익률 계산용)
        val kospiCloses = loadKospiCloses()
        if (kospiCloses.size < ESTIMATION_WINDOW) {
            return emptyResult("KOSPI 데이터 부족 (${kospiCloses.size}일)")
        }

        // 4. 각 공시에 대해 이벤트 스터디 수행
        val stockReturns = computeLogReturns(prices.map { it.date to it.closePrice.toDouble() })
        val marketReturns = computeLogReturns(kospiCloses)

        val eventStudies = mutableListOf<EventStudyResult>()
        val today = LocalDate.now()

        for (disclosure in disclosures) {
            val eventDate = try {
                LocalDate.parse(disclosure.rceptDt, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (e: Exception) {
                continue
            }

            // 이벤트 후 최소 거래일 확인
            val daysSinceEvent = ChronoUnit.DAYS.between(eventDate, today)
            if (daysSinceEvent < MIN_POST_DAYS) {
                Timber.d("DART: 이벤트 %s 이후 %d일 경과 — 스킵 (최소 %d일 필요)",
                    disclosure.rceptDt, daysSinceEvent, MIN_POST_DAYS)
                continue
            }

            val car = computeCar(
                stockReturns = stockReturns,
                marketReturns = marketReturns,
                eventDateStr = disclosure.rceptDt
            ) ?: continue

            eventStudies.add(
                EventStudyResult(
                    beta = car.beta,
                    carFinal = car.carFinal,
                    tStat = car.tStat,
                    nObs = car.nObs,
                    eventType = disclosure.eventType,
                    eventDate = disclosure.rceptDt,
                    significant = abs(car.tStat) > 2.0
                )
            )
        }

        // 5. 가중 평균 신호 산출
        return buildSignalResult(eventStudies, disclosures, prices)
    }

    /**
     * OLS 베타 추정 (시장 모형: R_stock = alpha + beta * R_market + epsilon)
     *
     * @return 베타 값 (관측치 부족 시 1.0 반환)
     */
    internal fun estimateBeta(
        stockReturns: Map<String, Double>,
        marketReturns: Map<String, Double>,
        endDate: String,
        windowDays: Int = ESTIMATION_WINDOW
    ): Double {
        val endDt = try {
            LocalDate.parse(endDate, DateTimeFormatter.BASIC_ISO_DATE)
        } catch (e: Exception) {
            return 1.0
        }

        // 추정 윈도우: 이벤트 전 PRE_EVENT_DAYS 이전 ~ windowDays
        val estEnd = endDt.minusDays(PRE_EVENT_DAYS.toLong())

        val pairs = mutableListOf<Pair<Double, Double>>()
        val sortedDates = stockReturns.keys.intersect(marketReturns.keys).sorted()

        for (date in sortedDates) {
            val dt = try {
                LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (e: Exception) {
                continue
            }
            if (dt > estEnd) break
            if (dt <= estEnd.minusDays(windowDays.toLong())) continue

            val sr = stockReturns[date] ?: continue
            val mr = marketReturns[date] ?: continue
            if (sr.isNaN() || sr.isInfinite() || mr.isNaN() || mr.isInfinite()) continue

            pairs.add(sr to mr)
        }

        if (pairs.size < MIN_ESTIMATION_OBS) {
            Timber.d("DART: 베타 추정 관측치 부족 (%d < %d) → 1.0 반환", pairs.size, MIN_ESTIMATION_OBS)
            return 1.0
        }

        // OLS: beta = Cov(stock, market) / Var(market)
        val meanS = pairs.map { it.first }.average()
        val meanM = pairs.map { it.second }.average()

        var cov = 0.0
        var varM = 0.0
        for ((s, m) in pairs) {
            cov += (s - meanS) * (m - meanM)
            varM += (m - meanM) * (m - meanM)
        }

        return if (varM > 1e-10) (cov / varM) else 1.0
    }

    /**
     * CAR (Cumulative Abnormal Return) 산출
     *
     * AR_t = R_stock_t - beta * R_market_t
     * CAR = sum(AR_t) for t in [event-pre, event+post]
     * t_stat = mean(AR) / (std(AR) / sqrt(n))
     */
    internal fun computeCar(
        stockReturns: Map<String, Double>,
        marketReturns: Map<String, Double>,
        eventDateStr: String
    ): CarResult? {
        val beta = estimateBeta(stockReturns, marketReturns, eventDateStr)

        val eventDate = try {
            LocalDate.parse(eventDateStr, DateTimeFormatter.BASIC_ISO_DATE)
        } catch (e: Exception) {
            return null
        }

        val windowStart = eventDate.minusDays(PRE_EVENT_DAYS.toLong())
        val windowEnd = eventDate.plusDays(POST_EVENT_DAYS.toLong())

        val sortedDates = stockReturns.keys.intersect(marketReturns.keys).sorted()
        val abnormalReturns = mutableListOf<Double>()

        for (date in sortedDates) {
            val dt = try {
                LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (e: Exception) {
                continue
            }
            if (dt < windowStart) continue
            if (dt > windowEnd) break

            val sr = stockReturns[date] ?: continue
            val mr = marketReturns[date] ?: continue
            if (sr.isNaN() || mr.isNaN()) continue

            val ar = sr - beta * mr
            abnormalReturns.add(ar)
        }

        if (abnormalReturns.size < MIN_POST_DAYS) return null

        val carFinal = abnormalReturns.sum()
        val meanAr = abnormalReturns.average()
        val stdAr = abnormalReturns.standardDeviation()
        val n = abnormalReturns.size
        val tStat = if (stdAr > 1e-10) meanAr / (stdAr / sqrt(n.toDouble())) else 0.0

        return CarResult(
            beta = beta,
            carFinal = carFinal,
            tStat = tStat,
            nObs = n
        )
    }

    /**
     * corp_code 조회 (캐시 → 다운로드)
     */
    private suspend fun resolveCorpCode(apiKey: String, ticker: String): String? {
        // 캐시 확인
        val cached = dartDao.getCorpCode(ticker)
        if (cached != null) {
            val cacheAge = System.currentTimeMillis() - cached.updatedAt
            val cacheMaxMs = CORP_CODE_CACHE_DAYS * 24 * 60 * 60 * 1000L
            if (cacheAge < cacheMaxMs) {
                return cached.corpCode
            }
        }

        // 캐시 전체 갱신 필요 확인
        val lastUpdate = dartDao.lastUpdatedAt() ?: 0L
        val cacheAge = System.currentTimeMillis() - lastUpdate
        val cacheMaxMs = CORP_CODE_CACHE_DAYS * 24 * 60 * 60 * 1000L
        if (cacheAge > cacheMaxMs || dartDao.count() == 0) {
            Timber.d("DART: corp_code 캐시 갱신 (마지막 갱신: %dms 전)", cacheAge)
            try {
                val entries = dartApiClient.downloadCorpCodeMaster(apiKey)
                if (entries.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val entities = entries.map {
                        DartCorpCodeEntity(
                            ticker = it.stockCode,
                            corpCode = it.corpCode,
                            corpName = it.corpName,
                            updatedAt = now
                        )
                    }
                    dartDao.deleteAll()
                    dartDao.insertAll(entities)
                    Timber.d("DART: corp_code 캐시 갱신 완료 (%d건)", entities.size)
                }
            } catch (e: Exception) {
                Timber.w(e, "DART: corp_code 마스터 다운로드 실패")
            }
        }

        return dartDao.getCorpCode(ticker)?.corpCode
    }

    /**
     * KOSPI 일별 종가 로드 (RegimeDao의 kospi_index 테이블 활용)
     */
    private suspend fun loadKospiCloses(): List<Pair<String, Double>> {
        val entities = regimeDao.getAllKospiIndex()
        return entities.map { it.date to it.closeValue }
            .sortedBy { it.first }
    }

    /**
     * 이벤트 스터디 결과를 종합하여 DartEventResult 생성
     */
    private fun buildSignalResult(
        eventStudies: List<EventStudyResult>,
        disclosures: List<DartDisclosure>,
        prices: List<DailyTrading>
    ): DartEventResult {
        val today = LocalDate.now()

        if (eventStudies.isEmpty()) {
            // 이벤트는 있지만 스터디 불가 (너무 최근이거나 데이터 부족)
            return DartEventResult(
                signalScore = 0.5,
                dominantEventType = disclosures.firstOrNull()?.eventType ?: DartEventType.OTHER,
                latestCar = 0.0,
                nEvents = disclosures.size,
                eventStudies = emptyList(),
                eventTypeSignals = emptyMap(),
                dataDate = prices.lastOrNull()?.date ?: "",
                unavailableReason = if (disclosures.isNotEmpty()) "이벤트 후 최소 거래일 미충족" else null
            )
        }

        // 가중 평균 신호: weight = 1 / (days_since_event + 1)
        var weightedCarSum = 0.0
        var weightSum = 0.0

        for (study in eventStudies) {
            val eventDate = try {
                LocalDate.parse(study.eventDate, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (e: Exception) {
                continue
            }
            val daysSince = ChronoUnit.DAYS.between(eventDate, today).toDouble()
            val weight = 1.0 / (daysSince + 1.0)
            weightedCarSum += study.carFinal * weight
            weightSum += weight
        }

        val weightedCar = if (weightSum > 0) weightedCarSum / weightSum else 0.0

        // CAR → 0~1 신호 점수 (시그모이드 변환, scale factor = 20)
        val signalScore = sigmoid(weightedCar * 20.0)

        // 가장 큰 영향의 이벤트 타입
        val dominantStudy = eventStudies.maxByOrNull { abs(it.carFinal) }
        val dominantType = dominantStudy?.eventType ?: DartEventType.OTHER

        // 이벤트 타입별 원핫 인코딩 + CAR 값
        val typeSignals = mutableMapOf<String, Double>()
        for (type in DartEventType.ALL_TYPES) {
            val typeStudies = eventStudies.filter { it.eventType == type }
            typeSignals["has_$type"] = if (typeStudies.isNotEmpty()) 1.0 else 0.0
            typeSignals["car_$type"] = typeStudies.map { it.carFinal }.average().takeIf { !it.isNaN() } ?: 0.0
        }

        return DartEventResult(
            signalScore = signalScore,
            dominantEventType = dominantType,
            latestCar = eventStudies.maxByOrNull { it.eventDate }?.carFinal ?: 0.0,
            nEvents = eventStudies.size,
            eventStudies = eventStudies.sortedByDescending { it.eventDate }.take(3),
            eventTypeSignals = typeSignals,
            dataDate = prices.lastOrNull()?.date ?: ""
        )
    }

    private fun emptyResult(unavailableReason: String?): DartEventResult {
        return DartEventResult(
            signalScore = 0.5,
            dominantEventType = DartEventType.OTHER,
            latestCar = 0.0,
            nEvents = 0,
            dataDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            unavailableReason = unavailableReason
        )
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + kotlin.math.exp(-x))

    /**
     * 로그 수익률 계산: ln(P_t / P_{t-1})
     */
    internal fun computeLogReturns(
        datePricePairs: List<Pair<String, Double>>
    ): Map<String, Double> {
        val sorted = datePricePairs.sortedBy { it.first }
        val returns = mutableMapOf<String, Double>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1].second
            val curr = sorted[i].second
            if (prev > 0 && curr > 0) {
                returns[sorted[i].first] = kotlin.math.ln(curr / prev)
            }
        }
        return returns
    }

    internal data class CarResult(
        val beta: Double,
        val carFinal: Double,
        val tStat: Double,
        val nObs: Int
    )

    private fun List<Double>.standardDeviation(): Double {
        if (size < 2) return 0.0
        val mean = average()
        val variance = sumOf { (it - mean) * (it - mean) } / (size - 1)
        return sqrt(variance)
    }
}
