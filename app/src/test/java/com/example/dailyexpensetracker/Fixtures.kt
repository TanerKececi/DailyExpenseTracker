package com.example.dailyexpensetracker

import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.model.CardType
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType

/** Model builders for the ViewModel tests, so each suite only names the fields it cares about. */

fun testCategory(
    id: Long,
    name: String = "Category $id",
    isExpense: Boolean = true,
    budgetLimit: Double? = null
) = Category(
    id = id,
    name = name,
    iconName = "ic_food",
    colorHex = "#FF5722",
    budgetLimit = budgetLimit,
    isExpense = isExpense
)

fun testTransaction(
    id: Long,
    title: String = "Transaction $id",
    amount: Double = 10.0,
    date: Long = 0L,
    categoryId: Long = 1L,
    cardId: Long? = null,
    type: TransactionType = TransactionType.EXPENSE,
    isScheduled: Boolean = false,
    status: TransactionStatus = TransactionStatus.PAID,
    payeeName: String? = null,
    payeeRole: String? = null
) = Transaction(
    id = id,
    title = title,
    amount = amount,
    date = date,
    categoryId = categoryId,
    cardId = cardId,
    type = type,
    isScheduled = isScheduled,
    status = status,
    payeeName = payeeName,
    payeeRole = payeeRole
)

fun testCard(id: Long, name: String = "Card $id") = Card(
    id = id,
    cardName = name,
    cardNumberMasked = "**** 1234",
    currentBalance = 500.0,
    cardType = CardType.VISA
)

const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
