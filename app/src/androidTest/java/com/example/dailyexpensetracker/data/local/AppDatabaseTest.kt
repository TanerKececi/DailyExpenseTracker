package com.example.dailyexpensetracker.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity
import com.example.dailyexpensetracker.data.repository.DataResetRepositoryImpl
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The DAOs are faked everywhere in the JVM suite, so these run the real queries against real
 * SQLite. Each test pins behaviour the app depends on but that a fake cannot exhibit:
 * AUTOINCREMENT's sequence, foreign key enforcement, BETWEEN's bounds, and SUM's null.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var cardDao: CardDao
    private lateinit var transactionDao: TransactionDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = db.categoryDao()
        cardDao = db.cardDao()
        transactionDao = db.transactionDao()
    }

    @After
    fun closeDb() = db.close()

    /**
     * The reason this suite exists. `DatabaseSeeder` used to hardcode category ids 1..10, which
     * holds only on a fresh database. Reset data wipes every row and reseeds, and AUTOINCREMENT
     * does not reuse ids, so the second seed lands on fresh ones. If the seeder ever goes back to
     * constants this fails on the foreign key.
     */
    @Test
    fun resetToSeed_survives_a_second_run_with_reassigned_ids() = runBlocking {
        val reset = DataResetRepositoryImpl(categoryDao, cardDao, transactionDao)

        reset.resetToSeed()
        val firstIds = categoryDao.getAll().first().map { it.id }

        reset.resetToSeed()
        val secondIds = categoryDao.getAll().first().map { it.id }

        assertTrue(
            "Reseeded categories should get fresh ids, got $secondIds after $firstIds",
            secondIds.min() > firstIds.max()
        )

        // The point of the fix: every transaction still resolves to a category that exists.
        val categoryIds = secondIds.toSet()
        val transactions = transactionDao.getAll().first()
        assertTrue("Seeder produced no transactions", transactions.isNotEmpty())
        transactions.forEach {
            assertTrue(
                "Transaction '${it.title}' references missing category ${it.categoryId}",
                it.categoryId in categoryIds
            )
        }
    }

    /** The mechanism behind the test above, asserted on its own so a failure says why. */
    @Test
    fun autoincrement_does_not_reuse_ids_after_a_delete() = runBlocking {
        val first = categoryDao.insertAll(listOf(category("Grocery"))).single()
        categoryDao.deleteAll()
        val second = categoryDao.insertAll(listOf(category("Grocery"))).single()

        assertTrue("Expected a fresh id after delete, got $second following $first", second > first)
    }

    /**
     * The seeder's correctness rests on the foreign key actually rejecting an orphan, which in
     * turn rests on Room enabling the foreign_keys pragma.
     */
    @Test(expected = SQLiteConstraintException::class)
    fun inserting_a_transaction_with_an_unknown_category_is_rejected() = runBlocking {
        transactionDao.insert(transaction(categoryId = 99_999L, date = 1_000L))
        Unit
    }

    /** Every month-range query in the app leans on BETWEEN including both endpoints. */
    @Test
    fun getByDateRange_includes_both_endpoints() = runBlocking {
        val categoryId = categoryDao.insertAll(listOf(category("Grocery"))).single()
        transactionDao.insert(transaction(categoryId, date = 99L, title = "before"))
        transactionDao.insert(transaction(categoryId, date = 100L, title = "start"))
        transactionDao.insert(transaction(categoryId, date = 150L, title = "middle"))
        transactionDao.insert(transaction(categoryId, date = 200L, title = "end"))
        transactionDao.insert(transaction(categoryId, date = 201L, title = "after"))

        val titles = transactionDao.getByDateRange(100L, 200L).first().map { it.title }

        assertEquals(listOf("end", "middle", "start"), titles)
    }

    /**
     * SUM over no rows is null, not 0 — which is why the DAO returns `Flow<Double?>` and the call
     * sites coalesce. A fake returning 0.0 would hide this.
     */
    @Test
    fun getSumByTypeAndDateRange_is_null_when_the_range_is_empty() = runBlocking {
        val categoryId = categoryDao.insertAll(listOf(category("Grocery"))).single()
        transactionDao.insert(transaction(categoryId, date = 50L))

        val empty = transactionDao
            .getSumByTypeAndDateRange(TransactionType.EXPENSE, 100L, 200L)
            .first()
        assertNull("SUM over an empty range should be null", empty)

        val populated = transactionDao
            .getSumByTypeAndDateRange(TransactionType.EXPENSE, 0L, 100L)
            .first()
        assertNotNull(populated)
        assertEquals(10.0, populated!!, 0.001)
    }

    private fun category(name: String) = CategoryEntity(
        name = name,
        iconName = "grocery",
        colorHex = "#5A67F2",
        budgetLimit = 100.0,
        isExpense = true
    )

    private fun transaction(
        categoryId: Long,
        date: Long,
        title: String = "Test",
        amount: Double = 10.0
    ) = TransactionEntity(
        title = title,
        amount = amount,
        date = date,
        categoryId = categoryId,
        cardId = null,
        type = TransactionType.EXPENSE,
        status = TransactionStatus.PAID
    )
}
