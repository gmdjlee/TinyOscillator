package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EstimatedEarningsRepositoryTest {

    private lateinit var kisApiClient: KisApiClient
    private lateinit var json: Json
    private lateinit var repository: EstimatedEarningsRepository

    private val validConfig = KisApiKeyConfig(
        appKey = "test-app-key",
        appSecret = "test-app-secret"
    )
    private val invalidConfig = KisApiKeyConfig(appKey = "", appSecret = "")

    @Before
    fun setup() {
        kisApiClient = mockk(relaxed = true)
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        repository = EstimatedEarningsRepository(kisApiClient, json)
    }

    @Test
    fun `empty ticker returns failure`() = runTest {
        val result = repository.getEstimatedEarnings("", validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invalid config returns failure`() = runTest {
        val result = repository.getEstimatedEarnings("005930", invalidConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `successful API response returns summary`() = runTest {
        val responseJson = """
        {
            "rt_cd": "0",
            "msg_cd": "MCA00000",
            "msg1": "정상처리",
            "output1": {
                "sht_cd": "A005930",
                "item_kor_nm": "삼성전자",
                "name1": "김철수",
                "name2": "",
                "estdate": "20260327",
                "rcmd_name": "매수",
                "capital": "50000.0",
                "forn_item_lmtrt": "0.00"
            },
            "output2": [
                {"data1": "3000000.0", "data2": "2589354.0", "data3": "2718073.0", "data4": "2850000.0", "data5": "3000000.0"},
                {"data1": "52.0", "data2": "126.0", "data3": "50.0", "data4": "49.0", "data5": "53.0"},
                {"data1": "500000.0", "data2": "656700.0", "data3": "1538330.0", "data4": "2000000.0", "data5": "2500000.0"},
                {"data1": "-105.0", "data2": "312.0", "data3": "1343.0", "data4": "300.0", "data5": "250.0"},
                {"data1": "1200000.0", "data2": "1548700.0", "data3": "2632780.0", "data4": "3000000.0", "data5": "3500000.0"},
                {"data1": "-58.0", "data2": "290.0", "data3": "699.0", "data4": "140.0", "data5": "167.0"}
            ],
            "output3": [
                {"data1": "105000.0", "data2": "123000.0", "data3": "185000.0", "data4": "220000.0", "data5": "260000.0"},
                {"data1": "18000.0", "data2": "21310.0", "data3": "38350.0", "data4": "45000.0", "data5": "52000.0"},
                {"data1": "-153.0", "data2": "184.0", "data3": "799.0", "data4": "173.0", "data5": "156.0"}
            ],
            "output4": [
                {"dt": "2022.12"},
                {"dt": "2023.12"},
                {"dt": "2024.12"},
                {"dt": "2025.12E"},
                {"dt": "2026.12E"}
            ]
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(),
                url = any(),
                queryParams = any(),
                config = any(),
                parser = any<(String) -> String>()
            )
        } answers {
            val parser = arg<(String) -> String>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getEstimatedEarnings("005930", validConfig)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals("005930", summary.info.ticker)
        assertEquals("삼성전자", summary.info.stockName)
        assertEquals("김철수", summary.info.analystName)
        assertEquals("2026.03.27", summary.info.estimateDate)
        assertEquals("매수", summary.info.recommendation)

        // output2: 6 rows with formatting
        assertEquals(6, summary.earningsData.size)
        assertEquals("매출액", summary.earningsData[0].label)
        assertEquals("3,000,000", summary.earningsData[0].data1)       // formatAmount
        assertEquals("2,589,354", summary.earningsData[0].data2)
        assertEquals("매출액증감율", summary.earningsData[1].label)
        assertEquals("5.2", summary.earningsData[1].data1)             // formatScaledRate: 52.0/10
        assertEquals("12.6", summary.earningsData[1].data2)            // 126.0/10
        assertEquals("영업이익증감율", summary.earningsData[3].label)

        // output3: 3 rows
        assertEquals(3, summary.valuationData.size)
        assertEquals("EBITDA", summary.valuationData[0].label)
        assertEquals("105,000", summary.valuationData[0].data1)        // formatAmount
        assertEquals("EPS", summary.valuationData[1].label)
        assertEquals("1,800", summary.valuationData[1].data1)          // formatScaledInt: 18000/10
        assertEquals("2,131", summary.valuationData[1].data2)          // 21310/10
        assertEquals("EPS증감율", summary.valuationData[2].label)
        assertEquals("-15.3", summary.valuationData[2].data1)          // formatScaledRate: -153/10

        assertEquals(5, summary.periods.size)
        assertEquals("2022.12", summary.periods[0])
        assertEquals("2026.12E", summary.periods[4])
    }

    @Test
    fun `API error response returns failure`() = runTest {
        val responseJson = """
        {
            "rt_cd": "1",
            "msg_cd": "EGW00123",
            "msg1": "조회 가능한 데이터가 없습니다"
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(), url = any(), queryParams = any(), config = any(),
                parser = any<(String) -> String>()
            )
        } answers {
            val parser = arg<(String) -> String>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getEstimatedEarnings("005930", validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("KIS API 오류") == true)
    }

    @Test
    fun `null output1 returns failure`() = runTest {
        val responseJson = """
        {
            "rt_cd": "0",
            "msg_cd": "MCA00000",
            "msg1": "정상처리"
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(), url = any(), queryParams = any(), config = any(),
                parser = any<(String) -> String>()
            )
        } answers {
            val parser = arg<(String) -> String>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getEstimatedEarnings("005930", validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("비어있습니다") == true)
    }

    @Test
    fun `API failure returns failure result`() = runTest {
        coEvery {
            kisApiClient.get(
                trId = any(), url = any(), queryParams = any(), config = any(),
                parser = any<(String) -> String>()
            )
        } returns Result.failure(RuntimeException("Network error"))

        val result = repository.getEstimatedEarnings("005930", validConfig)
        assertTrue(result.isFailure)
    }

    @Test
    fun `empty output2 and output3 returns summary with empty lists`() = runTest {
        val responseJson = """
        {
            "rt_cd": "0",
            "msg_cd": "MCA00000",
            "msg1": "정상처리",
            "output1": {
                "sht_cd": "005930",
                "item_kor_nm": "삼성전자",
                "name1": "75000",
                "name2": "0",
                "estdate": "3",
                "rcmd_name": "0.00",
                "capital": "0",
                "forn_item_lmtrt": ""
            },
            "output2": [],
            "output3": [],
            "output4": []
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(), url = any(), queryParams = any(), config = any(),
                parser = any<(String) -> String>()
            )
        } answers {
            val parser = arg<(String) -> String>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getEstimatedEarnings("005930", validConfig)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(0, summary.earningsData.size)
        assertEquals(0, summary.valuationData.size)
        assertEquals(0, summary.periods.size)
    }
}
