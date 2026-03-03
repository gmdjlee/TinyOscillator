package com.tinyoscillator.data.dto

import org.junit.Assert.*
import org.junit.Test

/**
 * FinancialDto 단위 테스트
 *
 * parseNumericLong, parseNumericDouble, mapTo* 매퍼 함수 검증
 */
class FinancialDtoTest {

    // ========== parseNumericLong 테스트 ==========

    @Test
    fun `parseNumericLong - 콤마 포함 숫자`() {
        assertEquals(1234567L, parseNumericLong("1,234,567"))
    }

    @Test
    fun `parseNumericLong - 양수 부호 포함`() {
        assertEquals(1234L, parseNumericLong("+1234"))
    }

    @Test
    fun `parseNumericLong - 음수`() {
        assertEquals(-5000L, parseNumericLong("-5000"))
    }

    @Test
    fun `parseNumericLong - 공백 포함`() {
        assertEquals(1234L, parseNumericLong(" 1234 "))
    }

    @Test
    fun `parseNumericLong - null 입력`() {
        assertNull(parseNumericLong(null))
    }

    @Test
    fun `parseNumericLong - 빈 문자열`() {
        assertNull(parseNumericLong(""))
    }

    @Test
    fun `parseNumericLong - 공백만 있는 문자열`() {
        assertNull(parseNumericLong("   "))
    }

    @Test
    fun `parseNumericLong - 비숫자 문자열`() {
        assertNull(parseNumericLong("abc"))
    }

    @Test
    fun `parseNumericLong - 소수점 포함 시 정수로 변환`() {
        // toDoubleOrNull -> toLong 경로
        assertEquals(1234L, parseNumericLong("1234.56"))
    }

    // ========== parseNumericDouble 테스트 ==========

    @Test
    fun `parseNumericDouble - 소수점 포함`() {
        assertEquals(1.234, parseNumericDouble("1.234")!!, 1e-6)
    }

    @Test
    fun `parseNumericDouble - 콤마와 소수점 포함`() {
        assertEquals(1234.56, parseNumericDouble("1,234.56")!!, 1e-6)
    }

    @Test
    fun `parseNumericDouble - null 입력`() {
        assertNull(parseNumericDouble(null))
    }

    @Test
    fun `parseNumericDouble - 빈 문자열`() {
        assertNull(parseNumericDouble(""))
    }

    @Test
    fun `parseNumericDouble - 음수 소수`() {
        assertEquals(-45.67, parseNumericDouble("-45.67")!!, 1e-6)
    }

    // ========== mapToBalanceSheet 테스트 ==========

    @Test
    fun `mapToBalanceSheet - 완전한 데이터`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "cras" to "1,000",
            "fxas" to "2,000",
            "total_aset" to "3,000",
            "flow_lblt" to "500",
            "fix_lblt" to "800",
            "total_lblt" to "1,300",
            "cpfn" to "100",
            "cfp_surp" to "200",
            "rere" to "1,400",
            "total_cptl" to "1,700"
        )
        val bs = mapToBalanceSheet(item)

        assertNotNull(bs)
        assertEquals("202403", bs!!.period.yearMonth)
        assertEquals(1000L, bs.currentAssets)
        assertEquals(2000L, bs.fixedAssets)
        assertEquals(3000L, bs.totalAssets)
        assertEquals(500L, bs.currentLiabilities)
        assertEquals(800L, bs.fixedLiabilities)
        assertEquals(1300L, bs.totalLiabilities)
        assertEquals(100L, bs.capital)
        assertEquals(200L, bs.capitalSurplus)
        assertEquals(1400L, bs.retainedEarnings)
        assertEquals(1700L, bs.totalEquity)
    }

    @Test
    fun `mapToBalanceSheet - stac_yymm 누락 시 null 반환`() {
        val item = mapOf("cras" to "1000", "total_aset" to "3000")
        assertNull(mapToBalanceSheet(item))
    }

    @Test
    fun `mapToBalanceSheet - 값이 null인 필드`() {
        val item = mapOf<String, String?>(
            "stac_yymm" to "202403",
            "cras" to null,
            "total_aset" to "5000"
        )
        val bs = mapToBalanceSheet(item)
        assertNotNull(bs)
        assertNull(bs!!.currentAssets)
        assertEquals(5000L, bs.totalAssets)
    }

    // ========== mapToIncomeStatement 테스트 ==========

    @Test
    fun `mapToIncomeStatement - 완전한 데이터`() {
        val item = mapOf(
            "stac_yymm" to "202406",
            "sale_account" to "10,000",
            "sale_cost" to "6,000",
            "sale_totl_prfi" to "4,000",
            "bsop_prti" to "2,000",
            "op_prfi" to "1,800",
            "thtr_ntin" to "1,500"
        )
        val is_ = mapToIncomeStatement(item)

        assertNotNull(is_)
        assertEquals("202406", is_!!.period.yearMonth)
        assertEquals(10000L, is_.revenue)
        assertEquals(6000L, is_.costOfSales)
        assertEquals(4000L, is_.grossProfit)
        assertEquals(2000L, is_.operatingProfit)
        assertEquals(1800L, is_.ordinaryProfit)
        assertEquals(1500L, is_.netIncome)
    }

    @Test
    fun `mapToIncomeStatement - stac_yymm 누락 시 null 반환`() {
        val item = mapOf("sale_account" to "10000")
        assertNull(mapToIncomeStatement(item))
    }

    // ========== mapToGrowthRatios 테스트 ==========

    @Test
    fun `mapToGrowthRatios - 기본 필드명 사용`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "grs" to "15.5",
            "bsop_prfi_inrt" to "20.3",
            "ntin_inrt" to "18.7",
            "equt_inrt" to "5.2",
            "totl_aset_inrt" to "8.1"
        )
        val gr = mapToGrowthRatios(item)

        assertNotNull(gr)
        assertEquals(15.5, gr!!.revenueGrowth!!, 1e-6)
        assertEquals(20.3, gr.operatingProfitGrowth!!, 1e-6)
        assertEquals(18.7, gr.netIncomeGrowth!!, 1e-6)
        assertEquals(5.2, gr.equityGrowth!!, 1e-6)
        assertEquals(8.1, gr.totalAssetsGrowth!!, 1e-6)
    }

    @Test
    fun `mapToGrowthRatios - 대체 필드명 폴백`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "grs" to "15.5",
            "bsop_prfi_inrt" to "20.3",
            "thtr_ntin_inrt" to "12.0",    // ntin_inrt 대신 대체 필드
            "cptl_ntin_rate" to "3.5",      // equt_inrt 대신 대체 필드
            "total_aset_inrt" to "6.0"      // totl_aset_inrt 대신 대체 필드
        )
        val gr = mapToGrowthRatios(item)

        assertNotNull(gr)
        assertEquals(12.0, gr!!.netIncomeGrowth!!, 1e-6)
        assertEquals(3.5, gr.equityGrowth!!, 1e-6)
        assertEquals(6.0, gr.totalAssetsGrowth!!, 1e-6)
    }

    @Test
    fun `mapToGrowthRatios - 기본 필드가 있으면 대체 필드 무시`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "grs" to "15.5",
            "bsop_prfi_inrt" to "20.3",
            "ntin_inrt" to "18.7",
            "thtr_ntin_inrt" to "99.9",   // 무시됨 (ntin_inrt가 있으므로)
            "equt_inrt" to "5.2",
            "totl_aset_inrt" to "8.1"
        )
        val gr = mapToGrowthRatios(item)

        assertNotNull(gr)
        assertEquals(18.7, gr!!.netIncomeGrowth!!, 1e-6) // 기본 필드 우선
    }

    @Test
    fun `mapToGrowthRatios - stac_yymm 누락 시 null 반환`() {
        val item = mapOf("grs" to "15.5")
        assertNull(mapToGrowthRatios(item))
    }

    // ========== mapToProfitabilityRatios 테스트 ==========

    @Test
    fun `mapToProfitabilityRatios - 완전한 데이터`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "bsop_prfi_rate" to "15.2",
            "ntin_rate" to "10.5",
            "roe_val" to "12.8",
            "roa_val" to "6.3"
        )
        val pr = mapToProfitabilityRatios(item)

        assertNotNull(pr)
        assertEquals("202403", pr!!.period.yearMonth)
        assertEquals(15.2, pr.operatingMargin!!, 1e-6)
        assertEquals(10.5, pr.netMargin!!, 1e-6)
        assertEquals(12.8, pr.roe!!, 1e-6)
        assertEquals(6.3, pr.roa!!, 1e-6)
    }

    @Test
    fun `mapToProfitabilityRatios - stac_yymm 누락 시 null 반환`() {
        val item = mapOf("bsop_prfi_rate" to "15.2")
        assertNull(mapToProfitabilityRatios(item))
    }

    @Test
    fun `mapToProfitabilityRatios - 값이 null인 필드`() {
        val item = mapOf<String, String?>(
            "stac_yymm" to "202403",
            "bsop_prfi_rate" to null,
            "ntin_rate" to "10.5"
        )
        val pr = mapToProfitabilityRatios(item)
        assertNotNull(pr)
        assertNull(pr!!.operatingMargin)
        assertEquals(10.5, pr.netMargin!!, 1e-6)
    }

    @Test
    fun `mapToProfitabilityRatios - 음수 마진`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "bsop_prfi_rate" to "-5.3",
            "ntin_rate" to "-8.1",
            "roe_val" to "-2.5",
            "roa_val" to "-1.2"
        )
        val pr = mapToProfitabilityRatios(item)
        assertNotNull(pr)
        assertEquals(-5.3, pr!!.operatingMargin!!, 1e-6)
        assertEquals(-8.1, pr.netMargin!!, 1e-6)
    }

    // ========== mapToStabilityRatios 테스트 ==========

    @Test
    fun `mapToStabilityRatios - 완전한 데이터`() {
        val item = mapOf(
            "stac_yymm" to "202403",
            "lblt_rate" to "80.5",
            "crnt_rate" to "150.2",
            "quck_rate" to "120.3",
            "bram_depn" to "25.1",
            "inte_cvrg_rate" to "5.8"
        )
        val sr = mapToStabilityRatios(item)

        assertNotNull(sr)
        assertEquals("202403", sr!!.period.yearMonth)
        assertEquals(80.5, sr.debtRatio!!, 1e-6)
        assertEquals(150.2, sr.currentRatio!!, 1e-6)
        assertEquals(120.3, sr.quickRatio!!, 1e-6)
        assertEquals(25.1, sr.borrowingDependency!!, 1e-6)
        assertEquals(5.8, sr.interestCoverageRatio!!, 1e-6)
    }

    @Test
    fun `mapToStabilityRatios - stac_yymm 누락 시 null 반환`() {
        val item = mapOf("lblt_rate" to "80.5")
        assertNull(mapToStabilityRatios(item))
    }

    @Test
    fun `mapToStabilityRatios - 값이 null인 필드`() {
        val item = mapOf<String, String?>(
            "stac_yymm" to "202403",
            "lblt_rate" to "80.5",
            "crnt_rate" to null
        )
        val sr = mapToStabilityRatios(item)
        assertNotNull(sr)
        assertEquals(80.5, sr!!.debtRatio!!, 1e-6)
        assertNull(sr.currentRatio)
    }

    // ========== KisFinancialApiResponse 테스트 ==========

    @Test
    fun `KisFinancialApiResponse - output 우선 반환`() {
        val resp = KisFinancialApiResponse(
            rtCd = "0",
            output = listOf(mapOf("key" to "val1")),
            output1 = listOf(mapOf("key" to "val2"))
        )
        assertEquals("val1", resp.actualOutput!![0]["key"])
    }

    @Test
    fun `KisFinancialApiResponse - output null이면 output1 반환`() {
        val resp = KisFinancialApiResponse(
            rtCd = "0",
            output = null,
            output1 = listOf(mapOf("key" to "val2"))
        )
        assertEquals("val2", resp.actualOutput!![0]["key"])
    }

    @Test
    fun `KisFinancialApiResponse - 둘 다 null이면 null 반환`() {
        val resp = KisFinancialApiResponse(rtCd = "0")
        assertNull(resp.actualOutput)
    }
}
