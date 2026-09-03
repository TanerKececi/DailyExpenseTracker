package com.example.dailyexpensetracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dailyexpensetracker.data.local.AppDatabase
import com.example.dailyexpensetracker.data.local.DatabaseSeeder
import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // Captured by reference: onCreate only fires (async, on first DB access) after this
        // function returns and `instance` has been fully assigned.
        lateinit var instance: AppDatabase
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        instance = Room.databaseBuilder(context, AppDatabase::class.java, "daily_expense_tracker.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedScope.launch {
                        DatabaseSeeder.seed(instance.categoryDao(), instance.cardDao(), instance.transactionDao())
                    }
                }
            })
            .build()
        return instance
    }

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCardDao(db: AppDatabase): CardDao = db.cardDao()
}
