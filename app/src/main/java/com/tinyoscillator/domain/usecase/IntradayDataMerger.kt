package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.RealtimeSupplyData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 장중 실시간 수급 데이터를 DailyTrading 리스트에 병합.
 *
 * 병합 전략:
 * - 최신 날짜가 오늘이면 → 해당 날짜의 foreignNetBuy/instNetBuy 교체
 * - 최신 날짜가 어제(과거)이면 → 오늘 데이터를 끝에 추가
 *
 * 단위 변환:
 * - RealtimeSupplyData.netBuyAmount: 백만원(M₩)
 * - DailyTrading.foreignNetBuy/instNetBuy: 원(₩)
 *
 * 주의:
 * - ka10063은 외국인+기관 합산 순매수만 제공 → 개별 분리 불가
 * - 합산 순매수를 foreignNetBuy에 전액 반영, instNetBuy는 0으로 처리
 *   (오실레이터 계산은 합산을 사용하므로 결과에 영향 없음)
 */
object IntradayDataMerger {

    private val KRX_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 장중 데이터를 DailyTrading 리스트에 병합.
     *
     * @param baseData 기존 DailyTrading 리스트 (날짜 오름차순)
     * @param intradayData ka10063에서 가져온 실시간 수급 데이터
     * @return 병합된 DailyTrading 리스트
     */
    fun merge(baseData: List<DailyTrading>, intradayData: RealtimeSupplyData): List<DailyTrading> {
        if (baseData.isEmpty()) return baseData

        val todayStr = LocalDate.now().format(KRX_DATE_FORMATTER)
        val latestDate = baseData.last().date

        // ka10063의 netBuyAmount는 백만원 단위 → 원으로 변환
        val intradayNetBuyWon = intradayData.netBuyAmount * 1_000_000L

        return if (latestDate == todayStr) {
            // 오늘 데이터가 이미 존재 → 최신 값 교체
            baseData.toMutableList().apply {
                val last = this.last()
                this[lastIndex] = last.copy(
                    foreignNetBuy = intradayNetBuyWon,
                    instNetBuy = 0L
                )
            }
        } else {
            // 오늘 데이터가 없음 → 끝에 추가
            // 시가총액/종가는 전일 값 사용 (장중에는 변동이 미미)
            val latestItem = baseData.last()
            baseData + DailyTrading(
                date = todayStr,
                marketCap = latestItem.marketCap,
                foreignNetBuy = intradayNetBuyWon,
                instNetBuy = 0L,
                closePrice = latestItem.closePrice
            )
        }
    }
}
