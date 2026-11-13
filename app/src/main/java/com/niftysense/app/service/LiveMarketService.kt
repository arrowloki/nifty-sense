package com.niftysense.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.niftysense.app.MainActivity
import com.niftysense.app.NiftySenseApp
import com.niftysense.app.R
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.domain.usecase.Formatters
import com.niftysense.app.widget.provider.NiftySenseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class LiveMarketService : Service() {

    companion object {
        const val CHANNEL_ID = "niftysense_live_ticker"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.niftysense.app.ACTION_START_LIVE_STREAM"
        const val ACTION_STOP = "com.niftysense.app.ACTION_STOP_LIVE_STREAM"
        const val ACTION_REFRESH_NOW = "com.niftysense.app.ACTION_LIVE_REFRESH_NOW"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, LiveMarketService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveMarketService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
                return START_NOT_STICKY
            }
            ACTION_REFRESH_NOW -> {
                serviceScope.launch {
                    val app = applicationContext as? NiftySenseApp ?: return@launch
                    val snapshot = app.container.marketRepository.refresh().getOrNull()
                    updateNotification(snapshot)
                }
            }
            else -> {
                isServiceRunning = true
                val initialNotification = buildNotification(null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }
                startTickerLoop()
            }
        }
        return START_STICKY
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            val app = applicationContext as? NiftySenseApp ?: return@launch
            val repository = app.container.marketRepository
            val preferences = app.container.preferencesDataStore

            while (isActive) {
                try {
                    // Check market hours: if completely outside trading hours on weekend, slow down to 60s
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val isWeekend = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY
                    val isLateNight = hour < 8 || hour >= 18

                    if (isWeekend || isLateNight) {
                        // Market closed, maintain low-frequency 60s heartbeat
                        delay(60_000L)
                        continue
                    }

                    // Active trading hours: fetch live market data
                    val result = repository.refresh()
                    val snapshot = result.getOrNull() ?: repository.getCachedOrFreshSnapshot()

                    // Update notification and broadcast to all widgets
                    updateNotification(snapshot)
                    if (snapshot != null) {
                        NiftySenseWidgetProvider.updateAllWidgets(applicationContext, snapshot)
                    }

                    // Interval from preferences (default 15 seconds, compliant with Fyers free limit)
                    val intervalSeconds = preferences.liveStreamIntervalSecondsFlow.first().coerceIn(10, 60)
                    delay(intervalSeconds * 1000L)
                } catch (_: Exception) {
                    delay(15_000L)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NiftySense Live Stream Ticker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live NIFTY 50 and SENSEX prices on the home screen"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(snapshot: MarketSnapshot?): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveMarketService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val refreshIntent = Intent(this, LiveMarketService::class.java).apply { action = ACTION_REFRESH_NOW }
        val pendingRefresh = PendingIntent.getService(
            this,
            2,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText: String
        val titleText: String

        if (snapshot != null) {
            val nifty = snapshot.nifty50
            val sensex = snapshot.sensex
            val niftyStr = "NIFTY ${Formatters.formatIndexValue(nifty.currentValue)} (${Formatters.formatPercentageChange(nifty.percentageChange)})"
            val sensexStr = "SENSEX ${Formatters.formatIndexValue(sensex.currentValue)} (${Formatters.formatPercentageChange(sensex.percentageChange)})"
            contentText = "$niftyStr  •  $sensexStr"
            titleText = if (snapshot.marketStatus == MarketStatus.OPEN) "NiftySense • LIVE STREAM" else "NiftySense • MARKET CLOSED"
        } else {
            titleText = "NiftySense • Connecting Live Stream"
            contentText = "Fetching live NIFTY & SENSEX ticks…"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trending_up)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_refresh, "Refresh", pendingRefresh)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(snapshot: MarketSnapshot?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        tickerJob?.cancel()
        serviceScope.cancel()
    }
}
