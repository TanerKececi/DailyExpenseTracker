package com.example.dailyexpensetracker.di

import com.example.dailyexpensetracker.ui.settings.SettingsStore
import com.example.dailyexpensetracker.ui.settings.SharedPreferencesSettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindSettingsStore(impl: SharedPreferencesSettingsStore): SettingsStore
}
