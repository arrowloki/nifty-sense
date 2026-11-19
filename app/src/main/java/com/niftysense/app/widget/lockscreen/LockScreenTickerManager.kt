package com.niftysense.app.widget.lockscreen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.niftysense.app.MainActivity
import com.niftysense.app.R
import com.niftysense.app.data.model.MarketSnapshot
import com.niftysense.app.data.model.MarketStatus
import com.niftysense.app.domain.usecase.Formatters
import com.niftysense.app.widget.receiver.WidgetRefreshReceiver

object LockScreenTickerManager {

    const val CHANNEL_ID = "market_live_ticker"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lock Screen Live Market Ticker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live NIFTY 50 and SENSEX rates on lock screen"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun updateNotification(context: Context, snapshot: MarketSnapshot?, isEnabled: Boolean = true) {
        if (!isEnabled) {
            cancelNotification(context)
            return
        }

        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Tap notification to open App
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Refresh action directly from Lock Screen notification
        val refreshIntent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trending_up)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(appPendingIntent)
            .addAction(R.drawable.ic_refresh, "↻ Refresh Rates", refreshPendingIntent)

        if (snapshot != null) {
            val nifty = snapshot.nifty50
            val sensex = snapshot.sensex

            val niftyArrow = if (nifty.isPositive) "▲ +" else "▼ "
            val sensexArrow = if (sensex.isPositive) "▲ +" else "▼ "

            val titleText = "NIFTY: ₹${Formatters.formatIndexValue(nifty.currentValue)} ($niftyArrow${Formatters.formatPercentageChange(nifty.percentageChange).removePrefix("+")})"
            val contentText = "SENSEX: ₹${Formatters.formatIndexValue(sensex.currentValue)} ($sensexArrow${Formatters.formatPercentageChange(sensex.percentageChange).removePrefix("+")})"

            val statusText = when (snapshot.marketStatus) {
                MarketStatus.OPEN -> "LIVE (IST)"
                MarketStatus.PRE_OPEN -> "PRE-OPEN"
                MarketStatus.CLOSED -> "CLOSED"
            }

            val bigText = buildString {
                append("NIFTY 50: ₹${Formatters.formatIndexValue(nifty.currentValue)}  $niftyArrow${Formatters.formatPercentageChange(nifty.percentageChange).removePrefix("+")} (${Formatters.formatAbsoluteChange(nifty.absoluteChange)})\n")
                append("SENSEX:  ₹${Formatters.formatIndexValue(sensex.currentValue)}  $sensexArrow${Formatters.formatPercentageChange(sensex.percentageChange).removePrefix("+")} (${Formatters.formatAbsoluteChange(sensex.absoluteChange)})\n")
                append("Day Range: NIFTY H: ${Formatters.formatIndexValue(nifty.dayHigh)}  L: ${Formatters.formatIndexValue(nifty.dayLow)}\n")
                append("Status: $statusText • Updated ${Formatters.formatWidgetTimestamp(snapshot.lastSyncTimestamp)}")
            }

            builder.setContentTitle(titleText)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setSubText(statusText)
        } else {
            builder.setContentTitle("NiftySense • Syncing Live Market…")
                .setContentText("Fetching NIFTY 50 & SENSEX rates…")
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not yet granted
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
