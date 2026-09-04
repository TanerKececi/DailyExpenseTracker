package com.example.dailyexpensetracker.ui.bills

import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.ONE_DAY_MILLIS
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsByStatusUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Covers the tab/query wiring and, most importantly, that Overdue stays *derived* from the due
 * date once it is routed through the ViewModel — [BillBucketsTest] only proves the pure object.
 */
class BillsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    private fun viewModel() = BillsViewModel(
        GetTransactionsByStatusUseCase(transactionRepository),
        GetCategoriesUseCase(categoryRepository)
    )

    private fun seedBills() {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Utilities")))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, title = "Netflix", date = now, status = TransactionStatus.PAID),
                testTransaction(
                    2, title = "Rent", date = now + ONE_DAY_MILLIS,
                    isScheduled = true, status = TransactionStatus.UPCOMING
                ),
                testTransaction(
                    3, title = "Gym", date = now - ONE_DAY_MILLIS,
                    isScheduled = true, status = TransactionStatus.UPCOMING
                ),
                testTransaction(
                    4, title = "Water", date = now,
                    isScheduled = true, status = TransactionStatus.UPCOMING
                ),
                testTransaction(
                    5, title = "Phone", date = now - 7 * ONE_DAY_MILLIS,
                    isScheduled = true, status = TransactionStatus.OVERDUE
                )
            )
        )
    }

    @Test
    fun `defaults to the Paid tab`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        assertEquals(TransactionStatus.PAID, viewModel.status)
        assertEquals(listOf(1L), viewModel.bills.value.map { it.transaction.id })
    }

    @Test
    fun `Upcoming drops a bill whose due date has passed`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        viewModel.setStatus(TransactionStatus.UPCOMING)

        // 3 is stored UPCOMING but was due yesterday, so it belongs to Overdue now.
        assertEquals(listOf(2L, 4L), viewModel.bills.value.map { it.transaction.id })
    }

    @Test
    fun `a bill due today is upcoming, not overdue`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        viewModel.setStatus(TransactionStatus.UPCOMING)
        assertTrue(viewModel.bills.value.any { it.transaction.id == 4L })

        viewModel.setStatus(TransactionStatus.OVERDUE)
        assertTrue(viewModel.bills.value.none { it.transaction.id == 4L })
    }

    @Test
    fun `Overdue combines stored-overdue with upcoming bills past their due date`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        viewModel.setStatus(TransactionStatus.OVERDUE)

        assertEquals(setOf(5L, 3L), viewModel.bills.value.map { it.transaction.id }.toSet())
    }

    @Test
    fun `the query filters the current tab case-insensitively`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        viewModel.setStatus(TransactionStatus.UPCOMING)
        viewModel.setQuery("re")

        assertEquals(listOf(2L), viewModel.bills.value.map { it.transaction.id })

        viewModel.setQuery("no such bill")
        assertTrue(viewModel.bills.value.isEmpty())
    }

    @Test
    fun `the query survives a tab change`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        viewModel.setQuery("phone")
        viewModel.setStatus(TransactionStatus.OVERDUE)

        assertEquals(listOf(5L), viewModel.bills.value.map { it.transaction.id })
    }

    /**
     * The fragment reads [BillsViewModel.status] in `onViewCreated` to re-select the tab on a view
     * that outlived this ViewModel. If it stopped tracking `setStatus`, the TabLayout would silently
     * desync from the list it is labelling — which is exactly the bug that shipped once already.
     */
    @Test
    fun `status exposes the selected tab for view re-sync`() = runTest {
        seedBills()
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        viewModel.setStatus(TransactionStatus.OVERDUE)

        assertEquals(TransactionStatus.OVERDUE, viewModel.status)
    }

    @Test
    fun `each bill carries its category, or null when it has none`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Utilities")))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, status = TransactionStatus.PAID),
                testTransaction(2, categoryId = 99, status = TransactionStatus.PAID)
            )
        )
        val viewModel = viewModel()
        subscribe(viewModel.bills)

        val items = viewModel.bills.value
        assertEquals("Utilities", items.first { it.transaction.id == 1L }.category?.name)
        assertNull(items.first { it.transaction.id == 2L }.category)
    }
}
