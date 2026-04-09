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
                "sht_cd": "005930",
                "item_kor_nm": "삼성전자",
                "name1": "75,000",
                "name2": "1,000",
                "estdate": "2",
                "rcmd_name": "1.35",
                "capital": "12345678",
                "forn_item_lmtrt": ""
            },
            "output2": [
                {"data1": "매출액", "data2": "2,589,354", "data3": "2,718,073", "data4": "2,850,000", "data5": "3,000,000"},
                {"data1": "영업이익", "data2": "65,670", "data3": "153,833", "data4": "200,000", "data5": "250,000"},
                {"data1": "당기순이익", "data2": "154,870", "data3": "263,278", "data4": "300,000", "data5": "350,000"}
            ],
            "output3": [
                {"data1": "EPS", "data2": "2,131", "data3": "3,835", "data4": "4,500", "data5": "5,200"},
                {"data1": "PER", "data2": "35.2", "data3": "19.6", "data4": "16.7", "data5": "14.4"},
                {"data1": "ROE", "data2": "3.5", "data3": "6.8", "data4": "8.2", "data5": "9.5"}
            ],
            "output4": [
                {"dt": "202312"},
                {"dt": "202412"},
                {"dt": "202512"},
                {"dt": "202612"}
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
        assertEquals("75,000", summary.info.currentPrice)
        assertEquals("2", summary.info.changeSign)

        assertEquals(3, summary.earningsData.size)
        assertEquals("매출액", summary.earningsData[0].data1)
        assertEquals("2,589,354", summary.earningsData[0].data2)

        assertEquals(3, summary.valuationData.size)
        assertEquals("EPS", summary.valuationData[0].data1)

        assertEquals(4, summary.periods.size)
        assertEquals("202312", summary.periods[0])
        assertEquals("202612", summary.periods[3])
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
