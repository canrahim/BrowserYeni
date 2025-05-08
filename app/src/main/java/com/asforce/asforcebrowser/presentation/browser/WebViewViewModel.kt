package com.asforce.asforcebrowser.presentation.browser

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.domain.repository.TabHistoryRepository
import com.asforce.asforcebrowser.domain.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WebViewViewModel - WebViewFragment için ViewModel
 * 
 * WebView ile ilgili durumları ve verileri yönetir.
 * Referans: Android ViewModel ve MVVM mimarisi
 */
@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val tabHistoryRepository: TabHistoryRepository
) : ViewModel() {

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl

    /**
     * Mevcut URL'i günceller
     */
    fun updateCurrentUrl(url: String) {
        _currentUrl.value = url
    }

    /**
     * Sekme verisini günceller ve geçmişe ekler
     */
    suspend fun updateTab(tabId: Long, url: String, title: String, favicon: Bitmap?) {
        // Sekme verilerini güncelle
        val tab = tabRepository.getTabById(tabId)
        tab?.let {
            val updatedTab = it.copy(
                title = title,
                url = url,
                faviconUrl = favicon?.let { "favicon_$tabId" } // Gerçek uygulamada favicon'u bir dosyaya kaydedip yolunu saklayabilirsiniz
            )
            tabRepository.updateTab(updatedTab)
            
            // Geçmiş kaydını ekle
            tabHistoryRepository.addHistory(tabId, url, title)
        }
    }
}