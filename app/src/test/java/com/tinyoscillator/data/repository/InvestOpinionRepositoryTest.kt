package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.domain.model.InvestOpinion
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InvestOpinionRepositoryTest {

    private lateinit var kisApiClient: KisApiClient
    private lateinit var json: Json
    private lateinit var repository: InvestOpinionRepository

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
        repository = InvestOpinionRepository(kisApiClient, json)
    }

    @Test
    fun `empty ticker returns failure`() = runTest {
        val result = repository.getInvestOpinions("", "삼성전자", validConfig)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invalid config returns failure`() = runTest {
        val result = repository.getInvestOpinions("005930", "삼성전자", invalidConfig)
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
            "output": [
                {
                    "stck_bsop_date": "20260408",
                    "mbcr_name": "미래에셋증권",
                    "invt_opnn": "매수",
                    "invt_opnn_cls_code": "02",
                    "hts_goal_prc": "90000",
                    "stck_prpr": "75000",
                    "stck_nday_esdg": "2",
                    "stck_nday_sdpr": "1000"
                },
                {
                    "stck_bsop_date": "20260407",
                    "mbcr_name": "NH투자증권",
                    "invt_opnn": "중립",
                    "invt_opnn_cls_code": "03",
                    "hts_goal_prc": "80000",
                    "stck_prpr": "75000",
                    "stck_nday_esdg": "2",
                    "stck_nday_sdpr": "500"
                },
                {
                    "stck_bsop_date": "20260406",
                    "mbcr_name": "한국투자증권",
                    "invt_opnn": "매수",
                    "invt_opnn_cls_code": "02",
                    "hts_goal_prc": "85000",
                    "stck_prpr": "75000",
                    "stck_nday_esdg": "5",
                    "stck_nday_sdpr": "0"
                }
            ]
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(),
                url = any(),
                queryParams = any(),
                config = any(),
                parser = any<(String) -> List<InvestOpinion>>()
            )
        } answers {
            val parser = arg<(String) -> List<InvestOpinion>>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getInvestOpinions("005930", "삼성전자", validConfig)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals("005930", summary.ticker)
        assertEquals("삼성전자", summary.stockName)
        assertEquals(3, summary.opinions.size)
        assertEquals(2, summary.buyCount)
        assertEquals(1, summary.holdCount)
        assertEquals(0, summary.sellCount)
        assertEquals(85000L, summary.avgTargetPrice)  // (90000+80000+85000)/3
        assertEquals(75000L, summary.currentPrice)
    }

    @Test
    fun `empty API response returns empty summary`() = runTest {
        val responseJson = """
        {
            "rt_cd": "0",
            "msg_cd": "MCA00000",
            "msg1": "정상처리",
            "output": []
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(),
                url = any(),
                queryParams = any(),
                config = any(),
                parser = any<(String) -> List<InvestOpinion>>()
            )
        } answers {
            val parser = arg<(String) -> List<InvestOpinion>>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getInvestOpinions("005930", "삼성전자", validConfig)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().opinions.size)
    }

    @Test
    fun `API failure returns failure result`() = runTest {
        coEvery {
            kisApiClient.get(
                trId = any(),
                url = any(),
                queryParams = any(),
                config = any(),
                parser = any<(String) -> List<InvestOpinion>>()
            )
        } returns Result.failure(RuntimeException("Network error"))

        val result = repository.getInvestOpinions("005930", "삼성전자", validConfig)
        assertTrue(result.isFailure)
    }

    @Test
    fun `opinion classification - buy opinions counted correctly`() = runTest {
        val responseJson = """
        {
            "rt_cd": "0",
            "msg_cd": "MCA00000",
            "msg1": "정상처리",
            "output": [
                {"stck_bsop_date": "20260408", "mbcr_name": "A증권", "invt_opnn": "매수", "invt_opnn_cls_code": "02", "hts_goal_prc": "100000", "stck_prpr": "80000"},
                {"stck_bsop_date": "20260408", "mbcr_name": "B증권", "invt_opnn": "Buy", "invt_opnn_cls_code": "02", "hts_goal_prc": "110000", "stck_prpr": "80000"},
                {"stck_bsop_date": "20260408", "mbcr_name": "C증권", "invt_opnn": "Outperform", "invt_opnn_cls_code": "03", "hts_goal_prc": "105000", "stck_prpr": "80000"},
                {"stck_bsop_date": "20260408", "mbcr_name": "D증권", "invt_opnn": "매도", "invt_opnn_cls_code": "04", "hts_goal_prc": "70000", "stck_prpr": "80000"},
                {"stck_bsop_date": "20260408", "mbcr_name": "E증권", "invt_opnn": "중립", "invt_opnn_cls_code": "03", "hts_goal_prc": "80000", "stck_prpr": "80000"}
            ]
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(), url = any(), queryParams = any(), config = any(),
                parser = any<(String) -> List<InvestOpinion>>()
            )
        } answers {
            val parser = arg<(String) -> List<InvestOpinion>>(4)
            Result.success(parser(responseJson))
        }

        val result = repository.getInvestOpinions("005930", "삼성전자", validConfig)
        val summary = result.getOrThrow()

        assertEquals(3, summary.buyCount)   // 매수 + Buy + Outperform
        assertEquals(1, summary.holdCount)  // 중립
        assertEquals(1, summary.sellCount)  // 매도
    }

    @Test
    fun `upside percentage calculated correctly`() = runTest {
        val responseJson = """
        {
            "rt_cd": "0",
            "msg_cd": "MCA00000",
            "msg1": "정상처리",
            "output": [
                {"stck_bsop_date": "20260408", "mbcr_name": "A증권", "invt_opnn": "매수", "invt_opnn_cls_code": "02", "hts_goal_prc": "100000", "stck_prpr": "80000"}
            ]
        }
        """.trimIndent()

        coEvery {
            kisApiClient.get(
                trId = any(), url = any(), queryParams = any(), config = any(),
                parser = any<(String) -> List<InvestOpinion>>()
            )
        } answers {
            val parser = arg<(String) -> List<InvestOpinion>>(4)
            Result.success(parser(responseJson))
        }

        val summary = repository.getInvestOpinions("005930", "삼성전자", validConfig).getOrThrow()
        assertNotNull(summary.upsidePct)
        assertEquals(25.0, summary.upsidePct!!, 0.1)  // (100000-80000)/80000 * 100
    }
}
