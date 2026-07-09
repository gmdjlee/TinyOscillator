package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshExternalAutoInputsUseCaseTest {

    @Test
    fun `invoke는 repository refreshExternalAutoInputs에 위임한다`() = runTest {
        val repository = mockk<BearSignalRepository>()
        val expected = AutoBearSignalInputs(
            up3 = AutoIndicator(14, InputSource.AUTO, 1L),
            down3 = AutoIndicator(12, InputSource.AUTO, 1L),
            up4 = AutoIndicator(3, InputSource.AUTO, 1L),
            down4 = AutoIndicator(2, InputSource.AUTO, 1L),
            kospi2 = AutoIndicator(56.0, InputSource.AUTO, 1L),
            semi = AutoIndicator(23.1, InputSource.AUTO, 1L),
            buffer = AutoIndicator(true, InputSource.AUTO, 1L),
            rate = AutoIndicator(3.75, InputSource.AUTO, 1L),
            dir = AutoIndicator("hike", InputSource.AUTO, 1L),
            etf = AutoIndicator("up", InputSource.AUTO, 1L)
        )
        coEvery { repository.refreshExternalAutoInputs() } returns Result.success(expected)

        val useCase = RefreshExternalAutoInputsUseCase(repository)
        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshExternalAutoInputs() }
    }
}
