package com.tinyoscillator.core.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * [DartApiClient.fetchRecentDisclosures]의 detailType 루프(A001/B001/C001)가 각기 다른
 * `pblntf_detail_ty` 쿼리 파라미터로 요청하는지 검증 (P1a-1, TASK_code_review_improvements.md).
 *
 * 회귀 방지: 과거 `typeUrl`이 `pblntf_detail_ty`를 누락해 3회 모두 동일한 무필터 쿼리를
 * 던져 DART 일일 쿼터(10,000건)를 3배 낭비했다.
 */
class DartApiClientDisclosureUrlTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DartApiClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = DartApiClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/api").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `detailType 루프가 A001 B001 C001로 서로 다른 쿼리를 던진다`() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"000","list":[]}"""))
        }

        client.fetchRecentDisclosures(apiKey = "test-key", corpCode = "00126380", daysBack = 30)

        assertEquals(3, server.requestCount)

        val detailTypes = (0 until 3).map { index ->
            val request = server.takeRequest()
            val detailType = request.requestUrl?.queryParameter("pblntf_detail_ty")
            assertNotNull("요청 #$index 에 pblntf_detail_ty 파라미터가 있어야 한다", detailType)
            detailType
        }

        assertEquals(listOf("A001", "B001", "C001"), detailTypes)
    }
}
