package com.niftysense.app.data.remote

import android.util.Log
import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.data.remote.dto.FyersApiException
import com.niftysense.app.data.remote.dto.FyersJwtDecoder
import com.niftysense.app.data.remote.dto.FyersJwtPayload
import com.niftysense.app.data.remote.dto.FyersQuoteData
import com.niftysense.app.data.remote.dto.FyersQuotesResponse
import com.niftysense.app.data.remote.dto.FyersTestConnectionResult
import com.niftysense.app.data.remote.dto.FyersTokenSanitizer
import com.niftysense.app.data.remote.dto.FyersValidateAuthCodeResponse
import com.niftysense.app.data.remote.dto.computeFyersAppIdHash
import com.niftysense.app.data.repository.MarketDataProvider
import com.niftysense.app.domain.usecase.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    data class CandidateAuth(
        val appId: String?,
        val token: String,
        val authHeader: String
    )

    /**
     * Resolves the proper authorization header from either separate (appId, token)
     * or a combined "APP_ID:ACCESS_TOKEN" string.
     */
    fun resolveAuthHeader(appId: String?, token: String?): String? {
        val cleanedToken = FyersTokenSanitizer.cleanToken(token)
        if (cleanedToken.isEmpty()) return null

        // If the token itself already contains the colon (i.e. APP_ID:ACCESS_TOKEN), use it directly
        if (cleanedToken.contains(":")) {
            val parts = cleanedToken.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return cleanedToken
            }
        }

        val cleanedAppId = FyersTokenSanitizer.cleanAppId(appId).takeIf { it.isNotBlank() }
        if (cleanedAppId != null) {
            val normalizedAppId = if (!cleanedAppId.contains("-")) "$cleanedAppId-100" else cleanedAppId
            return "$normalizedAppId:$cleanedToken"
        }

        // Auto-extract app_id or fy_id from JWT payload if user only pasted raw access token
        val jwt = FyersJwtDecoder.parse(cleanedToken)
        if (jwt != null) {
            val extractedAppId = jwt.app_id ?: jwt.client_id
            if (!extractedAppId.isNullOrBlank()) {
                val normalizedExtracted = if (!extractedAppId.contains("-")) "$extractedAppId-100" else extractedAppId
                return "$normalizedExtracted:$cleanedToken"
            }
            val fyId = jwt.fy_id ?: jwt.user_id
            if (!fyId.isNullOrBlank()) {
                return "$fyId-100:$cleanedToken"
            }
        }

        return cleanedToken
    }

    /**
     * Builds prioritized list of candidate authorization headers to test
     * against Fyers APIs (e.g. XC12345-100 vs XC12345).
     */
    fun buildCandidateAuthHeaders(appId: String?, token: String, jwt: FyersJwtPayload?): List<CandidateAuth> {
        val list = mutableListOf<CandidateAuth>()

        // 1. If token already contains colon (APP_ID:TOKEN)
        if (token.contains(":")) {
            val parts = token.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                list.add(CandidateAuth(appId = parts[0], token = parts[1], authHeader = token))
                return list
            }
        }

        // 2. User-specified App ID (test with -100 first, then without)
        if (!appId.isNullOrBlank()) {
            if (appId.endsWith("-100")) {
                list.add(CandidateAuth(appId = appId, token = token, authHeader = "$appId:$token"))
                val stripped = appId.removeSuffix("-100")
                if (stripped.isNotBlank()) {
                    list.add(CandidateAuth(appId = stripped, token = token, authHeader = "$stripped:$token"))
                }
            } else {
                list.add(CandidateAuth(appId = "$appId-100", token = token, authHeader = "$appId-100:$token"))
                list.add(CandidateAuth(appId = appId, token = token, authHeader = "$appId:$token"))
            }
        }

        // 3. Extracted from JWT payload
        if (jwt != null) {
            val extractedAppId = jwt.app_id ?: jwt.client_id
            if (!extractedAppId.isNullOrBlank()) {
                if (!extractedAppId.endsWith("-100")) {
                    list.add(CandidateAuth(appId = "$extractedAppId-100", token = token, authHeader = "$extractedAppId-100:$token"))
                }
                list.add(CandidateAuth(appId = extractedAppId, token = token, authHeader = "$extractedAppId:$token"))
            }
            val fyId = jwt.fy_id ?: jwt.user_id
            if (!fyId.isNullOrBlank()) {
                list.add(CandidateAuth(appId = "$fyId-100", token = token, authHeader = "$fyId-100:$token"))
                list.add(CandidateAuth(appId = fyId, token = token, authHeader = "$fyId:$token"))
            }
        }

        // 4. Raw token fallback
        list.add(CandidateAuth(appId = appId, token = token, authHeader = token))

        return list.distinctBy { it.authHeader }
    }

    /**
     * Exchanges a temporary authorization code (from redirect URL or manual paste)
     * for a valid Fyers access token via POST https://api-t1.fyers.in/api/v3/validate-authcode.
     */
    suspend fun exchangeAuthCodeForAccessToken(
        appId: String,
        secretKey: String,
        authCodeOrUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanAuthCode = FyersTokenSanitizer.cleanToken(authCodeOrUrl)
        if (cleanAuthCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Auth code cannot be empty."))
        }
        val cleanSecret = secretKey.trim()
        if (cleanSecret.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("App Secret Key cannot be empty."))
        }

        val inputAppId = FyersTokenSanitizer.cleanAppId(appId)
        val candidateAppIds = buildList {
            if (inputAppId.endsWith("-100")) {
                add(inputAppId)
                add(inputAppId.removeSuffix("-100"))
            } else {
                add("$inputAppId-100")
                add(inputAppId)
            }
        }.distinct()

        var lastError: Exception? = null

        for (candidateId in candidateAppIds) {
            val hash = computeFyersAppIdHash(candidateId, cleanSecret)
            val jsonBody = """{"grant_type":"authorization_code","appIdHash":"$hash","code":"$cleanAuthCode"}"""

            val request = Request.Builder()
                .url("https://api-t1.fyers.in/api/v3/validate-authcode")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    val parsed = runCatching { json.decodeFromString<FyersValidateAuthCodeResponse>(bodyStr) }.getOrNull()
                    if (parsed != null && (parsed.code == 200 || parsed.s == "ok") && !parsed.access_token.isNullOrBlank()) {
                        return@withContext Result.success(parsed.access_token)
                    } else {
                        val code = parsed?.code ?: response.code
                        val msg = parsed?.message?.takeIf { it.isNotBlank() } ?: "Error $code"
                        lastError = FyersApiException(code, msg)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: IOException("Failed to exchange auth code for access token."))
    }

    /**
     * Test connection against Fyers API to verify whether the token is valid.
     * Evaluates multiple candidate header formats (with and without -100) and
     * detects auth codes or expired tokens before making calls.
     */
    suspend fun testConnection(appId: String?, token: String?): Result<FyersTestConnectionResult> = withContext(Dispatchers.IO) {
        val cleanedToken = FyersTokenSanitizer.cleanToken(token)
        if (cleanedToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your Fyers Access Token."))
        }

        val cleanedAppId = FyersTokenSanitizer.cleanAppId(appId).takeIf { it.isNotBlank() }

        // Step 1: Decode JWT & perform preflight checks
        val jwt = FyersJwtDecoder.parse(cleanedToken)
        if (jwt != null) {
            if (jwt.isAuthCode()) {
                return@withContext Result.failure(
                    FyersApiException(
                        code = -98,
                        message = "⚠️ You entered an Authorization Code ('auth_code'), NOT an Access Token!\n\nWhen you log in to Fyers in the browser, the redirect URL contains an auth_code which expires in 2 minutes and cannot be used directly for market data.\n\n👉 Use '⚡ 1-Tap Fyers Login' or 'Convert Auth Code' below to generate your daily Access Token in 1 tap."
                    )
                )
            }

            if (jwt.isExpired()) {
                val expDateStr = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(Date((jwt.exp ?: 0L) * 1000))
                return@withContext Result.failure(
                    FyersApiException(
                        code = -99,
                        message = "Access token expired on $expDateStr. Fyers access tokens expire daily at 6:30 AM IST. Please generate a fresh token."
                    )
                )
            }
        }

        // Step 2: Build candidate pairs
        val candidates = buildCandidateAuthHeaders(cleanedAppId, cleanedToken, jwt)

        var lastError: Exception? = null

        for (candidate in candidates) {
            try {
                val response = try {
                    executeQuotesRequest(primaryBaseUrl, candidate.authHeader)
                } catch (e: FyersApiException) {
                    if (e.code == -15) throw e // Token rejected, try next candidate
                    throw e
                } catch (e: Exception) {
                    executeQuotesRequest(secondaryBaseUrl, candidate.authHeader)
                }

                val items = response.items
                if (items.isEmpty()) {
                    throw IOException(response.message ?: "No market data received for NIFTY/SENSEX.")
                }

                val nifty = items.find { isNifty(it) }?.v?.lp ?: 0.0
                val sensex = items.find { isSensex(it) }?.v?.lp ?: 0.0

                val successMsg = if (nifty > 0.0 && sensex > 0.0) {
                    "✓ Connected to Fyers Live Feed!\nNIFTY 50: ₹${Formatters.formatIndexValue(nifty)} | SENSEX: ₹${Formatters.formatIndexValue(sensex)}"
                } else {
                    "✓ Fyers Authenticated successfully! (Indices will stream live prices during market hours)"
                }

                return@withContext Result.success(
                    FyersTestConnectionResult(
                        message = successMsg,
                        resolvedAppId = candidate.appId,
                        resolvedToken = candidate.token
                    )
                )
            } catch (e: FyersApiException) {
                lastError = e
                if (e.code != -15) break
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: IOException("Connection test failed"))
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
                    -15 -> "Please provide valid token. Note: Fyers requires format 'APP_ID:TOKEN' (e.g. YOUR_APP_ID-100:eyJ...). If you generated an Auth Code from the web login, please use '⚡ 1-Tap Fyers Login' or 'Convert Auth Code' to exchange it for an Access Token."
                    -98 -> rawMsg
                    -99 -> "Token is expired. Fyers access tokens expire daily at 6:30 AM IST. Please generate a fresh token from your Fyers dashboard or 1-Tap Login."
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
            symbol = fallbackSymbol,
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
