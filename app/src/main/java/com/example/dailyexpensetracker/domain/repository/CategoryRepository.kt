package com.example.dailyexpensetracker.domain.repository

import com.example.dailyexpensetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAll(): Flow<List<Category>>
    fun getByType(isExpense: Boolean): Flow<List<Category>>
    suspend fun updateBudget(id: Long, limit: Double?)
}
