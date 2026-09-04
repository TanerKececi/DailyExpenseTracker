package com.example.dailyexpensetracker.ui.reports

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns the month all three Reports tabs share. Child fragments obtain this same instance with
 * `viewModels(ownerProducer = { requireParentFragment() })`, so moving the month moves every tab.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor() : ViewModel() {

    private val _selectedMonthStart = MutableStateFlow(MonthRange.monthStart(System.currentTimeMillis()))
    val selectedMonthStart: StateFlow<Long> = _selectedMonthStart.asStateFlow()

    /**
     * Which tab is showing. Held here, not in the view, because this ViewModel outlives the
     * fragment's view — a recreated TabLayout defaults to index 0 while the restored child
     * fragment is whatever was showing before, and the two silently desync. Bills hit exactly
     * this and it broke a whole screen.
     */
    var selectedTab: Int = 0
        private set

    fun selectTab(position: Int) {
        selectedTab = position
    }

    fun previousMonth() {
        _selectedMonthStart.value = MonthRange.previousMonthStart(_selectedMonthStart.value)
    }

    fun nextMonth() {
        _selectedMonthStart.value = MonthRange.nextMonthStart(_selectedMonthStart.value)
    }
}
