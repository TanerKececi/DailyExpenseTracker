package com.example.dailyexpensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.CardEntity
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, CardEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun cardDao(): CardDao
}
