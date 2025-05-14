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
 * Suggestion modülü için Hilt bağımlılık enjeksiyonu modülü
 */
@Module
@InstallIn(SingletonComponent::class)
object SuggestionModule {
    
    /**
     * SuggestionDatabase için provider
     * 
     * @param context Uygulama context'i
     * @return SuggestionDatabase instance
     */
    @Provides
    @Singleton
    fun provideSuggestionDatabase(@ApplicationContext context: Context): SuggestionDatabase {
        return SuggestionDatabase.getInstance(context)
    }
    
    /**
     * SuggestionDao için provider
     * 
     * @param database Veritabanı instance'ı
     * @return SuggestionDao instance
     */
    @Provides
    @Singleton
    fun provideSuggestionDao(database: SuggestionDatabase): SuggestionDao {
        return database.suggestionDao()
    }
    
    /**
     * SuggestionRepository için provider
     * 
     * @param suggestionDao SuggestionDao instance'ı
     * @return SuggestionRepository instance
     */
    @Provides
    @Singleton
    fun provideSuggestionRepository(suggestionDao: SuggestionDao): SuggestionRepository {
        return SuggestionRepository(suggestionDao)
    }
}
