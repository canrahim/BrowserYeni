package com.asforce.asforcebrowser.suggestion.di

import android.content.Context
import com.asforce.asforcebrowser.suggestion.data.local.SuggestionDao
import com.asforce.asforcebrowser.suggestion.data.local.SuggestionDatabase
import com.asforce.asforcebrowser.suggestion.data.repository.SuggestionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Öneri modülü bağımlılık enjeksiyonu
 * 
 * Hilt ile öneri modülü için bağımlılıkları sağlar.
 */
@Module
@InstallIn(SingletonComponent::class)
object SuggestionModule {
    
    /**
     * SuggestionDatabase bağımlılığını sağlar
     * 
     * @param context Uygulama context'i
     * @return SuggestionDatabase örneği
     */
    @Provides
    @Singleton
    fun provideSuggestionDatabase(@ApplicationContext context: Context): SuggestionDatabase {
        return SuggestionDatabase.getInstance(context)
    }
    
    /**
     * SuggestionDao bağımlılığını sağlar
     * 
     * @param database SuggestionDatabase örneği
     * @return SuggestionDao örneği
     */
    @Provides
    @Singleton
    fun provideSuggestionDao(database: SuggestionDatabase): SuggestionDao {
        return database.suggestionDao()
    }
    
    /**
     * SuggestionRepository bağımlılığını sağlar
     * 
     * @param dao SuggestionDao örneği
     * @return SuggestionRepository örneği
     */
    @Provides
    @Singleton
    fun provideSuggestionRepository(dao: SuggestionDao): SuggestionRepository {
        return SuggestionRepository(dao)
    }
}