package com.tinyoscillator.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 장중 실시간 수급 데이터 모델.
 *
 * Kiwoom ka10063 API 응답을 매핑.
 * netBuyAmount 단위: 백만원 (M₩)
 */
data class RealtimeSupplyData(
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val netBuyAmount: Long,      // 순매수금액 (백만원)
    val buyAmount: Long,         // 매수금액 (백만원)
    val sellAmount: Long,        // 매도금액 (백만원)
    val netBuyQuantity: Long,    // 순매수수량
    val accumulatedVolume: Long, // 누적거래량
    val fetchedAt: Long          // epoch millis
)

/**
 * 한국 주식시장 장중 시간 판별.
 * 09:00~15:30 평일 (Asia/Seoul 기준)
 */
object TradingHours {
    private const val OPEN_HOUR = 9
    private const val OPEN_MINUTE = 0
    private const val CLOSE_HOUR = 15
    private const val CLOSE_MINUTE = 30

    fun isTradingHours(): Boolean {
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val dayOfWeek = now.dayOfWeek
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) return false
        val time = now.toLocalTime()
        val open = LocalTime.of(OPEN_HOUR, OPEN_MINUTE)
        val close = LocalTime.of(CLOSE_HOUR, CLOSE_MINUTE)
        return time in open..close
    }
}
