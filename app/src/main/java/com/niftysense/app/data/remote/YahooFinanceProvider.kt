package com.niftysense.app.data.remote

import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
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
import java.util.concurrent.TimeUnit

class YahooFinanceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : MarketDataProvider {

    override val providerName: String = "NSE & BSE Live (Yahoo Finance Feed)"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val primaryBaseUrl = "https://query1.finance.yahoo.com/v8/finance/chart/"
    private val secondaryBaseUrl = "https://query2.finance.yahoo.com/v8/finance/chart/"

    companion object {
        const val NIFTY_SYMBOL = "%5ENSEI"
        const val SENSEX_SYMBOL = "%5EBSESN"
    }

    override suspend fun fetchMarketSnapshot(): Result<MarketSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                val niftyDeferred = async { fetchIndexData(NIFTY_SYMBOL, "NIFTY 50", "NSE") }
                val sensexDeferred = async { fetchIndexData(SENSEX_SYMBOL, "SENSEX", "BSE") }

                val nifty = niftyDeferred.await().getOrThrow()
                val sensex = sensexDeferred.await().getOrThrow()

                val marketStatus = MarketStatus.current()

                MarketSnapshot(
                    nifty50 = nifty,
                    sensex = sensex,
                    marketStatus = marketStatus,
                    lastSyncTimestamp = System.currentTimeMillis(),
                    isCached = false,
                    sourceProvider = providerName
                )
            }
        }
    }

    private suspend fun fetchIndexData(
        symbolParam: String,
        displayName: String,
        exchange: String
    ): Result<MarketIndexData> {
        // Try primary URL, then secondary URL
        val urls = listOf(
            "$primaryBaseUrl$symbolParam?interval=5m&range=1d",
            "$secondaryBaseUrl$symbolParam?interval=5m&range=1d"
        )

        var lastException: Throwable? = null

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP error ${response.code} from $url")
                    }

                    val bodyString = response.body?.string()
                        ?: throw IOException("Empty response body from $url")

                    val chartResponse = json.decodeFromString<YahooChartResponse>(bodyString)
                    val result = chartResponse.chart.result?.firstOrNull()
                        ?: throw IOException("No chart result found for $displayName")

                    val meta = result.meta
                    val currentPrice = meta.regularMarketPrice
                        ?: throw IOException("Missing current market price for $displayName")

                    val prevClose = meta.chartPreviousClose ?: meta.previousClose ?: currentPrice
                    val absChange = currentPrice - prevClose
                    val pctChange = if (prevClose > 0.0) (absChange / prevClose) * 100.0 else 0.0

                    // Extract close quotes for sparkline
                    val rawCloses = result.indicators?.quote?.firstOrNull()?.close ?: emptyList()
                    val validCloses = rawCloses.filterNotNull()
                    val sparkline = if (validCloses.isNotEmpty()) {
                        validCloses
                    } else {
                        listOf(prevClose, currentPrice)
                    }

                    val openPrice = result.indicators?.quote?.firstOrNull()?.open?.firstOrNull { it != null }
                        ?: prevClose

                    val dayHigh = meta.regularMarketDayHigh
                        ?: sparkline.maxOrNull()
                        ?: currentPrice

                    val dayLow = meta.regularMarketDayLow
                        ?: sparkline.minOrNull()
                        ?: currentPrice

                    val lastUpdated = (meta.regularMarketTime ?: (System.currentTimeMillis() / 1000L)) * 1000L

                    return Result.success(
                        MarketIndexData(
                            symbol = meta.symbol,
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
                lastException = e
            }
        }

        return Result.failure(lastException ?: IOException("Failed to fetch market data for $displayName"))
    }
}
