package com.niftysense.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketIndexData(
    val symbol: String,
    val displayName: String,
    val exchange: String,
    val currentValue: Double,
    val previousClose: Double,
    val openPrice: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val absoluteChange: Double,
    val percentageChange: Double,
    val lastUpdated: Long,
    val sparklinePoints: List<Double> = emptyList()
) {
    val isPositive: Boolean
        get() = absoluteChange >= 0.0

    val dayRangeProgress: Float
        get() {
            val range = dayHigh - dayLow
            if (range <= 0.0) return 0.5f
            val currentOffset = currentValue - dayLow
            return (currentOffset / range).toFloat().coerceIn(0f, 1f)
        }
}
