package com.tinyoscillator.domain.model

data class VolumeBucket(
    val priceLevel: Float,
    val bullVolume: Long,
    val bearVolume: Long,
) {
    val totalVolume: Long get() = bullVolume + bearVolume
}

data class VolumeProfile(
    val buckets: List<VolumeBucket>,
    val pocPrice: Float,
    val valueAreaHigh: Float,
    val valueAreaLow: Float,
    val priceStep: Float,
    val priceMin: Float,
    val priceMax: Float,
)
