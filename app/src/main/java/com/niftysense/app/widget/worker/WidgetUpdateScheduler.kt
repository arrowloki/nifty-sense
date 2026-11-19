package com.niftysense.app.widget.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.niftysense.app.widget.receiver.WidgetRefreshReceiver

object WidgetUpdateScheduler {

    private const val ALARM_REQUEST_CODE = 2001

    fun scheduleRepeatingAlarm(context: Context, intervalMinutes: Int = 15) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMs = (intervalMinutes * 60 * 1000L).coerceAtLeast(60_000L)
        val triggerAtMs = SystemClock.elapsedRealtime() + intervalMs

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                triggerAtMs,
                intervalMs,
                pendingIntent
            )
        } catch (_: Exception) {
        }
    }

    fun cancelRepeatingAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            } catch (_: Exception) {
            }
        }
    }
}
