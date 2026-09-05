package com.example.dailyexpensetracker.ui.categories

import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CategoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val categoryRepository = FakeCategoryRepository()

    @Before
    fun seed() {
        categoryRepository.setCategories(
            listOf(
                testCategory(1, name = "Groceries", isExpense = true),
                testCategory(2, name = "Rent", isExpense = true),
                testCategory(3, name = "Salary", isExpense = false)
            )
        )
    }

    private fun viewModel() = CategoriesViewModel(GetCategoriesUseCase(categoryRepository))

    @Test
    fun `defaults to expense categories`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.categories)

        assertTrue(viewModel.isExpense.value)
        assertEquals(listOf("Groceries", "Rent"), viewModel.categories.value.map { it.name })
    }

    @Test
    fun `switching to income reloads the list`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.categories)

        viewModel.setExpense(false)

        assertFalse(viewModel.isExpense.value)
        assertEquals(listOf("Salary"), viewModel.categories.value.map { it.name })
    }

    @Test
    fun `switching back restores the expense list`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.categories)

        viewModel.setExpense(false)
        viewModel.setExpense(true)

        assertEquals(listOf("Groceries", "Rent"), viewModel.categories.value.map { it.name })
    }
}
