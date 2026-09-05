package com.example.dailyexpensetracker.ui.settings

import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

class FakeSettingsStore(
    override var currencySymbol: String = CurrencyFormatter.DEFAULT_SYMBOL,
    override var weekStart: Int = SettingsStore.DEFAULT_WEEK_START,
    override var nightMode: Int = SettingsStore.DEFAULT_NIGHT_MODE
) : SettingsStore
