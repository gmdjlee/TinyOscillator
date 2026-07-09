package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResetToReportBaselineUseCaseTest {

    @Test
    fun `invoke는 repository resetToReportBaseline에 위임한다`() = runTest {
        val repository = mockk<BearSignalRepository>()
        coEvery { repository.resetToReportBaseline() } returns Unit

        val useCase = ResetToReportBaselineUseCase(repository)
        useCase()

        coVerify(exactly = 1) { repository.resetToReportBaseline() }
    }
}
