package com.tinyoscillator.feature.bearsignal.data.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FredApiClient] 응답 파싱 테스트 — FRED `series/observations` 실 응답 형태 fixture로 검증
 * (TASK.md §4 "미 연준 상단").
 */
class FredApiClientTest {

    private val client = FredApiClient(httpClient = OkHttpClient(), json = Json { ignoreUnknownKeys = true })

    @Test
    fun `parseLatestValid 최신 관측값(첫 행) 반환`() {
        val fixture = """
            {
              "realtime_start": "2026-07-09",
              "realtime_end": "2026-07-09",
              "observation_start": "1600-01-01",
              "observation_end": "9999-12-31",
              "units": "lin",
              "output_type": 1,
              "file_type": "json",
              "order_by": "observation_date",
              "sort_order": "desc",
              "count": 3,
              "offset": 0,
              "limit": 10,
              "observations": [
                { "realtime_start": "2026-07-09", "realtime_end": "2026-07-09", "date": "2026-06-30", "value": "3.75" },
                { "realtime_start": "2026-07-09", "realtime_end": "2026-07-09", "date": "2026-06-29", "value": "3.75" },
                { "realtime_start": "2026-07-09", "realtime_end": "2026-07-09", "date": "2026-06-28", "value": "3.75" }
              ]
            }
        """.trimIndent()

        val result = client.parseLatestValid(fixture)

        assertEquals("2026-06-30", result!!.date)
        assertEquals(3.75, result.value, 1e-9)
    }

    @Test
    fun `parseLatestValid 결측치(마침표) 건너뛰고 다음 유효값 반환`() {
        val fixture = """
            {
              "observations": [
                { "date": "2026-07-05", "value": "." },
                { "date": "2026-07-04", "value": "." },
                { "date": "2026-07-03", "value": "4.00" }
              ]
            }
        """.trimIndent()

        val result = client.parseLatestValid(fixture)

        assertEquals("2026-07-03", result!!.date)
        assertEquals(4.00, result.value, 1e-9)
    }

    @Test
    fun `parseLatestValid observations 전부 결측치면 null`() {
        val fixture = """{ "observations": [ { "date": "2026-07-05", "value": "." } ] }"""

        assertNull(client.parseLatestValid(fixture))
    }

    @Test
    fun `parseLatestValid observations 필드 없으면 null`() {
        assertNull(client.parseLatestValid("""{ "error_message": "Bad Request" }"""))
    }

    @Test
    fun `parseLatestValid 손상된 JSON은 null(예외 방어)`() {
        assertNull(client.parseLatestValid("not a json"))
    }

    @Test
    fun `parseLatestValid 빈 observations는 null`() {
        assertNull(client.parseLatestValid("""{ "observations": [] }"""))
    }
}
