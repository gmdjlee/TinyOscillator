package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.OscillatorConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CalcOscillatorUseCase - EMA 계산 상세 테스트
 *
 * 다양한 입력에 대한 EMA 계산 정확성 검증
 */
class CalcOscillatorEmaTest {

    private lateinit var useCase: CalcOscillatorUseCase
    private val TOLERANCE = 1e-12

    @Before
    fun setup() {
        useCase = CalcOscillatorUseCase(OscillatorConfig())
    }

    @Test
    fun `빈 리스트는 빈 EMA를 반환한다`() {
        val result = useCase.calcEma(emptyList(), 12)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `단일 값은 그대로 반환한다`() {
        val result = useCase.calcEma(listOf(5.0), 12)
        assertEquals(1, result.size)
        assertEquals(5.0, result[0], TOLERANCE)
    }

    @Test
    fun `모든 값이 같으면 EMA도 동일하다`() {
        val values = List(20) { 3.14 }
        val result = useCase.calcEma(values, 12)
        for (r in result) {
            assertEquals(3.14, r, 1e-10)
        }
    }

    @Test
    fun `period=1이면 alpha=1이므로 원래 값과 동일하다`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val result = useCase.calcEma(values, 1)
        for (i in values.indices) {
            assertEquals(values[i], result[i], TOLERANCE)
        }
    }

    @Test
    fun `큰 period에서 EMA는 느리게 반응한다`() {
        val values = List(5) { 0.0 } + List(5) { 1.0 }
        val emaFast = useCase.calcEma(values, 3)
        val emaSlow = useCase.calcEma(values, 20)

        // Fast EMA는 1.0에 더 빨리 수렴
        assertTrue("Fast EMA 마지막 값", emaFast.last() > emaSlow.last())
    }

    @Test
    fun `음수 값에 대한 EMA 계산`() {
        val values = listOf(-1.0, -2.0, -3.0, -2.0, -1.0)
        val period = 3
        val alpha = 2.0 / (period + 1)

        val result = useCase.calcEma(values, period)

        var expected = values[0]
        assertEquals(expected, result[0], TOLERANCE)
        for (i in 1 until values.size) {
            expected = alpha * values[i] + (1 - alpha) * expected
            assertEquals("EMA[$i]", expected, result[i], TOLERANCE)
        }
    }

    @Test
    fun `EMA 결과 크기는 입력 크기와 동일하다`() {
        val values = List(50) { Math.random() }
        val result = useCase.calcEma(values, 12)
        assertEquals(values.size, result.size)
    }

    @Test
    fun `매우 작은 값에 대한 EMA 정밀도`() {
        val values = listOf(1e-10, 2e-10, 3e-10)
        val result = useCase.calcEma(values, 9)
        // 수급비율은 매우 작은 값이므로 정밀도 확인
        assertEquals(1e-10, result[0], 1e-25)
        assertTrue(result[1] > result[0])
    }

    @Test
    fun `증가하는 시퀀스에서 EMA는 값보다 항상 작다 (첫 값 제외)`() {
        val values = List(20) { (it + 1).toDouble() }
        val result = useCase.calcEma(values, 12)
        for (i in 1 until values.size) {
            assertTrue("EMA[$i] < values[$i]", result[i] < values[i])
        }
    }

    @Test
    fun `감소하는 시퀀스에서 EMA는 값보다 항상 크다 (첫 값 제외)`() {
        val values = List(20) { (20 - it).toDouble() }
        val result = useCase.calcEma(values, 12)
        for (i in 1 until values.size) {
            assertTrue("EMA[$i] > values[$i]", result[i] > values[i])
        }
    }
}
