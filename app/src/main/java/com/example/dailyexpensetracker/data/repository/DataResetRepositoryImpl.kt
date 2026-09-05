package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.DatabaseSeeder
import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.domain.repository.DataResetRepository
import javax.inject.Inject

class DataResetRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val cardDao: CardDao,
    private val transactionDao: TransactionDao
) : DataResetRepository {

    override suspend fun resetToSeed() {
        // Transactions first: they hold the foreign keys into categories and cards.
        transactionDao.deleteAll()
        categoryDao.deleteAll()
        cardDao.deleteAll()
        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)
    }
}
