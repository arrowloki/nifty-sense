package com.niftysense.app

import com.niftysense.app.domain.usecase.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun testFormatIndexValue() {
        val formattedNifty = Formatters.formatIndexValue(24941.55)
        assertEquals("24,941.55", formattedNifty)

        val formattedSensex = Formatters.formatIndexValue(80777.45)
        assertEquals("80,777.45", formattedSensex)
    }

    @Test
    fun testFormatAbsoluteChange() {
        assertEquals("+125.60", Formatters.formatAbsoluteChange(125.60))
        assertEquals("-245.30", Formatters.formatAbsoluteChange(-245.30))
        assertEquals("0.00", Formatters.formatAbsoluteChange(0.00))
    }

    @Test
    fun testFormatPercentageChange() {
        assertEquals("+0.48%", Formatters.formatPercentageChange(0.48))
        assertEquals("-0.29%", Formatters.formatPercentageChange(-0.29))
        assertEquals("0.00%", Formatters.formatPercentageChange(0.00))
    }

    @Test
    fun testFormatCombinedChange() {
        val resultPositive = Formatters.formatCombinedChange(125.60, 0.48)
        assertEquals("+125.60 (+0.48%)", resultPositive)

        val resultNegative = Formatters.formatCombinedChange(-245.30, -0.29)
        assertEquals("-245.30 (-0.29%)", resultNegative)
    }
}
