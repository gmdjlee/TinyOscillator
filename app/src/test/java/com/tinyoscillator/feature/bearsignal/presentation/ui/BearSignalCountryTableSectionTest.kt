package com.tinyoscillator.feature.bearsignal.presentation.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Phase 6-7-4 코드리뷰(Phase 6-4) — [formatMarketReturnValue]가 [Locale.getDefault]와 무관하게
 * 항상 점(`.`) 소수점으로 렌더되고, 그 결과가 [String.toDoubleOrNull]로 다시 파싱 가능한지
 * (표시 → 저장 왕복) 검증한다. 콤마 소수점 로케일(예: 독일어/프랑스어)에서
 * `"%.1f".format(...)`(로케일 의존)을 쓰면 "1,2"처럼 렌더돼 [String.toDoubleOrNull]이 실패했던
 * 결함(§6-4)의 회귀 가드.
 */
class BearSignalCountryTableSectionTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `콤마 소수점 로케일에서도 점 소수점으로 포맷된다`() {
        Locale.setDefault(Locale.GERMANY)

        val formatted = formatMarketReturnValue(1.2)

        assertEquals("1.2", formatted)
    }

    @Test
    fun `콤마 소수점 로케일 왕복 — 포맷 후 재파싱이 원래 값과 일치한다`() {
        Locale.setDefault(Locale.FRANCE)

        val original = -3.4
        val formatted = formatMarketReturnValue(original)
        val reparsed = formatted.toDoubleOrNull()

        assertNotNull("콤마 로케일 포맷 결과가 toDoubleOrNull로 재파싱돼야 한다(회귀 가드)", reparsed)
        assertEquals(original, reparsed!!, 0.0001)
    }

    @Test
    fun `US 로케일에서도 동일하게 점 소수점을 유지한다`() {
        Locale.setDefault(Locale.US)

        assertEquals("0.0", formatMarketReturnValue(0.0))
        assertEquals("-5.1", formatMarketReturnValue(-5.1))
    }
}
