package com.example.dailyexpensetracker.ui.common.util

import java.util.Locale

object CurrencyFormatter {
    fun format(amount: Double): String = String.format(Locale.US, "$%,.2f", amount)
}
