package com.asforce.asforcebrowser.data.local

import androidx.room.*
import com.asforce.asforcebrowser.data.model.Tab
import kotlinx.coroutines.flow.Flow

/**
 * TabDao - Sekme Entity'si için veri erişim nesnesi
 * 
 * Sekmelerin oluşturulması, güncellenmesi, silinmesi ve sorgulanması işlemlerini sağlar.
 * Referans: Room Database Data Access Object (DAO) pattern
 */
@Dao
interface TabDao {
    @Insert
    suspend fun insertTab(tab: Tab): Long

    @Update
    suspend fun updateTab(tab: Tab)

    @Delete
    suspend fun deleteTab(tab: Tab)

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAllTabs(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    fun getActiveTab(): Flow<Tab?>

    @Query("UPDATE tabs SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActiveTab()

    @Query("UPDATE tabs SET isActive = 1 WHERE id = :tabId")
    suspend fun setActiveTab(tabId: Long)

    @Query("SELECT * FROM tabs WHERE id = :tabId")
    suspend fun getTabById(tabId: Long): Tab?

    @Query("UPDATE tabs SET position = position - 1 WHERE position > :position")
    suspend fun shiftTabPositionsAfterDelete(position: Int)

    @Query("SELECT COUNT(*) FROM tabs")
    suspend fun getTabCount(): Int

    @Query("SELECT MAX(position) FROM tabs")
    suspend fun getMaxPosition(): Int?

    @Transaction
    suspend fun deleteTabAndUpdatePositions(tab: Tab) {
        deleteTab(tab)
        shiftTabPositionsAfterDelete(tab.position)
    }
    
    @Query("DELETE FROM tabs")
    suspend fun deleteAllTabs()
}