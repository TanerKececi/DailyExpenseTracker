package com.example.dailyexpensetracker.ui.billdetail

import androidx.lifecycle.SavedStateHandle
import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.usecase.DeleteTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.FakeCardRepository
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetCardsUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionByIdUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateTransactionStatusUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCard
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BillDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()
    private val cardRepository = FakeCardRepository()

    @Before
    fun seed() {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Utilities")))
        cardRepository.setCards(listOf(testCard(7, name = "Everyday")))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(
                    5, title = "Electricity", categoryId = 1, cardId = 7,
                    isScheduled = true, status = TransactionStatus.UPCOMING
                )
            )
        )
    }

    private fun viewModel(transactionId: Long = 5L) = BillDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("transactionId" to transactionId)),
        getTransactionById = GetTransactionByIdUseCase(transactionRepository),
        getCategories = GetCategoriesUseCase(categoryRepository),
        getCards = GetCardsUseCase(cardRepository),
        updateTransactionStatus = UpdateTransactionStatusUseCase(transactionRepository),
        deleteTransaction = DeleteTransactionUseCase(transactionRepository)
    )

    @Test
    fun `the bill is joined with its category and card`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals("Electricity", state.transaction?.title)
        assertEquals("Utilities", state.category?.name)
        assertEquals("Everyday", state.card?.cardName)
    }

    @Test
    fun `a bill with no card resolves to a null card rather than a stale one`() = runTest {
        transactionRepository.setTransactions(listOf(testTransaction(5, categoryId = 1, cardId = null)))
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        assertNull(viewModel.uiState.value.card)
    }

    @Test
    fun `an unknown id yields an empty state`() = runTest {
        val viewModel = viewModel(transactionId = 999L)
        subscribe(viewModel.uiState)

        assertEquals(BillDetailUiState(), viewModel.uiState.value)
    }

    @Test
    fun `approve marks the bill paid`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.approve()

        assertEquals(TransactionStatus.PAID, transactionRepository.getById(5L).first()?.status)
    }

    @Test
    fun `decline deletes the bill`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.decline()

        assertNull(transactionRepository.getById(5L).first())
    }
}
