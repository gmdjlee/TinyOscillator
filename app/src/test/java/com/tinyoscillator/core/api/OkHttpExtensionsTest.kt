package com.tinyoscillator.core.api

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * OkHttp suspend extension 테스트.
 */
class OkHttpExtensionsTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `await는 성공 응답을 반환한다`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"status": "ok"}""")
            .setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            assertEquals(200, it.code)
            val body = it.body?.string()
            assertNotNull(body)
            assertTrue(body!!.contains("ok"))
        }
    }

    @Test
    fun `await는 4xx 응답도 반환한다`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"error": "not found"}""")
            .setResponseCode(404))

        val request = Request.Builder()
            .url(server.url("/missing"))
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            assertEquals(404, it.code)
        }
    }

    @Test
    fun `await는 5xx 응답도 반환한다`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"error": "internal"}""")
            .setResponseCode(500))

        val request = Request.Builder()
            .url(server.url("/error"))
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            assertEquals(500, it.code)
        }
    }

    @Test
    fun `await는 빈 응답 본문도 처리한다`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(204))

        val request = Request.Builder()
            .url(server.url("/empty"))
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            assertEquals(204, it.code)
        }
    }

    @Test
    fun `await는 큰 응답도 처리한다`() = runTest {
        val largeBody = "x".repeat(100_000)
        server.enqueue(MockResponse()
            .setBody(largeBody)
            .setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/large"))
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            assertEquals(200, it.code)
            assertEquals(100_000, it.body?.string()?.length)
        }
    }

    @Test
    fun `await는 헤더를 정상적으로 전달한다`() = runTest {
        server.enqueue(MockResponse()
            .setBody("{}")
            .setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/headers"))
            .addHeader("X-Custom", "test-value")
            .addHeader("Authorization", "Bearer token123")
            .get()
            .build()

        httpClient.newCall(request).await().use { it.body?.string() }

        val recorded = server.takeRequest()
        assertEquals("test-value", recorded.getHeader("X-Custom"))
        assertEquals("Bearer token123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `await는 POST 요청도 지원한다`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"created": true}""")
            .setResponseCode(201))

        val request = Request.Builder()
            .url(server.url("/create"))
            .post(
                """{"name": "test"}""".toRequestBody("application/json".toMediaType())
            )
            .build()

        val response = httpClient.newCall(request).await()
        response.use {
            assertEquals(201, it.code)
        }

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("test"))
    }

    @Test
    fun `await는 연결 실패 시 IOException을 던진다`() = runTest {
        // 서버 종료 후 연결 시도
        server.shutdown()

        val request = Request.Builder()
            .url("http://localhost:${server.port}/fail")
            .get()
            .build()

        try {
            httpClient.newCall(request).await()
            fail("Expected IOException")
        } catch (e: java.io.IOException) {
            // Expected
        }
    }
}
