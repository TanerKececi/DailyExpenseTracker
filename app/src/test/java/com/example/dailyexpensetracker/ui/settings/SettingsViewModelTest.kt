package com.example.dailyexpensetracker.ui.settings

import com.example.dailyexpensetracker.domain.repository.DataResetRepository
import com.example.dailyexpensetracker.domain.usecase.ResetDataUseCase
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SettingsViewModelTest {

    private class RecordingResetRepository : DataResetRepository {
        var resetCount = 0
        override suspend fun resetToSeed() {
            resetCount++
        }
    }

    /** `CurrencyFormatter.symbol` is global; leaving it changed would leak into other suites. */
    @After
    fun resetSymbol() {
        CurrencyFormatter.symbol = CurrencyFormatter.DEFAULT_SYMBOL
    }

    private fun viewModel(
        store: SettingsStore = FakeSettingsStore(),
        repository: DataResetRepository = RecordingResetRepository()
    ) = SettingsViewModel(store, ResetDataUseCase(repository))

    @Test
    fun `opens showing what is stored`() {
        val store = FakeSettingsStore(currencySymbol = "£", weekStart = Calendar.MONDAY)

        val state = viewModel(store).uiState.value

        assertEquals("£", state.currencySymbol)
        assertEquals(Calendar.MONDAY, state.weekStart)
    }

    @Test
    fun `changing the currency persists it and updates the formatter`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        viewModel.setCurrencySymbol("€")

        assertEquals("€", viewModel.uiState.value.currencySymbol)
        assertEquals("€", store.currencySymbol)
        assertEquals("€", CurrencyFormatter.symbol)
        assertTrue(CurrencyFormatter.format(1.0).startsWith("€"))
    }

    @Test
    fun `changing the week start persists it`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        viewModel.setWeekStart(Calendar.MONDAY)

        assertEquals(Calendar.MONDAY, viewModel.uiState.value.weekStart)
        assertEquals(Calendar.MONDAY, store.weekStart)
    }

    @Test
    fun `changing one setting leaves the other alone`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        viewModel.setCurrencySymbol("¥")

        assertEquals(SettingsStore.DEFAULT_WEEK_START, viewModel.uiState.value.weekStart)
        assertEquals(SettingsStore.DEFAULT_WEEK_START, store.weekStart)
    }

    @Test
    fun `every offered symbol round-trips`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        for (symbol in SettingsStore.SUPPORTED_SYMBOLS) {
            viewModel.setCurrencySymbol(symbol)

            assertEquals(symbol, viewModel.uiState.value.currencySymbol)
            assertTrue(CurrencyFormatter.format(12.5).startsWith(symbol))
        }
    }

    @Test
    fun `reset delegates to the use case`() = runTest {
        val repository = RecordingResetRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.resetData()

        assertEquals(1, repository.resetCount)
    }
}
