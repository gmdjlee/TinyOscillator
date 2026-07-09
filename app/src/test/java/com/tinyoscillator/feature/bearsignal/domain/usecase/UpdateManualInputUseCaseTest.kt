package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManualInputUseCaseTest {

    private val repository = mockk<BearSignalRepository>(relaxed = true)
    private val useCase = UpdateManualInputUseCase(repository)

    @Test
    fun `Loss 갱신은 검증 없이 repository로 위임`() = runTest {
        coEvery { repository.updateManualInput(any()) } returns Unit

        useCase(ManualFieldUpdate.Loss(72.0))

        coVerify(exactly = 1) { repository.updateManualInput(ManualFieldUpdate.Loss(72.0)) }
    }

    @Test
    fun `Credit Margin IssueRatio도 검증 없이 위임`() = runTest {
        useCase(ManualFieldUpdate.Credit(40.0))
        useCase(ManualFieldUpdate.Margin(true))
        useCase(ManualFieldUpdate.IssueRatio(15.0))

        coVerify { repository.updateManualInput(ManualFieldUpdate.Credit(40.0)) }
        coVerify { repository.updateManualInput(ManualFieldUpdate.Margin(true)) }
        coVerify { repository.updateManualInput(ManualFieldUpdate.IssueRatio(15.0)) }
    }

    @Test
    fun `Big 유효값(smooth pending failed)은 정상 위임`() = runTest {
        useCase(ManualFieldUpdate.Big("smooth"))
        useCase(ManualFieldUpdate.Big("pending"))
        useCase(ManualFieldUpdate.Big("failed"))

        coVerify(exactly = 3) { repository.updateManualInput(any()) }
    }

    @Test
    fun `Big 잘못된 값은 IllegalArgumentException — repository 호출 없음`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(ManualFieldUpdate.Big("bad-value")) }
        }
        coVerify(exactly = 0) { repository.updateManualInput(any()) }
    }

    @Test
    fun `Dir 유효값(ease hold hike)은 정상 위임`() = runTest {
        useCase(ManualFieldUpdate.Dir("ease"))
        useCase(ManualFieldUpdate.Dir("hold"))
        useCase(ManualFieldUpdate.Dir("hike"))

        coVerify(exactly = 3) { repository.updateManualInput(any()) }
    }

    @Test
    fun `Dir 잘못된 값은 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(ManualFieldUpdate.Dir("neutral")) }
        }
        coVerify(exactly = 0) { repository.updateManualInput(any()) }
    }

    @Test
    fun `MarketReturn 4기간 리스트는 정상 위임`() = runTest {
        val update = ManualFieldUpdate.MarketReturn("RTS", listOf(-1.0, -2.0, -3.0, -4.0))
        useCase(update)

        coVerify(exactly = 1) { repository.updateManualInput(update) }
    }

    @Test
    fun `MarketReturn 기간 수가 4가 아니면 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(ManualFieldUpdate.MarketReturn("RTS", listOf(-1.0, -2.0))) }
        }
        coVerify(exactly = 0) { repository.updateManualInput(any()) }
    }
}
