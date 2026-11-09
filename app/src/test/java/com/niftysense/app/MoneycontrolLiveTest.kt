package com.niftysense.app

import com.niftysense.app.data.remote.dto.MoneycontrolResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MoneycontrolLiveTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient()

    @Test
    fun testLiveMoneycontrolNiftyAndSensex() {
        val urls = mapOf(
            "NIFTY 50" to "https://priceapi.moneycontrol.com/pricefeed/notapplicable/inidicesindia/in%3BNSX",
            "SENSEX" to "https://priceapi.moneycontrol.com/pricefeed/notapplicable/inidicesindia/in%3BSEN"
        )

        for ((name, url) in urls) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                assertTrue("Expected 200 for $name", response.isSuccessful)
                val body = response.body?.string()
                assertNotNull(body)
                val parsed = json.decodeFromString<MoneycontrolResponse>(body!!)
                val data = parsed.data
                assertNotNull("Expected data for $name", data)
                println("$name price: ${data?.pricecurrent}, change: ${data?.pricechange}, lastupd: ${data?.lastupd}")

                val price = data?.pricecurrent?.replace(",", "")?.toDoubleOrNull()
                assertNotNull("Valid price for $name", price)
                assertTrue("Price > 0 for $name", price!! > 0.0)

                if (data?.lastupd != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                    }
                    val timestamp = sdf.parse(data.lastupd)?.time
                    println("$name parsed timestamp: $timestamp")
                    assertNotNull(timestamp)
                }
            }
        }
    }
}
