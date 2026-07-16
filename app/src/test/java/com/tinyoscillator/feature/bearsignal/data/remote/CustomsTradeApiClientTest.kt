package com.tinyoscillator.feature.bearsignal.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CustomsTradeApiClient] 응답 파싱 테스트 — 관세청 품목별 수출입실적(GW, 15101609)
 * `getItemtradeList` XML 응답 fixture로 검증한다(TASK.md §4 "수출 비중").
 * fixture 구조는 2026-07-16 실키 호출 응답 원문 형태를 따른다(XML 전용, `hsCode` 10단위,
 * `year`는 "yyyy.mm" 표기).
 */
class CustomsTradeApiClientTest {

    private val client = CustomsTradeApiClient(httpClient = OkHttpClient())

    private val successFixture = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <response>
          <header><resultCode>00</resultCode><resultMsg>정상서비스.</resultMsg></header>
          <body><items>
            <item><balPayments>18100000</balPayments><expDlr>23100000</expDlr><expWgt>100</expWgt><hsCode>8542321000</hsCode><impDlr>5000000</impDlr><impWgt>50</impWgt><statKor>메모리</statKor><year>2026.05</year></item>
            <item><balPayments>14000000</balPayments><expDlr>15000000</expDlr><expWgt>200</expWgt><hsCode>8703231010</hsCode><impDlr>1000000</impDlr><impWgt>80</impWgt><statKor>승용자동차</statKor><year>2026.05</year></item>
            <item><balPayments>8000000</balPayments><expDlr>10000000</expDlr><expWgt>300</expWgt><hsCode>8450111000</hsCode><impDlr>2000000</impDlr><impWgt>90</impWgt><statKor>세탁기</statKor><year>2026.05</year></item>
            <item><balPayments>5000000</balPayments><expDlr>8000000</expDlr><expWgt>400</expWgt><hsCode>2710192000</hsCode><impDlr>3000000</impDlr><impWgt>120</impWgt><statKor>경유</statKor><year>2026.05</year></item>
          </items></body>
        </response>
    """.trimIndent()

    @Test
    fun `parseResponse 정상 XML 응답 4건 파싱`() {
        val items = client.parseResponse(successFixture)

        assertEquals(4, items.size)
        val semi = items.first { it.statKor == "메모리" }
        assertEquals("8542321000", semi.hsCd)
        assertEquals(23_100_000.0, semi.exportUsd, 1e-6)
        assertEquals(5_000_000.0, semi.importUsd, 1e-6)
        assertEquals("2026.05", semi.yearMonth)
    }

    @Test
    fun `parseResponse expDlr 0 항목은 스킵하지 않는다 - 수입 전용 품목`() {
        val fixture = """
            <response>
              <header><resultCode>00</resultCode><resultMsg>정상서비스.</resultMsg></header>
              <body><items>
                <item><expDlr>0</expDlr><hsCode>0101291000</hsCode><impDlr>445409</impDlr><statKor>경주마</statKor><year>2026.05</year></item>
              </items></body>
            </response>
        """.trimIndent()

        val items = client.parseResponse(fixture)

        assertEquals(1, items.size)
        assertEquals(0.0, items[0].exportUsd, 1e-6)
        assertEquals(445_409.0, items[0].importUsd, 1e-6)
    }

    @Test
    fun `parseResponse 월 총계 행(hsCode 하이픈)은 제외한다 - 분모 2배 오염 방지`() {
        // 실응답 말미의 총계 행(2026-07-16 실측): expDlr가 그 달 총수출 전체와 같아
        // 포함 시 semi 분모가 정확히 2배가 된다.
        val fixture = """
            <response>
              <header><resultCode>00</resultCode><resultMsg>정상서비스.</resultMsg></header>
              <body><items>
                <item><expDlr>23100000</expDlr><hsCode>8542321000</hsCode><impDlr>5000000</impDlr><statKor>메모리</statKor><year>2026.05</year></item>
                <item><balPayments>26840938027</balPayments><expDlr>87583046102</expDlr><expWgt>13571773579</expWgt><hsCode>-</hsCode><impDlr>60742108075</impDlr><impWgt>39665399024</impWgt><statKor>-</statKor><year>총계</year></item>
              </items></body>
            </response>
        """.trimIndent()

        val items = client.parseResponse(fixture)

        assertEquals(1, items.size)
        assertEquals("8542321000", items[0].hsCd)
    }

    @Test
    fun `parseResponse hsCode 태그 누락 항목은 스킵`() {
        val fixture = """
            <response>
              <header><resultCode>00</resultCode><resultMsg>정상서비스.</resultMsg></header>
              <body><items>
                <item><expDlr>100</expDlr><impDlr>0</impDlr><statKor>품목</statKor><year>2026.05</year></item>
              </items></body>
            </response>
        """.trimIndent()

        assertTrue(client.parseResponse(fixture).isEmpty())
    }

    @Test
    fun `parseResponse expDlr 태그 누락 항목은 스킵`() {
        val fixture = """
            <response>
              <header><resultCode>00</resultCode><resultMsg>정상서비스.</resultMsg></header>
              <body><items>
                <item><hsCode>8542321000</hsCode><impDlr>5000000</impDlr><statKor>메모리</statKor><year>2026.05</year></item>
                <item><expDlr>15000000</expDlr><hsCode>8703231010</hsCode><impDlr>1000000</impDlr><statKor>승용자동차</statKor><year>2026.05</year></item>
              </items></body>
            </response>
        """.trimIndent()

        val items = client.parseResponse(fixture)

        assertEquals(1, items.size)
        assertEquals("승용자동차", items[0].statKor)
    }

    @Test
    fun `parseResponse 오류 코드 응답은 빈 리스트`() {
        val errorFixture = """
            <response>
              <header><resultCode>30</resultCode><resultMsg>SERVICE KEY IS NOT REGISTERED ERROR.</resultMsg></header>
              <body><items/></body>
            </response>
        """.trimIndent()

        assertTrue(client.parseResponse(errorFixture).isEmpty())
    }

    @Test
    fun `parseResponse 빈 items는 빈 리스트`() {
        val emptyFixture = """
            <response>
              <header><resultCode>00</resultCode><resultMsg>정상서비스.</resultMsg></header>
              <body><items></items></body>
            </response>
        """.trimIndent()

        assertTrue(client.parseResponse(emptyFixture).isEmpty())
    }

    @Test
    fun `parseResponse 게이트웨이 오류 텍스트(비XML)는 빈 리스트`() {
        assertTrue(client.parseResponse("Forbidden").isEmpty())
        assertTrue(client.parseResponse("Unauthorized").isEmpty())
    }

    @Test
    fun `parseResponse resultCode 없는 임의 XML도 빈 리스트`() {
        assertTrue(client.parseResponse("<html><body>error page</body></html>").isEmpty())
    }

    // ── encodeServiceKey — data.go.kr 인증키 percent-encode (403 원인 수정, 2026-07-16) ──

    @Test
    fun `encodeServiceKey 원문(Decoding) 키의 + 슬래시 =는 percent-encode된다`() {
        assertEquals(
            "abc%2Bdef%2Fghi%3D%3D",
            CustomsTradeApiClient.encodeServiceKey("abc+def/ghi==")
        )
    }

    @Test
    fun `encodeServiceKey 이미 인코딩된(Encoding) 키는 그대로 반환한다 - 이중 인코딩 방지`() {
        assertEquals(
            "abc%2Bdef%2Fghi%3D%3D",
            CustomsTradeApiClient.encodeServiceKey("abc%2Bdef%2Fghi%3D%3D")
        )
    }

    @Test
    fun `encodeServiceKey 특수문자 없는 키는 무변경`() {
        assertEquals("plainkey123", CustomsTradeApiClient.encodeServiceKey("plainkey123"))
    }
}
