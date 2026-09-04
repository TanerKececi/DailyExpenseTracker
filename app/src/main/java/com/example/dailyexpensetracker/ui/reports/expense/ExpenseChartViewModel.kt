package com.example.dailyexpensetracker.ui.reports.expense

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.model.BudgetSummary
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ExpenseChartViewModel @Inject constructor(
    private val getBudgetSummary: GetBudgetSummaryUseCase
) : ViewModel() {

    /** The month is owned by ReportsViewModel, so the fragment passes it in rather than holding it. */
    fun summaryFor(monthStart: Long): Flow<BudgetSummary> =
        getBudgetSummary(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
}
