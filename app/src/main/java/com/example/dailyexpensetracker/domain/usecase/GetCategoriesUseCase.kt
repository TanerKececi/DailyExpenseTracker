package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    operator fun invoke(isExpense: Boolean? = null): Flow<List<Category>> =
        if (isExpense == null) repository.getAll() else repository.getByType(isExpense)
}
