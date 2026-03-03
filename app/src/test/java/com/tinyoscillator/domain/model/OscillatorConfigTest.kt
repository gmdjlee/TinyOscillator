package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * OscillatorConfig 단위 테스트
 *
 * EMA 파라미터, alpha 계산, 상수 값 검증
 */
class OscillatorConfigTest {

    // ========== 기본값 검증 ==========

    @Test
    fun `기본 config는 EMA_FAST=12, EMA_SLOW=26, EMA_SIGNAL=9`() {
        val config = OscillatorConfig()
        assertEquals(12, config.emaFast)
        assertEquals(26, config.emaSlow)
        assertEquals(9, config.emaSignal)
    }

    @Test
    fun `기본 rollingWindow는 5이다`() {
        val config = OscillatorConfig()
        assertEquals(5, config.rollingWindow)
    }

    // ========== Alpha 계산 검증 ==========

    @Test
    fun `alphaFast는 2 나누기 (emaFast + 1)이다`() {
        val config = OscillatorConfig()
        assertEquals(2.0 / 13, config.alphaFast, 1e-15)
    }

    @Test
    fun `alphaSlow는 2 나누기 (emaSlow + 1)이다`() {
        val config = OscillatorConfig()
        assertEquals(2.0 / 27, config.alphaSlow, 1e-15)
    }

    @Test
    fun `alphaSignal은 2 나누기 (emaSignal + 1)이다`() {
        val config = OscillatorConfig()
        assertEquals(0.2, config.alphaSignal, 1e-15)
    }

    @Test
    fun `alphaFast가 alphaSlow보다 크다`() {
        val config = OscillatorConfig()
        assertTrue("Fast alpha should be > slow alpha", config.alphaFast > config.alphaSlow)
    }

    // ========== 커스텀 config 검증 ==========

    @Test
    fun `커스텀 EMA 기간으로 올바른 alpha 계산`() {
        val config = OscillatorConfig(emaFast = 5, emaSlow = 10, emaSignal = 3)
        assertEquals(2.0 / 6, config.alphaFast, 1e-15)
        assertEquals(2.0 / 11, config.alphaSlow, 1e-15)
        assertEquals(2.0 / 4, config.alphaSignal, 1e-15)
    }

    @Test
    fun `rollingWindow 커스텀 값`() {
        val config = OscillatorConfig(rollingWindow = 10)
        assertEquals(10, config.rollingWindow)
    }

    @Test
    fun `emaFast=1일 때 alpha=1`() {
        val config = OscillatorConfig(emaFast = 1)
        assertEquals(1.0, config.alphaFast, 1e-15)
    }

    // ========== Companion object 상수 검증 ==========

    @Test
    fun `EMA_FAST 상수는 12이다`() {
        assertEquals(12, OscillatorConfig.EMA_FAST)
    }

    @Test
    fun `EMA_SLOW 상수는 26이다`() {
        assertEquals(26, OscillatorConfig.EMA_SLOW)
    }

    @Test
    fun `EMA_SIGNAL 상수는 9이다`() {
        assertEquals(9, OscillatorConfig.EMA_SIGNAL)
    }

    @Test
    fun `ROLLING_WINDOW 상수는 5이다`() {
        assertEquals(5, OscillatorConfig.ROLLING_WINDOW)
    }

    @Test
    fun `MARKET_CAP_DIVISOR는 10의 12승이다`() {
        assertEquals(1_000_000_000_000.0, OscillatorConfig.MARKET_CAP_DIVISOR, 0.0)
    }

    @Test
    fun `DEFAULT_ANALYSIS_DAYS는 365이다`() {
        assertEquals(365, OscillatorConfig.DEFAULT_ANALYSIS_DAYS)
    }

    @Test
    fun `DEFAULT_DISPLAY_DAYS는 100이다`() {
        assertEquals(100, OscillatorConfig.DEFAULT_DISPLAY_DAYS)
    }

    // ========== data class 동작 검증 ==========

    @Test
    fun `같은 값의 config는 equals가 true이다`() {
        val config1 = OscillatorConfig()
        val config2 = OscillatorConfig()
        assertEquals(config1, config2)
    }

    @Test
    fun `다른 값의 config는 equals가 false이다`() {
        val config1 = OscillatorConfig(emaFast = 12)
        val config2 = OscillatorConfig(emaFast = 20)
        assertNotEquals(config1, config2)
    }

    @Test
    fun `copy로 부분 수정이 가능하다`() {
        val config = OscillatorConfig()
        val modified = config.copy(emaFast = 20)
        assertEquals(20, modified.emaFast)
        assertEquals(26, modified.emaSlow)  // 나머지는 유지
    }
}
