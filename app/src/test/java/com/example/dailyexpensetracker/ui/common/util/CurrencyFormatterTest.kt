package com.example.dailyexpensetracker.ui.common.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {

    /**
     * `symbol` is global mutable state shared by every test in this JVM. Leaving it changed would
     * break any later suite that asserts on a formatted string.
     */
    @After
    fun resetSymbol() {
        CurrencyFormatter.symbol = CurrencyFormatter.DEFAULT_SYMBOL
    }

    @Test
    fun `defaults to a dollar sign`() {
        assertEquals("$1,234.56", CurrencyFormatter.format(1234.56))
    }

    @Test
    fun `uses the configured symbol`() {
        CurrencyFormatter.symbol = "€"

        assertEquals("€1,234.56", CurrencyFormatter.format(1234.56))
    }

    @Test
    fun `keeps grouping and two decimal places for every symbol`() {
        CurrencyFormatter.symbol = "₺"

        assertEquals("₺1,000,000.00", CurrencyFormatter.format(1_000_000.0))
        assertEquals("₺0.50", CurrencyFormatter.format(0.5))
    }
}
