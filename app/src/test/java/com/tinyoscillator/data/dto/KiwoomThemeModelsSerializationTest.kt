package com.tinyoscillator.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KiwoomThemeModels 직렬화 단위 테스트
 *
 * 검증 대상:
 *   1. ka90001/ka90002 응답 DTO의 round-trip
 *   2. 모든 필드 nullable + 기본값 (필드 누락 시 NPE/예외 없음)
 *   3. `ignoreUnknownKeys` (명세 추정과 실제 응답 키가 달라도 매핑 실패 방지)
 *   4. 정규화 헬퍼 (`+1234`/`-1234`/공백/null/빈문자/소수점 흡수)
 *   5. 엔드포인트/ID 상수
 *
 * 인라인 JSON 리터럴 사용: fixture 파일 의존 없음.
 * `KiwoomApiClient.createDefaultJson()`과 동일한 설정의 `json` 인스턴스를 사용해 실기 동작 재현.
 */
class KiwoomThemeModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ========== ka90001 — KiwoomThemeListResponse ==========

    @Test
    fun `ka90001 응답 - 완전한 데이터 역직렬화`() {
        val jsonStr = """
            {
                "return_code": 0,
                "return_msg": "정상처리",
                "thema_grp": [
                    {
                        "thema_grp_cd": "319",
                        "thema_nm": "2차전지",
                        "stk_num": "45",
                        "flu_sig": "2",
                        "flu_rt": "+2.34",
                        "rising_stk_num": "30",
                        "falling_stk_num": "12",
                        "dt_prft_rt": "+15.67",
                        "main_stk": "삼성SDI,LG에너지솔루션,에코프로"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<KiwoomThemeListResponse>(jsonStr)
        assertEquals(0, response.returnCode)
        assertEquals("정상처리", response.returnMsg)
        assertNotNull(response.themeGroups)
        assertEquals(1, response.themeGroups!!.size)
        val item = response.themeGroups!![0]
        assertEquals("319", item.themeCode)
        assertEquals("2차전지", item.themeName)
        assertEquals("45", item.stockCount)
        assertEquals("+2.34", item.fluctuationRate)
        assertEquals("+15.67", item.periodReturnRate)
        assertEquals("삼성SDI,LG에너지솔루션,에코프로", item.mainStocks)
    }

    @Test
    fun `ka90001 응답 - thema_grp 누락 시 null`() {
        val jsonStr = """{"return_code": 0, "return_msg": "no data"}"""
        val response = json.decodeFromString<KiwoomThemeListResponse>(jsonStr)
        assertEquals(0, response.returnCode)
        assertNull(response.themeGroups)
    }

    @Test
    fun `ka90001 응답 - 빈 thema_grp`() {
        val jsonStr = """{"return_code": 0, "thema_grp": []}"""
        val response = json.decodeFromString<KiwoomThemeListResponse>(jsonStr)
        assertNotNull(response.themeGroups)
        assertTrue(response.themeGroups!!.isEmpty())
    }

    @Test
    fun `KiwoomThemeListResponse 기본값`() {
        val response = KiwoomThemeListResponse()
        assertEquals(0, response.returnCode)
        assertNull(response.returnMsg)
        assertNull(response.themeGroups)
    }

    @Test
    fun `KiwoomThemeGroupItem 기본값 - 모든 필드 null`() {
        val item = KiwoomThemeGroupItem()
        assertNull(item.themeCode)
        assertNull(item.themeName)
        assertNull(item.stockCount)
        assertNull(item.fluctuationSign)
        assertNull(item.fluctuationRate)
        assertNull(item.risingStockCount)
        assertNull(item.fallingStockCount)
        assertNull(item.periodReturnRate)
        assertNull(item.mainStocks)
    }

    @Test
    fun `ka90001 응답 - 일부 필드만 있는 항목 역직렬화`() {
        val jsonStr = """
            {
                "return_code": 0,
                "thema_grp": [
                    {"thema_grp_cd": "100", "thema_nm": "AI"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<KiwoomThemeListResponse>(jsonStr)
        val item = response.themeGroups!![0]
        assertEquals("100", item.themeCode)
        assertEquals("AI", item.themeName)
        assertNull(item.stockCount)
        assertNull(item.fluctuationRate)
        assertNull(item.mainStocks)
    }

    @Test
    fun `ka90001 응답 - ignoreUnknownKeys로 미지의 키 흡수`() {
        // 명세 추정과 다른 키(`new_field`)가 섞여 있어도 매핑 실패하지 않음
        val jsonStr = """
            {
                "return_code": 0,
                "return_msg": "ok",
                "new_field": "experimental",
                "thema_grp": [
                    {
                        "thema_grp_cd": "777",
                        "thema_nm": "비밀테마",
                        "extra_unknown": "ignored",
                        "another_field": 123
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<KiwoomThemeListResponse>(jsonStr)
        assertEquals(1, response.themeGroups!!.size)
        assertEquals("777", response.themeGroups!![0].themeCode)
        assertEquals("비밀테마", response.themeGroups!![0].themeName)
    }

    // ========== ka90002 — KiwoomThemeStockResponse ==========

    @Test
    fun `ka90002 응답 - 완전한 데이터 역직렬화`() {
        val jsonStr = """
            {
                "return_code": 0,
                "return_msg": "정상처리",
                "thema_comp_stk": [
                    {
                        "stk_cd": "005930",
                        "stk_nm": "삼성전자",
                        "cur_prc": "70000",
                        "flu_sig": "2",
                        "pred_pre": "+500",
                        "flu_rt": "+0.72",
                        "acc_trde_qty": "12345678",
                        "dt_prft_rt": "+5.43"
                    },
                    {
                        "stk_cd": "000660",
                        "stk_nm": "SK하이닉스",
                        "cur_prc": "150000",
                        "flu_sig": "5",
                        "pred_pre": "-1000",
                        "flu_rt": "-0.66",
                        "acc_trde_qty": "9876543",
                        "dt_prft_rt": "-2.10"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<KiwoomThemeStockResponse>(jsonStr)
        assertEquals(0, response.returnCode)
        assertEquals(2, response.themeStocks!!.size)
        val first = response.themeStocks!![0]
        assertEquals("005930", first.stockCode)
        assertEquals("삼성전자", first.stockName)
        assertEquals("70000", first.currentPrice)
        assertEquals("+500", first.priorDiff)
        assertEquals("+0.72", first.fluctuationRate)
        assertEquals("12345678", first.accumulatedVolume)
        val second = response.themeStocks!![1]
        assertEquals("000660", second.stockCode)
        assertEquals("-1000", second.priorDiff)
        assertEquals("-0.66", second.fluctuationRate)
    }

    @Test
    fun `ka90002 응답 - thema_comp_stk 누락 시 null`() {
        val jsonStr = """{"return_code": 0, "return_msg": "empty"}"""
        val response = json.decodeFromString<KiwoomThemeStockResponse>(jsonStr)
        assertNull(response.themeStocks)
    }

    @Test
    fun `KiwoomThemeStockResponse 기본값`() {
        val response = KiwoomThemeStockResponse()
        assertEquals(0, response.returnCode)
        assertNull(response.returnMsg)
        assertNull(response.themeStocks)
    }

    @Test
    fun `KiwoomThemeStockItem 기본값 - 모든 필드 null`() {
        val item = KiwoomThemeStockItem()
        assertNull(item.stockCode)
        assertNull(item.stockName)
        assertNull(item.currentPrice)
        assertNull(item.fluctuationSign)
        assertNull(item.priorDiff)
        assertNull(item.fluctuationRate)
        assertNull(item.accumulatedVolume)
        assertNull(item.periodReturnRate)
    }

    @Test
    fun `ka90002 응답 - 일부 필드만 있는 항목 역직렬화`() {
        val jsonStr = """
            {
                "return_code": 0,
                "thema_comp_stk": [
                    {"stk_cd": "005930", "stk_nm": "삼성전자"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<KiwoomThemeStockResponse>(jsonStr)
        val item = response.themeStocks!![0]
        assertEquals("005930", item.stockCode)
        assertEquals("삼성전자", item.stockName)
        assertNull(item.currentPrice)
        assertNull(item.priorDiff)
        assertNull(item.fluctuationRate)
    }

    // ========== 정규화 헬퍼 ==========

    @Test
    fun `toDoubleOrZero - 양수 prefix 제거`() {
        assertEquals(2.34, "+2.34".toDoubleOrZero(), 0.0001)
        assertEquals(15.67, "+15.67".toDoubleOrZero(), 0.0001)
    }

    @Test
    fun `toDoubleOrZero - 음수 부호는 보존`() {
        assertEquals(-2.34, "-2.34".toDoubleOrZero(), 0.0001)
        assertEquals(-0.66, "-0.66".toDoubleOrZero(), 0.0001)
    }

    @Test
    fun `toDoubleOrZero - 공백 trim`() {
        assertEquals(1.5, "  1.5  ".toDoubleOrZero(), 0.0001)
        assertEquals(2.0, " +2.0".toDoubleOrZero(), 0.0001)
    }

    @Test
    fun `toDoubleOrZero - null 빈문자 비숫자는 0`() {
        assertEquals(0.0, (null as String?).toDoubleOrZero(), 0.0001)
        assertEquals(0.0, "".toDoubleOrZero(), 0.0001)
        assertEquals(0.0, "   ".toDoubleOrZero(), 0.0001)
        assertEquals(0.0, "abc".toDoubleOrZero(), 0.0001)
    }

    @Test
    fun `toLongOrZero - 양수 prefix 제거`() {
        assertEquals(1234L, "+1234".toLongOrZero())
        assertEquals(12345678L, "12345678".toLongOrZero())
    }

    @Test
    fun `toLongOrZero - 음수 부호는 보존`() {
        assertEquals(-1000L, "-1000".toLongOrZero())
    }

    @Test
    fun `toLongOrZero - null 빈문자 소수점은 0`() {
        assertEquals(0L, (null as String?).toLongOrZero())
        assertEquals(0L, "".toLongOrZero())
        // toLongOrNull은 소수점을 거부하므로 0으로 처리됨
        assertEquals(0L, "1.5".toLongOrZero())
    }

    @Test
    fun `toIntOrZero - 양수 prefix 제거`() {
        assertEquals(45, "+45".toIntOrZero())
        assertEquals(0, "0".toIntOrZero())
    }

    @Test
    fun `toIntOrZero - 음수 부호 보존 및 fallback`() {
        assertEquals(-7, "-7".toIntOrZero())
        assertEquals(0, (null as String?).toIntOrZero())
        assertEquals(0, "n/a".toIntOrZero())
    }

    // ========== 엔드포인트 / ID 상수 ==========

    @Test
    fun `ThemeApiEndpoints 상수 검증`() {
        assertEquals("/api/dostk/thme", ThemeApiEndpoints.THEME_BASE)
    }

    @Test
    fun `ThemeApiIds 상수 검증`() {
        assertEquals("ka90001", ThemeApiIds.THEME_GROUP_LIST)
        assertEquals("ka90002", ThemeApiIds.THEME_COMPONENT_STOCKS)
    }

    // ========== Round-trip 직렬화 ==========

    @Test
    fun `KiwoomThemeListResponse 직렬화-역직렬화 왕복`() {
        val original = KiwoomThemeListResponse(
            returnCode = 0,
            returnMsg = "OK",
            themeGroups = listOf(
                KiwoomThemeGroupItem(
                    themeCode = "319",
                    themeName = "2차전지",
                    stockCount = "45",
                    fluctuationRate = "+2.34",
                    periodReturnRate = "+15.67",
                    mainStocks = "삼성SDI,LG에너지솔루션"
                )
            )
        )
        val encoded = json.encodeToString(KiwoomThemeListResponse.serializer(), original)
        val decoded = json.decodeFromString<KiwoomThemeListResponse>(encoded)
        assertEquals(original.returnCode, decoded.returnCode)
        assertEquals(original.themeGroups?.size, decoded.themeGroups?.size)
        assertEquals(original.themeGroups!![0].themeCode, decoded.themeGroups!![0].themeCode)
        assertEquals(original.themeGroups!![0].periodReturnRate, decoded.themeGroups!![0].periodReturnRate)
    }

    @Test
    fun `KiwoomThemeStockResponse 직렬화-역직렬화 왕복`() {
        val original = KiwoomThemeStockResponse(
            returnCode = 0,
            returnMsg = "OK",
            themeStocks = listOf(
                KiwoomThemeStockItem(
                    stockCode = "005930",
                    stockName = "삼성전자",
                    currentPrice = "70000",
                    priorDiff = "+500",
                    fluctuationRate = "+0.72",
                    accumulatedVolume = "12345678"
                )
            )
        )
        val encoded = json.encodeToString(KiwoomThemeStockResponse.serializer(), original)
        val decoded = json.decodeFromString<KiwoomThemeStockResponse>(encoded)
        assertEquals(original.returnCode, decoded.returnCode)
        assertEquals(original.themeStocks?.size, decoded.themeStocks?.size)
        assertEquals(original.themeStocks!![0].stockCode, decoded.themeStocks!![0].stockCode)
        assertEquals(original.themeStocks!![0].priorDiff, decoded.themeStocks!![0].priorDiff)
    }
}
