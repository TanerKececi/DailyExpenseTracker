package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.BudgetSummary
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetBudgetSummaryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(start: Long, end: Long): Flow<BudgetSummary> =
        combine(
            transactionRepository.getByDateRange(start, end),
            categoryRepository.getAll()
        ) { transactions, categories ->
            val totalEarned = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val totalSpent = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val totalBudget = categories.filter { it.isExpense }.sumOf { it.budgetLimit ?: 0.0 }
            val breakdown = categories
                .filter { it.isExpense }
                .map { category ->
                    val spent = transactions
                        .filter { it.categoryId == category.id && it.type == TransactionType.EXPENSE }
                        .sumOf { it.amount }
                    CategorySpend(category, spent)
                }
                .filter { it.spent > 0.0 }
                .sortedByDescending { it.spent }

            BudgetSummary(
                totalEarned = totalEarned,
                totalSpent = totalSpent,
                totalBudget = totalBudget,
                remaining = totalBudget - totalSpent,
                categoryBreakdown = breakdown
            )
        }
}
