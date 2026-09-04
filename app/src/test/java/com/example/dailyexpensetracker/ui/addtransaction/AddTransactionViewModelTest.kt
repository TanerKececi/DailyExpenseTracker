package com.example.dailyexpensetracker.ui.addtransaction

import androidx.lifecycle.SavedStateHandle
import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.AddTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.DeleteTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionByIdUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateTransactionUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The sheet doubles as the transaction editor, so most of these guard the edit path: it has to
 * carry through fields it never shows (`cardId`, `status`), or editing a bill quietly demotes it
 * into an ordinary paid transaction.
 */
class AddTransactionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    @Before
    fun seedCategories() {
        categoryRepository.setCategories(
            listOf(
                testCategory(1, name = "Groceries", isExpense = true),
                testCategory(2, name = "Salary", isExpense = false)
            )
        )
    }

    private fun viewModel(transactionId: Long? = null) = AddTransactionViewModel(
        savedStateHandle = SavedStateHandle(
            if (transactionId == null) emptyMap() else mapOf("transactionId" to transactionId)
        ),
        getCategories = GetCategoriesUseCase(categoryRepository),
        addTransaction = AddTransactionUseCase(transactionRepository),
        updateTransaction = UpdateTransactionUseCase(transactionRepository),
        deleteTransaction = DeleteTransactionUseCase(transactionRepository),
        getTransactionById = GetTransactionByIdUseCase(transactionRepository)
    )

    /** `events` is a replayless SharedFlow, so a collector has to be in place before the action. */
    private fun TestScope.recordEvents(viewModel: AddTransactionViewModel): List<AddTransactionEvent> {
        val events = mutableListOf<AddTransactionEvent>()
        backgroundScope.launch(Dispatchers.Main) { viewModel.events.collect { events += it } }
        return events
    }

    private suspend fun stored(id: Long) = transactionRepository.getById(id).first()

    @Test
    fun `no transaction id means add mode`() = runTest {
        assertFalse(viewModel().isEditing)
        assertTrue(viewModel(5L).isEditing)
    }

    @Test
    fun `categories follow the selected type`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.categories)

        assertEquals(listOf("Groceries"), viewModel.categories.value.map { it.name })

        viewModel.setType(TransactionType.INCOME)

        assertEquals(listOf("Salary"), viewModel.categories.value.map { it.name })
    }

    @Test
    fun `changing type clears the selected category`() = runTest {
        val viewModel = viewModel()
        viewModel.setCategory(1L)

        viewModel.setType(TransactionType.INCOME)

        assertNull(viewModel.selectedCategoryId.value)
    }

    @Test
    fun `save rejects a blank title, a bad amount and a missing category`() = runTest {
        val viewModel = viewModel()
        val events = recordEvents(viewModel)

        viewModel.save("  ", "10", false, "", "")
        viewModel.setCategory(1L)
        viewModel.save("Coffee", "0", false, "", "")
        viewModel.save("Coffee", "not a number", false, "", "")
        viewModel.setCategory(null)
        viewModel.save("Coffee", "10", false, "", "")

        assertEquals(
            listOf(
                R.string.add_transaction_error_title,
                R.string.add_transaction_error_amount,
                R.string.add_transaction_error_amount,
                R.string.add_transaction_error_category
            ),
            events.map { (it as AddTransactionEvent.Error).messageRes }
        )
        assertTrue(transactionRepository.getAll().first().isEmpty())
    }

    @Test
    fun `saving an unscheduled new transaction stores it as paid`() = runTest {
        val viewModel = viewModel()
        val events = recordEvents(viewModel)
        viewModel.setCategory(1L)

        viewModel.save("  Coffee  ", "12.50", false, "", "")

        val saved = transactionRepository.getAll().first().single()
        assertEquals("Coffee", saved.title)
        assertEquals(12.50, saved.amount, 0.001)
        assertEquals(TransactionStatus.PAID, saved.status)
        assertFalse(saved.isScheduled)
        assertEquals(listOf(AddTransactionEvent.Saved), events)
    }

    @Test
    fun `saving a scheduled new transaction stores it as upcoming with its payee`() = runTest {
        val viewModel = viewModel()
        viewModel.setCategory(1L)

        viewModel.save("Rent", "800", true, " Acme Lettings ", " Landlord ")

        val saved = transactionRepository.getAll().first().single()
        assertTrue(saved.isScheduled)
        assertEquals(TransactionStatus.UPCOMING, saved.status)
        assertEquals("Acme Lettings", saved.payeeName)
        assertEquals("Landlord", saved.payeeRole)
    }

    @Test
    fun `a blank payee is stored as null rather than an empty string`() = runTest {
        val viewModel = viewModel()
        viewModel.setCategory(1L)

        viewModel.save("Rent", "800", true, "   ", "")

        val saved = transactionRepository.getAll().first().single()
        assertNull(saved.payeeName)
        assertNull(saved.payeeRole)
    }

    @Test
    fun `edit mode preloads the transaction being edited`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(5, date = 1_700_000_000_000, categoryId = 2, type = TransactionType.INCOME))
        )

        val viewModel = viewModel(5L)

        assertNotNull(viewModel.original.value)
        assertEquals(TransactionType.INCOME, viewModel.type.value)
        assertEquals(1_700_000_000_000, viewModel.dateMillis.value)
        assertEquals(2L, viewModel.selectedCategoryId.value)
    }

    @Test
    fun `saving an edit updates the existing row instead of adding one`() = runTest {
        transactionRepository.setTransactions(listOf(testTransaction(5, title = "Old")))
        val viewModel = viewModel(5L)

        viewModel.save("New", "99", false, "", "")

        val all = transactionRepository.getAll().first()
        assertEquals(1, all.size)
        assertEquals(5L, all.single().id)
        assertEquals("New", all.single().title)
    }

    /**
     * `cardId` has no field in the sheet. If save built the Transaction from form state alone it
     * would write null here and silently detach the transaction from its card.
     */
    @Test
    fun `saving an edit carries the card through`() = runTest {
        transactionRepository.setTransactions(listOf(testTransaction(5, cardId = 7L)))
        val viewModel = viewModel(5L)

        viewModel.save("Groceries", "20", false, "", "")

        assertEquals(7L, stored(5L)?.cardId)
    }

    /**
     * The trap this exists to catch: an approved bill is `isScheduled = true, status = PAID`. Editing
     * it with the switch still on must not reset it to UPCOMING and resurrect it in the Bills tabs.
     */
    @Test
    fun `editing an approved bill keeps it paid`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(5, isScheduled = true, status = TransactionStatus.PAID))
        )
        val viewModel = viewModel(5L)

        viewModel.save("Rent", "800", true, "", "")

        assertEquals(TransactionStatus.PAID, stored(5L)?.status)
        assertTrue(stored(5L)!!.isScheduled)
    }

    @Test
    fun `turning the schedule switch off marks the transaction paid`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(5, isScheduled = true, status = TransactionStatus.UPCOMING))
        )
        val viewModel = viewModel(5L)

        viewModel.save("Rent", "800", false, "", "")

        assertEquals(TransactionStatus.PAID, stored(5L)?.status)
        assertFalse(stored(5L)!!.isScheduled)
    }

    @Test
    fun `scheduling a previously unscheduled transaction makes it upcoming`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(5, isScheduled = false, status = TransactionStatus.PAID))
        )
        val viewModel = viewModel(5L)

        viewModel.save("Rent", "800", true, "", "")

        assertEquals(TransactionStatus.UPCOMING, stored(5L)?.status)
    }

    @Test
    fun `delete removes the edited transaction and reports back`() = runTest {
        transactionRepository.setTransactions(listOf(testTransaction(5)))
        val viewModel = viewModel(5L)
        val events = recordEvents(viewModel)

        viewModel.delete()

        assertNull(stored(5L))
        assertEquals(listOf(AddTransactionEvent.Saved), events)
    }

    @Test
    fun `delete does nothing in add mode`() = runTest {
        transactionRepository.setTransactions(listOf(testTransaction(5)))
        val viewModel = viewModel()

        viewModel.delete()

        assertEquals(1, transactionRepository.getAll().first().size)
    }
}
