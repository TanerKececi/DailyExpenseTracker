package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import javax.inject.Inject

class UpdateTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    /** Replaces the whole row, so [transaction] must carry every field, not just edited ones. */
    suspend operator fun invoke(transaction: Transaction) = repository.update(transaction)
}
