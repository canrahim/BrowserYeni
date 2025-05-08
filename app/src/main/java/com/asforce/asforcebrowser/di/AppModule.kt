package com.asforce.asforcebrowser.di

import android.content.Context
import com.asforce.asforcebrowser.data.local.BrowserDatabase
import com.asforce.asforcebrowser.data.local.TabDao
import com.asforce.asforcebrowser.data.local.TabHistoryDao
import com.asforce.asforcebrowser.data.repository.TabHistoryRepositoryImpl
import com.asforce.asforcebrowser.data.repository.TabRepositoryImpl
import com.asforce.asforcebrowser.domain.repository.TabHistoryRepository
import com.asforce.asforcebrowser.domain.repository.TabRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule - Dependency Injection için modül
 * 
 * Uygulama genelinde kullanılacak bağımlılıkları sağlar.
 * Referans: Dagger Hilt Dependency Injection
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBrowserDatabase(@ApplicationContext context: Context): BrowserDatabase {
        return BrowserDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTabDao(database: BrowserDatabase): TabDao {
        return database.tabDao()
    }

    @Provides
    @Singleton
    fun provideTabHistoryDao(database: BrowserDatabase): TabHistoryDao {
        return database.tabHistoryDao()
    }

    @Provides
    @Singleton
    fun provideTabRepository(tabDao: TabDao): TabRepository {
        return TabRepositoryImpl(tabDao)
    }

    @Provides
    @Singleton
    fun provideTabHistoryRepository(tabHistoryDao: TabHistoryDao): TabHistoryRepository {
        return TabHistoryRepositoryImpl(tabHistoryDao)
    }
}