package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.core.testing.fixture.SyntheticData
import org.junit.experimental.categories.Category
import org.junit.Assert.*
import org.junit.Test

@Category(FastTest::class)
class BuildVolumeProfileUseCaseTest {

    private val useCase = BuildVolumeProfileUseCase()

    @Test
    fun `poc has highest total volume among all buckets`() {
        val candles = SyntheticData.candles(100, seed = 7L)
        val profile = useCase(candles)
        val expected = profile.buckets.maxByOrNull { it.totalVolume }?.priceLevel
        assertEquals(expected, profile.pocPrice)
    }

    @Test
    fun `value area covers at least 70pct of total volume`() {
        val profile = useCase(SyntheticData.candles(200, seed = 42L))
        val vaVol = profile.buckets
            .filter { it.priceLevel in profile.valueAreaLow..profile.valueAreaHigh }
            .sumOf { it.totalVolume }.toFloat()
        val totalVol = profile.buckets.sumOf { it.totalVolume }.toFloat()
        assertTrue(
            "VA covers ${vaVol / totalVol * 100f}% (expected >= 70%)",
            vaVol / totalVol >= 0.70f,
        )
    }

    @Test
    fun `buckets sorted ascending by price level`() {
        val prices = useCase(SyntheticData.candles(50)).buckets.map { it.priceLevel }
        assertEquals(prices, prices.sorted())
    }

    @Test
    fun `value area low le poc le value area high`() {
        val profile = useCase(SyntheticData.candles(100))
        assertTrue(profile.valueAreaLow <= profile.pocPrice)
        assertTrue(profile.pocPrice <= profile.valueAreaHigh)
    }

    @Test
    fun `bull plus bear volume equals total for every bucket`() {
        useCase(SyntheticData.candles(50)).buckets.forEach { b ->
            assertEquals(b.totalVolume, b.bullVolume + b.bearVolume)
        }
    }

    @Test
    fun `auto price step 500 for 20000 won stock`() {
        val candles = SyntheticData.candles(10, basePrice = 20_000f, volatility = 0.001f)
        assertEquals(500f, BuildVolumeProfileUseCase.autoPriceStep(candles))
    }

    @Test
    fun `auto price step 1000 for 150000 won stock`() {
        val candles = SyntheticData.candles(10, basePrice = 150_000f, volatility = 0.001f)
        assertEquals(1_000f, BuildVolumeProfileUseCase.autoPriceStep(candles))
    }

    @Test
    fun `auto price step 5000 for 600000 won stock`() {
        val candles = SyntheticData.candles(10, basePrice = 600_000f, volatility = 0.001f)
        assertEquals(5_000f, BuildVolumeProfileUseCase.autoPriceStep(candles))
    }

    @Test
    fun `empty candle list returns empty profile`() {
        val profile = useCase(emptyList())
        assertTrue(profile.buckets.isEmpty())
        assertEquals(0f, profile.pocPrice)
    }

    @Test
    fun `single candle list does not crash`() {
        val single = listOf(SyntheticData.candles(1).first())
        val profile = useCase(single)
        assertNotNull(profile)
        assertTrue(profile.buckets.isNotEmpty())
    }

    @Test
    fun `priceMin le priceMax`() {
        val profile = useCase(SyntheticData.candles(80))
        assertTrue(profile.priceMin <= profile.priceMax)
    }

    @Test
    fun `500 candles processed under 10ms`() {
        val candles = SyntheticData.candles(500)
        val start = System.nanoTime()
        useCase(candles)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("Took ${elapsedMs}ms (expected < 10ms)", elapsedMs < 10L)
    }
}
