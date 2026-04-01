package com.tinyoscillator.presentation.marketanalysis

import com.tinyoscillator.domain.model.DemarkPeriodType
import org.junit.Assert.*
import org.junit.Test

/**
 * MarketDemarkViewModel 상태 모델 및 기본 동작 검증
 */
class MarketDemarkViewModelTest {

    @Test
    fun `MarketDemarkState sealed class variants`() {
        val idle: MarketDemarkState = MarketDemarkState.Idle
        val loading: MarketDemarkState = MarketDemarkState.Loading
        val error: MarketDemarkState = MarketDemarkState.Error("테스트 에러")

        assertTrue(idle is MarketDemarkState.Idle)
        assertTrue(loading is MarketDemarkState.Loading)
        assertTrue(error is MarketDemarkState.Error)
        assertEquals("테스트 에러", (error as MarketDemarkState.Error).message)
    }

    @Test
    fun `DemarkPeriodType values`() {
        assertEquals(2, DemarkPeriodType.entries.size)
        assertTrue(DemarkPeriodType.entries.contains(DemarkPeriodType.DAILY))
        assertTrue(DemarkPeriodType.entries.contains(DemarkPeriodType.WEEKLY))
    }

    @Test
    fun `Error state preserves message`() {
        val msg = "KRX 자격증명이 설정되지 않았습니다. 설정에서 입력해주세요."
        val state = MarketDemarkState.Error(msg)
        assertEquals(msg, state.message)
    }
}
