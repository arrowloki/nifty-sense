package com.niftysense.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketSnapshot(
    val nifty50: MarketIndexData,
    val sensex: MarketIndexData,
    val marketStatus: MarketStatus,
    val lastSyncTimestamp: Long,
    val isCached: Boolean = false,
    val sourceProvider: String = "Yahoo Finance (NSE/BSE Realtime)"
)
