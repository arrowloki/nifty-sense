package com.niftysense.app

import com.niftysense.app.data.model.MarketStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MarketStatusTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    @Test
    fun testMarketOpenOnWeekdayAt1030AmIst() {
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 10) // Tuesday
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }

        val status = MarketStatus.current(cal.timeInMillis)
        assertEquals(MarketStatus.OPEN, status)
    }

    @Test
    fun testMarketClosedOnSunday() {
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 8) // Sunday
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }

        val status = MarketStatus.current(cal.timeInMillis)
        assertEquals(MarketStatus.CLOSED, status)
    }

    @Test
    fun testPreMarketAt905AmIst() {
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 9) // Monday
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 5)
        }

        val status = MarketStatus.current(cal.timeInMillis)
        assertEquals(MarketStatus.PRE_OPEN, status)
    }

    @Test
    fun testMarketClosedAfter330PmIst() {
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 10) // Tuesday
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
        }

        val status = MarketStatus.current(cal.timeInMillis)
        assertEquals(MarketStatus.CLOSED, status)
    }
}
