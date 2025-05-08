package com.asforce.asforcebrowser.domain.repository

import com.asforce.asforcebrowser.data.model.TabHistory
import kotlinx.coroutines.flow.Flow

/**
 * TabHistoryRepository interface - Sekme geçmişi işlemleri için repository tanımı
 * 
 * Sekmelerin gezinme geçmişiyle ilgili tüm işlemleri tanımlar.
 * Referans: Repository Pattern (Clean Architecture)
 */
interface TabHistoryRepository {
    suspend fun addHistory(tabId: Long, url: String, title: String): Long
    suspend fun getTabHistory(tabId: Long): Flow<List<TabHistory>>
    suspend fun getHistoryAtPosition(tabId: Long, position: Int): TabHistory?
    suspend fun getCurrentPosition(tabId: Long): Int
    suspend fun deleteHistoryAfterPosition(tabId: Long, position: Int)
    suspend fun canGoBack(tabId: Long): Boolean
    suspend fun canGoForward(tabId: Long): Boolean
    suspend fun clearHistoryForTab(tabId: Long)
}