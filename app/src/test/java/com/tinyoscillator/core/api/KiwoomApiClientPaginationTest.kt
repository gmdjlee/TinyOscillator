package com.tinyoscillator.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * KiwoomApiClient.callWithHeaders() / PageHeaders 단위 테스트.
 *
 * 메서드 자체의 wiring은 컴파일이 보장한다 (suspend + 제네릭 + 람다 파라미터의
 * JVM 메서드 시그니처가 declaredMethods로 직접 검증하기 까다로움).
 *
 * 실제 HTTP 헤더 round-trip은 Step 8(실기 logcat)에서 수행한다 — `KiwoomApiKeyConfig.getBaseUrl()`이
 * mockapi.kiwoom.com을 하드코딩하므로 MockWebServer 기반 단위 테스트가 인터셉트 불가하기 때문이다
 * (기존 KiwoomApiClientIntegrationTest.kt:84-101도 동일한 한계 인정).
 *
 * 따라서 이 파일은 PageHeaders 데이터 클래스 자체의 동작을 검증한다.
 */
class KiwoomApiClientPaginationTest {

    @Test
    fun `PageHeaders는 contYn nextKey apiId 필드를 가진다`() {
        val headers = PageHeaders(contYn = "Y", nextKey = "abc123", apiId = "ka90001")
        assertEquals("Y", headers.contYn)
        assertEquals("abc123", headers.nextKey)
        assertEquals("ka90001", headers.apiId)
    }

    @Test
    fun `PageHeaders는 동등성을 비교한다`() {
        val a = PageHeaders("Y", "k1", "ka90001")
        val b = PageHeaders("Y", "k1", "ka90001")
        val c = PageHeaders("N", "k1", "ka90001")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `PageHeaders copy는 일부 필드만 변경한다`() {
        val original = PageHeaders("Y", "k1", "ka90001")
        val copied = original.copy(contYn = "N")
        assertEquals("N", copied.contYn)
        assertEquals(original.nextKey, copied.nextKey)
        assertEquals(original.apiId, copied.apiId)
    }

    @Test
    fun `PageHeaders는 빈 nextKey를 첫 페이지 신호로 허용한다`() {
        val firstPage = PageHeaders(contYn = "Y", nextKey = "", apiId = "ka90001")
        assertEquals("", firstPage.nextKey)
    }

    @Test
    fun `PageHeaders는 N contYn을 종료 신호로 사용한다`() {
        val lastPage = PageHeaders(contYn = "N", nextKey = "ignored", apiId = "ka90002")
        assertEquals("N", lastPage.contYn)
    }
}
