package com.niftysense.app

import com.niftysense.app.data.remote.HybridMarketDataProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMarketDataProviderTest {

    @Test
    fun testRealtimeHybridFetch() = runBlocking {
        val provider = HybridMarketDataProvider()
        val result = provider.fetchMarketSnapshot()
        assertTrue("Fetch should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val snapshot = result.getOrThrow()
        assertNotNull(snapshot.nifty50)
        assertNotNull(snapshot.sensex)

        println("HYBRID REALTIME TEST:")
        println("NIFTY 50: ${snapshot.nifty50.currentValue}, change: ${snapshot.nifty50.absoluteChange} (${snapshot.nifty50.percentageChange}%), updated: ${snapshot.nifty50.lastUpdated}")
        println("SENSEX: ${snapshot.sensex.currentValue}, change: ${snapshot.sensex.absoluteChange} (${snapshot.sensex.percentageChange}%), updated: ${snapshot.sensex.lastUpdated}")
        println("Provider: ${snapshot.sourceProvider}")

        assertTrue(snapshot.nifty50.currentValue > 10000.0)
        assertTrue(snapshot.sensex.currentValue > 30000.0)
    }
}
