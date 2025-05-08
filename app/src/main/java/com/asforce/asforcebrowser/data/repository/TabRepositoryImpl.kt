package com.asforce.asforcebrowser.data.repository

import com.asforce.asforcebrowser.data.local.TabDao
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.domain.repository.TabRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * TabRepository implementasyonu
 * 
 * Sekme repository arayüzünün somut uygulaması. TabDao ile veritabanı işlemlerini gerçekleştirir.
 * Referans: Repository Pattern Implementation (Clean Architecture)
 */
class TabRepositoryImpl @Inject constructor(
    private val tabDao: TabDao
) : TabRepository {

    override suspend fun addTab(title: String, url: String, faviconUrl: String?): Long {
        val maxPosition = tabDao.getMaxPosition() ?: -1
        val newPosition = maxPosition + 1
        
        // Önceki aktif sekmeyi deaktive et
        tabDao.clearActiveTab()
        
        val tab = Tab(
            title = title,
            url = url,
            faviconUrl = faviconUrl,
            position = newPosition,
            isActive = true
        )
        
        return tabDao.insertTab(tab)
    }

    override suspend fun updateTab(tab: Tab) {
        tabDao.updateTab(tab)
    }

    override suspend fun deleteTab(tab: Tab) {
        tabDao.deleteTabAndUpdatePositions(tab)
    }

    override suspend fun getTabById(tabId: Long): Tab? {
        return tabDao.getTabById(tabId)
    }

    override fun getAllTabs(): Flow<List<Tab>> {
        return tabDao.getAllTabs()
    }

    override fun getActiveTab(): Flow<Tab?> {
        return tabDao.getActiveTab()
    }

    override suspend fun setActiveTab(tabId: Long) {
        tabDao.clearActiveTab()
        tabDao.setActiveTab(tabId)
    }

    override suspend fun getTabCount(): Int {
        return tabDao.getTabCount()
    }

    override suspend fun deleteAllTabs() {
        tabDao.deleteAllTabs()
    }
    
    override suspend fun updateTabPositions(tabs: List<Tab>) {
        // Tüm sekmelerin pozisyonlarını aynı anda ve toplu olarak güncelleyelim
        // Bu işlem veritabanı işlem sayısını azaltarak performansı artırır
        
        // Önce pozisyon sırasını doğrulayarak pozisyon değeri ile indeks değeri aynı olmasını sağlayalım
        val updatedTabs = tabs.mapIndexed { index, tab ->
            if (tab.position != index) {
                tab.copy(position = index)
            } else {
                tab
            }
        }
        
        // Sadece değiştirilen sekmeleri güncelle
        for (tab in updatedTabs) {
            tabDao.updateTab(tab)
        }
    }
}