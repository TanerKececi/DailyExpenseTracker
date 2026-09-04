package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCategoryBudgetUseCaseTest {

    private fun category(id: Long, budgetLimit: Double?) = Category(
        id = id,
        name = "Category $id",
        iconName = "grocery",
        colorHex = "#5A67F2",
        budgetLimit = budgetLimit,
        isExpense = true
    )

    @Test
    fun `sets a budget limit on the matching category`() = runBlocking {
        val repo = FakeCategoryRepository()
        repo.setCategories(listOf(category(1, null), category(2, 100.0)))
        val useCase = UpdateCategoryBudgetUseCase(repo)

        useCase(1L, 250.0)

        assertEquals(250.0, repo.getAll().first().first { it.id == 1L }.budgetLimit)
    }

    @Test
    fun `clearing a budget writes null`() = runBlocking {
        val repo = FakeCategoryRepository()
        repo.setCategories(listOf(category(1, 400.0)))
        val useCase = UpdateCategoryBudgetUseCase(repo)

        useCase(1L, null)

        assertNull(repo.getAll().first().first { it.id == 1L }.budgetLimit)
    }

    @Test
    fun `leaves other categories untouched`() = runBlocking {
        val repo = FakeCategoryRepository()
        repo.setCategories(listOf(category(1, 100.0), category(2, 200.0)))
        val useCase = UpdateCategoryBudgetUseCase(repo)

        useCase(1L, 999.0)

        assertEquals(200.0, repo.getAll().first().first { it.id == 2L }.budgetLimit)
    }
}
