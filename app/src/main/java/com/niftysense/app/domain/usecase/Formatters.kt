package com.niftysense.app.domain.usecase

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Formatters {

    private val indianLocale = Locale("en", "IN")
    private val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private val indexNumberFormat = DecimalFormat("##,##,##0.00", DecimalFormatSymbols(indianLocale))

    fun formatIndexValue(value: Double): String {
        return indexNumberFormat.format(value)
    }

    fun formatAbsoluteChange(change: Double): String {
        val sign = if (change > 0.0) "+" else if (change < 0.0) "-" else ""
        val absVal = kotlin.math.abs(change)
        return "$sign${indexNumberFormat.format(absVal)}"
    }

    fun formatPercentageChange(percentage: Double): String {
        val sign = if (percentage > 0.0) "+" else if (percentage < 0.0) "-" else ""
        val absVal = kotlin.math.abs(percentage)
        val formatted = DecimalFormat("0.00").format(absVal)
        return "$sign$formatted%"
    }

    fun formatCombinedChange(absChange: Double, pctChange: Double): String {
        val formattedAbs = formatAbsoluteChange(absChange)
        val formattedPct = formatPercentageChange(pctChange)
        return "$formattedAbs ($formattedPct)"
    }

    fun formatTimeOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", indianLocale).apply {
            timeZone = istTimeZone
        }
        return sdf.format(Date(timestamp)) + " IST"
    }

    fun formatWidgetTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMins = diffMs / (60 * 1000)

        return when {
            diffMins < 1 -> "Just now"
            diffMins < 60 -> "${diffMins}m ago"
            else -> {
                val sdf = SimpleDateFormat("hh:mm a", indianLocale).apply {
                    timeZone = istTimeZone
                }
                sdf.format(Date(timestamp))
            }
        }
    }

    fun formatFullDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", indianLocale).apply {
            timeZone = istTimeZone
        }
        return sdf.format(Date(timestamp)) + " IST"
    }
}
