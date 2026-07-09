package com.tinyoscillator.feature.bearsignal.data.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CustomsTradeApiClient] 응답 파싱 테스트 — 공공데이터포털(data.go.kr) 표준 응답 래퍼
 * (`response.header`/`response.body.items.item[]`) 기반 fixture로 검증한다(TASK.md §4 "수출 비중").
 */
class CustomsTradeApiClientTest {

    private val client = CustomsTradeApiClient(httpClient = OkHttpClient(), json = Json { ignoreUnknownKeys = true })

    private val successFixture = """
        {
          "response": {
            "header": { "resultCode": "00", "resultMsg": "NORMAL_CODE" },
            "body": {
              "items": {
                "item": [
                  { "statKor": "반도체", "hsCd": "854239", "expDlr": "23100000", "impDlr": "5000000", "year": "202605" },
                  { "statKor": "자동차", "hsCd": "870323", "expDlr": "15000000", "impDlr": "1000000", "year": "202605" },
                  { "statKor": "일반기계", "hsCd": "845011", "expDlr": "10000000", "impDlr": "2000000", "year": "202605" },
                  { "statKor": "석유제품", "hsCd": "271019", "expDlr": "8000000", "impDlr": "3000000", "year": "202605" }
                ]
              },
              "numOfRows": 10,
              "pageNo": 1,
              "totalCount": 4
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parseResponse 정상 응답 4건 파싱`() {
        val items = client.parseResponse(successFixture)

        assertEquals(4, items.size)
        val semi = items.first { it.statKor == "반도체" }
        assertEquals("854239", semi.hsCd)
        assertEquals(23_100_000.0, semi.exportUsdThousand, 1e-6)
        assertEquals(5_000_000.0, semi.importUsdThousand, 1e-6)
        assertEquals("202605", semi.yearMonth)
    }

    @Test
    fun `parseResponse item이 단건(JsonObject)이어도 리스트로 파싱`() {
        val singleItemFixture = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL_CODE" },
                "body": {
                  "items": {
                    "item": { "statKor": "반도체", "hsCd": "854239", "expDlr": "23100000", "impDlr": "5000000", "year": "202605" }
                  },
                  "numOfRows": 1, "pageNo": 1, "totalCount": 1
                }
              }
            }
        """.trimIndent()

        val items = client.parseResponse(singleItemFixture)

        assertEquals(1, items.size)
        assertEquals("반도체", items[0].statKor)
    }

    @Test
    fun `parseResponse 콤마 포함 금액 문자열도 파싱`() {
        val commaFixture = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL_CODE" },
                "body": {
                  "items": { "item": [ { "statKor": "반도체", "hsCd": "854239", "expDlr": "23,100,000", "impDlr": "5,000,000", "year": "202605" } ] },
                  "numOfRows": 1, "pageNo": 1, "totalCount": 1
                }
              }
            }
        """.trimIndent()

        val items = client.parseResponse(commaFixture)

        assertEquals(23_100_000.0, items[0].exportUsdThousand, 1e-6)
    }

    @Test
    fun `parseResponse 오류 코드 응답은 빈 리스트`() {
        val errorFixture = """
            {
              "response": {
                "header": { "resultCode": "30", "resultMsg": "SERVICE KEY IS NOT REGISTERED ERROR" },
                "body": { "items": { "item": [] }, "numOfRows": 0, "pageNo": 1, "totalCount": 0 }
              }
            }
        """.trimIndent()

        val items = client.parseResponse(errorFixture)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `parseResponse 빈 items는 빈 리스트`() {
        val emptyFixture = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL_CODE" },
                "body": { "items": { "item": [] }, "numOfRows": 0, "pageNo": 1, "totalCount": 0 }
              }
            }
        """.trimIndent()

        assertTrue(client.parseResponse(emptyFixture).isEmpty())
    }

    @Test
    fun `parseResponse 손상된 JSON은 빈 리스트(예외 방어)`() {
        assertTrue(client.parseResponse("not a json").isEmpty())
    }

    @Test
    fun `parseResponse expDlr 누락 항목은 스킵`() {
        val fixture = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL_CODE" },
                "body": {
                  "items": {
                    "item": [
                      { "statKor": "반도체", "hsCd": "854239", "impDlr": "5000000", "year": "202605" },
                      { "statKor": "자동차", "hsCd": "870323", "expDlr": "15000000", "impDlr": "1000000", "year": "202605" }
                    ]
                  },
                  "numOfRows": 2, "pageNo": 1, "totalCount": 2
                }
              }
            }
        """.trimIndent()

        val items = client.parseResponse(fixture)

        assertEquals(1, items.size)
        assertEquals("자동차", items[0].statKor)
    }
}
