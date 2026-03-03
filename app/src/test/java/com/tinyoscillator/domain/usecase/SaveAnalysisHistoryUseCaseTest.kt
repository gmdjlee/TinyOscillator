package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SaveAnalysisHistoryUseCaseTest {

    private lateinit var analysisHistoryDao: AnalysisHistoryDao
    private lateinit var useCase: SaveAnalysisHistoryUseCase

    private val testTicker = "005930"
    private val testName = "삼성전자"

    @Before
    fun setup() {
        analysisHistoryDao = mockk(relaxed = true)
        useCase = SaveAnalysisHistoryUseCase(analysisHistoryDao)
    }

    @Test
    fun `정상 저장 시 saveWithFifo가 올바른 인자로 호출된다`() = runTest {
        useCase(testTicker, testName)

        coVerify(exactly = 1) {
            analysisHistoryDao.saveWithFifo(testTicker, testName, 30)
        }
    }

    @Test
    fun `다른 종목도 saveWithFifo에 올바르게 전달된다`() = runTest {
        useCase("035720", "카카오")

        coVerify(exactly = 1) {
            analysisHistoryDao.saveWithFifo("035720", "카카오", 30)
        }
    }

    @Test
    fun `saveWithFifo에 MAX_HISTORY 30이 전달된다`() = runTest {
        useCase(testTicker, testName)

        coVerify {
            analysisHistoryDao.saveWithFifo(any(), any(), eq(30))
        }
    }

    @Test
    fun `saveWithFifo 예외 발생 시 예외가 전파된다`() = runTest {
        coEvery {
            analysisHistoryDao.saveWithFifo(any(), any(), any())
        } throws RuntimeException("DB error")

        try {
            useCase(testTicker, testName)
            assert(false) { "예외가 발생해야 합니다" }
        } catch (e: RuntimeException) {
            assert(e.message == "DB error")
        }
    }
}
