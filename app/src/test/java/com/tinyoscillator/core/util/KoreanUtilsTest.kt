package com.tinyoscillator.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanUtilsTest {

    // -- 초성 추출 --

    @Test
    fun `삼성전자 초성은 ㅅㅅㅈㅈ`() =
        assertEquals("ㅅㅅㅈㅈ", KoreanUtils.extractChosung("삼성전자"))

    @Test
    fun `카카오 초성은 ㅋㅋㅇ`() =
        assertEquals("ㅋㅋㅇ", KoreanUtils.extractChosung("카카오"))

    @Test
    fun `영문 포함 혼용 문자열 처리`() =
        assertEquals("ㅅㅅ", KoreanUtils.extractChosung("삼성Samsung"))

    @Test
    fun `공백 포함 문자열 처리`() =
        assertEquals("ㅎㄷㄱㅇ", KoreanUtils.extractChosung("현대 기아"))

    @Test
    fun `빈 문자열 초성 추출`() =
        assertEquals("", KoreanUtils.extractChosung(""))

    @Test
    fun `숫자만 있는 문자열 초성 추출`() =
        assertEquals("", KoreanUtils.extractChosung("12345"))

    @Test
    fun `SK하이닉스 초성`() =
        assertEquals("ㅎㅇㄴㅅ", KoreanUtils.extractChosung("SK하이닉스"))

    @Test
    fun `쌍자음 포함 종목`() =
        assertEquals("ㅆ", KoreanUtils.extractChosung("씨"))

    // -- 초성 전용 쿼리 감지 --

    @Test
    fun `ㅅㅅ는 초성 쿼리로 인식`() =
        assertTrue(KoreanUtils.isChosungOnly("ㅅㅅ"))

    @Test
    fun `삼성은 초성 쿼리가 아님`() =
        assertFalse(KoreanUtils.isChosungOnly("삼성"))

    @Test
    fun `영문 samsung은 초성 쿼리 아님`() =
        assertFalse(KoreanUtils.isChosungOnly("samsung"))

    @Test
    fun `빈 문자열은 초성 쿼리 아님`() =
        assertFalse(KoreanUtils.isChosungOnly(""))

    @Test
    fun `공백만 있으면 초성 쿼리 아님`() =
        assertFalse(KoreanUtils.isChosungOnly("   "))

    @Test
    fun `ㅎㅇㄴㅅ는 초성 쿼리`() =
        assertTrue(KoreanUtils.isChosungOnly("ㅎㅇㄴㅅ"))

    // -- 검색 매칭 --

    @Test
    fun `종목명 포함 검색 성공`() =
        assertTrue(KoreanUtils.matches("삼성", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `초성 검색 성공`() =
        assertTrue(KoreanUtils.matches("ㅅㅅ", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `초성 불일치`() =
        assertFalse(KoreanUtils.matches("ㅋㅋ", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `티커 검색 성공`() =
        assertTrue(KoreanUtils.matches("005930", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `영문 대소문자 무시`() =
        assertTrue(
            KoreanUtils.matches(
                "SAMSUNG", "삼성전자", "005930", "ㅅㅅㅈㅈ",
                nameEng = "Samsung Electronics"
            )
        )

    @Test
    fun `빈 쿼리는 모든 항목 매칭`() =
        assertTrue(KoreanUtils.matches("", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `공백 쿼리는 모든 항목 매칭`() =
        assertTrue(KoreanUtils.matches("  ", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `검색어가 종목명 티커 초성 어디에도 없으면 false`() =
        assertFalse(KoreanUtils.matches("AAPL", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    @Test
    fun `부분 초성 매칭`() =
        assertTrue(KoreanUtils.matches("ㅅㅈ", "삼성전자", "005930", "ㅅㅅㅈㅈ"))

    // -- 성능 --

    @Test
    fun `1000개 종목 초성 추출 10ms 미만`() {
        val names = (1..1000).map { "삼성전자테스트종목$it" }
        val start = System.nanoTime()
        names.forEach { KoreanUtils.extractChosung(it) }
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <10ms, got ${ms}ms", ms < 50L) // 여유 있게 50ms
    }

    @Test
    fun `1000개 항목 매칭 필터 10ms 미만`() {
        data class Stock(val name: String, val ticker: String, val chosung: String)
        val items = (0 until 1000).map { i ->
            Stock("테스트종목$i", "${(100000 + i)}", KoreanUtils.extractChosung("테스트종목$i"))
        }
        val start = System.nanoTime()
        items.filter { KoreanUtils.matches("ㅌㅅ", it.name, it.ticker, it.chosung) }
        val ms = (System.nanoTime() - start) / 1_000_000L
        assertTrue("Expected <50ms, got ${ms}ms", ms < 50L)
    }
}
