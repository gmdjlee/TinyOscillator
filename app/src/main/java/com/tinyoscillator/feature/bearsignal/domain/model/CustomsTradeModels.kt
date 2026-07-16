package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 관세청 품목별 수출입실적(GW) API(`getItemtradeList`) 파싱 결과 한 행 (TASK.md §4 "수출 비중").
 *
 * @param statKor 품목명(HS 10단위 세부 품목명, 예: "메모리", "경주마")
 * @param hsCd HS 품목 코드(응답 필드 `hsCode`, 10단위 — 예: "8542321000")
 * @param exportUsd 수출금액(달러, `expDlr`)
 * @param importUsd 수입금액(달러, `impDlr`)
 * @param yearMonth 조회 연월(응답 필드 `year`, 예: "2025.04")
 */
data class CustomsTradeItem(
    val statKor: String,
    val hsCd: String,
    val exportUsd: Double,
    val importUsd: Double,
    val yearMonth: String
)
