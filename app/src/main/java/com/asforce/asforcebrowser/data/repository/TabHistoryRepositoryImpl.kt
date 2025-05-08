package com.asforce.asforcebrowser.data.repository

import com.asforce.asforcebrowser.data.local.TabHistoryDao
import com.asforce.asforcebrowser.data.model.TabHistory
import com.asforce.asforcebrowser.domain.repository.TabHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * TabHistoryRepository implementasyonu
 * 
 * Sekme geçmişi repository arayüzünün somut uygulaması. TabHistoryDao ile veritabanı işlemlerini gerçekleştirir.
 * Referans: Repository Pattern Implementation (Clean Architecture)
 */
class TabHistoryRepositoryImpl @Inject constructor(
    private val tabHistoryDao: TabHistoryDao
) : TabHistoryRepository {

    override suspend fun addHistory(tabId: Long, url: String, title: String): Long {
        // Sekme geçmişinden mevcut konumu bul
        val currentPosition = getCurrentPosition(tabId)
        
        // Mevcut konumdan sonraki tüm geçmişi sil
        tabHistoryDao.deleteHistoryAfterPosition(tabId, currentPosition)
        
        // Yeni geçmiş öğesini ekle
        val history = TabHistory(
            tabId = tabId,
            url = url,
            title = title,
            position = currentPosition + 1
        )
        
        return tabHistoryDao.insertHistory(history)
    }

    override suspend fun getTabHistory(tabId: Long): Flow<List<TabHistory>> {
        return tabHistoryDao.getTabHistory(tabId)
    }

    override suspend fun getHistoryAtPosition(tabId: Long, position: Int): TabHistory? {
        return tabHistoryDao.getHistoryAtPosition(tabId, position)
    }

    override suspend fun getCurrentPosition(tabId: Long): Int {
        return tabHistoryDao.getMaxPosition(tabId) ?: -1
    }

    override suspend fun deleteHistoryAfterPosition(tabId: Long, position: Int) {
        tabHistoryDao.deleteHistoryAfterPosition(tabId, position)
    }

    override suspend fun canGoBack(tabId: Long): Boolean {
        val currentPosition = getCurrentPosition(tabId)
        return currentPosition > 0
    }

    override suspend fun canGoForward(tabId: Long): Boolean {
        val currentPosition = getCurrentPosition(tabId)
        val totalHistoryCount = tabHistoryDao.getHistoryCount(tabId)
        return currentPosition < totalHistoryCount - 1
    }

    override suspend fun clearHistoryForTab(tabId: Long) {
        tabHistoryDao.deleteAllHistoryForTab(tabId)
    }
}