package com.asforce.asforcebrowser.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.domain.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainViewModel - Ana aktivite için ViewModel
 * 
 * Tarayıcının genel durumunu ve sekme yönetimini kontrol eder.
 * Referans: Android ViewModel ve MVVM mimarisi
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tabRepository: TabRepository
) : ViewModel() {

    // Tüm sekmeleri tutan akış
    val tabs = tabRepository.getAllTabs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Aktif sekme
    val activeTab = tabRepository.getActiveTab()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // URL giriş alanının değeri
    private val _addressBarText = MutableStateFlow("")
    val addressBarText: StateFlow<String> = _addressBarText

    // Yükleme durumu
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        // Başlangıçta hiç sekme yoksa, bir tane açılış sekmesi oluştur
        viewModelScope.launch {
            if (tabRepository.getTabCount() == 0) {
                createNewTab("https://www.google.com")
            }
        }
    }

    /**
     * Yeni sekme oluşturur
     */
    fun createNewTab(url: String) {
        viewModelScope.launch {
            tabRepository.addTab("Yeni Sekme", url)
        }
    }

    /**
     * Aktif sekmeyi değiştirir
     */
    fun setActiveTab(tabId: Long) {
        viewModelScope.launch {
            tabRepository.setActiveTab(tabId)
        }
    }

    /**
     * Bir sekmeyi kapatır
     */
    fun closeTab(tab: Tab) {
        viewModelScope.launch {
            // Öncelikle sekmeyi sil
            tabRepository.deleteTab(tab)
            
            // Eğer kapatılan sekme aktifse ve başka sekmeler varsa, bunlardan birini aktif yap
            if (tab.isActive) {
                val remainingTabs = tabRepository.getAllTabs().first()
                if (remainingTabs.isNotEmpty()) {
                    tabRepository.setActiveTab(remainingTabs.first().id)
                } else {
                    // Hiç sekme kalmadıysa yeni bir tane aç
                    createNewTab("https://www.google.com")
                }
            }
        }
    }

    /**
     * Sekmelerin pozisyonlarını günceller (sürükle-bırak sonrası)
     */
    fun updateTabPositions(tabs: List<Tab>) {
        viewModelScope.launch {
            tabRepository.updateTabPositions(tabs)
        }
    }

    /**
     * URL değişimini takip eder ve adres çubuğunu günceller
     */
    fun updateAddressBar(url: String) {
        _addressBarText.value = url
    }

    /**
     * Yükleme durumunu günceller
     */
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
}