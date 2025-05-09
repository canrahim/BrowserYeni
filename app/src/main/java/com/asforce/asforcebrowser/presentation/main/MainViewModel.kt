package com.asforce.asforcebrowser.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.domain.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainViewModel - Ana kullanıcı arayüzü mantığını yöneten ViewModel
 *
 * Tarayıcı sekme yönetimi, URL adres çubuğu ve sayfa yükleme durumlarını kontrol eder.
 * Sekme oluşturma, geçiş yapma, kapatma ve sıralama işlemlerini yönetir.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tabRepository: TabRepository
) : ViewModel() {

    // Tüm sekmelerin listesini sağlayan StateFlow
    val tabs = tabRepository.getAllTabs()
        .catch { e ->
            // Hata durumunda boş liste dön ve sessizce devam et
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily, // Lazily kullanarak ömrü optimize ediliyor
            initialValue = emptyList()
        )

    // Aktif sekmeyi takip eden StateFlow
    val activeTab = tabRepository.getActiveTab()
        .catch { e ->
            // Hata durumunda null dön ve sessizce devam et
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily, // Lazily kullanarak ömrü optimize ediliyor
            initialValue = null
        )

    // URL giriş alanının değeri
    private val _addressBarText = MutableStateFlow("")
    val addressBarText: StateFlow<String> = _addressBarText

    // Sayfa yükleme durumu
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Aktif işlemleri izlemek için Job nesnesi
    private var activeTabJob: Job? = null

    init {
        // Başlangıçta hiç sekme yoksa, varsayılan açılış sekmesi oluştur
        viewModelScope.launch {
            try {
                if (tabRepository.getTabCount() == 0) {
                    createNewTab(DEFAULT_URL)
                }
            } catch (e: Exception) {
                // Başlatma hatası durumunda varsayılan sekme oluşturmayı dene
                createNewTab(DEFAULT_URL)
            }
        }
    }

    /**
     * Yeni sekme oluşturur ve aktif sekme yapar
     *
     * @param url Açılacak URL
     */
    fun createNewTab(url: String) {
        viewModelScope.launch {
            try {
                tabRepository.addTab(NEW_TAB_TITLE, url)
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * Belirtilen sekmeyi aktif hale getirir
     *
     * @param tabId Aktif yapılacak sekmenin ID'si
     */
    fun setActiveTab(tabId: Long) {
        // Önceki işlemi iptal et ve yeni işlemi başlat
        activeTabJob?.cancel()
        activeTabJob = viewModelScope.launch {
            try {
                tabRepository.setActiveTab(tabId)
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * Bir sekmeyi kapatır
     * Eğer kapatılan sekme aktifse, başka bir sekme aktif yapılır
     * Hiç sekme kalmazsa yeni bir sekme açılır
     *
     * @param tab Kapatılacak sekme
     */
    fun closeTab(tab: Tab) {
        viewModelScope.launch {
            try {
                // Öncelikle sekmeyi sil
                tabRepository.deleteTab(tab)

                // Eğer kapatılan sekme aktifse ve başka sekmeler varsa, bunlardan birini aktif yap
                if (tab.isActive) {
                    val remainingTabs = tabRepository.getAllTabs().first()
                    if (remainingTabs.isNotEmpty()) {
                        tabRepository.setActiveTab(remainingTabs.first().id)
                    } else {
                        // Hiç sekme kalmadıysa yeni bir tane aç
                        createNewTab(DEFAULT_URL)
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * Sekmelerin pozisyonlarını günceller (sürükle-bırak sonrası)
     *
     * @param tabs Yeni sıralamaya göre güncellenmiş sekme listesi
     */
    fun updateTabPositions(tabs: List<Tab>) {
        viewModelScope.launch {
            try {
                tabRepository.updateTabPositions(tabs)
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * URL değişimini takip eder ve adres çubuğunu günceller
     *
     * @param url Güncellenecek URL
     */
    fun updateAddressBar(url: String) {
        _addressBarText.value = url
    }

    /**
     * Sayfa yükleme durumunu günceller
     *
     * @param isLoading Yükleme durumu
     */
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel temizlenirken aktif işi iptal et
        activeTabJob?.cancel()
    }

    companion object {
        private const val DEFAULT_URL = "https://www.google.com"
        private const val NEW_TAB_TITLE = "Yeni Sekme"
    }
}