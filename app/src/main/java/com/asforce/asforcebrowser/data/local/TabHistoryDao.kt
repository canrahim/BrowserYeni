package com.asforce.asforcebrowser.data.local

import androidx.room.*
import com.asforce.asforcebrowser.data.model.TabHistory
import kotlinx.coroutines.flow.Flow

/**
 * TabHistoryDao - TabHistory Entity'si için veri erişim nesnesi
 * 
 * Her bir sekmenin gezinme geçmişini yönetmek için gerekli metodları içerir.
 * Referans: Room Database Data Access Object (DAO) pattern
 */
@Dao
interface TabHistoryDao {
    @Insert
    suspend fun insertHistory(history: TabHistory): Long

    @Update
    suspend fun updateHistory(history: TabHistory)

    @Delete
    suspend fun deleteHistory(history: TabHistory)

    @Query("SELECT * FROM tab_history WHERE tabId = :tabId ORDER BY position ASC")
    fun getTabHistory(tabId: Long): Flow<List<TabHistory>>

    @Query("SELECT * FROM tab_history WHERE tabId = :tabId AND position = :position LIMIT 1")
    suspend fun getHistoryAtPosition(tabId: Long, position: Int): TabHistory?

    @Query("SELECT MAX(position) FROM tab_history WHERE tabId = :tabId")
    suspend fun getMaxPosition(tabId: Long): Int?

    @Query("SELECT COUNT(*) FROM tab_history WHERE tabId = :tabId")
    suspend fun getHistoryCount(tabId: Long): Int

    @Query("DELETE FROM tab_history WHERE tabId = :tabId AND position > :position")
    suspend fun deleteHistoryAfterPosition(tabId: Long, position: Int)

    @Query("DELETE FROM tab_history WHERE tabId = :tabId")
    suspend fun deleteAllHistoryForTab(tabId: Long)
}