package com.niftysense.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class YahooChartResponse(
    val chart: YahooChartBody
)

@Serializable
data class YahooChartBody(
    val result: List<YahooChartResult>? = null,
    val error: YahooChartError? = null
)

@Serializable
data class YahooChartError(
    val code: String? = null,
    val description: String? = null
)

@Serializable
data class YahooChartResult(
    val meta: YahooChartMeta,
    val indicators: YahooChartIndicators? = null
)

@Serializable
data class YahooChartMeta(
    val currency: String? = "INR",
    val symbol: String,
    val regularMarketPrice: Double? = null,
    val chartPreviousClose: Double? = null,
    val previousClose: Double? = null,
    val regularMarketDayHigh: Double? = null,
    val regularMarketDayLow: Double? = null,
    val regularMarketTime: Long? = null
)

@Serializable
data class YahooChartIndicators(
    val quote: List<YahooChartQuote>? = null
)

@Serializable
data class YahooChartQuote(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null
)
