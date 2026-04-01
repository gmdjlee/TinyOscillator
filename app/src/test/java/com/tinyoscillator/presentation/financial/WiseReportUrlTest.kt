package com.tinyoscillator.presentation.financial

import org.junit.Assert.*
import org.junit.Test

class WiseReportUrlTest {

    @Test
    fun `of generates correct URL for standard ticker`() {
        val url = WiseReportUrl.of("000660")
        assertEquals(
            "https://navercomp.wisereport.co.kr/company/c1010001.aspx?cmp_cd=000660",
            url
        )
    }

    @Test
    fun `of generates correct URL for KOSDAQ ticker`() {
        val url = WiseReportUrl.of("035420")
        assertTrue(url.contains("cmp_cd=035420"))
    }

    @Test
    fun `of handles empty ticker`() {
        val url = WiseReportUrl.of("")
        assertEquals(
            "https://navercomp.wisereport.co.kr/company/c1010001.aspx?cmp_cd=",
            url
        )
    }
}
