package com.example.dailyexpensetracker.ui.categorydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.Category
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

data class CategoryDetailUiState(
    val category: Category? = null,
    val monthLabel: String = "",
    val total: Double = 0.0,
    val budgetLimit: Double? = null,
    val items: List<TransactionListItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCategories: GetCategoriesUseCase,
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.get<Long>("categoryId") ?: 0L

    private val _monthStart = MutableStateFlow(MonthRange.monthStart(System.currentTimeMillis()))

    // getByDateRange uses SQL BETWEEN, inclusive at both ends, so the upper bound is one
    // millisecond before the next month starts.
    private val monthTransactions = _monthStart.flatMapLatest { monthStart ->
        getTransactionsForPeriod(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
    }

    val uiState: StateFlow<CategoryDetailUiState> = combine(
        _monthStart, monthTransactions, getCategories()
    ) { monthStart, transactions, categories ->
        // Filtered here rather than in SQL so every query stays bounded by date range, which is
        // the rule this project holds to. A month is a few dozen rows.
        val category = categories.find { it.id == categoryId }
        val mine = transactions.filter { it.categoryId == categoryId }
        CategoryDetailUiState(
            category = category,
            monthLabel = MonthRange.label(monthStart),
            total = mine.sumOf { it.amount },
            budgetLimit = category?.budgetLimit,
            items = mine.map { TransactionListItem(it, category) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDetailUiState())

    fun previousMonth() {
        _monthStart.value = MonthRange.previousMonthStart(_monthStart.value)
    }

    fun nextMonth() {
        _monthStart.value = MonthRange.nextMonthStart(_monthStart.value)
    }
}
