package com.niftysense.app

import com.niftysense.app.data.remote.FyersMarketDataProvider
import com.niftysense.app.data.remote.dto.FyersQuoteData
import com.niftysense.app.data.remote.dto.FyersQuoteValues
import com.niftysense.app.data.remote.dto.FyersQuotesResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FyersMarketDataProviderTest {

    private val provider = FyersMarketDataProvider(
        credentialsProvider = { Pair("MYAPPID-100", "sample_access_token_1234567890") }
    )

    @Test
    fun testResolveAuthHeader_SeparateAppIdAndToken() {
        val header = provider.resolveAuthHeader("MYAPPID-100", "sample_token_xyz")
        assertEquals("MYAPPID-100:sample_token_xyz", header)
    }

    @Test
    fun testResolveAuthHeader_CombinedPastedToken() {
        val combined = "MYAPPID-100:sample_token_xyz_9876543210"
        val header = provider.resolveAuthHeader(null, combined)
        assertEquals(combined, header)
    }

    @Test
    fun testResolveAuthHeader_EmptyTokenReturnsNull() {
        val header = provider.resolveAuthHeader("MYAPPID-100", "")
        assertNull(header)
    }

    @Test
    fun testFyersQuotesResponseJsonParsing() {
        val sampleJson = """
        {
          "s": "ok",
          "code": 200,
          "message": "",
          "d": [
            {
              "name": "NSE:NIFTY50-INDEX",
              "symbol": "NSE:NIFTY50-INDEX",
              "v": {
                "lp": 25112.10,
                "ch": 112.50,
                "chp": 0.48,
                "open_price": 25050.00,
                "high_price": 25180.00,
                "low_price": 25020.00,
                "prev_close_price": 24999.60,
                "exch_feed_time": 1718011986
              }
            },
            {
              "name": "BSE:SENSEX-INDEX",
              "symbol": "BSE:SENSEX-INDEX",
              "v": {
                "lp": 81460.10,
                "ch": 340.20,
                "chp": 0.42,
                "open_price": 81200.00,
                "high_price": 81650.00,
                "low_price": 81100.00,
                "prev_close_price": 81119.90,
                "exch_feed_time": 1718011986
              }
            }
          ]
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val parsed = json.decodeFromString<FyersQuotesResponse>(sampleJson)

        assertEquals("ok", parsed.s)
        assertEquals(200, parsed.code)
        assertEquals(2, parsed.items.size)

        val nifty = parsed.items.find { it.symbol == "NSE:NIFTY50-INDEX" }
        assertNotNull(nifty)
        assertEquals(25112.10, nifty!!.v!!.lp!!, 0.01)
        assertEquals(112.50, nifty.v!!.ch!!, 0.01)
        assertEquals(0.48, nifty.v!!.chp!!, 0.01)

        val sensex = parsed.items.find { it.symbol == "BSE:SENSEX-INDEX" }
        assertNotNull(sensex)
        assertEquals(81460.10, sensex!!.v!!.lp!!, 0.01)
        assertEquals(340.20, sensex.v!!.ch!!, 0.01)
        assertEquals(0.42, sensex.v!!.chp!!, 0.01)
    }

    @Test
    fun testFyersV3QuotesRealFormatWithNKey() {
        val sampleJson = """
        {
          "s": "ok",
          "code": 200,
          "message": "",
          "d": [
            {
              "n": "NSE:NIFTY50-INDEX",
              "s": "ok",
              "v": {
                "lp": 24150.30,
                "ch": -45.20,
                "chp": -0.19,
                "open_price": 24200.00,
                "high_price": 24250.00,
                "low_price": 24100.00,
                "prev_close_price": 24195.50
              }
            },
            {
              "n": "BSE:SENSEX-INDEX",
              "s": "ok",
              "v": {
                "lp": 79500.50,
                "ch": -120.00,
                "chp": -0.15,
                "open_price": 79650.00,
                "high_price": 79800.00,
                "low_price": 79400.00,
                "prev_close_price": 79620.50
              }
            }
          ]
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val parsed = json.decodeFromString<FyersQuotesResponse>(sampleJson)

        assertEquals("ok", parsed.s)
        assertEquals(2, parsed.items.size)

        val nifty = parsed.items.find { it.symbolIdentifier == "NSE:NIFTY50-INDEX" }
        assertNotNull(nifty)
        assertEquals("NSE:NIFTY50-INDEX", nifty!!.n)
        assertEquals(24150.30, nifty.v!!.lp!!, 0.01)

        val sensex = parsed.items.find { it.symbolIdentifier == "BSE:SENSEX-INDEX" }
        assertNotNull(sensex)
        assertEquals("BSE:SENSEX-INDEX", sensex!!.n)
        assertEquals(79500.50, sensex.v!!.lp!!, 0.01)
    }
}
