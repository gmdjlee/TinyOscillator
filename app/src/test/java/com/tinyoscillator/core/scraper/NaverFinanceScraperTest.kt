package com.tinyoscillator.core.scraper

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * NaverFinanceScraper 단위 테스트
 *
 * parseDate, parseNumber, parseHtml 메서드를 reflection으로 테스트
 */
class NaverFinanceScraperTest {

    private lateinit var scraper: NaverFinanceScraper
    private lateinit var parseDateMethod: Method
    private lateinit var parseNumberMethod: Method
    private lateinit var parseHtmlMethod: Method

    @Before
    fun setUp() {
        scraper = NaverFinanceScraper()

        parseDateMethod = NaverFinanceScraper::class.java
            .getDeclaredMethod("parseDate", String::class.java)
            .also { it.isAccessible = true }

        parseNumberMethod = NaverFinanceScraper::class.java
            .getDeclaredMethod("parseNumber", String::class.java)
            .also { it.isAccessible = true }

        parseHtmlMethod = NaverFinanceScraper::class.java
            .getDeclaredMethod("parseHtml", String::class.java)
            .also { it.isAccessible = true }
    }

    // ========== Helper ==========

    private fun parseDate(s: String): String? =
        parseDateMethod.invoke(scraper, s) as String?

    private fun parseNumber(s: String): Double =
        parseNumberMethod.invoke(scraper, s) as Double

    @Suppress("UNCHECKED_CAST")
    private fun parseHtml(html: String): List<Any> =
        parseHtmlMethod.invoke(scraper, html) as List<Any>

    // ========== parseDate - YYYY-MM-DD 형식 ==========

    @Test
    fun `parseDate - YYYY-MM-DD 정상 파싱`() {
        assertEquals("2024-03-15", parseDate("2024-03-15"))
    }

    @Test
    fun `parseDate - YYYY-MM-DD 연초 날짜`() {
        assertEquals("2024-01-01", parseDate("2024-01-01"))
    }

    @Test
    fun `parseDate - YYYY-MM-DD 연말 날짜`() {
        assertEquals("2024-12-31", parseDate("2024-12-31"))
    }

    // ========== parseDate - YYYY.MM.DD 형식 ==========

    @Test
    fun `parseDate - YYYY dot MM dot DD 정상 파싱`() {
        assertEquals("2024-03-15", parseDate("2024.03.15"))
    }

    @Test
    fun `parseDate - YYYY dot M dot D 한자리 월일 패딩`() {
        assertEquals("2024-03-05", parseDate("2024.3.5"))
    }

    @Test
    fun `parseDate - YYYY dot MM dot DD 공백 포함`() {
        assertEquals("2024-03-15", parseDate("2024. 03. 15"))
    }

    // ========== parseDate - YY.MM.DD 2자리 연도 ==========

    @Test
    fun `parseDate - YY dot MM dot DD 2자리 연도`() {
        assertEquals("2024-03-15", parseDate("24.03.15"))
    }

    @Test
    fun `parseDate - YY dot M dot D 2자리 연도 한자리 월일`() {
        assertEquals("2024-01-05", parseDate("24.1.5"))
    }

    @Test
    fun `parseDate - YY dot MM dot DD 00년대`() {
        assertEquals("2005-06-07", parseDate("05.06.07"))
    }

    // ========== parseDate - YYYYMMDD 형식 ==========

    @Test
    fun `parseDate - YYYYMMDD 8자리 정상 파싱`() {
        assertEquals("2024-03-15", parseDate("20240315"))
    }

    @Test
    fun `parseDate - YYYYMMDD 연초`() {
        assertEquals("2024-01-01", parseDate("20240101"))
    }

    @Test
    fun `parseDate - YYYYMMDD 연말`() {
        assertEquals("2024-12-31", parseDate("20241231"))
    }

    // ========== parseDate - YYYY/MM/DD 형식 ==========

    @Test
    fun `parseDate - YYYY slash MM slash DD 정상 파싱`() {
        assertEquals("2024-03-15", parseDate("2024/03/15"))
    }

    @Test
    fun `parseDate - YYYY slash M slash D 한자리 월일 패딩`() {
        assertEquals("2024-03-05", parseDate("2024/3/5"))
    }

    @Test
    fun `parseDate - YY slash MM slash DD 2자리 연도`() {
        assertEquals("2024-03-15", parseDate("24/03/15"))
    }

    @Test
    fun `parseDate - YYYY slash MM slash DD 공백 포함`() {
        assertEquals("2024-03-15", parseDate("2024/ 03/ 15"))
    }

    // ========== parseDate - 엣지 케이스 ==========

    @Test
    fun `parseDate - 빈 문자열은 null`() {
        assertNull(parseDate(""))
    }

    @Test
    fun `parseDate - 공백만 있으면 null`() {
        assertNull(parseDate("   "))
    }

    @Test
    fun `parseDate - 잘못된 형식은 null`() {
        assertNull(parseDate("abc"))
    }

    @Test
    fun `parseDate - 숫자만 있지만 8자리 아님 null`() {
        assertNull(parseDate("202403"))
    }

    @Test
    fun `parseDate - 9자리 숫자는 null`() {
        assertNull(parseDate("202403150"))
    }

    @Test
    fun `parseDate - dot 2개 미만은 null`() {
        assertNull(parseDate("2024.03"))
    }

    @Test
    fun `parseDate - slash 2개 미만은 null`() {
        assertNull(parseDate("2024/03"))
    }

    @Test
    fun `parseDate - 앞뒤 공백 제거 후 정상 파싱`() {
        assertEquals("2024-03-15", parseDate("  2024-03-15  "))
    }

    // ========== parseNumber - 기본 ==========

    @Test
    fun `parseNumber - 정수 파싱`() {
        assertEquals(12345.0, parseNumber("12345"), 0.001)
    }

    @Test
    fun `parseNumber - 소수 파싱`() {
        assertEquals(123.45, parseNumber("123.45"), 0.001)
    }

    @Test
    fun `parseNumber - 음수 파싱`() {
        assertEquals(-500.0, parseNumber("-500"), 0.001)
    }

    @Test
    fun `parseNumber - 음수 소수 파싱`() {
        assertEquals(-12.34, parseNumber("-12.34"), 0.001)
    }

    // ========== parseNumber - 한국어 단위 제거 ==========

    @Test
    fun `parseNumber - 억원 단위 제거`() {
        assertEquals(1234.0, parseNumber("1,234억원"), 0.001)
    }

    @Test
    fun `parseNumber - 억 단위 제거`() {
        assertEquals(5678.0, parseNumber("5,678억"), 0.001)
    }

    @Test
    fun `parseNumber - 억원 단위 공백 포함`() {
        assertEquals(100.0, parseNumber(" 100억원 "), 0.001)
    }

    // ========== parseNumber - 콤마 제거 ==========

    @Test
    fun `parseNumber - 천 단위 콤마 제거`() {
        assertEquals(1234567.0, parseNumber("1,234,567"), 0.001)
    }

    @Test
    fun `parseNumber - 콤마와 억원 혼합`() {
        assertEquals(12345.0, parseNumber("12,345억원"), 0.001)
    }

    @Test
    fun `parseNumber - 음수 콤마`() {
        assertEquals(-1234.0, parseNumber("-1,234"), 0.001)
    }

    // ========== parseNumber - 빈 값 처리 ==========

    @Test
    fun `parseNumber - 빈 문자열은 0`() {
        assertEquals(0.0, parseNumber(""), 0.001)
    }

    @Test
    fun `parseNumber - 공백만 있으면 0`() {
        assertEquals(0.0, parseNumber("   "), 0.001)
    }

    @Test
    fun `parseNumber - 대시는 0`() {
        assertEquals(0.0, parseNumber("-"), 0.001)
    }

    @Test
    fun `parseNumber - 파싱 불가능한 문자열은 0`() {
        assertEquals(0.0, parseNumber("abc"), 0.001)
    }

    @Test
    fun `parseNumber - 0 파싱`() {
        assertEquals(0.0, parseNumber("0"), 0.001)
    }

    // ========== parseHtml - 정상 테이블 ==========

    @Test
    fun `parseHtml - 유효한 테이블에서 레코드 추출`() {
        val html = buildValidTableHtml(
            listOf(
                listOf("2024-03-15", "1,234", "56", "789", "12"),
                listOf("2024-03-14", "1,200", "-34", "800", "11")
            )
        )
        val result = parseHtml(html)
        assertEquals(2, result.size)
    }

    @Test
    fun `parseHtml - 첫 2행(헤더) 건너뜀`() {
        val html = """
            <table class="type_1">
                <tr><th>날짜</th><th>예탁금</th><th>증감</th><th>신용</th><th>증감</th></tr>
                <tr><th>서브헤더</th><th>-</th><th>-</th><th>-</th><th>-</th></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td><td>789</td><td>12</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertEquals(1, result.size)
    }

    @Test
    fun `parseHtml - 날짜 필드 비어있으면 해당 행 스킵`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><td></td><td>1,234</td><td>56</td><td>789</td><td>12</td></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td><td>789</td><td>12</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertEquals(1, result.size)
    }

    // ========== parseHtml - 테이블 없음 ==========

    @Test
    fun `parseHtml - type_1 테이블 없으면 빈 리스트`() {
        val html = "<html><body><table class='other'><tr><td>data</td></tr></table></body></html>"
        val result = parseHtml(html)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseHtml - 빈 HTML은 빈 리스트`() {
        val result = parseHtml("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseHtml - 테이블 없는 HTML은 빈 리스트`() {
        val result = parseHtml("<html><body><p>no table</p></body></html>")
        assertTrue(result.isEmpty())
    }

    // ========== parseHtml - 5개 미만 컬럼 ==========

    @Test
    fun `parseHtml - 5개 미만 컬럼 행은 스킵`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th></tr>
                <tr><th>h1</th><th>h2</th></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td></tr>
                <tr><td>2024-03-14</td><td>1,200</td><td>-34</td><td>800</td><td>11</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertEquals(1, result.size)
    }

    @Test
    fun `parseHtml - 4개 컬럼만 있으면 스킵`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th></tr>
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td><td>789</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertTrue(result.isEmpty())
    }

    // ========== parseHtml - 데이터 정합성 ==========

    @Test
    fun `parseHtml - DepositRecord 필드값 검증`() {
        val html = buildValidTableHtml(
            listOf(listOf("2024-03-15", "1,234", "-56", "789", "12"))
        )
        val result = parseHtml(html)
        assertEquals(1, result.size)

        // reflection으로 DepositRecord 필드 확인
        val record = result[0]
        val recordClass = record::class.java
        assertEquals("2024-03-15", recordClass.getDeclaredField("date").also { it.isAccessible = true }.get(record))
        assertEquals(1234.0, recordClass.getDeclaredField("depositAmount").also { it.isAccessible = true }.get(record) as Double, 0.001)
        assertEquals(-56.0, recordClass.getDeclaredField("depositChange").also { it.isAccessible = true }.get(record) as Double, 0.001)
        assertEquals(789.0, recordClass.getDeclaredField("creditAmount").also { it.isAccessible = true }.get(record) as Double, 0.001)
        assertEquals(12.0, recordClass.getDeclaredField("creditChange").also { it.isAccessible = true }.get(record) as Double, 0.001)
    }

    @Test
    fun `parseHtml - 억원 단위 포함된 데이터 파싱`() {
        val html = buildValidTableHtml(
            listOf(listOf("2024-03-15", "1,234억원", "56억원", "789억", "12억"))
        )
        val result = parseHtml(html)
        assertEquals(1, result.size)

        val record = result[0]
        val recordClass = record::class.java
        assertEquals(1234.0, recordClass.getDeclaredField("depositAmount").also { it.isAccessible = true }.get(record) as Double, 0.001)
        assertEquals(56.0, recordClass.getDeclaredField("depositChange").also { it.isAccessible = true }.get(record) as Double, 0.001)
    }

    @Test
    fun `parseHtml - dot 형식 날짜도 파싱`() {
        val html = buildValidTableHtml(
            listOf(listOf("2024.03.15", "1,234", "56", "789", "12"))
        )
        val result = parseHtml(html)
        assertEquals(1, result.size)

        val record = result[0]
        val recordClass = record::class.java
        assertEquals("2024-03-15", recordClass.getDeclaredField("date").also { it.isAccessible = true }.get(record))
    }

    @Test
    fun `parseHtml - YYYYMMDD 형식 날짜도 파싱`() {
        val html = buildValidTableHtml(
            listOf(listOf("20240315", "1,234", "56", "789", "12"))
        )
        val result = parseHtml(html)
        assertEquals(1, result.size)

        val record = result[0]
        val recordClass = record::class.java
        assertEquals("2024-03-15", recordClass.getDeclaredField("date").also { it.isAccessible = true }.get(record))
    }

    // ========== parseHtml - 빈 행 처리 ==========

    @Test
    fun `parseHtml - 빈 행이 섞여있어도 정상 파싱`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td><td>789</td><td>12</td></tr>
                <tr></tr>
                <tr><td>2024-03-14</td><td>1,200</td><td>-34</td><td>800</td><td>11</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertEquals(2, result.size)
    }

    @Test
    fun `parseHtml - 헤더만 있고 데이터 없으면 빈 리스트`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertTrue(result.isEmpty())
    }

    // ========== parseHtml - 여러 행 ==========

    @Test
    fun `parseHtml - 여러 행 정상 파싱`() {
        val rows = (1..10).map { i ->
            listOf("2024-03-${i.toString().padStart(2, '0')}", "${i * 100}", "$i", "${i * 50}", "${i * 2}")
        }
        val html = buildValidTableHtml(rows)
        val result = parseHtml(html)
        assertEquals(10, result.size)
    }

    // ========== parseHtml - 잘못된 날짜 행 스킵 ==========

    @Test
    fun `parseHtml - 파싱 불가 날짜 행은 스킵`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th></tr>
                <tr><td>invalid</td><td>1,234</td><td>56</td><td>789</td><td>12</td></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td><td>789</td><td>12</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertEquals(1, result.size)
    }

    // ========== scrapeDepositData - 입력 검증 ==========

    @Test
    fun `scrapeDepositData - numPages 0이면 null`() = runTest {
        val result = scraper.scrapeDepositData(0)
        assertNull(result)
    }

    @Test
    fun `scrapeDepositData - numPages 음수이면 null`() = runTest {
        val result = scraper.scrapeDepositData(-1)
        assertNull(result)
    }

    // ========== parseHtml - 5개 초과 컬럼은 처리 ==========

    @Test
    fun `parseHtml - 6개 이상 컬럼도 첫 5개로 정상 파싱`() {
        val html = """
            <table class="type_1">
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th><th>h6</th></tr>
                <tr><th>h1</th><th>h2</th><th>h3</th><th>h4</th><th>h5</th><th>h6</th></tr>
                <tr><td>2024-03-15</td><td>1,234</td><td>56</td><td>789</td><td>12</td><td>extra</td></tr>
            </table>
        """.trimIndent()
        val result = parseHtml(html)
        assertEquals(1, result.size)
    }

    // ========== parseNumber - 추가 엣지 케이스 ==========

    @Test
    fun `parseNumber - 단위만 있는 경우 0`() {
        assertEquals(0.0, parseNumber("억원"), 0.001)
    }

    @Test
    fun `parseNumber - 양수 부호`() {
        // "+"는 toDoubleOrNull이 처리 가능
        assertEquals(100.0, parseNumber("+100"), 0.001)
    }

    @Test
    fun `parseNumber - 매우 큰 수`() {
        assertEquals(999999999.0, parseNumber("999,999,999"), 0.001)
    }

    // ========== Helper 함수 ==========

    private fun buildValidTableHtml(rows: List<List<String>>): String {
        val rowsHtml = rows.joinToString("\n") { cols ->
            "<tr>${cols.joinToString("") { "<td>$it</td>" }}</tr>"
        }
        return """
            <html><body>
            <table class="type_1">
                <tr><th>날짜</th><th>고객예탁금</th><th>증감</th><th>신용잔고</th><th>증감</th></tr>
                <tr><th>서브헤더</th><th>-</th><th>-</th><th>-</th><th>-</th></tr>
                $rowsHtml
            </table>
            </body></html>
        """.trimIndent()
    }
}
