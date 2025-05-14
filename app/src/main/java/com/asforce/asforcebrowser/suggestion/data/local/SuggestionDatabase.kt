package com.asforce.asforcebrowser.suggestion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity

/**
 * Öneri verileri için Room veritabanı
 * 
 * Önerileri depolamak için SQLite veritabanını yönetir.
 */
@Database(
    entities = [SuggestionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SuggestionDatabase : RoomDatabase() {
    /**
     * SuggestionDao'ya erişim sağlar
     */
    abstract fun suggestionDao(): SuggestionDao
    
    companion object {
        private const val DB_NAME = "suggestions.db"
        
        @Volatile
        private var INSTANCE: SuggestionDatabase? = null
        
        /**
         * Veritabanı örneğini döndürür veya oluşturur
         * 
         * @param context Uygulama/Aktivite Context'i
         * @return Veritabanı örneği
         */
        fun getInstance(context: Context): SuggestionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SuggestionDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration() // Şema değişikliklerinde veriyi sıfırla
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}