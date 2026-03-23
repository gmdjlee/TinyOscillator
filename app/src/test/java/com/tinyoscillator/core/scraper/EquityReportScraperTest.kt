package com.tinyoscillator.core.scraper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EquityReportScraperTest {

    private lateinit var scraper: EquityReportScraper

    @Before
    fun setUp() {
        scraper = EquityReportScraper()
    }

    // ========== parseDate ==========

    @Test
    fun `parseDate - 26 slash 03 slash 23 returns 2026-03-23`() {
        assertEquals("2026-03-23", scraper.parseDate("26/03/23"))
    }

    @Test
    fun `parseDate - 25 slash 12 slash 01 returns 2025-12-01`() {
        assertEquals("2025-12-01", scraper.parseDate("25/12/01"))
    }

    @Test
    fun `parseDate - empty string returns null`() {
        assertNull(scraper.parseDate(""))
    }

    @Test
    fun `parseDate - invalid string returns null`() {
        assertNull(scraper.parseDate("invalid"))
    }

    // ========== parsePrice ==========

    @Test
    fun `parsePrice - 300,000 returns 300000`() {
        assertEquals(300000L, scraper.parsePrice("300,000"))
    }

    @Test
    fun `parsePrice - 0 returns 0`() {
        assertEquals(0L, scraper.parsePrice("0"))
    }

    @Test
    fun `parsePrice - empty string returns 0`() {
        assertEquals(0L, scraper.parsePrice(""))
    }

    @Test
    fun `parsePrice - dash returns 0`() {
        assertEquals(0L, scraper.parsePrice("-"))
    }

    @Test
    fun `parsePrice - 23,600 returns 23600`() {
        assertEquals(23600L, scraper.parsePrice("23,600"))
    }

    // ========== parsePage ==========

    @Test
    fun `parsePage - valid HTML table extracts reports`() {
        val html = buildEquityTableHtml(
            listOf(
                listOf("26/03/23", "IT", "Buy", "Strong Buy", "Buy", "삼성전자(005930) 목표가 상향", "홍길동", "미래에셋", "link", "300,000", "212,000", "41.51")
            )
        )
        val result = scraper.parsePage(html)
        assertEquals(1, result.size)
        val report = result[0]
        assertEquals("2026-03-23", report.writeDate)
        assertEquals("IT", report.category)
        assertEquals("Buy", report.opinion)
        assertEquals("005930", report.stockTicker)
        assertEquals("홍길동", report.author)
        assertEquals("미래에셋", report.institution)
        assertEquals(300000L, report.targetPrice)
        assertEquals(212000L, report.currentPrice)
    }

    @Test
    fun `parsePage - empty HTML returns empty list`() {
        val result = scraper.parsePage("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePage - no list table returns empty list`() {
        val html = "<html><body><table class='other'><tr><td>data</td></tr></table></body></html>"
        val result = scraper.parsePage(html)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePage - row without stock ticker is skipped`() {
        val html = buildEquityTableHtml(
            listOf(
                listOf("26/03/23", "IT", "Buy", "Strong Buy", "Buy", "제목만 있는 리포트", "홍길동", "미래에셋", "link", "300,000", "212,000", "41.51")
            )
        )
        val result = scraper.parsePage(html)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePage - row with fewer than 12 columns is skipped`() {
        val html = """
            <html><body>
            <table class="list">
                <tbody>
                    <tr><td>26/03/23</td><td>IT</td><td>Buy</td></tr>
                </tbody>
            </table>
            </body></html>
        """.trimIndent()
        val result = scraper.parsePage(html)
        assertTrue(result.isEmpty())
    }

    // ========== Helper ==========

    private fun buildEquityTableHtml(rows: List<List<String>>): String {
        val rowsHtml = rows.joinToString("\n") { cols ->
            val tds = cols.mapIndexed { i, v ->
                if (i == 5) "<td><a href=\"#\">$v</a></td>" else "<td>$v</td>"
            }.joinToString("")
            "<tr>$tds</tr>"
        }
        return """
            <html><body>
            <table class="list">
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
            </body></html>
        """.trimIndent()
    }
}
