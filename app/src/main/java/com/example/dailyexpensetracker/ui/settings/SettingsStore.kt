package com.example.dailyexpensetracker.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's persisted preferences.
 *
 * Deliberately not reactive. Every fragment collects its state inside
 * `repeatOnLifecycle(STARTED)`, so returning to a screen re-subscribes, the StateFlow re-emits and
 * the binding block re-runs — which re-formats every amount and rebuilds the calendar grid. A
 * synchronous read at bind time therefore reaches every screen on its next visit. Do not convert
 * this to a Flow; the lifecycle already does that work.
 */
interface SettingsStore {
    var currencySymbol: String
    var weekStart: Int

    companion object {
        val SUPPORTED_SYMBOLS = listOf("$", "€", "£", "₺", "¥")
        const val DEFAULT_WEEK_START = Calendar.SUNDAY
    }
}

@Singleton
class SharedPreferencesSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : SettingsStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY_SYMBOL, CurrencyFormatter.DEFAULT_SYMBOL)
            ?: CurrencyFormatter.DEFAULT_SYMBOL
        set(value) = prefs.edit().putString(KEY_CURRENCY_SYMBOL, value).apply()

    override var weekStart: Int
        get() = prefs.getInt(KEY_WEEK_START, SettingsStore.DEFAULT_WEEK_START)
        set(value) = prefs.edit().putInt(KEY_WEEK_START, value).apply()

    private companion object {
        const val KEY_CURRENCY_SYMBOL = "currency_symbol"
        const val KEY_WEEK_START = "week_start"
    }
}
