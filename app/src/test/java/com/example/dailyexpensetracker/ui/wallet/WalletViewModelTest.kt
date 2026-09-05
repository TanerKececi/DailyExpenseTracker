package com.example.dailyexpensetracker.ui.wallet

import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.usecase.FakeCardRepository
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCardsUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCard
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WalletViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()
    private val cardRepository = FakeCardRepository()

    private fun viewModel() = WalletViewModel(
        GetBudgetSummaryUseCase(transactionRepository, categoryRepository),
        GetCardsUseCase(cardRepository),
        GetTransactionsForPeriodUseCase(transactionRepository),
        GetCategoriesUseCase(categoryRepository)
    )

    @Test
    fun `remaining is the budget less the spend`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, budgetLimit = 200.0)))
        transactionRepository.setTransactions(listOf(testTransaction(1, amount = 75.0, date = now)))
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals(200.0, state.totalBudget, 0.001)
        assertEquals(75.0, state.spent, 0.001)
        assertEquals(125.0, state.remaining, 0.001)
    }

    /** `onTrack` is `spent <= budget`, so spending the budget exactly still counts as on track. */
    @Test
    fun `spending exactly the budget is still on track`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, budgetLimit = 100.0)))
        transactionRepository.setTransactions(listOf(testTransaction(1, amount = 100.0, date = now)))
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        assertTrue(viewModel.uiState.value.onTrack)
    }

    @Test
    fun `going a penny over the budget is off track`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, budgetLimit = 100.0)))
        transactionRepository.setTransactions(listOf(testTransaction(1, amount = 100.01, date = now)))
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        assertFalse(viewModel.uiState.value.onTrack)
    }

    @Test
    fun `recent transactions are the ten newest, newest first`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1)))
        transactionRepository.setTransactions(
            (1L..12L).map { testTransaction(it, date = it * 1_000_000L) }
        )
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val recent = viewModel.uiState.value.recentTransactions
        assertEquals(10, recent.size)
        assertEquals((12L downTo 3L).toList(), recent.map { it.transaction.id })
    }

    @Test
    fun `each recent transaction carries its category, or null when it has none`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Groceries")))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, date = now),
                testTransaction(2, categoryId = 99, date = now)
            )
        )
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val recent = viewModel.uiState.value.recentTransactions
        assertEquals("Groceries", recent.first { it.transaction.id == 1L }.category?.name)
        assertNull(recent.first { it.transaction.id == 2L }.category)
    }

    @Test
    fun `cards are passed straight through`() = runTest {
        cardRepository.setCards(listOf(testCard(1, name = "Everyday"), testCard(2)))
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        assertEquals(listOf("Everyday", "Card 2"), viewModel.uiState.value.cards.map { it.cardName })
    }
}
