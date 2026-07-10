package com.tinyoscillator.feature.bearsignal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BearSignalStaticContent] 정적 참조 데이터 무손실 검증 (TASK.md 부록 B #5·#6·#9).
 *
 * 프로토타입 `TYPES`/역사 검증 섹션 텍스트가 값·개수 그대로 이관됐는지 확인한다.
 */
class BearSignalStaticContentTest {

    @Test
    fun `약세장 3유형이 프로토타입과 동일한 순서·개수로 존재한다`() {
        val types = BearSignalStaticContent.TYPES
        assertEquals(3, types.size)
        assertEquals(listOf(0, 1, 2), types.map { it.index })
        assertEquals(listOf("경쟁 · 역전", "전방 수요 · 사이클", "밸류에이션 · 금리"), types.map { it.title })
    }

    @Test
    fun `유형3(밸류에이션 금리)이 활성 방아쇠 인덱스다`() {
        assertEquals(2, BearSignalStaticContent.ACTIVE_TYPE_INDEX)
        assertEquals("밸류에이션 · 금리", BearSignalStaticContent.TYPES[BearSignalStaticContent.ACTIVE_TYPE_INDEX].title)
    }

    @Test
    fun `각 유형은 모니터링 체크리스트를 1개 이상 보유한다`() {
        BearSignalStaticContent.TYPES.forEach { type ->
            assertTrue("${type.title}의 monitor가 비어있음", type.monitor.isNotEmpty())
        }
    }

    @Test
    fun `역사 검증 3대 모니터링 지표가 프로토타입과 동일하다`() {
        val metrics = BearSignalStaticContent.HISTORY_METRICS
        assertEquals(3, metrics.size)
        assertEquals(listOf("매크로", "경쟁", "포트폴리오"), metrics.map { it.header })
    }

    @Test
    fun `면책·지표매핑 문구가 비어있지 않다`() {
        assertTrue(BearSignalStaticContent.DISCLAIMER.isNotBlank())
        assertTrue(BearSignalStaticContent.INDICATOR_MAPPING.isNotBlank())
        assertTrue(BearSignalStaticContent.DISCLAIMER.contains("신영증권"))
    }
}
