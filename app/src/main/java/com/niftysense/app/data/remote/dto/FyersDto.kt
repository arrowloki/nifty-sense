package com.niftysense.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

class FyersApiException(
    val code: Int?,
    override val message: String
) : IOException(message)

@Serializable
data class FyersJwtPayload(
    val fy_id: String? = null,
    val app_id: String? = null,
    val client_id: String? = null,
    val user_id: String? = null,
    val hsm_key: String? = null,
    val exp: Long? = null,
    val iat: Long? = null
)

object FyersJwtDecoder {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(token: String?): FyersJwtPayload? {
        if (token.isNullOrBlank()) return null
        return try {
            val raw = if (token.contains(":")) token.substringAfter(":") else token
            val parts = raw.trim().split(".")
            if (parts.size >= 2) {
                val pad = when (parts[1].length % 4) {
                    2 -> "=="
                    3 -> "="
                    else -> ""
                }
                val decodedBytes = java.util.Base64.getUrlDecoder().decode(parts[1] + pad)
                val decodedJson = String(decodedBytes, Charsets.UTF_8)
                json.decodeFromString<FyersJwtPayload>(decodedJson)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

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
    val n: String? = null,              // Standard Fyers symbol identifier (e.g. "NSE:NIFTY50-INDEX")
    val symbol: String? = null,
    val name: String? = null,
    val s: String? = null,
    val v: FyersQuoteValues? = null
) {
    val symbolIdentifier: String
        get() = n ?: symbol ?: name ?: v?.symbol ?: ""
}

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
    val exch_feed_time: Long? = null,
    val short_name: String? = null,
    val symbol: String? = null,
    val description: String? = null
)
