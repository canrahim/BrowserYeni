package com.asforce.asforcebrowser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.data.model.TabHistory

/**
 * BrowserDatabase - Uygulama veritabanını tanımlar
 * 
 * Sekmeler ve gezinme geçmişi için veritabanı oluşturur ve yönetir.
 * Singleton pattern uygulanmıştır, böylece uygulama genelinde tek bir veritabanı örneği kullanılır.
 * Referans: Room Database ve Singleton pattern
 */
@Database(
    entities = [Tab::class, TabHistory::class],
    version = 2,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun tabHistoryDao(): TabHistoryDao

    companion object {
        private const val DATABASE_NAME = "asforce_browser.db"
        
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}