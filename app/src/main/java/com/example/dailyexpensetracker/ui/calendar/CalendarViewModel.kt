package com.example.dailyexpensetracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import com.example.dailyexpensetracker.ui.wallet.adapter.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CalendarUiState(
    val monthLabel: String = "",
    val cells: List<DayCell> = emptyList(),
    val selectedDayMillis: Long = 0L,
    val dayItems: List<TransactionListItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase,
    getCategories: GetCategoriesUseCase
) : ViewModel() {

    private val today = MonthRange.dayStart(System.currentTimeMillis())
    private val _monthStart = MutableStateFlow(MonthRange.monthStart(today))
    private val _selectedDay = MutableStateFlow(today)

    // getByDateRange uses SQL BETWEEN, which is inclusive on both ends, so the upper bound is
    // one millisecond before the next month starts.
    private val monthTransactions = _monthStart.flatMapLatest { monthStart ->
        getTransactionsForPeriod(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        _monthStart, monthTransactions, getCategories(), _selectedDay
    ) { monthStart, transactions, categories, selectedDay ->
        val categoryById = categories.associateBy { it.id }
        CalendarUiState(
            monthLabel = MonthRange.label(monthStart),
            cells = CalendarMonth.cellsFor(monthStart, transactions),
            selectedDayMillis = selectedDay,
            dayItems = transactions
                .filter { MonthRange.dayStart(it.date) == selectedDay }
                .map { TransactionListItem(it, categoryById[it.categoryId]) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun selectDay(dayMillis: Long) {
        _selectedDay.value = dayMillis
    }

    fun previousMonth() = moveTo(MonthRange.previousMonthStart(_monthStart.value))

    fun nextMonth() = moveTo(MonthRange.nextMonthStart(_monthStart.value))

    /** Keeps the selected day-of-month across a month change so a day is always selected. */
    private fun moveTo(newMonthStart: Long) {
        _selectedDay.value = MonthRange.sameDayInMonth(newMonthStart, _selectedDay.value)
        _monthStart.value = newMonthStart
    }
}
