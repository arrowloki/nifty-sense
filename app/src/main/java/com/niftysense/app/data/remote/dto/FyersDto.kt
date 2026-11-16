package com.niftysense.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest

class FyersApiException(
    val code: Int?,
    override val message: String
) : IOException(message)

data class FyersTestConnectionResult(
    val message: String,
    val resolvedAppId: String?,
    val resolvedToken: String
)

@Serializable
data class FyersJwtPayload(
    val fy_id: String? = null,
    val app_id: String? = null,
    val client_id: String? = null,
    val user_id: String? = null,
    val hsm_key: String? = null,
    val sub: String? = null,
    val iss: String? = null,
    val typ: String? = null,
    val exp: Long? = null,
    val iat: Long? = null
) {
    /**
     * Determines whether this JWT is an OAuth authorization code ('auth_code')
     * rather than an actual access_token.
     * Auth codes in Fyers have sub="auth_code", short lifespan (<= 10 mins),
     * or lack the hsm_key present in real access tokens.
     */
    fun isAuthCode(): Boolean {
        if (sub?.contains("auth_code", ignoreCase = true) == true) return true
        if (typ?.contains("auth_code", ignoreCase = true) == true) return true
        if (iss?.contains("login", ignoreCase = true) == true && hsm_key.isNullOrBlank()) return true
        val duration = (exp ?: 0L) - (iat ?: 0L)
        if (duration in 1..600 && hsm_key.isNullOrBlank()) return true
        return false
    }

    fun isExpired(): Boolean {
        val expSec = exp ?: return false
        if (expSec <= 0) return false
        val nowSec = System.currentTimeMillis() / 1000
        return expSec < nowSec
    }
}

object FyersTokenSanitizer {
    /**
     * Extracts token or auth code from raw string or URL.
     * Removes "Bearer ", quotes, newlines, and trailing colons.
     */
    fun cleanToken(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var t = raw.trim()

        // If user pasted a full URL (e.g. redirect URL), extract query params
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
            val query = if (t.contains("?")) t.substringAfter("?") else ""
            val params = query.split("&").associate { param ->
                val pair = param.split("=", limit = 2)
                val key = pair.getOrNull(0)?.trim() ?: ""
                val value = pair.getOrNull(1)?.trim() ?: ""
                key to value
            }
            val fromParam = params["auth_code"] ?: params["code"] ?: params["access_token"]
            if (!fromParam.isNullOrBlank()) {
                t = fromParam
            }
        }

        // Remove surrounding single/double quotes
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            t = t.substring(1, t.length - 1).trim()
        }

        // Remove Bearer prefix
        if (t.startsWith("Bearer ", ignoreCase = true)) {
            t = t.substring(7).trim()
        }

        // Remove whitespace and newlines
        return t.replace("\r", "").replace("\n", "").trim()
    }

    /**
     * Cleans App ID by trimming whitespace, outer quotes, and trailing colon.
     */
    fun cleanAppId(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var id = raw.trim()
        if ((id.startsWith("\"") && id.endsWith("\"")) || (id.startsWith("'") && id.endsWith("'"))) {
            id = id.substring(1, id.length - 1).trim()
        }
        id = id.removeSuffix(":")
        return id.replace("\r", "").replace("\n", "").trim()
    }
}

/**
 * Computes SHA-256(app_id:secret_key) required by Fyers validate-authcode endpoint.
 */
fun computeFyersAppIdHash(appId: String, secretKey: String): String {
    val cleanId = FyersTokenSanitizer.cleanAppId(appId)
    val cleanSecret = secretKey.trim()
    val raw = "$cleanId:$cleanSecret"
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

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
data class FyersValidateAuthCodeResponse(
    val s: String? = null,
    val code: Int? = null,
    val message: String? = null,
    val access_token: String? = null
)

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
