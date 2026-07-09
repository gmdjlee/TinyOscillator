package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 관세청 무역통계 Open API(`getNitemtradeList`) 파싱 결과 한 행 (TASK.md §4 "수출 비중").
 *
 * @param statKor 품목명(15대 품목 기준, 예: "반도체", "자동차", "일반기계", "석유제품")
 * @param hsCd 품목 코드(HS 부호 또는 통계품목코드)
 * @param exportUsdThousand 수출금액(천 달러, `expDlr`)
 * @param importUsdThousand 수입금액(천 달러, `impDlr`)
 * @param yearMonth 조회 연월(yyyymm)
 */
data class CustomsTradeItem(
    val statKor: String,
    val hsCd: String,
    val exportUsdThousand: Double,
    val importUsdThousand: Double,
    val yearMonth: String
)
