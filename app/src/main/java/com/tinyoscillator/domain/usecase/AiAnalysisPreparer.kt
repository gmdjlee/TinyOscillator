package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.FinancialData
import com.tinyoscillator.domain.model.MarketDeposit
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.SignalAnalysis
import com.tinyoscillator.domain.model.StockAggregatedTimePoint

/**
 * AI 분석용 데이터 전처리.
 *
 * 순수 함수, I/O 없음. 365일 데이터를 ~400 토큰으로 압축.
 * CalcOscillatorUseCase 패턴 준수.
 */
class AiAnalysisPreparer {

    /**
     * 종목 오실레이터 분석용 데이터 전처리.
     * 365일 → 최근 20일 샘플링, 조/억 단위 축약, 추세 요약.
     */
    fun prepareStockAnalysis(
        stockName: String,
        ticker: String,
        rows: List<OscillatorRow>,
        signals: List<SignalAnalysis>
    ): String {
        if (rows.isEmpty()) return "데이터 없음"

        val sb = StringBuilder()
        sb.appendLine("종목: $stockName ($ticker)")
        sb.appendLine("분석기간: ${rows.first().date}~${rows.last().date} (${rows.size}일)")

        // 최근 20일 샘플링
        val sampled = sampleRows(rows, 20)
        sb.appendLine()
        sb.appendLine("[수급 오실레이터 데이터]")
        sb.appendLine("날짜|시총(조)|외인5일(억)|기관5일(억)|수급비|MACD|시그널|오실레이터")
        for (row in sampled) {
            sb.appendLine(
                "${row.date}|${formatTril(row.marketCapTril)}|${formatEok(row.foreign5d)}|${formatEok(row.inst5d)}|" +
                    "${f2(row.supplyRatio)}|${f2(row.macd)}|${f2(row.signal)}|${f2(row.oscillator)}"
            )
        }

        // 추세 요약
        if (rows.size >= 5) {
            val recent5 = rows.takeLast(5)
            val avgOsc = recent5.map { it.oscillator }.average()
            val trend = when {
                recent5.last().oscillator > recent5.first().oscillator -> "상승"
                recent5.last().oscillator < recent5.first().oscillator -> "하락"
                else -> "횡보"
            }
            sb.appendLine()
            sb.appendLine("[추세 요약]")
            sb.appendLine("최근5일 평균 오실레이터: ${f2(avgOsc)}, 추세: $trend")
        }

        // 시그널
        if (signals.isNotEmpty()) {
            val recentSignals = signals.takeLast(3)
            sb.appendLine()
            sb.appendLine("[최근 시그널]")
            for (sig in recentSignals) {
                val cross = sig.crossSignal?.name ?: "-"
                sb.appendLine("${sig.date} ${sig.trend.name} 오실레이터=${f2(sig.oscillator)} 교차=$cross")
            }
        }

        return sb.toString()
    }

    /**
     * ETF 금액 순위 분석용 데이터 전처리.
     * Top 20 종목, 업종별 집중도, 주간 변동 요약.
     */
    fun prepareEtfRankingAnalysis(
        rankings: List<AmountRankingItem>,
        date: String?
    ): String {
        if (rankings.isEmpty()) return "데이터 없음"

        val sb = StringBuilder()
        sb.appendLine("기준일: ${date ?: "미정"}")

        // Top 20
        val top20 = rankings.take(20)
        sb.appendLine()
        sb.appendLine("[ETF 금액 순위 Top 20]")
        sb.appendLine("순위|종목|금액(억)|ETF수|시장|업종|최대비중%")
        for (item in top20) {
            sb.appendLine(
                "${item.rank}|${item.stockName}|${f0(item.totalAmountBillion * 10)}|${item.etfCount}|" +
                    "${item.market ?: "-"}|${item.sector ?: "-"}|${item.maxWeight?.let { f1(it) } ?: "-"}"
            )
        }

        // 업종별 집중도
        val sectorGroups = rankings.filter { it.sector != null }
            .groupBy { it.sector!! }
            .mapValues { (_, items) -> items.sumOf { it.totalAmountBillion } }
            .entries.sortedByDescending { it.value }
            .take(10)

        if (sectorGroups.isNotEmpty()) {
            val total = rankings.sumOf { it.totalAmountBillion }
            sb.appendLine()
            sb.appendLine("[업종별 자금 집중도]")
            for ((sector, amount) in sectorGroups) {
                val pct = if (total > 0) amount / total * 100 else 0.0
                sb.appendLine("$sector: ${f0(amount * 10)}억 (${f1(pct)}%)")
            }
        }

        // 변동 요약
        val newCount = rankings.count { it.newCount > 0 }
        val removedCount = rankings.count { it.removedCount > 0 }
        if (newCount > 0 || removedCount > 0) {
            sb.appendLine()
            sb.appendLine("[변동 요약] 신규편입 ETF있는 종목: ${newCount}개, 제외된 ETF있는 종목: ${removedCount}개")
        }

        return sb.toString()
    }

    /**
     * 시장 지표 분석용 데이터 전처리.
     * 최근 14일 오실레이터 + 자금동향 10일.
     */
    fun prepareMarketAnalysis(
        kospiData: List<MarketOscillator>,
        kosdaqData: List<MarketOscillator>,
        deposits: List<MarketDeposit> = emptyList()
    ): String {
        val sb = StringBuilder()

        // KOSPI
        if (kospiData.isNotEmpty()) {
            val recent = kospiData.take(14)
            sb.appendLine("[KOSPI 오실레이터 (최근 ${recent.size}일)]")
            sb.appendLine("날짜|지수|오실레이터%")
            for (item in recent) {
                sb.appendLine("${item.date}|${f0(item.indexValue)}|${f1(item.oscillator)}")
            }
        }

        // KOSDAQ
        if (kosdaqData.isNotEmpty()) {
            val recent = kosdaqData.take(14)
            sb.appendLine()
            sb.appendLine("[KOSDAQ 오실레이터 (최근 ${recent.size}일)]")
            sb.appendLine("날짜|지수|오실레이터%")
            for (item in recent) {
                sb.appendLine("${item.date}|${f0(item.indexValue)}|${f1(item.oscillator)}")
            }
        }

        // 자금동향
        if (deposits.isNotEmpty()) {
            val recent = deposits.take(10)
            sb.appendLine()
            sb.appendLine("[투자자 예탁금 동향 (최근 ${recent.size}일)]")
            sb.appendLine("날짜|예탁금(조)|증감(억)|신용(조)|증감(억)")
            for (d in recent) {
                sb.appendLine(
                    "${d.date}|${formatTrilDouble(d.depositAmount)}|${f0(d.depositChange)}|" +
                        "${formatTrilDouble(d.creditAmount)}|${f0(d.creditChange)}"
                )
            }
        }

        return sb.toString()
    }

    /**
     * 종목 종합 분석용 데이터 전처리.
     * 오실레이터+DeMark+재무정보+ETF 데이터를 ~800토큰으로 압축.
     */
    fun prepareComprehensiveStockAnalysis(
        stockName: String,
        ticker: String,
        oscillatorRows: List<OscillatorRow>,
        signals: List<SignalAnalysis>,
        demarkRows: List<DemarkTDRow>,
        financialData: FinancialData?,
        etfAggregated: List<StockAggregatedTimePoint>,
        market: String?,
        sector: String?
    ): String {
        if (oscillatorRows.isEmpty()) return "데이터 없음"

        val sb = StringBuilder()
        sb.appendLine("종목: $stockName ($ticker)")
        if (market != null || sector != null) {
            sb.appendLine("시장: ${market ?: "-"} | 업종: ${sector ?: "-"}")
        }
        sb.appendLine("분석기간: ${oscillatorRows.first().date}~${oscillatorRows.last().date} (${oscillatorRows.size}일)")

        // 1. 오실레이터 (10개 샘플링)
        val sampledOsc = sampleRows(oscillatorRows, 10)
        sb.appendLine()
        sb.appendLine("[수급 오실레이터]")
        sb.appendLine("날짜|시총(조)|외인5일(억)|기관5일(억)|수급비|MACD|시그널|오실레이터")
        for (row in sampledOsc) {
            sb.appendLine(
                "${row.date}|${formatTril(row.marketCapTril)}|${formatEok(row.foreign5d)}|${formatEok(row.inst5d)}|" +
                    "${f2(row.supplyRatio)}|${f2(row.macd)}|${f2(row.signal)}|${f2(row.oscillator)}"
            )
        }

        // 추세 요약
        if (oscillatorRows.size >= 5) {
            val recent5 = oscillatorRows.takeLast(5)
            val avgOsc = recent5.map { it.oscillator }.average()
            val trend = when {
                recent5.last().oscillator > recent5.first().oscillator -> "상승"
                recent5.last().oscillator < recent5.first().oscillator -> "하락"
                else -> "횡보"
            }
            sb.appendLine("최근5일 평균=${f2(avgOsc)}, 추세=$trend")
        }

        // 시그널
        if (signals.isNotEmpty()) {
            val recentSignals = signals.takeLast(3)
            sb.appendLine()
            sb.appendLine("[최근 시그널]")
            for (sig in recentSignals) {
                val cross = sig.crossSignal?.name ?: "-"
                sb.appendLine("${sig.date} ${sig.trend.name} 오실레이터=${f2(sig.oscillator)} 교차=$cross")
            }
        }

        // 2. DeMark (최근 10일)
        if (demarkRows.isNotEmpty()) {
            val recentDemark = demarkRows.takeLast(10)
            sb.appendLine()
            sb.appendLine("[DeMark TD]")
            sb.appendLine("날짜|종가|매도카운트|매수카운트")
            for (row in recentDemark) {
                sb.appendLine("${row.date}|${row.closePrice}|${row.tdSellCount}|${row.tdBuyCount}")
            }
        }

        // 3. 재무정보 (최근 4분기, 핵심 6지표)
        if (financialData != null) {
            val periods = financialData.periods.take(4)
            if (periods.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("[재무정보 (최근 ${periods.size}분기)]")
                sb.appendLine("기간|매출(억)|영업이익(억)|순이익(억)|부채비율%|ROE%|매출성장%")
                for (period in periods) {
                    val income = financialData.incomeStatements[period]
                    val stability = financialData.stabilityRatios[period]
                    val profitability = financialData.profitabilityRatios[period]
                    val growth = financialData.growthRatios[period]
                    sb.appendLine(
                        "$period|${fLong(income?.revenue)}|${fLong(income?.operatingProfit)}|${fLong(income?.netIncome)}|" +
                            "${fDouble(stability?.debtRatio)}|${fDouble(profitability?.roe)}|${fDouble(growth?.revenueGrowth)}"
                    )
                }
            }
        }

        // 4. ETF 편입 추이 (최근 5시점)
        if (etfAggregated.isNotEmpty()) {
            val recentEtf = etfAggregated.takeLast(5)
            sb.appendLine()
            sb.appendLine("[ETF 편입 추이]")
            sb.appendLine("날짜|총금액(억)|ETF수|최대비중%|평균비중%")
            for (point in recentEtf) {
                sb.appendLine(
                    "${point.date}|${f0(point.totalAmount / 1_0000_0000.0)}|${point.etfCount}|" +
                        "${point.maxWeight?.let { f1(it) } ?: "-"}|${point.avgWeight?.let { f1(it) } ?: "-"}"
                )
            }
        }

        return sb.toString()
    }

    /** 채팅용 시스템 프롬프트 — 사용자 질문에 대화형으로 답변 */
    fun getChatSystemPrompt(type: AiAnalysisType, dataContext: String): String {
        val basePrompt = when (type) {
            AiAnalysisType.MARKET_OVERVIEW ->
                "당신은 한국 주식시장 분석 전문 어시스턴트입니다. " +
                    "아래 시장 데이터(KOSPI/KOSDAQ 오실레이터, 투자자 예탁금 동향)를 참고하여 " +
                    "사용자의 질문에 정확하고 간결하게 답변해주세요. " +
                    "질문과 관련된 데이터만 활용하고, 불필요한 전체 분석은 하지 마세요. " +
                    "투자 권유가 아닌 참고 의견임을 필요시 명시하세요."

            AiAnalysisType.COMPREHENSIVE_STOCK ->
                "당신은 한국 주식 분석 전문 어시스턴트입니다. " +
                    "아래 종목 데이터(수급 오실레이터, DeMark TD, 재무제표, ETF 편입 현황)를 참고하여 " +
                    "사용자의 질문에 정확하고 간결하게 답변해주세요. " +
                    "질문과 관련된 데이터만 활용하고, 불필요한 전체 분석은 하지 마세요. " +
                    "투자 권유가 아닌 참고 의견임을 필요시 명시하세요."

            else -> getSystemPrompt(type)
        }
        return "$basePrompt\n\n[참고 데이터]\n$dataContext"
    }

    /** 분석 유형별 시스템 프롬프트 (일회성 분석용) */
    fun getSystemPrompt(type: AiAnalysisType): String = when (type) {
        AiAnalysisType.STOCK_OSCILLATOR ->
            "당신은 한국 주식 수급 분석 전문가입니다. 오실레이터(MACD 기반 수급지표)를 분석하여 " +
                "외국인/기관 수급 동향, 매매 시그널, 향후 수급 전망을 간결하게 제시해주세요. " +
                "구체적인 데이터를 근거로 분석하되, 투자 권유가 아닌 참고 의견임을 명시하세요."

        AiAnalysisType.ETF_RANKING ->
            "당신은 한국 ETF 시장 분석 전문가입니다. ETF 자금 흐름과 종목별 편입 현황을 분석하여 " +
                "시장의 관심 섹터, 자금 집중/이탈 동향, 주목할 종목을 간결하게 제시해주세요. " +
                "구체적인 데이터를 근거로 분석하되, 투자 권유가 아닌 참고 의견임을 명시하세요."

        AiAnalysisType.MARKET_OVERVIEW ->
            "당신은 한국 주식시장 거시 분석 전문가입니다. KOSPI/KOSDAQ 과매수/과매도 지표와 " +
                "투자자 예탁금 동향을 분석하여 시장 전반의 과열/침체 상태, 자금 흐름 추이, " +
                "향후 시장 전망을 간결하게 제시해주세요. " +
                "구체적인 데이터를 근거로 분석하되, 투자 권유가 아닌 참고 의견임을 명시하세요."

        AiAnalysisType.COMPREHENSIVE_STOCK ->
            "당신은 한국 주식 종합 분석 전문가입니다. 수급 오실레이터, DeMark TD 지표, 재무제표, " +
                "ETF 편입 현황을 종합하여 다음을 분석해주세요:\n" +
                "1. 수급 동향: 외인/기관 수급 흐름과 MACD 오실레이터 시그널\n" +
                "2. 기술적 피로도: DeMark 매도/매수 피로 카운트 의미\n" +
                "3. 펀더멘털: 매출·이익 추이, 부채비율, ROE 등 재무 건전성\n" +
                "4. ETF 수급: ETF 편입 금액·비중 변화가 주가에 미치는 영향\n" +
                "5. 종합 판단: 시장/업종 내 위치와 향후 전망\n" +
                "데이터가 없는 항목은 건너뛰세요. 간결하게 요약하되, 투자 권유가 아닌 참고 의견임을 명시하세요."

        AiAnalysisType.PROBABILITY_INTERPRETATION ->
            "당신은 한국 주식 확률분석 전문가입니다. 7개 통계 알고리즘(나이브 베이즈, 로지스틱 회귀, " +
                "HMM 레짐, 패턴 스캔, 신호 점수, 상관 분석, 베이지안 갱신)의 결과를 종합 해석해주세요.\n" +
                "1. 종합 요약: 전체 알고리즘이 시사하는 방향성과 신뢰도\n" +
                "2. 핵심 인사이트: 가장 주목할 결과 2~3개\n" +
                "3. 지표 간 일치/충돌: 알고리즘 간 의견 일치 여부\n" +
                "4. 리스크 요인: 주의할 점\n" +
                "5. 시사점: 투자 전략적 함의\n" +
                "간결하고 구조적으로 작성하되, 투자 권유가 아닌 참고 의견임을 명시하세요."
    }

    // --- 내부 유틸 ---

    internal fun sampleRows(rows: List<OscillatorRow>, targetCount: Int): List<OscillatorRow> {
        if (rows.size <= targetCount) return rows
        val step = rows.size.toDouble() / targetCount
        return (0 until targetCount).map { i ->
            rows[minOf((i * step).toInt(), rows.lastIndex)]
        }
    }

    private fun formatTril(value: Double): String {
        return if (value >= 1.0) "${f1(value)}조" else "${f0(value * 10000)}억"
    }

    private fun formatTrilDouble(value: Double): String {
        val tril = value / 1_0000_0000_0000.0
        return if (tril >= 1.0) "${f1(tril)}조" else "${f0(value / 1_0000_0000.0)}억"
    }

    private fun formatEok(value: Long): String {
        val eok = value / 1_0000_0000.0
        return f0(eok)
    }

    private fun f0(v: Double): String = String.format("%.0f", v)
    private fun f1(v: Double): String = String.format("%.1f", v)
    private fun f2(v: Double): String = String.format("%.2f", v)
    private fun fLong(v: Long?): String = v?.let { f0(it / 1_0000_0000.0) } ?: "-"
    private fun fDouble(v: Double?): String = v?.let { f1(it) } ?: "-"
}
