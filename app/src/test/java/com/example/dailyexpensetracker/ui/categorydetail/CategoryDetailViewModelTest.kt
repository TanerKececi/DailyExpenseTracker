package com.example.dailyexpensetracker.ui.categorydetail

import androidx.lifecycle.SavedStateHandle
import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class CategoryDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()
    private val monthStart = MonthRange.monthStart(now)
    private val nextMonthStart = MonthRange.nextMonthStart(monthStart)

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    /** Mid-morning on [day] of the month starting at [monthStartMillis]. */
    private fun dayInMonth(monthStartMillis: Long, day: Int): Long =
        MonthRange.calendarAt(monthStartMillis).apply {
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 10)
        }.timeInMillis

    private fun viewModel(categoryId: Long = 1L) = CategoryDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("categoryId" to categoryId)),
        getCategories = GetCategoriesUseCase(categoryRepository),
        getTransactionsForPeriod = GetTransactionsForPeriodUseCase(transactionRepository)
    )

    @Before
    fun seed() {
        categoryRepository.setCategories(
            listOf(
                testCategory(1, name = "Groceries", budgetLimit = 500.0),
                testCategory(2, name = "Fuel", budgetLimit = 200.0),
                testCategory(3, name = "Salary", isExpense = false, budgetLimit = null)
            )
        )
    }

    @Test
    fun `shows only the requested category's transactions`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 2, amount = 99.0, date = dayInMonth(monthStart, 3)),
                testTransaction(3, categoryId = 1, amount = 20.0, date = dayInMonth(monthStart, 4))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals(listOf(1L, 3L), viewModel.uiState.value.items.map { it.transaction.id })
    }

    @Test
    fun `the total is the sum of that category's transactions for the month`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 2, amount = 99.0, date = dayInMonth(monthStart, 3)),
                testTransaction(3, categoryId = 1, amount = 20.0, date = dayInMonth(monthStart, 4))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals(50.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `transactions outside the month are excluded`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 1, amount = 999.0, date = dayInMonth(nextMonthStart, 1))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.transaction.id })
        assertEquals(30.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `moving to the next month re-queries`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 1, amount = 999.0, date = dayInMonth(nextMonthStart, 1))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        viewModel.nextMonth()

        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.transaction.id })
        assertEquals(MonthRange.label(nextMonthStart), viewModel.uiState.value.monthLabel)
    }

    @Test
    fun `month navigation round-trips`() = runTest {
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        viewModel.previousMonth()
        viewModel.nextMonth()

        assertEquals(MonthRange.label(monthStart), viewModel.uiState.value.monthLabel)
    }

    @Test
    fun `the category's name and budget reach the state`() = runTest {
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals("Groceries", viewModel.uiState.value.category?.name)
        assertEquals(500.0, viewModel.uiState.value.budgetLimit!!, 0.001)
    }

    /** Income categories carry no limit, so the view has to hide the budget rather than show 0. */
    @Test
    fun `a category with no budget limit reports null`() = runTest {
        val viewModel = viewModel(3L)
        subscribe(viewModel.uiState)

        assertEquals("Salary", viewModel.uiState.value.category?.name)
        assertNull(viewModel.uiState.value.budgetLimit)
    }

    @Test
    fun `an unknown category id yields an empty state rather than throwing`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)))
        )
        val viewModel = viewModel(999L)
        subscribe(viewModel.uiState)

        assertNull(viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `each item carries the category so the row can render its icon`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)))
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals("Groceries", viewModel.uiState.value.items.single().category?.name)
    }
}
