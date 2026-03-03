package com.tinyoscillator.core.api

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JSON normalization scanner tests.
 *
 * Verifies the single-pass character scanner correctly removes leading '+' from
 * numeric values in various JSON contexts.
 */
class NormalizeJsonTest {

    private lateinit var client: KiwoomApiClient
    private lateinit var normalizeMethod: java.lang.reflect.Method

    @Before
    fun setup() {
        client = KiwoomApiClient(
            httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
            json = KiwoomApiClient.createDefaultJson()
        )
        normalizeMethod = KiwoomApiClient::class.java.getDeclaredMethod(
            "normalizeJsonNumbers", String::class.java
        ).apply { isAccessible = true }
    }

    private fun normalize(input: String): String =
        normalizeMethod.invoke(client, input) as String

    @Test
    fun `빈 문자열은 그대로 반환된다`() {
        assertEquals("", normalize(""))
    }

    @Test
    fun `+가 없는 JSON은 그대로 반환된다`() {
        val input = """{"value": 1234, "name": "test"}"""
        assertEquals(input, normalize(input))
    }

    @Test
    fun `인용된 +숫자에서 +가 제거된다`() {
        assertEquals("""{"value": "1234"}""", normalize("""{"value": "+1234"}"""))
    }

    @Test
    fun `콜론 뒤 +숫자에서 +가 제거된다`() {
        assertEquals("""{"value":1234}""", normalize("""{"value":+1234}"""))
    }

    @Test
    fun `콤마 뒤 +숫자에서 +가 제거된다`() {
        assertEquals("""[1234,5678]""", normalize("""[1234,+5678]"""))
    }

    @Test
    fun `공백 뒤 +숫자에서 +가 제거된다`() {
        assertEquals("""{"value": 1234}""", normalize("""{"value": +1234}"""))
    }

    @Test
    fun `문자열 중간의 +는 유지된다`() {
        val input = """{"email": "user+tag@mail.com"}"""
        assertEquals(input, normalize(input))
    }

    @Test
    fun `여러 +숫자가 모두 정규화된다`() {
        val input = """{"a": "+100", "b": "+200"}"""
        val expected = """{"a": "100", "b": "200"}"""
        assertEquals(expected, normalize(input))
    }

    @Test
    fun `+뒤에 숫자가 아니면 유지된다`() {
        val input = """{"text": "+abc"}"""
        assertEquals(input, normalize(input))
    }

    @Test
    fun `마지막 문자가 +이면 유지된다`() {
        val input = """{"value": "+"""
        // '+' at end, no digit after → preserved
        assertEquals(input, normalize(input))
    }

    @Test
    fun `큰 숫자도 정규화된다`() {
        assertEquals("""{"cap": "1234567890"}""", normalize("""{"cap": "+1234567890"}"""))
    }

    @Test
    fun `음수는 영향받지 않는다`() {
        val input = """{"value": -1234}"""
        assertEquals(input, normalize(input))
    }
}
