package com.tinyoscillator.feature.bearsignal.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StooqCsvClient] CSV 파싱 테스트 — Stooq `q/d/l` 실 응답 형태(`Date,Open,High,Low,Close,Volume`)
 * fixture로 검증 (TASK.md §4 "IPO ETF 방향", "해외 19개 지수").
 */
class StooqCsvClientTest {

    private val client = StooqCsvClient(httpClient = OkHttpClient())

    @Test
    fun `parseCsv 정상 CSV 종가 오름차순 파싱`() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2026-06-01,50.00,51.00,49.50,50.50,120000
            2026-06-02,50.50,52.00,50.00,51.80,150000
            2026-06-03,51.80,53.00,51.50,52.90,98000
        """.trimIndent()

        val bars = client.parseCsv(csv)

        assertEquals(3, bars.size)
        assertEquals("2026-06-01", bars[0].date)
        assertEquals(50.50, bars[0].close, 1e-9)
        assertEquals("2026-06-03", bars[2].date)
        assertEquals(52.90, bars[2].close, 1e-9)
    }

    @Test
    fun `parseCsv 역순 입력도 날짜 오름차순으로 정렬`() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2026-06-03,51.80,53.00,51.50,52.90,98000
            2026-06-01,50.00,51.00,49.50,50.50,120000
            2026-06-02,50.50,52.00,50.00,51.80,150000
        """.trimIndent()

        val bars = client.parseCsv(csv)

        assertEquals(listOf("2026-06-01", "2026-06-02", "2026-06-03"), bars.map { it.date })
    }

    @Test
    fun `parseCsv N-D 결측 행은 스킵`() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2026-06-01,50.00,51.00,49.50,50.50,120000
            2026-06-02,N/D,N/D,N/D,N/D,N/D
            2026-06-03,51.80,53.00,51.50,52.90,98000
        """.trimIndent()

        val bars = client.parseCsv(csv)

        assertEquals(2, bars.size)
    }

    @Test
    fun `parseCsv 헤더만 있으면 빈 리스트`() {
        val csv = "Date,Open,High,Low,Close,Volume"

        assertTrue(client.parseCsv(csv).isEmpty())
    }

    @Test
    fun `parseCsv 심볼 없음(No data) 응답은 빈 리스트`() {
        val csv = "No data"

        assertTrue(client.parseCsv(csv).isEmpty())
    }

    @Test
    fun `parseCsv 빈 문자열은 빈 리스트`() {
        assertTrue(client.parseCsv("").isEmpty())
    }

    @Test
    fun `parseCsv 컬럼 부족한 행은 스킵`() {
        val csv = """
            Date,Open,High,Low,Close,Volume
            2026-06-01,50.00,51.00
            2026-06-02,50.50,52.00,50.00,51.80,150000
        """.trimIndent()

        val bars = client.parseCsv(csv)

        assertEquals(1, bars.size)
        assertEquals("2026-06-02", bars[0].date)
    }
}
