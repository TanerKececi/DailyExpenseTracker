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
    fun `DeleteTransactionUseCase removes the transaction`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.OVERDUE)))
        val useCase = DeleteTransactionUseCase(repo)

        useCase(1L)

        assertNull(repo.getById(1L).first())
    }
}
