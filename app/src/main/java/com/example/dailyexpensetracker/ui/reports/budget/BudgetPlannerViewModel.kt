package com.example.dailyexpensetracker.ui.reports.budget

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateCategoryBudgetUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class BudgetPlannerViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase,
    private val getTransactionsForPeriod: GetTransactionsForPeriodUseCase,
    private val updateCategoryBudget: UpdateCategoryBudgetUseCase
) : ViewModel() {

    /**
     * Every expense category with its spend for the month — including categories with zero spend,
     * which is why this doesn't reuse BudgetSummary.categoryBreakdown (that filters to spent > 0).
     * You cannot set a limit on a category the planner refuses to list.
     */
    fun plansFor(monthStart: Long): Flow<List<CategorySpend>> = combine(
        getCategories(isExpense = true),
        getTransactionsForPeriod(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
    ) { categories, transactions ->
        categories.map { category ->
            val spent = transactions
                .filter { it.categoryId == category.id && it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            CategorySpend(category, spent)
        }
    }

    suspend fun setBudget(categoryId: Long, limit: Double?) = updateCategoryBudget(categoryId, limit)
}
