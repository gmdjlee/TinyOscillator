package com.tinyoscillator.domain.repository

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow

/**
 * 통계 분석 엔진에 데이터를 제공하는 Repository 인터페이스.
 *
 * Room DB 데이터를 조회하고, 오실레이터/DeMark 계산 결과를 포함하여
 * 7개 통계 엔진이 필요로 하는 모든 데이터를 제공한다.
 */
interface StatisticalRepository {

    /** 일별 거래 데이터 (시가총액, 외국인/기관 순매수, 종가) */
    suspend fun getDailyPrices(ticker: String, limit: Int = 365): List<DailyTrading>

    /** 종목명 조회 */
    suspend fun getStockName(ticker: String): String?

    /** 오실레이터 데이터 (MACD, EMA, 수급비율 등) — CalcOscillatorUseCase로 계산 */
    suspend fun getOscillatorData(ticker: String, limit: Int = 365): List<OscillatorRow>

    /** DeMark TD 데이터 — CalcDemarkTDUseCase로 계산 */
    suspend fun getDemarkData(ticker: String, limit: Int = 365): List<DemarkTDRow>

    /** 펀더멘털 데이터 (PBR, PER, 배당수익률 등) — 최근 N일 */
    suspend fun getFundamentalData(ticker: String, limit: Int = 365): List<FundamentalSnapshot>

    /** 해당 종목을 보유한 ETF 수 (최신 날짜 기준) */
    suspend fun getEtfHoldingCount(ticker: String): Int

    /** 해당 종목을 보유한 ETF 금액 추이 */
    suspend fun getEtfAmountTrend(ticker: String): List<EtfAmountPoint>

    /** 섹터 ETF 수익률 (해당 종목 섹터와 관련된 ETF) */
    suspend fun getSectorEtfReturns(ticker: String, limit: Int = 60): List<SectorEtfReturn>
}

/** 펀더멘털 스냅샷 (일별) */
data class FundamentalSnapshot(
    val date: String,
    val close: Long,
    val per: Double,
    val pbr: Double,
    val eps: Long,
    val bps: Long,
    val dividendYield: Double
)

/** ETF 보유 금액 포인트 (시계열) */
data class EtfAmountPoint(
    val date: String,
    val totalAmount: Long,
    val etfCount: Int
)

/** 섹터 ETF 수익률 */
data class SectorEtfReturn(
    val date: String,
    val etfTicker: String,
    val etfName: String,
    val dailyReturn: Double
)
