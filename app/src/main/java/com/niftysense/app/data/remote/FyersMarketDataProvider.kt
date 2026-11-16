package com.niftysense.app.data.remote

import android.util.Log
import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.data.remote.dto.FyersApiException
import com.niftysense.app.data.remote.dto.FyersJwtDecoder
import com.niftysense.app.data.remote.dto.FyersQuoteData
import com.niftysense.app.data.remote.dto.FyersQuotesResponse
import com.niftysense.app.data.repository.MarketDataProvider
import com.niftysense.app.domain.usecase.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

class FyersMarketDataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val credentialsProvider: () -> Pair<String?, String?>
) : MarketDataProvider {

    companion object {
        private const val TAG = "FyersMarketDataProvider"
        const val NIFTY_SYMBOL = "NSE:NIFTY50-INDEX"
        const val SENSEX_SYMBOL = "BSE:SENSEX-INDEX"
    }

    override val providerName: String = "Fyers Live Real-time Broker Feed"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val userAgent = "NiftySense-Android/1.0"

    private val primaryBaseUrl = "https://api-t1.fyers.in/data/quotes"
    private val secondaryBaseUrl = "https://api.fyers.in/data/quotes"

    /**
     * Resolves the proper authorization header from either separate (appId, token)
     * or a combined "APP_ID:ACCESS_TOKEN" string.
     */
    fun resolveAuthHeader(appId: String?, token: String?): String? {
        val trimmedToken = token?.trim() ?: return null
        if (trimmedToken.isEmpty()) return null

        // If the token itself already contains the colon (i.e. APP_ID:ACCESS_TOKEN), use it directly
        if (trimmedToken.contains(":")) {
            val parts = trimmedToken.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return trimmedToken
            }
        }

        val trimmedAppId = appId?.trim()
        if (!trimmedAppId.isNullOrEmpty()) {
            return "$trimmedAppId:$trimmedToken"
        }

        // Auto-extract app_id or fy_id from JWT payload if user only pasted raw access token
        val jwt = FyersJwtDecoder.parse(trimmedToken)
        if (jwt != null) {
            val extractedAppId = jwt.app_id ?: jwt.client_id
            if (!extractedAppId.isNullOrBlank()) {
                return "$extractedAppId:$trimmedToken"
            }
            val fyId = jwt.fy_id ?: jwt.user_id
            if (!fyId.isNullOrBlank()) {
                return "$fyId-100:$trimmedToken"
            }
        }

        return trimmedToken
    }

    /**
     * Test connection against Fyers API to verify whether the token is valid.
     * Returns a human-friendly string with live prices upon success.
     */
    suspend fun testConnection(appId: String?, token: String?): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = token?.trim()
        if (trimmedToken.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your Fyers Access Token."))
        }

        // Check if token is expired before doing a network call
        val jwt = FyersJwtDecoder.parse(trimmedToken)
        if (jwt != null) {
            val exp = jwt.exp
            if (exp != null && exp > 0) {
                val nowSec = System.currentTimeMillis() / 1000
                if (exp < nowSec) {
                    val expDateStr = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(Date(exp * 1000))
                    return@withContext Result.failure(
                        FyersApiException(
                            code = -99,
                            message = "Access token expired on $expDateStr. Fyers access tokens expire daily. Please generate a fresh token from your Fyers dashboard."
                        )
                    )
                }
            }
        }

        val authHeader = resolveAuthHeader(appId, token)
            ?: return@withContext Result.failure(IllegalArgumentException("Please enter a valid Fyers Access Token."))

        runCatching {
            val response = try {
                executeQuotesRequest(primaryBaseUrl, authHeader)
            } catch (e: FyersApiException) {
                throw e
            } catch (e: Exception) {
                executeQuotesRequest(secondaryBaseUrl, authHeader)
            }

            val items = response.items
            if (items.isEmpty()) {
                throw IOException(response.message ?: "No market data received for NIFTY/SENSEX.")
            }

            val nifty = items.find { isNifty(it) }?.v?.lp ?: 0.0
            val sensex = items.find { isSensex(it) }?.v?.lp ?: 0.0

            if (nifty > 0.0 && sensex > 0.0) {
                "✓ Connected to Fyers Live Feed!\nNIFTY 50: ₹${Formatters.formatIndexValue(nifty)} | SENSEX: ₹${Formatters.formatIndexValue(sensex)}"
            } else {
                "✓ Fyers Authenticated successfully! (Indices will stream live prices during market hours)"
            }
        }
    }

    override suspend fun fetchMarketSnapshot(): Result<MarketSnapshot> = withContext(Dispatchers.IO) {
        val (appId, token) = credentialsProvider()
        val authHeader = resolveAuthHeader(appId, token)
            ?: return@withContext Result.failure(IllegalStateException("Fyers credentials are not configured."))

        runCatching {
            val response = try {
                executeQuotesRequest(primaryBaseUrl, authHeader)
            } catch (e: FyersApiException) {
                throw e
            } catch (e: Exception) {
                executeQuotesRequest(secondaryBaseUrl, authHeader)
            }

            val items = response.items
            if (items.isEmpty()) {
                throw IOException(response.message ?: "Empty quote list from Fyers.")
            }

            val rawNifty = items.find { isNifty(it) }
                ?: throw IOException("NIFTY 50 data missing from Fyers response.")
            val rawSensex = items.find { isSensex(it) }
                ?: throw IOException("SENSEX data missing from Fyers response.")

            val niftyData = mapToMarketIndexData(rawNifty, "NIFTY 50", "NSE", "^NSEI")
            val sensexData = mapToMarketIndexData(rawSensex, "SENSEX", "BSE", "^BSESN")

            MarketSnapshot(
                nifty50 = niftyData,
                sensex = sensexData,
                marketStatus = MarketStatus.current(),
                lastSyncTimestamp = System.currentTimeMillis(),
                isCached = false,
                sourceProvider = providerName
            )
        }
    }

    private fun executeQuotesRequest(baseUrl: String, authHeader: String): FyersQuotesResponse {
        val url = "$baseUrl?symbols=$NIFTY_SYMBOL,$SENSEX_SYMBOL&_=${System.currentTimeMillis()}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("version", "3")
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val parsed = runCatching { json.decodeFromString<FyersQuotesResponse>(body) }.getOrNull()

            if (!response.isSuccessful || parsed?.s == "error" || (parsed?.code != null && parsed.code != 200 && parsed.code != 204)) {
                val errorCode = parsed?.code ?: response.code
                val rawMsg = parsed?.message?.takeIf { it.isNotBlank() }
                    ?: response.message.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code}"

                val friendlyMsg = when (errorCode) {
                    -15 -> "Please provide valid token. Note: Fyers requires format 'APP_ID:TOKEN' (e.g. YOUR_APP_ID-100:eyJ...). If you only pasted the token, please enter your App ID above."
                    -99 -> "Token is expired. Fyers access tokens expire daily at 6:30 AM. Please generate a fresh token from your Fyers dashboard."
                    401 -> "Authentication failed (HTTP 401): Please verify that your App ID and Access Token are correct."
                    403 -> "Access forbidden (HTTP 403): Your Fyers app does not have permissions for data quotes."
                    429 -> "Fyers rate limit reached (HTTP 429). Please wait a moment."
                    else -> rawMsg
                }
                throw FyersApiException(code = errorCode, message = friendlyMsg)
            }

            return parsed ?: throw IOException("Empty response from Fyers server.")
        }
    }

    private fun isNifty(item: FyersQuoteData): Boolean {
        val sym = item.symbolIdentifier.uppercase()
        return sym.contains("NIFTY50") || sym.contains("NIFTY 50") || sym.contains("NIFTY")
    }

    private fun isSensex(item: FyersQuoteData): Boolean {
        val sym = item.symbolIdentifier.uppercase()
        return sym.contains("SENSEX") || sym.contains("BSESN")
    }

    private fun mapToMarketIndexData(
        quote: FyersQuoteData,
        displayName: String,
        exchange: String,
        fallbackSymbol: String
    ): MarketIndexData {
        val v = quote.v ?: throw IOException("Missing quote values for $displayName")

        val currentPrice = v.lp ?: v.original_ltp ?: 0.0
        val prevClose = v.prev_close_price ?: currentPrice
        val absChange = v.ch ?: (currentPrice - prevClose)
        val pctChange = v.chp ?: if (prevClose > 0.0) ((absChange / prevClose) * 100.0) else 0.0
        val dayHigh = v.high_price ?: currentPrice
        val dayLow = v.low_price ?: currentPrice
        val openPrice = v.open_price ?: prevClose

        val lastUpdated = if (v.exch_feed_time != null && v.exch_feed_time > 0) {
            if (v.exch_feed_time < 10_000_000_000L) v.exch_feed_time * 1000L else v.exch_feed_time
        } else {
            System.currentTimeMillis()
        }

        val sparkline = listOf(prevClose, openPrice, dayLow, dayHigh, currentPrice)

        return MarketIndexData(
            symbol = quote.symbolIdentifier.ifEmpty { fallbackSymbol },
            displayName = displayName,
            exchange = exchange,
            currentValue = currentPrice,
            previousClose = prevClose,
            openPrice = openPrice,
            dayHigh = dayHigh,
            dayLow = dayLow,
            absoluteChange = absChange,
            percentageChange = pctChange,
            lastUpdated = lastUpdated,
            sparklinePoints = sparkline
        )
    }
}
