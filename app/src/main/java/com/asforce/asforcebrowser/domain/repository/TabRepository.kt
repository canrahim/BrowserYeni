package com.asforce.asforcebrowser.domain.repository

import com.asforce.asforcebrowser.data.model.Tab
import kotlinx.coroutines.flow.Flow

/**
 * TabRepository interface - Sekme işlemleri için repository tanımı
 * 
 * Sekmelerle ilgili tüm işlemler için kullanılacak metodları tanımlar.
 * Referans: Repository Pattern (Clean Architecture)
 */
interface TabRepository {
    suspend fun addTab(title: String, url: String, faviconUrl: String? = null): Long
    suspend fun updateTab(tab: Tab)
    suspend fun deleteTab(tab: Tab)
    suspend fun getTabById(tabId: Long): Tab?
    fun getAllTabs(): Flow<List<Tab>>
    fun getActiveTab(): Flow<Tab?>
    suspend fun setActiveTab(tabId: Long)
    suspend fun getTabCount(): Int
    suspend fun deleteAllTabs()
    suspend fun updateTabPositions(tabs: List<Tab>)
}