package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshMarketReturnsUseCaseTest {

    @Test
    fun `invoke는 repository refreshMarketReturns에 위임한다`() = runTest {
        val repository = mockk<BearSignalRepository>()
        val expected = MarketReturnsSnapshot(
            markets = listOf(
                AutoMarketReturn("코스피", listOf(173.1, 103.7, 54.0, 4.5), lead = true, coverage = MarketCoverage.AUTO, updatedAt = 1L)
            )
        )
        coEvery { repository.refreshMarketReturns() } returns Result.success(expected)

        val useCase = RefreshMarketReturnsUseCase(repository)
        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshMarketReturns() }
    }
}
