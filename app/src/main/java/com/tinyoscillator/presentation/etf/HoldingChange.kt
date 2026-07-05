package com.tinyoscillator.presentation.etf

import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import kotlin.math.roundToLong

/** 직전 스냅샷 대비 구성종목 변화 유형 */
enum class HoldingChange {
    NEW,        // 신규 편입 (직전 스냅샷에 없던 종목)
    INCREASED,  // 비중 증가
    DECREASED   // 비중 감소
}

/**
 * 직전 스냅샷과 비교하여 종목별 변화 배지를 계산.
 *
 * - 직전 스냅샷에 없는 종목 → NEW
 * - 양쪽 비중이 모두 존재하고 표시 정밀도(소수 2자리) 기준으로 달라진 경우 → INCREASED/DECREASED
 * - 비중 동일하거나 어느 한쪽 비중이 null → 배지 없음 (맵에서 제외)
 *
 * @return stockTicker → HoldingChange (변화 없는 종목은 미포함)
 */
fun computeHoldingChanges(
    current: List<EtfHoldingEntity>,
    previous: List<EtfHoldingEntity>
): Map<String, HoldingChange> {
    val prevByTicker = previous.associateBy { it.stockTicker }
    val result = mutableMapOf<String, HoldingChange>()
    for (holding in current) {
        val prev = prevByTicker[holding.stockTicker]
        if (prev == null) {
            result[holding.stockTicker] = HoldingChange.NEW
            continue
        }
        val curWeight = holding.weight ?: continue
        val prevWeight = prev.weight ?: continue
        // 화면 표시 정밀도(%.2f)와 동일한 기준으로 비교 — 표시상 같은 값에 배지가 붙는 혼동 방지
        val curRounded = (curWeight * 100).roundToLong()
        val prevRounded = (prevWeight * 100).roundToLong()
        when {
            curRounded > prevRounded -> result[holding.stockTicker] = HoldingChange.INCREASED
            curRounded < prevRounded -> result[holding.stockTicker] = HoldingChange.DECREASED
        }
    }
    return result
}
