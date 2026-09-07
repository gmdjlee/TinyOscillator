package com.tinyoscillator.data.dto

import com.tinyoscillator.domain.model.InvestorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [toDailyInvestorFlowOrNull] 단위 테스트. 명세: docs/TASK_investor_volume_profile.md §9.3.
 */
class InvestorFlowDtoTest {

    /** 필수 필드가 전부 채워진 완전한 행. leg 값을 오버라이드해 개별 케이스를 구성한다. */
    private fun fullRow(
        date: String = "20260902",
        high: String = "255500",
        low: String = "249500",
        close: String = "250500",
        acmlVol: String = "15176841",
        prsnShnuVol: String = "4662430",
        prsnShnuTrPbmn: String = "1175428",
        prsnSelnVol: String = "4000000",
        prsnSelnTrPbmn: String = "1000000",
        frgnShnuVol: String = "3000000",
        frgnShnuTrPbmn: String = "760000",
        frgnSelnVol: String = "2900000",
        frgnSelnTrPbmn: String = "730000",
        orgnShnuVol: String = "2000000",
        orgnShnuTrPbmn: String = "500000",
        orgnSelnVol: String = "1900000",
        orgnSelnTrPbmn: String = "480000"
    ): Map<String, String?> = mapOf(
        "stck_bsop_date" to date,
        "stck_hgpr" to high,
        "stck_lwpr" to low,
        "stck_clpr" to close,
        "acml_vol" to acmlVol,
        "prsn_shnu_vol" to prsnShnuVol,
        "prsn_shnu_tr_pbmn" to prsnShnuTrPbmn,
        "prsn_seln_vol" to prsnSelnVol,
        "prsn_seln_tr_pbmn" to prsnSelnTrPbmn,
        "frgn_shnu_vol" to frgnShnuVol,
        "frgn_shnu_tr_pbmn" to frgnShnuTrPbmn,
        "frgn_seln_vol" to frgnSelnVol,
        "frgn_seln_tr_pbmn" to frgnSelnTrPbmn,
        "orgn_shnu_vol" to orgnShnuVol,
        "orgn_shnu_tr_pbmn" to orgnShnuTrPbmn,
        "orgn_seln_vol" to orgnSelnVol,
        "orgn_seln_tr_pbmn" to orgnSelnTrPbmn
    )

    @Test
    fun `백만원 거래대금은 원 단위 Long으로 승격된다`() {
        val row = fullRow(prsnShnuTrPbmn = "3931838")
        val flow = row.toDailyInvestorFlowOrNull()

        assertEquals(3_931_838_000_000L, flow?.flows?.get(InvestorType.INDIVIDUAL)?.buyAmount)
    }

    @Test
    fun `실측 행 20260902 삼성전자 개인 매수 vwap은 고저가 범위 안에 있다`() {
        val row = fullRow(
            date = "20260902",
            high = "255500",
            low = "249500",
            close = "250500",
            acmlVol = "15176841",
            prsnShnuVol = "4662430",
            prsnShnuTrPbmn = "1175428"
        )
        val flow = row.toDailyInvestorFlowOrNull()

        assertEquals(LocalDate.of(2026, 9, 2), flow?.date)
        assertEquals(255500L, flow?.high)
        assertEquals(249500L, flow?.low)
        assertEquals(15176841L, flow?.volume)

        val prsnLeg = flow?.flows?.get(InvestorType.INDIVIDUAL)
        requireNotNull(prsnLeg)
        assertEquals(252_106.0, prsnLeg.buyVwap, 1.0)
        assertEquals(true, prsnLeg.buyVwap in 249_500.0..255_500.0)
    }

    @Test
    fun `투자자 필드가 빈 문자열인 당일 미확정 행은 null을 반환한다`() {
        val row = fullRow(prsnShnuVol = "", prsnShnuTrPbmn = "", prsnSelnVol = "", prsnSelnTrPbmn = "")

        assertNull(row.toDailyInvestorFlowOrNull())
    }

    @Test
    fun `가격 필드가 빈 문자열이면 null을 반환한다`() {
        val row = fullRow(close = "")

        assertNull(row.toDailyInvestorFlowOrNull())
    }

    @Test
    fun `일자 필드가 빈 문자열이면 null을 반환한다`() {
        val row = fullRow(date = "")

        assertNull(row.toDailyInvestorFlowOrNull())
    }

    @Test
    fun `콤마 포함 숫자를 정상 파싱한다`() {
        val row = fullRow(acmlVol = "15,176,841", prsnShnuTrPbmn = "1,175,428")
        val flow = row.toDailyInvestorFlowOrNull()

        assertEquals(15_176_841L, flow?.volume)
        assertEquals(1_175_428_000_000L, flow?.flows?.get(InvestorType.INDIVIDUAL)?.buyAmount)
    }

    @Test
    fun `비숫자 문자열이면 null을 반환한다`() {
        val row = fullRow(acmlVol = "N/A")

        assertNull(row.toDailyInvestorFlowOrNull())
    }

    @Test
    fun `모든 주체 leg를 정확히 매핑한다`() {
        val row = fullRow()
        val flow = row.toDailyInvestorFlowOrNull()
        requireNotNull(flow)

        assertEquals(3, flow.flows.size)
        assertEquals(4_662_430L, flow.flows.getValue(InvestorType.INDIVIDUAL).buyVolume)
        assertEquals(3_000_000L, flow.flows.getValue(InvestorType.FOREIGN).buyVolume)
        assertEquals(2_000_000L, flow.flows.getValue(InvestorType.INSTITUTION).buyVolume)
    }
}
