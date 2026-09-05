package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import com.example.dailyexpensetracker.ui.settings.FakeSettingsStore
import com.example.dailyexpensetracker.ui.settings.SettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()
    private val monthStart = MonthRange.monthStart(now)
    private val nextMonthStart = MonthRange.nextMonthStart(monthStart)

    /** Mid-morning on [day] of the month starting at [monthStartMillis]. */
    private fun dayInMonth(monthStartMillis: Long, day: Int): Long =
        MonthRange.calendarAt(monthStartMillis).apply {
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 10)
        }.timeInMillis

    private val dayOne = dayInMonth(monthStart, 1)
    private val dayTwo = dayInMonth(monthStart, 2)

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    private fun viewModel(settingsStore: SettingsStore = FakeSettingsStore()) = CalendarViewModel(
        GetTransactionsForPeriodUseCase(transactionRepository),
        GetCategoriesUseCase(categoryRepository),
        settingsStore
    )

    private fun dayOfMonth(millis: Long) = MonthRange.calendarAt(millis).get(Calendar.DAY_OF_MONTH)

    @Test
    fun `opens on the current month with a full grid`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertEquals(MonthRange.label(monthStart), state.monthLabel)
        assertEquals(MonthRange.dayStart(now), state.selectedDayMillis)
        assertTrue(state.cells.isNotEmpty())
    }

    @Test
    fun `the day list holds only the selected day`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Groceries")))
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, date = dayOne, categoryId = 1),
                testTransaction(2, date = dayTwo, categoryId = 1)
            )
        )
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.selectDay(MonthRange.dayStart(dayOne))
        assertEquals(listOf(1L), viewModel.uiState.value.dayItems.map { it.transaction.id })

        viewModel.selectDay(MonthRange.dayStart(dayTwo))
        assertEquals(listOf(2L), viewModel.uiState.value.dayItems.map { it.transaction.id })
    }

    @Test
    fun `day items carry the joined category`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1, name = "Groceries")))
        transactionRepository.setTransactions(listOf(testTransaction(1, date = dayOne, categoryId = 1)))
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.selectDay(MonthRange.dayStart(dayOne))

        assertEquals("Groceries", viewModel.uiState.value.dayItems.single().category?.name)
    }

    @Test
    fun `moving months round-trips back to where it started`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.nextMonth()
        assertNotEquals(MonthRange.label(monthStart), viewModel.uiState.value.monthLabel)

        viewModel.previousMonth()
        assertEquals(MonthRange.label(monthStart), viewModel.uiState.value.monthLabel)
    }

    /** Something must stay selected across a month change, or the day list empties for no reason. */
    @Test
    fun `the selected day-of-month survives a month change`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.selectDay(MonthRange.dayStart(dayTwo))
        viewModel.nextMonth()

        assertEquals(2, dayOfMonth(viewModel.uiState.value.selectedDayMillis))
        assertEquals(MonthRange.label(nextMonthStart), viewModel.uiState.value.monthLabel)
    }

    /** The month query is re-run on navigation; a stale range would show last month's rows. */
    @Test
    fun `changing month refetches that month's transactions`() = runTest {
        categoryRepository.setCategories(listOf(testCategory(1)))
        val nextMonthDay = dayInMonth(nextMonthStart, 1)
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, date = dayOne, categoryId = 1),
                testTransaction(2, date = nextMonthDay, categoryId = 1)
            )
        )
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.selectDay(MonthRange.dayStart(dayOne))
        assertEquals(listOf(1L), viewModel.uiState.value.dayItems.map { it.transaction.id })

        viewModel.nextMonth()
        viewModel.selectDay(MonthRange.dayStart(nextMonthDay))
        assertEquals(listOf(2L), viewModel.uiState.value.dayItems.map { it.transaction.id })
    }

    /**
     * A Monday week shifts every column one place left of a Sunday week, wrapping at seven. The
     * fragment's header row must be rotated by the same amount or the grid and its labels disagree.
     */
    @Test
    fun `the grid honours a Monday week start`() = runTest {
        val sundayStart = viewModel(FakeSettingsStore(weekStart = Calendar.SUNDAY))
        subscribe(sundayStart.uiState)
        val sundayBlanks = sundayStart.uiState.value.cells.count { it.dayOfMonth == null }

        val mondayStart = viewModel(FakeSettingsStore(weekStart = Calendar.MONDAY))
        subscribe(mondayStart.uiState)
        val mondayBlanks = mondayStart.uiState.value.cells.count { it.dayOfMonth == null }

        assertEquals((sundayBlanks + 6) % 7, mondayBlanks)
    }
}
