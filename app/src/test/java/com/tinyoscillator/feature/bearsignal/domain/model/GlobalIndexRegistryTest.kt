package com.tinyoscillator.feature.bearsignal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GlobalIndexRegistry] 정합성 검증 — 도표48 20개 지수(코스피 포함) 커버리지가 TASK.md §4 리스크
 * 완화 지침("미커버 지수는 수동 폴백")대로 구성됐는지 확인.
 */
class GlobalIndexRegistryTest {

    @Test
    fun `해외지수는 19개(코스피 제외)`() {
        assertEquals(19, GlobalIndexRegistry.OVERSEAS_MARKETS.size)
    }

    @Test
    fun `해외지수 이름은 BearSignalReportBaseline MARKETS와 코스피를 제외하면 동일`() {
        val baselineOverseasNames = BearSignalReportBaseline.MARKETS
            .filterNot { it.name == GlobalIndexRegistry.KOSPI_NAME }
            .map { it.name }
            .toSet()
        val registryNames = GlobalIndexRegistry.OVERSEAS_MARKETS.map { it.name }.toSet()

        assertEquals(baselineOverseasNames, registryNames)
    }

    @Test
    fun `자동 커버 지수와 수동 필요 지수 합이 19`() {
        assertEquals(
            19,
            GlobalIndexRegistry.AUTO_COVERED.size + GlobalIndexRegistry.MANUAL_REQUIRED_NAMES.size
        )
    }

    @Test
    fun `자동 커버 지수는 모든 소스의 티커가 채워져 있다`() {
        // 채택 기준이 "두 소스 모두 티커 신뢰도 확보"이므로 6개 지수는 Yahoo/Stooq 둘 다 non-null
        assertTrue(
            GlobalIndexRegistry.AUTO_COVERED.all { spec ->
                GlobalIndexSource.entries.all { spec.tickerFor(it) != null }
            }
        )
    }

    @Test
    fun `수동 필요 지수는 어떤 소스의 티커도 없다`() {
        val manualSpecs = GlobalIndexRegistry.OVERSEAS_MARKETS.filter { it.name in GlobalIndexRegistry.MANUAL_REQUIRED_NAMES }
        assertTrue(
            manualSpecs.all { spec ->
                GlobalIndexSource.entries.all { spec.tickerFor(it) == null }
            }
        )
    }

    @Test
    fun `tickerFor는 소스별 티커를 반환한다`() {
        val dow = GlobalIndexRegistry.OVERSEAS_MARKETS.first { it.name == "다우" }

        assertEquals("^DJI", dow.tickerFor(GlobalIndexSource.YAHOO))
        assertEquals("^dji", dow.tickerFor(GlobalIndexSource.STOOQ))
    }

    @Test
    fun `코스피는 해외지수 목록에 없다`() {
        assertTrue(GlobalIndexRegistry.OVERSEAS_MARKETS.none { it.name == GlobalIndexRegistry.KOSPI_NAME })
    }
}
