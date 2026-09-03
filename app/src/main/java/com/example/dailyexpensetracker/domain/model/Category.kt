package com.example.dailyexpensetracker.domain.model

data class Category(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val budgetLimit: Double?,
    val isExpense: Boolean
)
