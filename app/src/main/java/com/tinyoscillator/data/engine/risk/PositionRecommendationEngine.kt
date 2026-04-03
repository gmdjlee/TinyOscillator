package com.tinyoscillator.data.engine.risk

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.PositionRecommendation
import com.tinyoscillator.domain.model.SizeReasonCode
import timber.log.Timber

/**
 * 포지션 사이징 추천 엔진.
 *
 * Kelly Criterion으로 최적 비중을 계산하고,
 * CVaR로 꼬리 위험을 제한하여 최종 추천 비중을 산출.
 *
 * 분석 참고용 — 매매 실행 없음, 투자 조언 아님.
 *
 * @param kellySizer Fractional Kelly 사이저
 * @param cvarOverlay CVaR 리스크 오버레이
 */
class PositionRecommendationEngine(
    private val kellySizer: KellyPositionSizer = KellyPositionSizer(),
    private val cvarOverlay: CVaRRiskOverlay = CVaRRiskOverlay()
) {

    /**
     * 포지션 사이징 추천 생성.
     *
     * @param ticker 종목코드
     * @param signalProb 앙상블 상승 확률 [0, 1]
     * @param prices 일별 거래 데이터 (종가 포함)
     * @param portfolioVolTarget 포트폴리오 변동성 목표 (연율, 기본 0.15)
     * @return PositionRecommendation
     */
    fun recommend(
        ticker: String,
        signalProb: Double,
        prices: List<DailyTrading>,
        portfolioVolTarget: Double = 0.15
    ): PositionRecommendation {
        // 데이터 유효성 검사
        val closePrices = prices.map { it.closePrice }
        val validPrices = closePrices.filter { it > 0 }

        if (validPrices.size < 30) {
            return unavailable(ticker, signalProb, "가격 데이터 부족 (${validPrices.size}일, 최소 30일 필요)")
        }

        // 일별 수익률 계산
        val returns = KellyPositionSizer.computeReturns(validPrices)
        if (returns.isEmpty()) {
            return unavailable(ticker, signalProb, "수익률 계산 실패")
        }

        // 신호 우위 확인
        val signalEdge = signalProb - 0.5
        if (signalEdge <= 0.0) {
            return noEdge(ticker, signalProb, returns, validPrices)
        }

        // 252일 Win/Loss Ratio
        val wlrReturns = if (returns.size > 252) returns.takeLast(252).toDoubleArray() else returns
        val wlr = kellySizer.estimateWinLossRatio(wlrReturns)

        // 20일 실현 변동성
        val realizedVol = KellyPositionSizer.realizedVolatility(returns)

        // Kelly 사이징
        val kellyResult = kellySizer.size(signalProb, wlr, portfolioVolTarget, realizedVol)

        // CVaR 계산 (Cornish-Fisher 우선)
        val cvarReturns = if (returns.size > cvarOverlay.lookback) {
            returns.takeLast(cvarOverlay.lookback).toDoubleArray()
        } else returns
        val cvar = cvarOverlay.cornishFisherCvar(cvarReturns)
        val cvarLimit = cvarOverlay.positionLimit(cvar)

        // 리스크 조정 최종 크기
        val riskAdjusted = cvarOverlay.riskAdjustedSize(kellyResult.recommendedPct, cvarLimit)
        val finalSize = riskAdjusted.coerceIn(0.0, kellySizer.maxPosition)

        // 제한 사유 결정
        val reasonCode = determineReasonCode(
            kellyResult.recommendedPct, cvarLimit, kellySizer.maxPosition, finalSize
        )

        Timber.d("포지션 추천 [%s]: signalProb=%.3f, WLR=%.2f, kelly=%.3f, cvar=%.4f, " +
                "cvarLimit=%.3f, final=%.3f (%s)",
            ticker, signalProb, wlr, kellyResult.fracKelly, cvar, cvarLimit, finalSize, reasonCode)

        return PositionRecommendation(
            ticker = ticker,
            recommendedPct = finalSize,
            kellyRaw = kellyResult.rawKelly,
            kellyFractional = kellyResult.fracKelly,
            volAdjustedSize = kellyResult.volAdjSize,
            cvar1d = cvar,
            cvarLimit = cvarLimit,
            signalEdge = signalEdge,
            realizedVol = realizedVol,
            winLossRatio = wlr,
            sizeReasonCode = reasonCode
        )
    }

    private fun determineReasonCode(
        kellySize: Double,
        cvarLimit: Double,
        maxPosition: Double,
        finalSize: Double
    ): SizeReasonCode {
        return when {
            finalSize >= maxPosition -> SizeReasonCode.MAX_POSITION
            cvarLimit < kellySize -> SizeReasonCode.CVAR_BOUND
            else -> SizeReasonCode.KELLY_BOUND
        }
    }

    private fun noEdge(
        ticker: String,
        signalProb: Double,
        returns: DoubleArray,
        validPrices: List<Int>
    ): PositionRecommendation {
        val realizedVol = KellyPositionSizer.realizedVolatility(returns)
        val cvarReturns = if (returns.size > cvarOverlay.lookback) {
            returns.takeLast(cvarOverlay.lookback).toDoubleArray()
        } else returns
        val cvar = cvarOverlay.cornishFisherCvar(cvarReturns)
        val wlrReturns = if (returns.size > 252) returns.takeLast(252).toDoubleArray() else returns
        val wlr = kellySizer.estimateWinLossRatio(wlrReturns)

        return PositionRecommendation(
            ticker = ticker,
            recommendedPct = 0.0,
            kellyRaw = 0.0,
            kellyFractional = 0.0,
            volAdjustedSize = 0.0,
            cvar1d = cvar,
            cvarLimit = cvarOverlay.positionLimit(cvar),
            signalEdge = signalProb - 0.5,
            realizedVol = realizedVol,
            winLossRatio = wlr,
            sizeReasonCode = SizeReasonCode.NO_EDGE
        )
    }

    private fun unavailable(
        ticker: String,
        signalProb: Double,
        reason: String
    ): PositionRecommendation {
        return PositionRecommendation(
            ticker = ticker,
            recommendedPct = 0.0,
            kellyRaw = 0.0,
            kellyFractional = 0.0,
            volAdjustedSize = 0.0,
            cvar1d = 0.0,
            cvarLimit = 0.0,
            signalEdge = signalProb - 0.5,
            realizedVol = 0.0,
            winLossRatio = 0.0,
            sizeReasonCode = SizeReasonCode.NO_EDGE,
            unavailableReason = reason
        )
    }
}
