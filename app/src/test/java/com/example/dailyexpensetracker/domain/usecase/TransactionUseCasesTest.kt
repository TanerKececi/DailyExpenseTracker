package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionUseCasesTest {

    private fun sampleTransaction(id: Long, status: TransactionStatus) = Transaction(
        id = id,
        title = "Test $id",
        amount = 10.0,
        date = 0L,
        categoryId = 1L,
        cardId = null,
        type = TransactionType.EXPENSE,
        isScheduled = true,
        status = status
    )

    @Test
    fun `GetTransactionsByStatusUseCase returns only matching status`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(
            listOf(
                sampleTransaction(1, TransactionStatus.PAID),
                sampleTransaction(2, TransactionStatus.UPCOMING),
                sampleTransaction(3, TransactionStatus.UPCOMING)
            )
        )
        val useCase = GetTransactionsByStatusUseCase(repo)

        val result = useCase(TransactionStatus.UPCOMING).first()

        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun `GetTransactionByIdUseCase returns the matching transaction`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.PAID)))
        val useCase = GetTransactionByIdUseCase(repo)

        assertEquals(1L, useCase(1L).first()?.id)
    }

    @Test
    fun `GetTransactionByIdUseCase returns null for an unknown id`() = runBlocking {
        val repo = FakeTransactionRepository()
        val useCase = GetTransactionByIdUseCase(repo)

        assertNull(useCase(999L).first())
    }

    @Test
    fun `UpdateTransactionStatusUseCase updates the transaction's status`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.UPCOMING)))
        val useCase = UpdateTransactionStatusUseCase(repo)

        useCase(1L, TransactionStatus.PAID)

        assertEquals(TransactionStatus.PAID, repo.getById(1L).first()?.status)
    }

    @Test
    fun `UpdateTransactionUseCase replaces the matching transaction`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.PAID)))
        val useCase = UpdateTransactionUseCase(repo)

        useCase(sampleTransaction(1, TransactionStatus.PAID).copy(title = "Edited", amount = 99.0))

        val updated = repo.getById(1L).first()
        assertEquals("Edited", updated?.title)
        assertEquals(99.0, updated!!.amount, 0.001)
    }

    @Test
    fun `UpdateTransactionUseCase leaves other transactions untouched`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(
            listOf(sampleTransaction(1, TransactionStatus.PAID), sampleTransaction(2, TransactionStatus.PAID))
        )
        val useCase = UpdateTransactionUseCase(repo)

        useCase(sampleTransaction(1, TransactionStatus.PAID).copy(title = "Edited"))

        assertEquals("Test 2", repo.getById(2L).first()?.title)
    }

    // The editor must carry scheduling fields through rather than defaulting them, or editing a
    // bill would quietly turn it into an ordinary paid transaction.
    @Test
    fun `UpdateTransactionUseCase writes every field it is given, including scheduling`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.UPCOMING)))
        val useCase = UpdateTransactionUseCase(repo)

        useCase(sampleTransaction(1, TransactionStatus.UPCOMING).copy(title = "Edited"))

        val updated = repo.getById(1L).first()
        assertEquals(TransactionStatus.UPCOMING, updated?.status)
        assertEquals(true, updated?.isScheduled)
    }

    @Test
    fun `DeleteTransactionUseCase removes the transaction`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.OVERDUE)))
        val useCase = DeleteTransactionUseCase(repo)

        useCase(1L)

        assertNull(repo.getById(1L).first())
    }
}
