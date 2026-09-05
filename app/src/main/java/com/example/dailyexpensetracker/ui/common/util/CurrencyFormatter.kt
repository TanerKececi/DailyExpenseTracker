package com.example.dailyexpensetracker.ui.common.util

import java.util.Locale

object CurrencyFormatter {

    const val DEFAULT_SYMBOL = "$"

    /**
     * ponytail: global mutable display state, in place of injecting a formatter into five adapters
     * that are plain fragment fields with no DI. Written on the main thread at app start and when
     * the setting changes; read on the main thread at bind time. Inject a formatter instead if this
     * is ever read off the main thread.
     */
    @Volatile
    var symbol: String = DEFAULT_SYMBOL

    fun format(amount: Double): String = String.format(Locale.US, "%s%,.2f", symbol, amount)
}
