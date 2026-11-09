package com.niftysense.app.data.remote

import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.data.remote.dto.MoneycontrolResponse
import com.niftysense.app.data.remote.dto.YahooChartResponse
import com.niftysense.app.data.repository.MarketDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class HybridMarketDataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : MarketDataProvider {

    override val providerName: String = "NSE & BSE Live Real-time Feed"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val mcNiftyUrl = "https://priceapi.moneycontrol.com/pricefeed/notapplicable/inidicesindia/in%3BNSX"
    private val mcSensexUrl = "https://priceapi.moneycontrol.com/pricefeed/notapplicable/inidicesindia/in%3BSEN"

    private val yahooNiftyUrl = "https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?interval=1m&range=1d"
    private val yahooSensexUrl = "https://query1.finance.yahoo.com/v8/finance/chart/%5EBSESN?interval=1m&range=1d"

    override suspend fun fetchMarketSnapshot(): Result<MarketSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                // 1. Fetch real-time tick data from Moneycontrol (second-by-second live)
                val mcNiftyDeferred = async { fetchMoneycontrolIndex(mcNiftyUrl, "NIFTY 50", "NSE", "^NSEI") }
                val mcSensexDeferred = async { fetchMoneycontrolIndex(mcSensexUrl, "SENSEX", "BSE", "^BSESN") }

                // 2. Fetch sparkline arrays from Yahoo Finance
                val yahooNiftyDeferred = async { fetchYahooSparkline(yahooNiftyUrl) }
                val yahooSensexDeferred = async { fetchYahooSparkline(yahooSensexUrl) }

                val mcNiftyResult = mcNiftyDeferred.await()
                val mcSensexResult = mcSensexDeferred.await()
                val niftySparkline = yahooNiftyDeferred.await().getOrNull() ?: emptyList()
                val sensexSparkline = yahooSensexDeferred.await().getOrNull() ?: emptyList()

                // If Moneycontrol succeeds for both, use the true real-time prices
                val niftyData: MarketIndexData
                val sensexData: MarketIndexData

                if (mcNiftyResult.isSuccess && mcSensexResult.isSuccess) {
                    val rawNifty = mcNiftyResult.getOrThrow()
                    val rawSensex = mcSensexResult.getOrThrow()

                    niftyData = rawNifty.copy(
                        sparklinePoints = if (niftySparkline.isNotEmpty()) niftySparkline else rawNifty.sparklinePoints
                    )
                    sensexData = rawSensex.copy(
                        sparklinePoints = if (sensexSparkline.isNotEmpty()) sensexSparkline else rawSensex.sparklinePoints
                    )
                } else {
                    // Fallback to Yahoo Finance full parse
                    val yahooProvider = YahooFinanceProvider(client)
                    val yahooSnapshot = yahooProvider.fetchMarketSnapshot().getOrThrow()
                    return@coroutineScope yahooSnapshot
                }

                val marketStatus = MarketStatus.current()

                MarketSnapshot(
                    nifty50 = niftyData,
                    sensex = sensexData,
                    marketStatus = marketStatus,
                    lastSyncTimestamp = System.currentTimeMillis(),
                    isCached = false,
                    sourceProvider = providerName
                )
            }
        }
    }

    private fun fetchMoneycontrolIndex(
        baseUrl: String,
        displayName: String,
        exchange: String,
        symbol: String
    ): Result<MarketIndexData> {
        return try {
            val cacheBusterUrl = "$baseUrl?_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(cacheBusterUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP ${response.code} from Moneycontrol for $displayName"))
                }

                val body = response.body?.string()
                    ?: return Result.failure(IOException("Empty response body for $displayName"))

                val parsed = json.decodeFromString<MoneycontrolResponse>(body)
                val data = parsed.data
                    ?: return Result.failure(IOException("Null data in response for $displayName"))

                val currentPrice = data.pricecurrent?.replace(",", "")?.toDoubleOrNull()
                    ?: return Result.failure(IOException("Missing pricecurrent for $displayName"))

                val prevClose = data.priceprevclose?.replace(",", "")?.toDoubleOrNull() ?: currentPrice
                val absChange = data.pricechange?.replace(",", "")?.toDoubleOrNull() ?: (currentPrice - prevClose)
                val pctChange = data.pricepercentchange?.replace(",", "")?.toDoubleOrNull()
                    ?: if (prevClose > 0.0) ((absChange / prevClose) * 100.0) else 0.0

                val dayHigh = data.HIGH?.replace(",", "")?.toDoubleOrNull() ?: currentPrice
                val dayLow = data.LOW?.replace(",", "")?.toDoubleOrNull() ?: currentPrice
                val openPrice = data.OPEN?.replace(",", "")?.toDoubleOrNull() ?: prevClose

                val lastUpdated = parseMoneycontrolTimestamp(data.lastupd)

                val sparkline = listOf(prevClose, openPrice, dayLow, dayHigh, currentPrice)

                Result.success(
                    MarketIndexData(
                        symbol = symbol,
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
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchYahooSparkline(baseUrl: String): Result<List<Double>> {
        return try {
            val cacheBusterUrl = "$baseUrl&_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(cacheBusterUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Yahoo chart HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return Result.failure(IOException("Empty Yahoo response"))
                val chartResponse = json.decodeFromString<YahooChartResponse>(body)
                val result = chartResponse.chart.result?.firstOrNull() ?: return Result.failure(IOException("No chart result"))
                val rawCloses = result.indicators?.quote?.firstOrNull()?.close ?: emptyList()
                val validCloses = rawCloses.filterNotNull()
                Result.success(validCloses)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMoneycontrolTimestamp(timestampStr: String?): Long {
        if (timestampStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            sdf.parse(timestampStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
