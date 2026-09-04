package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeCategoryRepository : CategoryRepository {
    private val state = MutableStateFlow<List<Category>>(emptyList())

    fun setCategories(categories: List<Category>) {
        state.value = categories
    }

    override fun getAll(): Flow<List<Category>> = state.asStateFlow()

    override fun getByType(isExpense: Boolean): Flow<List<Category>> =
        state.map { list -> list.filter { it.isExpense == isExpense } }

    override suspend fun updateBudget(id: Long, limit: Double?) {
        state.value = state.value.map { if (it.id == id) it.copy(budgetLimit = limit) else it }
    }
}
