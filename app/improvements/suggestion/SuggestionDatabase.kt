package com.asforce.asforcebrowser.suggestion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity

/**
 * Öneri verileri için Room veritabanı
 * 
 * Singleton olarak uygulanmıştır ve veri geçişleri için migration stratejisi içerir.
 */
@Database(
    entities = [SuggestionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SuggestionDatabase : RoomDatabase() {
    
    /**
     * Öneri DAO'sunu döndürür
     */
    abstract fun suggestionDao(): SuggestionDao
    
    companion object {
        private const val DATABASE_NAME = "suggestion_database"
        
        @Volatile
        private var INSTANCE: SuggestionDatabase? = null
        
        /**
         * Veritabanı instance'ını döndürür, yoksa oluşturur
         * 
         * @param context Context
         * @return SuggestionDatabase instance
         */
        fun getInstance(context: Context): SuggestionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SuggestionDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // Veritabanı şeması değiştiğinde sıfırlar
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
