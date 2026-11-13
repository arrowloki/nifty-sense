package com.niftysense.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FyersQuotesResponse(
    val s: String? = null,
    val code: Int? = null,
    val message: String? = null,
    val d: List<FyersQuoteData>? = null,
    val data: List<FyersQuoteData>? = null
) {
    val items: List<FyersQuoteData>
        get() = data ?: d ?: emptyList()
}

@Serializable
data class FyersQuoteData(
    val symbol: String? = null,
    val name: String? = null,
    val v: FyersQuoteValues? = null
)

@Serializable
data class FyersQuoteValues(
    val lp: Double? = null,              // Last Traded Price (LTP)
    val ch: Double? = null,              // Absolute price change
    val chp: Double? = null,             // Percentage change
    val open_price: Double? = null,
    val high_price: Double? = null,
    val low_price: Double? = null,
    val prev_close_price: Double? = null,
    val volume: Long? = null,
    val original_ltp: Double? = null,
    val last_traded_time: Long? = null,
    val exch_feed_time: Long? = null
)
