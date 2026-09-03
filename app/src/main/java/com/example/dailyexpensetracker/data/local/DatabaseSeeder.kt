package com.example.dailyexpensetracker.data.local

import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.CardEntity
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity
import com.example.dailyexpensetracker.domain.model.CardType
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import java.util.Calendar

/** Populates the database with sample data on first launch so every Phase 1 screen renders populated. */
object DatabaseSeeder {

    suspend fun seed(categoryDao: CategoryDao, cardDao: CardDao, transactionDao: TransactionDao) {
        val categories = listOf(
            CategoryEntity(name = "Grocery", iconName = "grocery", colorHex = "#5A67F2", budgetLimit = 1000.0, isExpense = true),
            CategoryEntity(name = "Food & Drink", iconName = "food", colorHex = "#F2994A", budgetLimit = 800.0, isExpense = true),
            CategoryEntity(name = "Clothes", iconName = "clothes", colorHex = "#B39DFB", budgetLimit = 500.0, isExpense = true),
            CategoryEntity(name = "Hotel", iconName = "hotel", colorHex = "#F2C94C", budgetLimit = 1200.0, isExpense = true),
            CategoryEntity(name = "Medicine", iconName = "medicine", colorHex = "#FF7675", budgetLimit = 400.0, isExpense = true),
            CategoryEntity(name = "Fuel", iconName = "fuel", colorHex = "#2ED9A6", budgetLimit = 600.0, isExpense = true),
            CategoryEntity(name = "Gifts", iconName = "gifts", colorHex = "#56CCF2", budgetLimit = 300.0, isExpense = true),
            CategoryEntity(name = "Travel", iconName = "travel", colorHex = "#7B61FF", budgetLimit = 1500.0, isExpense = true),
            CategoryEntity(name = "Kids", iconName = "kids", colorHex = "#EB5FBD", budgetLimit = 500.0, isExpense = true),
            CategoryEntity(name = "Salary", iconName = "salary", colorHex = "#5A67F2", budgetLimit = null, isExpense = false)
        )
        categoryDao.insertAll(categories)

        val cards = listOf(
            CardEntity(cardName = "Primary Visa", cardNumberMasked = "**** 9324", currentBalance = 10450.00, cardType = CardType.VISA),
            CardEntity(cardName = "Mastercard", cardNumberMasked = "**** 7645", currentBalance = 37.49, cardType = CardType.MASTERCARD)
        )
        cardDao.insertAll(cards)

        // categoryId indices below correspond to insertion order above (autoGenerate starts at 1)
        val groceryId = 1L
        val foodId = 2L
        val clothesId = 3L
        val medicineId = 5L
        val fuelId = 6L
        val salaryId = 10L

        val now = Calendar.getInstance()
        // Clamp within the current month so seed data always shows up in the "this month"
        // dashboard queries, regardless of what day of the month the app is first launched.
        fun daysAgo(days: Int): Long {
            val cal = now.clone() as Calendar
            val day = (cal.get(Calendar.DAY_OF_MONTH) - days).coerceAtLeast(1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            return cal.timeInMillis
        }

        fun daysAhead(days: Int): Long {
            val cal = now.clone() as Calendar
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val day = (cal.get(Calendar.DAY_OF_MONTH) + days).coerceAtMost(maxDay)
            cal.set(Calendar.DAY_OF_MONTH, day)
            return cal.timeInMillis
        }

        val transactions = listOf(
            TransactionEntity(title = "Medicine", amount = 2680.0, date = daysAgo(1), categoryId = medicineId, cardId = 1, type = TransactionType.EXPENSE, status = TransactionStatus.PAID),
            TransactionEntity(title = "Restaurant", amount = 2680.0, date = daysAgo(2), categoryId = foodId, cardId = 1, type = TransactionType.EXPENSE, status = TransactionStatus.PAID),
            TransactionEntity(title = "Cloth Shopping", amount = 2680.0, date = daysAgo(3), categoryId = clothesId, cardId = 2, type = TransactionType.EXPENSE, status = TransactionStatus.PAID),
            TransactionEntity(title = "Grocery Store", amount = 1230.0, date = daysAgo(4), categoryId = groceryId, cardId = 1, type = TransactionType.EXPENSE, status = TransactionStatus.PAID),
            TransactionEntity(title = "Gas Station", amount = 450.0, date = daysAgo(5), categoryId = fuelId, cardId = 1, type = TransactionType.EXPENSE, status = TransactionStatus.PAID),
            TransactionEntity(title = "Monthly Salary", amount = 10500.0, date = daysAgo(6), categoryId = salaryId, cardId = 1, type = TransactionType.INCOME, status = TransactionStatus.PAID),
            TransactionEntity(title = "Grocery Restock", amount = 540.0, date = daysAgo(0), categoryId = groceryId, cardId = 2, type = TransactionType.EXPENSE, status = TransactionStatus.PAID),
            TransactionEntity(title = "Pharmacy Refill", amount = 1250.65, date = daysAhead(3), categoryId = medicineId, cardId = 1, type = TransactionType.EXPENSE, isScheduled = true, status = TransactionStatus.UPCOMING),
            TransactionEntity(title = "Overdue Bill", amount = 320.0, date = daysAgo(2), categoryId = foodId, cardId = 2, type = TransactionType.EXPENSE, isScheduled = true, status = TransactionStatus.OVERDUE)
        )
        transactionDao.insertAll(transactions)
    }
}
