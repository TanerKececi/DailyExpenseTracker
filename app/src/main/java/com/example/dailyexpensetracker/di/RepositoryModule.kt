package com.example.dailyexpensetracker.di

import com.example.dailyexpensetracker.data.repository.CardRepositoryImpl
import com.example.dailyexpensetracker.data.repository.CategoryRepositoryImpl
import com.example.dailyexpensetracker.data.repository.TransactionRepositoryImpl
import com.example.dailyexpensetracker.domain.repository.CardRepository
import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindCardRepository(impl: CardRepositoryImpl): CardRepository
}
