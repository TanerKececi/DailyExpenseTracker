package com.example.dailyexpensetracker.ui.wallet.adapter

import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.Transaction

/** UI-layer join of a Transaction with its Category, built once in the ViewModel so adapters don't need a category lookup. */
data class TransactionListItem(
    val transaction: Transaction,
    val category: Category?
)
