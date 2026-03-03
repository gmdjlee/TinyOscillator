package com.tinyoscillator.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * StockApiModels 단위 테스트
 *
 * DTO 직렬화/역직렬화, 엔드포인트 상수, API ID 상수 검증
 */
class StockApiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ========== StockListResponse 테스트 ==========

    @Test
    fun `StockListResponse 역직렬화 - 완전한 데이터`() {
        val jsonStr = """
            {
                "return_code": 0,
                "return_msg": "success",
                "list": [
                    {"code": "005930", "name": "삼성전자", "marketName": "KOSPI"},
                    {"code": "000660", "name": "SK하이닉스", "marketName": "KOSPI"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<StockListResponse>(jsonStr)
        assertEquals(0, response.returnCode)
        assertEquals("success", response.returnMsg)
        assertNotNull(response.stkList)
        assertEquals(2, response.stkList!!.size)
        assertEquals("005930", response.stkList!![0].stkCd)
        assertEquals("삼성전자", response.stkList!![0].stkNm)
    }

    @Test
    fun `StockListResponse 역직렬화 - 빈 리스트`() {
        val jsonStr = """{"return_code": 0, "list": []}"""
        val response = json.decodeFromString<StockListResponse>(jsonStr)
        assertNotNull(response.stkList)
        assertTrue(response.stkList!!.isEmpty())
    }

    @Test
    fun `StockListResponse 역직렬화 - list 누락`() {
        val jsonStr = """{"return_code": 0}"""
        val response = json.decodeFromString<StockListResponse>(jsonStr)
        assertNull(response.stkList)
    }

    @Test
    fun `StockListResponse 기본값`() {
        val response = StockListResponse()
        assertEquals(0, response.returnCode)
        assertNull(response.returnMsg)
        assertNull(response.stkList)
    }

    // ========== StockListItem 테스트 ==========

    @Test
    fun `StockListItem 기본값 - 모든 필드 null`() {
        val item = StockListItem()
        assertNull(item.stkCd)
        assertNull(item.stkNm)
        assertNull(item.mrktNm)
    }

    @Test
    fun `StockListItem 역직렬화`() {
        val jsonStr = """{"code": "005930", "name": "삼성전자", "marketName": "KOSPI"}"""
        val item = json.decodeFromString<StockListItem>(jsonStr)
        assertEquals("005930", item.stkCd)
        assertEquals("삼성전자", item.stkNm)
        assertEquals("KOSPI", item.mrktNm)
    }

    // ========== InvestorTrendResponse 테스트 ==========

    @Test
    fun `InvestorTrendResponse 역직렬화 - 완전한 데이터`() {
        val jsonStr = """
            {
                "return_code": 0,
                "stk_invsr_orgn": [
                    {"dt": "20240101", "frgnr_invsr": 5000, "orgn": 3000, "ind_invsr": -8000, "mrkt_tot_amt": 100000}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<InvestorTrendResponse>(jsonStr)
        assertEquals(0, response.returnCode)
        assertNotNull(response.data)
        assertEquals(1, response.data!!.size)
        assertEquals("20240101", response.data!![0].date)
        assertEquals(5000L, response.data!![0].foreignNet)
        assertEquals(3000L, response.data!![0].institutionNet)
    }

    @Test
    fun `InvestorTrendResponse 역직렬화 - data 누락`() {
        val jsonStr = """{"return_code": 0}"""
        val response = json.decodeFromString<InvestorTrendResponse>(jsonStr)
        assertNull(response.data)
    }

    @Test
    fun `InvestorTrendItem - null 필드 기본값`() {
        val item = InvestorTrendItem()
        assertNull(item.date)
        assertNull(item.foreignNet)
        assertNull(item.institutionNet)
        assertNull(item.individualNet)
        assertNull(item.marketCap)
    }

    // ========== StockInfoResponse 테스트 ==========

    @Test
    fun `StockInfoResponse 역직렬화`() {
        val jsonStr = """
            {
                "return_code": 0,
                "stk_nm": "삼성전자",
                "cur_prc": "70000",
                "mac": "50000",
                "flo_stk": "5,969,782"
            }
        """.trimIndent()
        val response = json.decodeFromString<StockInfoResponse>(jsonStr)
        assertEquals("삼성전자", response.stkNm)
        assertEquals("70000", response.curPrc)
        assertEquals("5,969,782", response.floStk)
    }

    @Test
    fun `StockInfoResponse 기본값`() {
        val response = StockInfoResponse()
        assertEquals(0, response.returnCode)
        assertNull(response.stkNm)
        assertNull(response.curPrc)
        assertNull(response.mac)
        assertNull(response.floStk)
    }

    // ========== DailyOhlcvResponse 테스트 ==========

    @Test
    fun `DailyOhlcvResponse 역직렬화`() {
        val jsonStr = """
            {
                "return_code": 0,
                "stk_dt_pole_chart_qry": [
                    {"dt": "20240101", "open_pric": 70000, "high_pric": 71000, "low_pric": 69000, "cur_prc": 70500, "trde_qty": 1000000}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<DailyOhlcvResponse>(jsonStr)
        assertNotNull(response.data)
        assertEquals(1, response.data!!.size)
        val item = response.data!![0]
        assertEquals("20240101", item.date)
        assertEquals(70000, item.open)
        assertEquals(71000, item.high)
        assertEquals(69000, item.low)
        assertEquals(70500, item.close)
        assertEquals(1000000L, item.volume)
    }

    @Test
    fun `DailyOhlcvResponse 빈 데이터`() {
        val response = DailyOhlcvResponse()
        assertEquals(0, response.returnCode)
        assertNull(response.data)
    }

    @Test
    fun `OhlcvItem - null 필드`() {
        val item = OhlcvItem()
        assertNull(item.date)
        assertNull(item.open)
        assertNull(item.high)
        assertNull(item.low)
        assertNull(item.close)
        assertNull(item.volume)
    }

    // ========== 엔드포인트 상수 테스트 ==========

    @Test
    fun `StockApiEndpoints 상수 검증`() {
        assertEquals("/api/dostk/stkinfo", StockApiEndpoints.STOCK_LIST)
        assertEquals("/api/dostk/stkinfo", StockApiEndpoints.STOCK_INFO)
        assertEquals("/api/dostk/stkinfo", StockApiEndpoints.INVESTOR_TREND)
        assertEquals("/api/dostk/chart", StockApiEndpoints.DAILY_CHART)
    }

    // ========== API ID 상수 테스트 ==========

    @Test
    fun `StockApiIds 상수 검증`() {
        assertEquals("ka10099", StockApiIds.STOCK_LIST)
        assertEquals("ka10001", StockApiIds.STOCK_INFO)
        assertEquals("ka10059", StockApiIds.INVESTOR_TREND)
        assertEquals("ka10081", StockApiIds.DAILY_CHART)
    }

    // ========== 직렬화 왕복(round-trip) 테스트 ==========

    @Test
    fun `StockListResponse 직렬화-역직렬화 왕복`() {
        val original = StockListResponse(
            returnCode = 0,
            returnMsg = "OK",
            stkList = listOf(StockListItem("005930", "삼성전자", "KOSPI"))
        )
        val jsonStr = json.encodeToString(StockListResponse.serializer(), original)
        val decoded = json.decodeFromString<StockListResponse>(jsonStr)
        assertEquals(original.returnCode, decoded.returnCode)
        assertEquals(original.stkList?.size, decoded.stkList?.size)
        assertEquals(original.stkList?.get(0)?.stkCd, decoded.stkList?.get(0)?.stkCd)
    }

    @Test
    fun `InvestorTrendItem 역직렬화 - 음수 값`() {
        val jsonStr = """{"dt": "20240101", "frgnr_invsr": -5000, "orgn": -3000, "ind_invsr": 8000, "mrkt_tot_amt": 100000}"""
        val item = json.decodeFromString<InvestorTrendItem>(jsonStr)
        assertEquals(-5000L, item.foreignNet)
        assertEquals(-3000L, item.institutionNet)
        assertEquals(8000L, item.individualNet)
    }
}
