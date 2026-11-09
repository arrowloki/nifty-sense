package com.niftysense.app.data.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.TimeZone

@Serializable
enum class MarketStatus(val label: String) {
    OPEN("Market Open"),
    CLOSED("Market Closed"),
    PRE_OPEN("Pre-Market");

    companion object {
        private val IST_TIMEZONE = TimeZone.getTimeZone("Asia/Kolkata")

        fun current(currentTimeMillis: Long = System.currentTimeMillis()): MarketStatus {
            val calendar = Calendar.getInstance(IST_TIMEZONE).apply {
                timeInMillis = currentTimeMillis
            }

            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return CLOSED
            }

            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val totalMinutes = hour * 60 + minute

            // 09:00 AM IST = 540 mins, 09:15 AM = 555 mins, 15:30 (03:30 PM) = 930 mins
            return when {
                totalMinutes in 540 until 555 -> PRE_OPEN
                totalMinutes in 555 until 930 -> OPEN
                else -> CLOSED
            }
        }
    }
}
