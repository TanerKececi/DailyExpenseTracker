package com.example.dailyexpensetracker.domain.model

data class CategorySpend(
    val category: Category,
    val spent: Double
)

data class BudgetSummary(
    val totalEarned: Double,
    val totalSpent: Double,
    val totalBudget: Double,
    val remaining: Double,
    val categoryBreakdown: List<CategorySpend>
)
