package com.niftysense.app.data.repository

import com.niftysense.app.data.model.MarketSnapshot

interface MarketDataProvider {
    val providerName: String
    suspend fun fetchMarketSnapshot(): Result<MarketSnapshot>
}
