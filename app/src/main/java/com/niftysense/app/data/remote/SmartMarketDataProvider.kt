package com.niftysense.app.data.remote

import android.util.Log
import com.niftysense.app.data.local.MarketPreferencesDataStore
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.repository.MarketDataProvider

class SmartMarketDataProvider(
    val fyersProvider: FyersMarketDataProvider,
    val hybridProvider: HybridMarketDataProvider = HybridMarketDataProvider(),
    private val preferencesDataStore: MarketPreferencesDataStore
) : MarketDataProvider {

    companion object {
        private const val TAG = "SmartMarketDataProvider"
    }

    override val providerName: String
        get() = "Smart Switching Market Feed (Fyers + Public Fallback)"

    override suspend fun fetchMarketSnapshot(): Result<MarketSnapshot> {
        val (_, token) = preferencesDataStore.getFyersCredentials()

        if (!token.isNullOrBlank()) {
            // Fyers is configured and enabled!
            val fyersResult = fyersProvider.fetchMarketSnapshot()
            if (fyersResult.isSuccess) {
                return fyersResult
            } else {
                val error = fyersResult.exceptionOrNull()
                Log.w(TAG, "Fyers live fetch failed: ${error?.message}. Seamlessly falling back to public feed.")
            }
        }

        // Fallback to Moneycontrol & Yahoo Finance
        return hybridProvider.fetchMarketSnapshot()
    }
}
