package com.example.dailyexpensetracker

import android.app.Application
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.settings.SettingsStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DailyExpenseTrackerApp : Application() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate() {
        super.onCreate()
        // The formatter is an object read by DI-less adapters, so the stored symbol is pushed into
        // it once here rather than plumbed through every call site.
        CurrencyFormatter.symbol = settingsStore.currencySymbol
    }
}
