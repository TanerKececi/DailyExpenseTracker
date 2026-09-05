package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.FakeCardDao
import com.example.dailyexpensetracker.data.local.FakeCategoryDao
import com.example.dailyexpensetracker.data.local.FakeTransactionDao
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResetRepositoryImplTest {

    private val categoryDao = FakeCategoryDao()
    private val cardDao = FakeCardDao()
    private val transactionDao = FakeTransactionDao()

    private val repository = DataResetRepositoryImpl(categoryDao, cardDao, transactionDao)

    @Test
    fun `reset replaces edited data with a fresh seed`() = runTest {
        categoryDao.insertAll(
            listOf(
                CategoryEntity(
                    name = "A category the user made",
                    iconName = "grocery",
                    colorHex = "#FFFFFF",
                    budgetLimit = null,
                    isExpense = true
                )
            )
        )

        repository.resetToSeed()

        assertEquals(10, categoryDao.items.size)
        assertTrue(categoryDao.items.none { it.name == "A category the user made" })
        assertTrue(transactionDao.items.isNotEmpty())
    }

    @Test
    fun `reset leaves every transaction pointing at a seeded category`() = runTest {
        repository.resetToSeed()
        repository.resetToSeed()

        val categoryIds = categoryDao.items.map { it.id }.toSet()
        assertEquals(10, categoryIds.size)
        assertTrue(transactionDao.items.all { it.categoryId in categoryIds })
    }

    @Test
    fun `reset clears the old transactions rather than appending to them`() = runTest {
        repository.resetToSeed()
        val afterFirst = transactionDao.items.size

        repository.resetToSeed()

        assertEquals(afterFirst, transactionDao.items.size)
    }
}
