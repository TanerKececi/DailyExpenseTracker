package com.example.dailyexpensetracker.ui.home

import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The ViewModel derives its own day and month windows from the clock, so fixtures live "now". */
    private val now = System.currentTimeMillis()

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    private fun viewModel() = HomeViewModel(
        GetBudgetSummaryUseCase(transactionRepository, categoryRepository),
        GetTransactionsForPeriodUseCase(transactionRepository)
    )

    @Test
    fun `todayNet is today's income minus today's expense`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1)))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, amount = 100.0, date = now, type = TransactionType.INCOME),
                testTransaction(2, amount = 30.0, date = now, type = TransactionType.EXPENSE)
            )
        )
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals(70.0, state.todayNet, 0.001)
        assertEquals(100.0, state.earned, 0.001)
        assertEquals(30.0, state.spent, 0.001)
    }

    @Test
    fun `an empty repository leaves the default state`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        assertEquals(HomeUiState(), viewModel.uiState.value)
    }

    /**
     * The header list is capped at five while the budget list below it is not — they read the same
     * breakdown, so a single `take(5)` in the wrong place would truncate both.
     */
    @Test
    fun `topSpending keeps the five biggest while monthlyBudget keeps them all`() = runTest {
        categoryRepository.setCategories((1L..6L).map { testCategory(it) })
        transactionRepository.setTransactions(
            (1L..6L).map { testTransaction(it, amount = it * 10.0, date = now, categoryId = it) }
        )
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals(5, state.topSpending.size)
        assertEquals(6, state.monthlyBudget.size)
        assertEquals(listOf(60.0, 50.0, 40.0, 30.0, 20.0), state.topSpending.map { it.spent })
    }
}
