package com.example.dailyexpensetracker.ui.settings

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.usecase.ResetDataUseCase
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val currencySymbol: String,
    val weekStart: Int,
    val nightMode: Int
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val resetDataUseCase: ResetDataUseCase
) : ViewModel() {

    // A plain MutableStateFlow rather than stateIn: the source is a synchronous store, so there is
    // no upstream to share and nothing for SharingStarted to start.
    private val _uiState = MutableStateFlow(
        SettingsUiState(settingsStore.currencySymbol, settingsStore.weekStart, settingsStore.nightMode)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setCurrencySymbol(symbol: String) {
        settingsStore.currencySymbol = symbol
        // Pushed straight into the formatter so screens pick it up on their next visit rather than
        // waiting for a restart.
        CurrencyFormatter.symbol = symbol
        _uiState.value = _uiState.value.copy(currencySymbol = symbol)
    }

    fun setWeekStart(weekStart: Int) {
        settingsStore.weekStart = weekStart
        _uiState.value = _uiState.value.copy(weekStart = weekStart)
    }

    /**
     * Persists the choice only. Actually applying it is
     * `AppCompatDelegate.setDefaultNightMode`, which the fragment calls — it recreates the running
     * activity, so it is a view concern, and keeping it out of here leaves this class testable on a
     * plain JVM without stubbing AppCompat.
     */
    fun setNightMode(mode: Int) {
        settingsStore.nightMode = mode
        _uiState.value = _uiState.value.copy(nightMode = mode)
    }

    suspend fun resetData() = resetDataUseCase()
}
