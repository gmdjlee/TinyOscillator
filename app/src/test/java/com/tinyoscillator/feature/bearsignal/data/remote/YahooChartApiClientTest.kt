package com.tinyoscillator.feature.bearsignal.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [YahooChartApiClient] JSON 파싱 테스트 — chart API 실 응답 형태
 * (`chart.result[0].timestamp` + `indicators.quote[0].close`) fixture로 검증
 * (TASK.md §4 "IPO ETF 방향", "해외 19개 지수" — Stooq 차단 대체 소스).
 */
class YahooChartApiClientTest {

    private val client = YahooChartApiClient(httpClient = OkHttpClient())

    // 2026-06-01 00:00 UTC = 1780272000, +86400씩 증가
    private fun chartBody(timestamps: String, closes: String) = """
        {"chart":{"result":[{"meta":{"symbol":"^DJI"},"timestamp":[$timestamps],
        "indicators":{"quote":[{"close":[$closes],"open":[$closes],"high":[$closes],"low":[$closes],"volume":[0,0,0]}]}}],
        "error":null}}
    """.trimIndent()

    @Test
    fun `parseChart 정상 응답 종가 오름차순 파싱`() {
        val body = chartBody(
            timestamps = "1780272000,1780358400,1780444800",
            closes = "50.5,51.8,52.9"
        )

        val bars = client.parseChart(body)

        assertEquals(3, bars.size)
        assertEquals("2026-06-01", bars[0].date)
        assertEquals(50.5, bars[0].close, 1e-9)
        assertEquals("2026-06-03", bars[2].date)
        assertEquals(52.9, bars[2].close, 1e-9)
    }

    @Test
    fun `parseChart null 종가(휴장·결측일)는 스킵`() {
        val body = chartBody(
            timestamps = "1780272000,1780358400,1780444800",
            closes = "50.5,null,52.9"
        )

        val bars = client.parseChart(body)

        assertEquals(2, bars.size)
        assertEquals(listOf(50.5, 52.9), bars.map { it.close })
    }

    @Test
    fun `parseChart 미지 심볼(error 응답)은 빈 리스트`() {
        val body = """
            {"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}
        """.trimIndent()

        assertTrue(client.parseChart(body).isEmpty())
    }

    @Test
    fun `parseChart timestamp 없는 응답(휴장 기간)은 빈 리스트`() {
        val body = """
            {"chart":{"result":[{"meta":{"symbol":"^DJI"}}],"error":null}}
        """.trimIndent()

        assertTrue(client.parseChart(body).isEmpty())
    }

    @Test
    fun `parseChart 비JSON 응답은 빈 리스트`() {
        assertTrue(client.parseChart("<html>blocked</html>").isEmpty())
    }

    @Test
    fun `parseChart 빈 문자열은 빈 리스트`() {
        assertTrue(client.parseChart("").isEmpty())
    }

    @Test
    fun `parseChart timestamp보다 close가 짧으면 초과분 스킵`() {
        val body = chartBody(
            timestamps = "1780272000,1780358400,1780444800",
            closes = "50.5,51.8"
        )

        val bars = client.parseChart(body)

        assertEquals(2, bars.size)
    }
}
