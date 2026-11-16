package com.niftysense.app

import com.niftysense.app.data.remote.FyersWebSocketClient
import com.niftysense.app.data.remote.dto.FyersJwtDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FyersWebSocketClientTest {

    private val wsClient = FyersWebSocketClient()

    @Test
    fun testBuildAuthMessageStructure() {
        val hsmKey = "hsm_sample_secret_key"
        val packet = wsClient.buildAuthMessage(hsmKey, source = "NiftySense-Android")

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val length = buffer.short.toInt() and 0xFFFF
        assertEquals(packet.size - 2, length)

        val reqType = buffer.get().toInt() and 0xFF
        assertEquals(1, reqType) // 1 = Auth

        val fieldCount = buffer.get().toInt() and 0xFF
        assertEquals(4, fieldCount)

        // Field 1: AuthToken
        val f1Id = buffer.get().toInt() and 0xFF
        val f1Len = buffer.short.toInt() and 0xFFFF
        val f1Bytes = ByteArray(f1Len)
        buffer.get(f1Bytes)
        assertEquals(1, f1Id)
        assertEquals(hsmKey, String(f1Bytes, Charsets.UTF_8))

        // Field 2: mode
        val f2Id = buffer.get().toInt() and 0xFF
        val f2Len = buffer.short.toInt() and 0xFFFF
        val f2Bytes = ByteArray(f2Len)
        buffer.get(f2Bytes)
        assertEquals(2, f2Id)
        assertEquals("P", String(f2Bytes, Charsets.UTF_8))
    }

    @Test
    fun testBuildSubscriptionMessageStructure() {
        val symbols = listOf("if|nse_cm|Nifty 50", "if|bse_cm|SENSEX")
        val packet = wsClient.buildSubscriptionMessage(symbols, channelNum = 11)

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.short // length
        val reqType = buffer.get().toInt() and 0xFF
        assertEquals(4, reqType) // 4 = Subscription

        val fieldCount = buffer.get().toInt() and 0xFF
        assertEquals(2, fieldCount)
    }

    @Test
    fun testParseAuthResponse() {
        // Construct valid auth response (field 1 length 1 string "K")
        val buffer = ByteBuffer.allocate(15).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(13) // len
        buffer.put(1.toByte()) // respType = 1
        buffer.put(2.toByte()) // field count
        buffer.put(1.toByte()) // field 1 id
        buffer.putShort(1.toShort()) // field 1 len = 1
        buffer.put("K".toByteArray(Charsets.UTF_8)) // status = "K"
        buffer.put(2.toByte())
        buffer.putShort(4.toShort())
        buffer.putInt(100)

        val isSuccess = wsClient.parseAuthResponse(buffer.array())
        assertTrue("Auth 'K' should succeed", isSuccess)

        // Test failed auth response ("F")
        val failBuf = ByteBuffer.allocate(15).order(ByteOrder.BIG_ENDIAN)
        failBuf.putShort(13)
        failBuf.put(1.toByte())
        failBuf.put(2.toByte())
        failBuf.put(1.toByte())
        failBuf.putShort(1.toShort())
        failBuf.put("F".toByteArray(Charsets.UTF_8))
        failBuf.put(2.toByte())
        failBuf.putShort(4.toShort())
        failBuf.putInt(0)

        assertFalse("Auth 'F' should fail", wsClient.parseAuthResponse(failBuf.array()))
    }

    @Test
    fun testParseDataFeedSnapshotAndEmitSnapshot() {
        // Build synthetic Snapshot (83) frame for NIFTY 50 and SENSEX
        val pkt = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN)
        pkt.putShort(0) // placeholder length
        pkt.put(6.toByte()) // respType = 6 (Data Feed)
        pkt.putInt(1) // messageNum = 1
        pkt.putShort(2.toShort()) // scripCount = 2

        // Scrip 1: NIFTY 50
        pkt.put(83.toByte()) // dataType = 83 (Snapshot)
        // Topic ID little-endian
        val topicNifty = 101
        pkt.put((topicNifty and 0xFF).toByte())
        pkt.put(((topicNifty shr 8) and 0xFF).toByte())
        val niftyName = "if|nse_cm|Nifty 50"
        pkt.put(niftyName.length.toByte())
        pkt.put(niftyName.toByteArray(Charsets.UTF_8))

        pkt.put(6.toByte()) // 6 fields for index
        pkt.putInt(2415050) // ltp = 24150.50 (precision 2)
        pkt.putInt(2410000) // prev_close = 24100.00
        pkt.putInt(1718000000) // feed time
        pkt.putInt(2420000) // high = 24200.00
        pkt.putInt(2405000) // low = 24050.00
        pkt.putInt(2412000) // open = 24120.00

        pkt.putShort(0) // 2 bytes skip
        pkt.putShort(1.toShort()) // multiplier = 1
        pkt.put(2.toByte()) // precision = 2

        // 3 metadata strings
        for (str in listOf("NSE", "Nifty 50", "NSE:NIFTY50-INDEX")) {
            val b = str.toByteArray(Charsets.UTF_8)
            pkt.put(b.size.toByte())
            pkt.put(b)
        }

        // Scrip 2: SENSEX
        pkt.put(83.toByte()) // dataType = 83
        val topicSensex = 102
        pkt.put((topicSensex and 0xFF).toByte())
        pkt.put(((topicSensex shr 8) and 0xFF).toByte())
        val sensexName = "if|bse_cm|SENSEX"
        pkt.put(sensexName.length.toByte())
        pkt.put(sensexName.toByteArray(Charsets.UTF_8))

        pkt.put(6.toByte())
        pkt.putInt(7950050) // ltp = 79500.50
        pkt.putInt(7940000) // prev_close = 79400.00
        pkt.putInt(1718000000)
        pkt.putInt(7970000) // high
        pkt.putInt(7930000) // low
        pkt.putInt(7945000) // open

        pkt.putShort(0)
        pkt.putShort(1.toShort())
        pkt.put(2.toByte())

        for (str in listOf("BSE", "SENSEX", "BSE:SENSEX-INDEX")) {
            val b = str.toByteArray(Charsets.UTF_8)
            pkt.put(b.size.toByte())
            pkt.put(b)
        }

        val frameBytes = pkt.array().copyOf(pkt.position())
        ByteBuffer.wrap(frameBytes).order(ByteOrder.BIG_ENDIAN).putShort(0, (frameBytes.size - 2).toShort())

        // Parse through WebSocket client
        wsClient.parseDataFeed(frameBytes)

        val snapshot = wsClient.liveSnapshotFlow.value
        assertNotNull("MarketSnapshot should be emitted from WebSocket feed", snapshot)
        assertEquals("Fyers Live WebSocket", snapshot!!.sourceProvider)

        assertEquals("NSE:NIFTY50-INDEX", snapshot.nifty50.symbol)
        assertEquals(24150.50, snapshot.nifty50.currentValue, 0.01)
        assertEquals(24100.00, snapshot.nifty50.previousClose, 0.01)
        assertEquals(50.50, snapshot.nifty50.absoluteChange, 0.01)

        assertEquals("BSE:SENSEX-INDEX", snapshot.sensex.symbol)
        assertEquals(79500.50, snapshot.sensex.currentValue, 0.01)
        assertEquals(79400.00, snapshot.sensex.previousClose, 0.01)
        assertEquals(100.50, snapshot.sensex.absoluteChange, 0.01)
    }

    @Test
    fun testFyersJwtDecoder() {
        // Construct dummy JWT header.payload.signature
        val payloadJson = """{"fy_id":"XC12345","app_id":"MYAPP-100","hsm_key":"sample_hsm_123","exp":1893456000}"""
        val encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        val token = "eyJhbGciOiJIUzI1NiJ9.$encodedPayload.fake_signature"

        val parsed = FyersJwtDecoder.parse(token)
        assertNotNull(parsed)
        assertEquals("XC12345", parsed!!.fy_id)
        assertEquals("MYAPP-100", parsed.app_id)
        assertEquals("sample_hsm_123", parsed.hsm_key)
        assertEquals(1893456000L, parsed.exp)
    }
}
