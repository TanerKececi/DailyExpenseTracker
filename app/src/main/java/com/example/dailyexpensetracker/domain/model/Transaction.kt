package com.example.dailyexpensetracker.domain.model

data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val date: Long,
    val categoryId: Long,
    val cardId: Long?,
    val type: TransactionType,
    val isScheduled: Boolean,
    val status: TransactionStatus
)
