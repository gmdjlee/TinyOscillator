package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.VolumeBucket
import com.tinyoscillator.domain.model.VolumeProfile
import kotlin.math.roundToInt

class BuildVolumeProfileUseCase {

    operator fun invoke(
        candles: List<OhlcvPoint>,
        priceStep: Float = autoPriceStep(candles),
    ): VolumeProfile {
        if (candles.isEmpty()) return emptyProfile()

        val map = mutableMapOf<Int, Pair<Long, Long>>()
        candles.forEach { c ->
            val level = ((c.close / priceStep).roundToInt() * priceStep).toInt()
            val (bull, bear) = map.getOrDefault(level, 0L to 0L)
            if (c.close > c.open) map[level] = (bull + c.volume) to bear
            else map[level] = bull to (bear + c.volume)
        }

        val sorted = map.entries
            .map { (price, v) -> VolumeBucket(price.toFloat(), v.first, v.second) }
            .sortedBy { it.priceLevel }

        val pocBucket = sorted.maxByOrNull { it.totalVolume }
            ?: return emptyProfile()
        val poc = pocBucket.priceLevel

        // Value Area (70%)
        val total = sorted.sumOf { it.totalVolume }.toFloat()
        var lo = sorted.indexOfFirst { it.priceLevel == poc }
        var hi = lo
        var accum = sorted[lo].totalVolume.toFloat()
        while (accum / total < 0.70f && (lo > 0 || hi < sorted.lastIndex)) {
            val addLo = if (lo > 0) sorted[lo - 1].totalVolume.toFloat() else 0f
            val addHi = if (hi < sorted.lastIndex) sorted[hi + 1].totalVolume.toFloat() else 0f
            when {
                addLo >= addHi && lo > 0 -> { lo--; accum += addLo }
                hi < sorted.lastIndex -> { hi++; accum += addHi }
                else -> { lo--; accum += addLo }
            }
        }

        return VolumeProfile(
            buckets = sorted,
            pocPrice = poc,
            valueAreaHigh = sorted[hi].priceLevel,
            valueAreaLow = sorted[lo].priceLevel,
            priceStep = priceStep,
            priceMin = sorted.first().priceLevel,
            priceMax = sorted.last().priceLevel,
        )
    }

    companion object {
        fun autoPriceStep(candles: List<OhlcvPoint>): Float {
            if (candles.isEmpty()) return 100f
            val avg = candles.map { it.close }.average().toFloat()
            return when {
                avg >= 500_000f -> 5_000f
                avg >= 100_000f -> 1_000f
                avg >= 10_000f -> 500f
                else -> 100f
            }
        }

        private fun emptyProfile() = VolumeProfile(
            emptyList(), 0f, 0f, 0f, 0f, 0f, 0f,
        )
    }
}
