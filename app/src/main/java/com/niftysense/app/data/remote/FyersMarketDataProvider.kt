package com.niftysense.app.data.remote

import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.data.remote.dto.FyersQuoteData
import com.niftysense.app.data.remote.dto.FyersQuotesResponse
import com.niftysense.app.data.repository.MarketDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class FyersMarketDataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val credentialsProvider: () -> Pair<String?, String?>
) : MarketDataProvider {

    override val providerName: String = "Fyers Live Real-time Broker Feed"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val userAgent = "NiftySense-Android/1.0"

    private val primaryBaseUrl = "https://api-t1.fyers.in/data-rest/v3/quotes"
    private val secondaryBaseUrl = "https://api.fyers.in/data-rest/v3/quotes"

    companion object {
        const val NIFTY_SYMBOL = "NSE:NIFTY50-INDEX"
        const val SENSEX_SYMBOL = "BSE:SENSEX-INDEX"
    }

    /**
     * Resolves the proper authorization header from either separate (appId, token)
     * or a combined "APP_ID:ACCESS_TOKEN" string.
     */
    fun resolveAuthHeader(appId: String?, token: String?): String? {
        val trimmedToken = token?.trim() ?: return null
        if (trimmedToken.isEmpty()) return null

        // If the token itself already contains the colon (i.e. APP_ID:ACCESS_TOKEN), use it directly
        if (trimmedToken.contains(":") && trimmedToken.length > 20) {
            return trimmedToken
        }

        val trimmedAppId = appId?.trim()
        if (!trimmedAppId.isNullOrEmpty()) {
            return "$trimmedAppId:$trimmedToken"
        }

        // Auto-extract app_id from JWT payload if user only pasted raw access token
        try {
            val parts = trimmedToken.split(".")
            if (parts.size >= 2) {
                val pad = when (parts[1].length % 4) {
                    2 -> "=="
                    3 -> "="
                    else -> ""
                }
                val decoded = String(java.util.Base64.getUrlDecoder().decode(parts[1] + pad), Charsets.UTF_8)
                val appIdRegex = Regex(""""(?:app_id|client_id)"\s*:\s*"([^"]+)"""")
                val match = appIdRegex.find(decoded)
                val extracted = match?.groupValues?.get(1)
                if (!extracted.isNullOrBlank()) {
                    return "$extracted:$trimmedToken"
                }
            }
        } catch (_: Exception) {
        }

        return trimmedToken
    }

    /**
     * Test connection against Fyers API to verify whether the token is valid.
     * Returns a human-friendly string with live prices upon success.
     */
    suspend fun testConnection(appId: String?, token: String?): Result<String> = withContext(Dispatchers.IO) {
        val authHeader = resolveAuthHeader(appId, token)
            ?: return@withContext Result.failure(IllegalArgumentException("Please enter a valid Fyers Access Token."))

        runCatching {
            val response = executeQuotesRequest(primaryBaseUrl, authHeader)
                ?: executeQuotesRequest(secondaryBaseUrl, authHeader)
                ?: throw IOException("Fyers server did not return a response.")

            val items = response.items
            if (items.isEmpty()) {
                throw IOException(response.message ?: "No market data received for NIFTY/SENSEX.")
            }

            val nifty = items.find { isNifty(it) }?.v?.lp ?: 0.0
            val sensex = items.find { isSensex(it) }?.v?.lp ?: 0.0

            if (nifty > 0.0 && sensex > 0.0) {
                "Connected to Fyers Live Feed! NIFTY: ${"%,.2f".format(nifty)} | SENSEX: ${"%,.2f".format(sensex)}"
            } else {
                "Fyers Authenticated successfully! (Indices will update during market hours)"
            }
        }
    }

    override suspend fun fetchMarketSnapshot(): Result<MarketSnapshot> = withContext(Dispatchers.IO) {
        val (appId, token) = credentialsProvider()
        val authHeader = resolveAuthHeader(appId, token)
            ?: return@withContext Result.failure(IllegalStateException("Fyers credentials are not configured."))

        runCatching {
            val response = executeQuotesRequest(primaryBaseUrl, authHeader)
                ?: executeQuotesRequest(secondaryBaseUrl, authHeader)
                ?: throw IOException("Failed to connect to Fyers API endpoints.")

            if (response.code != null && response.code != 200 && response.code != 204) {
                throw IOException("Fyers API Error (${response.code}): ${response.message}")
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

    private fun executeQuotesRequest(baseUrl: String, authHeader: String): FyersQuotesResponse? {
        val url = "$baseUrl?symbols=$NIFTY_SYMBOL,$SENSEX_SYMBOL&_=${System.currentTimeMillis()}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw IOException("Fyers Token Expired or Invalid (HTTP ${response.code}). Please renew your token.")
            }
            if (response.code == 429) {
                throw IOException("Fyers rate limit reached (HTTP 429). Please wait a moment.")
            }
            if (!response.isSuccessful) {
                return null
            }

            val body = response.body?.string() ?: return null
            return json.decodeFromString<FyersQuotesResponse>(body)
        }
    }

    private fun isNifty(item: FyersQuoteData): Boolean {
        val sym = (item.symbol ?: item.name ?: "").uppercase()
        return sym.contains("NIFTY50") || sym.contains("NIFTY 50") || sym.contains("NIFTY")
    }

    private fun isSensex(item: FyersQuoteData): Boolean {
        val sym = (item.symbol ?: item.name ?: "").uppercase()
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
            // Fyers feed time is in seconds
            if (v.exch_feed_time < 10_000_000_000L) v.exch_feed_time * 1000L else v.exch_feed_time
        } else {
            System.currentTimeMillis()
        }

        // Construct 5-point sparkline approximation from OHLC
        val sparkline = listOf(prevClose, openPrice, dayLow, dayHigh, currentPrice)

        return MarketIndexData(
            symbol = quote.symbol ?: fallbackSymbol,
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
