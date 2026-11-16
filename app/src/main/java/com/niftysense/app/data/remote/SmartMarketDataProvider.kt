package com.niftysense.app.data.remote

import android.util.Log
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.repository.MarketDataProvider

class SmartMarketDataProvider(
    val fyersProvider: FyersMarketDataProvider,
    val fyersWebSocketClient: FyersWebSocketClient,
    val hybridProvider: HybridMarketDataProvider = HybridMarketDataProvider(),
    private val preferencesDataStore: MarketPreferencesDataStore
) : MarketDataProvider {

    companion object {
        private const val TAG = "SmartMarketDataProvider"
    }

    override val providerName: String
        get() = "Smart Switching Market Feed (Fyers WebSocket + REST Quotes + Public Fallback)"

    override suspend fun fetchMarketSnapshot(): Result<MarketSnapshot> {
        val (_, token) = preferencesDataStore.getFyersCredentials()

        if (!token.isNullOrBlank()) {
            // 1. Prioritize live sub-second Fyers WebSocket tick if fresh (< 45s)
            val wsSnapshot = fyersWebSocketClient.getLatestSnapshotIfFresh(maxAgeMs = 45_000L)
            if (wsSnapshot != null) {
                return Result.success(wsSnapshot)
            }

            // 2. Query Fyers REST quotes API
            val fyersResult = fyersProvider.fetchMarketSnapshot()
            if (fyersResult.isSuccess) {
                return fyersResult
            } else {
                val error = fyersResult.exceptionOrNull()
                Log.w(TAG, "Fyers live fetch failed: ${error?.message}. Seamlessly falling back to normal public feed.")
            }
        }

        // 3. Fallback to normal public web feeds
        return hybridProvider.fetchMarketSnapshot()
    }
}
