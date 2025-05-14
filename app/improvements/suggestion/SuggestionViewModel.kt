package com.asforce.asforcebrowser.suggestion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import com.asforce.asforcebrowser.suggestion.data.repository.SuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Öneri sistemi için ViewModel
 * 
 * Öneri verilerini yönetir ve depolamanın iş mantığını ele alır.
 */
@HiltViewModel
class SuggestionViewModel @Inject constructor(
    private val repository: SuggestionRepository
) : ViewModel() {
    
    // Mevcut odaklı alan
    private val _currentField = MutableStateFlow<String?>(null)
    val currentField: StateFlow<String?> = _currentField
    
    // Mevcut URL
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl
    
    // Öneri paneli gösterim durumu
    private val _isPanelShowing = MutableStateFlow(false)
    val isPanelShowing: StateFlow<Boolean> = _isPanelShowing
    
    /**
     * Yeni öneri ekler veya günceller
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Öneri değeri
     * @param fieldType Alan tipi
     * @param source Öneri kaynağı
     */
    fun addSuggestion(
        fieldIdentifier: String,
        value: String,
        fieldType: String = "text",
        source: String = "USER_INPUT"
    ) {
        if (value.isBlank()) return
        
        viewModelScope.launch {
            repository.addOrUpdateSuggestion(
                fieldIdentifier = fieldIdentifier,
                value = value,
                fieldType = fieldType,
                source = source,
                urlPattern = extractUrlPattern(_currentUrl.value)
            )
        }
    }
    
    /**
     * Öneri kullanım sayacını artır
     * 
     * @param suggestion Kullanılan öneri
     */
    fun incrementSuggestionUsage(suggestion: SuggestionEntity) {
        viewModelScope.launch {
            repository.addOrUpdateSuggestion(
                fieldIdentifier = suggestion.fieldIdentifier,
                value = suggestion.value,
                fieldType = suggestion.fieldType,
                source = suggestion.source,
                urlPattern = suggestion.urlPattern
            )
        }
    }
    
    /**
     * Öneriyi sil
     * 
     * @param suggestion Silinecek öneri
     */
    fun deleteSuggestion(suggestion: SuggestionEntity) {
        viewModelScope.launch {
            repository.deleteSuggestion(suggestion.id)
        }
    }
    
    /**
     * Belirli bir alan için tüm önerileri sil
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     */
    fun clearSuggestionsForField(fieldIdentifier: String) {
        viewModelScope.launch {
            repository.clearSuggestionsForField(fieldIdentifier)
        }
    }
    
    /**
     * Belirli bir alan için önerileri getir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param limit Maksimum sonuç sayısı
     * @return Önerilerin Flow listesi
     */
    fun getSuggestionsForField(fieldIdentifier: String, limit: Int = 15): Flow<List<SuggestionEntity>> {
        // Mevcut URL'ye göre URL deseni çıkar
        val urlPattern = extractUrlPattern(_currentUrl.value)
        
        // URL desenine göre önerileri getir
        return if (urlPattern != null) {
            repository.getSuggestionsForFieldAndUrl(fieldIdentifier, urlPattern, limit)
        } else {
            repository.getSuggestionsForField(fieldIdentifier, limit)
        }
    }
    
    /**
     * Mevcut URL'yi ayarla
     * 
     * @param url Yeni URL
     */
    fun setCurrentUrl(url: String?) {
        _currentUrl.value = url
    }
    
    /**
     * Mevcut odaklanılan alanı ayarla
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     */
    fun setCurrentField(fieldIdentifier: String?) {
        _currentField.value = fieldIdentifier
    }
    
    /**
     * Öneri panelinin gösterim durumunu ayarla
     * 
     * @param isShowing Panel gösteriliyor mu
     */
    fun setPanelShowing(isShowing: Boolean) {
        _isPanelShowing.value = isShowing
    }
    
    /**
     * URL'den URL desenini çıkar
     * Örnek: https://example.com/form -> example.com
     * 
     * @param url URL
     * @return URL deseni veya null
     */
    private fun extractUrlPattern(url: String?): String? {
        if (url.isNullOrBlank()) return null
        
        return try {
            val uri = java.net.URI(url)
            uri.host ?: null
        } catch (e: Exception) {
            null
        }
    }
}
