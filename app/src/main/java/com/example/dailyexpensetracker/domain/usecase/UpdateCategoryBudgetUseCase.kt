package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import javax.inject.Inject

class UpdateCategoryBudgetUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    /** Pass a null [limit] to clear the budget, returning the category to "no budget set". */
    suspend operator fun invoke(id: Long, limit: Double?) = repository.updateBudget(id, limit)
}
