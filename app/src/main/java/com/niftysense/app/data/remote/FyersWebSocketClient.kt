package com.niftysense.app.data.remote

import android.util.Log
import com.niftysense.app.data.model.MarketIndexData
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.data.remote.dto.FyersJwtDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class FyersWebSocketStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    SUBSCRIBED,
    STREAMING,
    ERROR
}

data class FyersWebSocketState(
    val status: FyersWebSocketStatus = FyersWebSocketStatus.DISCONNECTED,
    val message: String? = null,
    val lastTickTimestamp: Long = 0L
)

class FyersWebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS) // We manage custom Fyers binary keepalive
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket long-lived connection
        .build(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    companion object {
        private const val TAG = "FyersWebSocketClient"
        const val SOCKET_URL = "wss://socket.fyers.in/hsm/v1-5/prod"
        const val NIFTY_TOPIC = "if|nse_cm|Nifty 50"
        const val SENSEX_TOPIC = "if|bse_cm|SENSEX"
        const val NIFTY_SYMBOL = "NSE:NIFTY50-INDEX"
        const val SENSEX_SYMBOL = "BSE:SENSEX-INDEX"
    }

    private val _connectionState = MutableStateFlow(FyersWebSocketState())
    val connectionState: StateFlow<FyersWebSocketState> = _connectionState.asStateFlow()

    private val _liveSnapshotFlow = MutableStateFlow<MarketSnapshot?>(null)
    val liveSnapshotFlow: StateFlow<MarketSnapshot?> = _liveSnapshotFlow.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentHsmKey: String? = null
    private var isExplicitlyDisconnected = false

    // Topic ID to Topic Name mapping (learned from Snapshot 83 frames)
    private val topicIdToName = ConcurrentHashMap<Int, String>()
    private val topicIdToDivisor = ConcurrentHashMap<Int, Double>()

    // Latest index quotes received over WebSocket
    @Volatile private var latestNiftyData: MarketIndexData? = null
    @Volatile private var latestSensexData: MarketIndexData? = null

    fun connect(appId: String?, token: String?) {
        isExplicitlyDisconnected = false
        reconnectJob?.cancel()

        val trimmedToken = token?.trim()
        if (trimmedToken.isNullOrBlank()) {
            _connectionState.value = FyersWebSocketState(
                status = FyersWebSocketStatus.ERROR,
                message = "Access token is empty"
            )
            return
        }

        // Decode HSM key from JWT payload
        val jwt = FyersJwtDecoder.parse(trimmedToken)
        val hsmKey = jwt?.hsm_key

        if (hsmKey.isNullOrBlank()) {
            Log.w(TAG, "JWT payload does not contain 'hsm_key'. Falling back to REST quotes.")
            _connectionState.value = FyersWebSocketState(
                status = FyersWebSocketStatus.ERROR,
                message = "Token missing 'hsm_key'. Please generate a valid Fyers API v3 token."
            )
            return
        }

        // Check token expiration
        val exp = jwt.exp
        if (exp != null && exp > 0 && exp < System.currentTimeMillis() / 1000) {
            _connectionState.value = FyersWebSocketState(
                status = FyersWebSocketStatus.ERROR,
                message = "Fyers token is expired (daily expiry at 6:30 AM)."
            )
            return
        }

        currentHsmKey = hsmKey
        startSocketConnection()
    }

    private fun startSocketConnection() {
        val hsmKey = currentHsmKey ?: return
        if (isExplicitlyDisconnected) return

        disconnectInternal(keepHsmKey = true)

        _connectionState.value = FyersWebSocketState(
            status = FyersWebSocketStatus.CONNECTING,
            message = "Connecting to Fyers WebSocket..."
        )

        val request = Request.Builder()
            .url(SOCKET_URL)
            .header("User-Agent", "NiftySense-Android/1.0")
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected. Sending authentication packet...")
                _connectionState.value = FyersWebSocketState(
                    status = FyersWebSocketStatus.AUTHENTICATING,
                    message = "Authenticating with Fyers socket..."
                )

                // 1. Send binary Auth packet
                val authPacket = buildAuthMessage(hsmKey)
                webSocket.send(ByteString.of(*authPacket))

                // 2. Start keepalive ping routine (10s binary heartbeat)
                startPingKeepalive(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingBytes(webSocket, bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                pingJob?.cancel()
                if (!isExplicitlyDisconnected) {
                    _connectionState.value = FyersWebSocketState(
                        status = FyersWebSocketStatus.DISCONNECTED,
                        message = "Connection closed: $reason. Reconnecting..."
                    )
                    scheduleReconnect()
                } else {
                    _connectionState.value = FyersWebSocketState(
                        status = FyersWebSocketStatus.DISCONNECTED,
                        message = "Disconnected"
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                pingJob?.cancel()
                _connectionState.value = FyersWebSocketState(
                    status = FyersWebSocketStatus.ERROR,
                    message = t.localizedMessage ?: "WebSocket connection failed"
                )
                if (!isExplicitlyDisconnected) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleIncomingBytes(webSocket: WebSocket, data: ByteArray) {
        if (data.size < 3) return
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.BIG_ENDIAN)

        buffer.short // 2 bytes length
        val respType = buffer.get().toInt() and 0xFF

        when (respType) {
            1 -> {
                // Auth Response
                val success = parseAuthResponse(data)
                if (success) {
                    Log.i(TAG, "Fyers WebSocket authenticated! Subscribing to NIFTY 50 and SENSEX...")
                    _connectionState.value = FyersWebSocketState(
                        status = FyersWebSocketStatus.SUBSCRIBED,
                        message = "Authenticated. Subscribing to indices..."
                    )
                    val subPacket = buildSubscriptionMessage(listOf(NIFTY_TOPIC, SENSEX_TOPIC))
                    webSocket.send(ByteString.of(*subPacket))
                } else {
                    Log.e(TAG, "Fyers WebSocket authentication failed")
                    _connectionState.value = FyersWebSocketState(
                        status = FyersWebSocketStatus.ERROR,
                        message = "Fyers WebSocket authentication rejected"
                    )
                }
            }
            4 -> {
                // Subscription Response
                Log.i(TAG, "Fyers WebSocket subscribed successfully! Streaming live index feeds.")
                _connectionState.value = FyersWebSocketState(
                    status = FyersWebSocketStatus.STREAMING,
                    message = "Live streaming NIFTY & SENSEX ticks",
                    lastTickTimestamp = System.currentTimeMillis()
                )
            }
            6 -> {
                // Data Feed (Snapshot 83 or Update 85)
                parseDataFeed(data)
            }
            51 -> {
                // Custom Message / Server Error
                Log.w(TAG, "Fyers WebSocket custom message received: size=${data.size}")
            }
        }
    }

    /**
     * Unpacks DataFeed frame containing Snapshot (83) or Update (85) ticks.
     */
    fun parseDataFeed(data: ByteArray) {
        if (data.size < 9) return
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // Header: length(2) + respType(1) + messageNum(4) + scripCount(2)
        buffer.position(7)
        val scripCount = buffer.short.toInt() and 0xFFFF
        if (scripCount <= 0) return

        var hasNewTick = false

        for (s in 0 until scripCount) {
            if (buffer.remaining() < 1) break
            val dataType = buffer.get().toInt() and 0xFF

            when (dataType) {
                83 -> {
                    // Snapshot datafeed
                    if (buffer.remaining() < 3) break
                    val topicId = (buffer.get().toInt() and 0xFF) or ((buffer.get().toInt() and 0xFF) shl 8) // little-endian short
                    val topicNameLen = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() < topicNameLen) break
                    val nameBytes = ByteArray(topicNameLen)
                    buffer.get(nameBytes)
                    val topicName = String(nameBytes, Charsets.UTF_8)
                    topicIdToName[topicId] = topicName

                    if (topicName.startsWith("if")) {
                        // Index Snapshot
                        if (buffer.remaining() < 1) break
                        val fieldCount = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < fieldCount * 4 + 5) break

                        val fields = LongArray(fieldCount)
                        for (i in 0 until fieldCount) {
                            fields[i] = buffer.int.toLong()
                        }
                        buffer.short // 2 bytes skip
                        val multiplier = buffer.short.toInt() and 0xFFFF
                        val precision = buffer.get().toInt() and 0xFF

                        // Skip 3 metadata strings
                        for (si in 0 until 3) {
                            if (buffer.remaining() < 1) break
                            val slen = buffer.get().toInt() and 0xFF
                            if (buffer.remaining() >= slen) {
                                buffer.position(buffer.position() + slen)
                            }
                        }

                        val mult = if (multiplier > 0) multiplier else 1
                        val divisor = Math.pow(10.0, precision.toDouble()) * mult
                        topicIdToDivisor[topicId] = divisor

                        val ltp = fields.getOrNull(0)?.toDouble()?.div(divisor) ?: 0.0
                        val prevClose = fields.getOrNull(1)?.toDouble()?.div(divisor) ?: ltp
                        val feedTime = fields.getOrNull(2) ?: (System.currentTimeMillis() / 1000)
                        val high = fields.getOrNull(3)?.toDouble()?.div(divisor) ?: ltp
                        val low = fields.getOrNull(4)?.toDouble()?.div(divisor) ?: ltp
                        val open = fields.getOrNull(5)?.toDouble()?.div(divisor) ?: prevClose

                        if (ltp > 0.0) {
                            applyParsedTick(topicName, ltp, prevClose, open, high, low, feedTime)
                            hasNewTick = true
                        }
                    } else {
                        // Other topics (dp, sf) - skip remainder of scrip safely
                        break
                    }
                }
                85 -> {
                    // Update datafeed
                    if (buffer.remaining() < 3) break
                    val topicId = (buffer.get().toInt() and 0xFF) or ((buffer.get().toInt() and 0xFF) shl 8)
                    val fieldCount = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() < fieldCount * 4) break

                    val fields = LongArray(fieldCount)
                    for (i in 0 until fieldCount) {
                        fields[i] = buffer.int.toLong()
                    }

                    val topicName = topicIdToName[topicId]
                    val divisor = topicIdToDivisor[topicId] ?: 100.0

                    if (topicName != null && topicName.startsWith("if")) {
                        val ltp = fields.getOrNull(0)?.toDouble()?.div(divisor) ?: 0.0
                        val prevClose = fields.getOrNull(1)?.toDouble()?.div(divisor) ?: ltp
                        val feedTime = fields.getOrNull(2) ?: (System.currentTimeMillis() / 1000)
                        val high = fields.getOrNull(3)?.toDouble()?.div(divisor) ?: ltp
                        val low = fields.getOrNull(4)?.toDouble()?.div(divisor) ?: ltp
                        val open = fields.getOrNull(5)?.toDouble()?.div(divisor) ?: prevClose

                        if (ltp > 0.0) {
                            applyParsedTick(topicName, ltp, prevClose, open, high, low, feedTime)
                            hasNewTick = true
                        }
                    }
                }
            }
        }

        if (hasNewTick) {
            publishCurrentSnapshot()
        }
    }

    private fun applyParsedTick(
        topicName: String,
        ltp: Double,
        prevClose: Double,
        open: Double,
        high: Double,
        low: Double,
        feedTimeSec: Long
    ) {
        val absChange = ltp - prevClose
        val pctChange = if (prevClose > 0.0) (absChange / prevClose) * 100.0 else 0.0
        val lastUpdated = if (feedTimeSec < 10_000_000_000L) feedTimeSec * 1000L else feedTimeSec
        val sparkline = listOf(prevClose, open, low, high, ltp)

        if (topicName.contains("NIFTY", ignoreCase = true)) {
            latestNiftyData = MarketIndexData(
                symbol = NIFTY_SYMBOL,
                displayName = "NIFTY 50",
                exchange = "NSE",
                currentValue = ltp,
                previousClose = prevClose,
                openPrice = open,
                dayHigh = high,
                dayLow = low,
                absoluteChange = absChange,
                percentageChange = pctChange,
                lastUpdated = lastUpdated,
                sparklinePoints = sparkline
            )
        } else if (topicName.contains("SENSEX", ignoreCase = true) || topicName.contains("BSESN", ignoreCase = true)) {
            latestSensexData = MarketIndexData(
                symbol = SENSEX_SYMBOL,
                displayName = "SENSEX",
                exchange = "BSE",
                currentValue = ltp,
                previousClose = prevClose,
                openPrice = open,
                dayHigh = high,
                dayLow = low,
                absoluteChange = absChange,
                percentageChange = pctChange,
                lastUpdated = lastUpdated,
                sparklinePoints = sparkline
            )
        }
    }

    private fun publishCurrentSnapshot() {
        val nifty = latestNiftyData ?: return
        val sensex = latestSensexData ?: return

        val snapshot = MarketSnapshot(
            nifty50 = nifty,
            sensex = sensex,
            marketStatus = MarketStatus.current(),
            lastSyncTimestamp = System.currentTimeMillis(),
            isCached = false,
            sourceProvider = "Fyers Live WebSocket"
        )

        _liveSnapshotFlow.value = snapshot
        _connectionState.value = _connectionState.value.copy(
            status = FyersWebSocketStatus.STREAMING,
            lastTickTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Builds binary authentication packet for Fyers HSM socket.
     */
    fun buildAuthMessage(hsmKey: String, source: String = "NiftySense-Android", mode: String = "P"): ByteArray {
        val bufferSize = 18 + hsmKey.length + source.length
        val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort((bufferSize - 2).toShort())
        buffer.put(1.toByte()) // req_type = 1
        buffer.put(4.toByte()) // field_count = 4

        // Field 1: AuthToken
        buffer.put(1.toByte())
        buffer.putShort(hsmKey.length.toShort())
        buffer.put(hsmKey.toByteArray(Charsets.UTF_8))

        // Field 2: mode ('P')
        buffer.put(2.toByte())
        buffer.putShort(1.toShort())
        buffer.put(mode.toByteArray(Charsets.UTF_8))

        // Field 3: 1
        buffer.put(3.toByte())
        buffer.putShort(1.toShort())
        buffer.put(1.toByte())

        // Field 4: source
        buffer.put(4.toByte())
        buffer.putShort(source.length.toShort())
        buffer.put(source.toByteArray(Charsets.UTF_8))

        return buffer.array()
    }

    /**
     * Builds binary subscription message for specified HSM symbols.
     */
    fun buildSubscriptionMessage(symbols: List<String>, channelNum: Int = 11, source: String = "NiftySense-Android"): ByteArray {
        val scripsBos = ByteArrayOutputStream()
        val scripsDos = DataOutputStream(scripsBos)
        scripsDos.writeShort(symbols.size)
        for (sym in symbols) {
            val b = sym.toByteArray(Charsets.US_ASCII)
            scripsDos.writeByte(b.size)
            scripsDos.write(b)
        }
        val scripsData = scripsBos.toByteArray()

        val dataLen = 18 + scripsData.size + source.length
        val buffer = ByteBuffer.allocate(32 + scripsData.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(dataLen.toShort())
        buffer.put(4.toByte()) // req_type = 4
        buffer.put(2.toByte()) // field_count = 2

        // Field 1: scrips
        buffer.put(1.toByte())
        buffer.putShort(scripsData.size.toShort())
        buffer.put(scripsData)

        // Field 2: channel
        buffer.put(2.toByte())
        buffer.putShort(1.toShort())
        buffer.put(channelNum.toByte())

        return buffer.array().copyOf(buffer.position())
    }

    /**
     * Parses authentication response byte buffer.
     */
    fun parseAuthResponse(data: ByteArray): Boolean {
        if (data.size < 7) return false
        return try {
            val offset = 4 + 1 // skip header + field ID
            val fieldLen = ByteBuffer.wrap(data, offset, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val status = String(data, offset + 2, fieldLen, Charsets.UTF_8)
            status == "K"
        } catch (_: Exception) {
            false
        }
    }

    private fun startPingKeepalive(webSocket: WebSocket) {
        pingJob?.cancel()
        pingJob = scope.launch {
            // Heartbeat frame: bytes [0, 1, 11] as defined by Fyers protocol
            val pingFrame = ByteString.of(0.toByte(), 1.toByte(), 11.toByte())
            while (isActive) {
                delay(10_000L)
                try {
                    webSocket.send(pingFrame)
                } catch (e: Exception) {
                    Log.w(TAG, "Ping heartbeat failed: ${e.message}")
                    break
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (isExplicitlyDisconnected || currentHsmKey.isNullOrBlank()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000L)
            Log.i(TAG, "Attempting WebSocket reconnection...")
            startSocketConnection()
        }
    }

    fun getLatestSnapshotIfFresh(maxAgeMs: Long = 60_000L): MarketSnapshot? {
        val snapshot = _liveSnapshotFlow.value ?: return null
        val age = System.currentTimeMillis() - snapshot.lastSyncTimestamp
        return if (age <= maxAgeMs) snapshot else null
    }

    private fun disconnectInternal(keepHsmKey: Boolean) {
        pingJob?.cancel()
        pingJob = null
        try {
            activeWebSocket?.close(1000, "Client disconnect")
        } catch (_: Exception) {}
        activeWebSocket = null
        if (!keepHsmKey) {
            currentHsmKey = null
        }
    }

    fun disconnect() {
        isExplicitlyDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectInternal(keepHsmKey = false)
        _connectionState.value = FyersWebSocketState(
            status = FyersWebSocketStatus.DISCONNECTED,
            message = "Disconnected"
        )
    }
}
