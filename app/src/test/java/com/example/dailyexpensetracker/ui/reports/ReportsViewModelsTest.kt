package com.example.dailyexpensetracker.ui.reports

import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateCategoryBudgetUseCase
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import com.example.dailyexpensetracker.ui.reports.billing.BillingReportsViewModel
import com.example.dailyexpensetracker.ui.reports.budget.BudgetPlannerViewModel
import com.example.dailyexpensetracker.ui.reports.expense.ExpenseChartViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The three Reports tabs plus the host that owns the month they share. None of them touch
 * `viewModelScope` — the tabs expose cold flows the fragments collect — so no main dispatcher
 * is needed here.
 */
class ReportsViewModelsTest {

    private val now = System.currentTimeMillis()
    private val monthStart = MonthRange.monthStart(now)
    private val nextMonthStart = MonthRange.nextMonthStart(monthStart)

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    // ----- ReportsViewModel: the shared month and tab -----

    @Test
    fun `reports opens on the current month and the first tab`() {
        val viewModel = ReportsViewModel()

        assertEquals(monthStart, viewModel.selectedMonthStart.value)
        assertEquals(0, viewModel.selectedTab)
    }

    @Test
    fun `month navigation round-trips`() {
        val viewModel = ReportsViewModel()

        viewModel.previousMonth()
        assertEquals(MonthRange.previousMonthStart(monthStart), viewModel.selectedMonthStart.value)

        viewModel.nextMonth()
        assertEquals(monthStart, viewModel.selectedMonthStart.value)
    }

    /**
     * The tab index lives here rather than in the view because this ViewModel outlives the
     * fragment's view; a recreated TabLayout would otherwise reset to 0 and desync from the
     * restored child fragment.
     */
    @Test
    fun `the selected tab is remembered`() {
        val viewModel = ReportsViewModel()

        viewModel.selectTab(2)

        assertEquals(2, viewModel.selectedTab)
    }

    // ----- ExpenseChartViewModel -----

    @Test
    fun `the expense chart is bounded to the selected month`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, budgetLimit = 100.0)))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, amount = 40.0, date = monthStart),
                testTransaction(2, amount = 999.0, date = nextMonthStart)
            )
        )
        val viewModel = ExpenseChartViewModel(
            GetBudgetSummaryUseCase(transactionRepository, categoryRepository)
        )

        val summary = viewModel.summaryFor(monthStart).first()

        assertEquals(40.0, summary.totalSpent, 0.001)
        assertEquals(60.0, summary.remaining, 0.001)
    }

    // ----- BillingReportsViewModel -----

    @Test
    fun `billing reports bucket six months, keeping empty ones as zeroes`() = runTest {
        val months = MonthRange.monthsEndingAt(monthStart, BillingReportsViewModel.MONTHS_SHOWN)
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, amount = 50.0, date = months[5], type = TransactionType.EXPENSE),
                testTransaction(2, amount = 200.0, date = months[2], type = TransactionType.INCOME)
            )
        )
        val viewModel = BillingReportsViewModel(GetTransactionsForPeriodUseCase(transactionRepository))

        val totals = viewModel.totalsFor(monthStart).first()

        assertEquals(6, totals.size)
        assertEquals(months, totals.map { it.monthStartMillis })
        assertEquals(50.0, totals[5].expense, 0.001)
        assertEquals(200.0, totals[2].income, 0.001)
        assertEquals(0.0, totals[0].expense, 0.001)
        assertEquals(0.0, totals[0].income, 0.001)
    }

    @Test
    fun `billing reports ignore transactions outside the six-month window`() = runTest {
        val months = MonthRange.monthsEndingAt(monthStart, BillingReportsViewModel.MONTHS_SHOWN)
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, amount = 500.0, date = MonthRange.previousMonthStart(months[0])),
                testTransaction(2, amount = 500.0, date = nextMonthStart)
            )
        )
        val viewModel = BillingReportsViewModel(GetTransactionsForPeriodUseCase(transactionRepository))

        val totals = viewModel.totalsFor(monthStart).first()

        assertEquals(0.0, totals.sumOf { it.expense }, 0.001)
    }

    // ----- BudgetPlannerViewModel -----

    private fun budgetPlanner() = BudgetPlannerViewModel(
        GetCategoriesUseCase(categoryRepository),
        GetTransactionsForPeriodUseCase(transactionRepository),
        UpdateCategoryBudgetUseCase(categoryRepository)
    )

    /**
     * The planner must list a category with no spend at all — you cannot set a limit on a row it
     * refuses to show, which is why it doesn't reuse `BudgetSummary.categoryBreakdown`.
     */
    @Test
    fun `the planner lists expense categories with no spend`() = runTest {
        categoryRepository.setCategories(
            listOf(
                testCategory(1, name = "Groceries", budgetLimit = 100.0),
                testCategory(2, name = "Rent", budgetLimit = 500.0),
                testCategory(3, name = "Salary", isExpense = false)
            )
        )
        transactionRepository.setTransactions(listOf(testTransaction(1, amount = 30.0, date = monthStart)))

        val plans = budgetPlanner().plansFor(monthStart).first()

        assertEquals(listOf("Groceries", "Rent"), plans.map { it.category.name })
        assertEquals(30.0, plans[0].spent, 0.001)
        assertEquals(0.0, plans[1].spent, 0.001)
    }

    @Test
    fun `the planner counts only the selected month's expenses`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Groceries")))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, amount = 30.0, date = monthStart),
                testTransaction(2, amount = 999.0, date = nextMonthStart),
                testTransaction(3, amount = 500.0, date = monthStart, type = TransactionType.INCOME)
            )
        )

        val plans = budgetPlanner().plansFor(monthStart).first()

        assertEquals(30.0, plans.single().spent, 0.001)
    }

    @Test
    fun `setting a budget writes it through, and null clears it`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Groceries", budgetLimit = 100.0)))
        val viewModel = budgetPlanner()

        viewModel.setBudget(1L, 250.0)
        assertEquals(250.0, categoryRepository.getAll().first().single().budgetLimit!!, 0.001)

        viewModel.setBudget(1L, null)
        assertNull(categoryRepository.getAll().first().single().budgetLimit)
    }
}
