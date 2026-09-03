package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction) = repository.add(transaction)
}
