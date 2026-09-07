package com.tinyoscillator.core.database.entity

import androidx.room.Entity

/**
 * 투자자별(개인·외국인·기관) 일별 매수·매도 원천 캐시.
 * 명세: docs/TASK_investor_volume_profile.md §6.4.
 *
 * KIS FHPTJ04160001 응답을 그대로 저장한다. 가격은 수정주가 미반영 원주가, 수량은 원수량.
 * 컬럼명은 SSOT 코드 블록의 카멜케이스 프로퍼티명을 그대로 사용한다
 * ([com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotEntity] 관례 —
 * 프로젝트의 일반적인 snake_case `@ColumnInfo` 관례에서 의도적으로 벗어남).
 * `*Amt` 필드는 파싱 시 이미 원 단위로 승격된 값이다(백만원 → ×1_000_000L, `InvestorFlowDto` 참조).
 */
@Entity(tableName = "investor_flow", primaryKeys = ["ticker", "date"])
data class InvestorFlowEntity(
    val ticker: String,
    /** yyyyMMdd */
    val date: String,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
    val prsnBuyVol: Long,
    val prsnBuyAmt: Long,
    val prsnSellVol: Long,
    val prsnSellAmt: Long,
    val frgnBuyVol: Long,
    val frgnBuyAmt: Long,
    val frgnSellVol: Long,
    val frgnSellAmt: Long,
    val orgnBuyVol: Long,
    val orgnBuyAmt: Long,
    val orgnSellVol: Long,
    val orgnSellAmt: Long,
    val fetchedAt: Long
)
