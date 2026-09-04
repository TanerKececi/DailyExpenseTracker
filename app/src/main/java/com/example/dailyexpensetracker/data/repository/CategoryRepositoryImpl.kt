package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    budgetLimit = budgetLimit,
    isExpense = isExpense
)

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {
    override fun getAll(): Flow<List<Category>> = dao.getAll().map { list -> list.map { it.toDomain() } }
    override fun getByType(isExpense: Boolean): Flow<List<Category>> =
        dao.getByType(isExpense).map { list -> list.map { it.toDomain() } }

    override suspend fun updateBudget(id: Long, limit: Double?) {
        dao.updateBudget(id, limit)
    }
}
