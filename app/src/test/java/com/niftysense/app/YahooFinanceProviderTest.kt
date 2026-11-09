package com.niftysense.app

import com.niftysense.app.data.remote.YahooFinanceProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class YahooFinanceProviderTest {

    @Test
    fun testLiveFetch() = runBlocking {
        val provider = YahooFinanceProvider()
        val result = provider.fetchMarketSnapshot()
        println("Fetch result: $result")
        if (result.isFailure) {
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue("Expected live fetch to succeed, but got: ${result.exceptionOrNull()}", result.isSuccess)
        val snapshot = result.getOrNull()
        assertNotNull(snapshot)
        println("NIFTY 50: ${snapshot?.nifty50?.currentValue}, abs=${snapshot?.nifty50?.absoluteChange}, pct=${snapshot?.nifty50?.percentageChange}")
        println("SENSEX: ${snapshot?.sensex?.currentValue}, abs=${snapshot?.sensex?.absoluteChange}, pct=${snapshot?.sensex?.percentageChange}")
        assertTrue(snapshot!!.nifty50.currentValue > 0.0)
        assertTrue(snapshot.sensex.currentValue > 0.0)
    }
}
