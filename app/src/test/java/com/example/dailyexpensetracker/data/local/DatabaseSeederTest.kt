package com.example.dailyexpensetracker.data.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSeederTest {

    @Test
    fun `every seeded transaction points at a real category and card`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()

        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)

        val categoryIds = categoryDao.items.map { it.id }.toSet()
        val cardIds = cardDao.items.map { it.id }.toSet()
        assertTrue(transactionDao.items.isNotEmpty())
        assertTrue(transactionDao.items.all { it.categoryId in categoryIds })
        assertTrue(transactionDao.items.all { it.cardId in cardIds })
    }

    /**
     * The regression this test exists for. AUTOINCREMENT does not reset when rows are deleted, so a
     * reseed hands out fresh ids. The seeder used to hardcode 1..10, which would leave every
     * transaction pointing at a category that no longer existed — and the foreign key would reject
     * the insert on the very first use of Reset data.
     */
    @Test
    fun `a reseed after a clear still resolves every foreign key`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()

        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)
        transactionDao.deleteAll()
        categoryDao.deleteAll()
        cardDao.deleteAll()
        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)

        val categoryIds = categoryDao.items.map { it.id }.toSet()
        val cardIds = cardDao.items.map { it.id }.toSet()
        assertEquals(10, categoryIds.size)
        assertEquals(2, cardIds.size)
        assertTrue(transactionDao.items.all { it.categoryId in categoryIds })
        assertTrue(transactionDao.items.all { it.cardId in cardIds })
    }

    @Test
    fun `a reseed does not reuse the ids the first seed handed out`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()

        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)
        val firstIds = categoryDao.items.map { it.id }.toSet()
        transactionDao.deleteAll()
        categoryDao.deleteAll()
        cardDao.deleteAll()
        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)

        // Guards the fake itself: if its sequence reset, the test above would pass vacuously.
        assertTrue(categoryDao.items.none { it.id in firstIds })
    }
}
