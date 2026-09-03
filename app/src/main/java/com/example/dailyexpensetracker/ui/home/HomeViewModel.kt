package com.example.dailyexpensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val todayNet: Double = 0.0,
    val earned: Double = 0.0,
    val spent: Double = 0.0,
    val topSpending: List<CategorySpend> = emptyList(),
    val monthlyBudget: List<CategorySpend> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getBudgetSummary: GetBudgetSummaryUseCase,
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase
) : ViewModel() {

    private val monthStart = startOfMonth()
    private val monthEnd = endOfMonth()
    private val dayStart = startOfDay()
    private val dayEnd = endOfDay()

    val uiState: StateFlow<HomeUiState> = combine(
        getBudgetSummary(monthStart, monthEnd),
        getTransactionsForPeriod(dayStart, dayEnd)
    ) { summary, todayTransactions ->
        val todayEarned = todayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val todaySpent = todayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        HomeUiState(
            todayNet = todayEarned - todaySpent,
            earned = summary.totalEarned,
            spent = summary.totalSpent,
            topSpending = summary.categoryBreakdown.take(5),
            monthlyBudget = summary.categoryBreakdown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
