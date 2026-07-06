package com.tinyoscillator.core.api

import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DartApiClientParseTest {

    private lateinit var client: DartApiClient

    @Before
    fun setup() {
        client = DartApiClient(httpClient = mockk<OkHttpClient>(relaxed = true))
    }

    @Test
    fun `corpCode XML 정상 파싱`() {
        val xml = """
            <result>
              <list>
                <corp_code>00126380</corp_code>
                <corp_name>삼성전자</corp_name>
                <stock_code>005930</stock_code>
                <modify_date>20240101</modify_date>
              </list>
              <list>
                <corp_code>00164742</corp_code>
                <corp_name>현대자동차</corp_name>
                <stock_code>005380</stock_code>
              </list>
            </result>
        """.trimIndent()

        val entries = client.parseCorpCodeXml(xml)

        assertEquals(2, entries.size)
        assertEquals("00126380", entries[0].corpCode)
        assertEquals("삼성전자", entries[0].corpName)
        assertEquals("005930", entries[0].stockCode)
        assertEquals("005380", entries[1].stockCode)
    }

    @Test
    fun `비상장(stock_code 공백) 엔트리는 빈 stockCode로 파싱`() {
        val xml = """
            <result>
              <list>
                <corp_code>00999999</corp_code>
                <corp_name>비상장회사</corp_name>
                <stock_code> </stock_code>
              </list>
            </result>
        """.trimIndent()

        val entries = client.parseCorpCodeXml(xml)

        assertEquals(1, entries.size)
        assertEquals("", entries[0].stockCode)
    }

    @Test
    fun `corp_code 없는 엔트리는 건너뜀`() {
        val xml = """
            <result>
              <list><corp_name>이름만</corp_name></list>
              <list><corp_code>00000001</corp_code><corp_name>정상</corp_name><stock_code>000001</stock_code></list>
            </result>
        """.trimIndent()

        val entries = client.parseCorpCodeXml(xml)

        assertEquals(1, entries.size)
        assertEquals("00000001", entries[0].corpCode)
    }

    @Test
    fun `대용량(10만 엔트리) 파싱이 수 초 내 완료`() {
        val sb = StringBuilder("<result>")
        repeat(100_000) { i ->
            sb.append("<list><corp_code>")
                .append(String.format("%08d", i))
                .append("</corp_code><corp_name>회사")
                .append(i)
                .append("</corp_name><stock_code>")
                .append(String.format("%06d", i % 1_000_000))
                .append("</stock_code><modify_date>20240101</modify_date></list>")
        }
        sb.append("</result>")

        val start = System.currentTimeMillis()
        val entries = client.parseCorpCodeXml(sb.toString())
        val elapsed = System.currentTimeMillis() - start

        assertEquals(100_000, entries.size)
        // 기존 정규식 파서는 저사양 기기에서 수 분 소요 — 회귀 방지 상한
        assertTrue("파싱 ${elapsed}ms — 10초 초과", elapsed < 10_000)
    }

    @Test
    fun `빈 XML은 빈 리스트`() {
        assertTrue(client.parseCorpCodeXml("").isEmpty())
        assertTrue(client.parseCorpCodeXml("<result></result>").isEmpty())
    }
}
