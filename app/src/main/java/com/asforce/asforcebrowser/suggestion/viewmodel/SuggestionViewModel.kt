package com.asforce.asforcebrowser.suggestion.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import com.asforce.asforcebrowser.suggestion.data.repository.SuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Öneri sistemi için ViewModel
 * 
 * Öneri verilerini ve kullanıcı arayüzü durumlarını yönetir.
 */
@HiltViewModel
class SuggestionViewModel @Inject constructor(
    private val repository: SuggestionRepository
) : ViewModel() {
    
    // Mevcut odaklanılan alan kimliği
    private val _currentField = MutableStateFlow<String?>(null)
    val currentField: StateFlow<String?> = _currentField
    
    // Mevcut URL
    private val _currentUrl = MutableStateFlow<String?>(null)
    
    // Panel durumu (gösteriliyor mu?)
    private val _isPanelShowing = MutableLiveData(false)
    val isPanelShowing: LiveData<Boolean> = _isPanelShowing
    
    /**
     * Mevcut odaklı alanı ayarlar
     * 
     * @param fieldIdentifier Alan kimliği
     */
    fun setCurrentField(fieldIdentifier: String?) {
        _currentField.value = fieldIdentifier
    }
    
    /**
     * Mevcut URL'yi ayarlar
     * 
     * @param url Sayfa URL'si
     */
    fun setCurrentUrl(url: String) {
        _currentUrl.value = url
    }
    
    /**
     * Panel durumunu ayarlar
     * 
     * @param isShowing Panel gösteriliyor mu?
     */
    fun setPanelShowing(isShowing: Boolean) {
        _isPanelShowing.value = isShowing
    }
    
    /**
     * Yeni öneri ekler veya mevcut öneriyi günceller
     * 
     * @param fieldIdentifier Alan kimliği
     * @param value Değer
     * @param fieldType Alan tipi (varsayılan: text)
     */
    fun addSuggestion(fieldIdentifier: String, value: String, fieldType: String = "text") {
        if (fieldIdentifier.isBlank() || value.isBlank()) {
            return
        }
        
        // Şifre alanları için öneri eklenmesin
        if (fieldType == "password") {
            return
        }
        
        viewModelScope.launch {
            repository.addOrUpdateSuggestion(
                fieldIdentifier = fieldIdentifier,
                value = value,
                fieldType = fieldType,
                source = "USER_INPUT",
                urlPattern = _currentUrl.value
            )
        }
    }
    
    /**
     * Belirli bir alan için önerileri getirir
     * 
     * @param fieldIdentifier Alan kimliği
     * @return Öneriler listesi
     */
    suspend fun getSuggestionsForField(fieldIdentifier: String): List<SuggestionEntity> {
        val urlPattern = _currentUrl.value
        
        return if (urlPattern != null) {
            // İlk olarak URL deseni ile eşleşen önerileri al
            val urlBasedSuggestions = repository.getSuggestionsForFieldAndUrl(
                fieldIdentifier, urlPattern
            ).firstOrNull() ?: emptyList()
            
            // Eğer yeterli öneri yoksa genel önerileri de getir
            if (urlBasedSuggestions.size < 5) {
                val generalSuggestions = repository.getSuggestionsForField(fieldIdentifier).firstOrNull() ?: emptyList()
                
                // URL desenine göre filtrelenmiş önerileri önce göster, sonra diğerlerini ekle
                val combinedList = urlBasedSuggestions.toMutableList()
                
                // Genel önerilerden sadece URL desenine göre filtrelenmiş olanları zaten dahil edildiği için
                // listede olmayan önerileri ekle
                generalSuggestions.forEach { suggestion ->
                    if (!combinedList.any { it.id == suggestion.id }) {
                        combinedList.add(suggestion)
                    }
                }
                
                // Sınırla
                combinedList.take(15)
            } else {
                urlBasedSuggestions
            }
        } else {
            repository.getSuggestionsForField(fieldIdentifier).firstOrNull() ?: emptyList()
        }
    }
    
    /**
     * Öneri kullanım sayacını artırır
     * 
     * @param suggestion Güncellenecek öneri
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
     * Öneriyi siler
     * 
     * @param suggestion Silinecek öneri
     */
    fun deleteSuggestion(suggestion: SuggestionEntity) {
        viewModelScope.launch {
            repository.deleteSuggestion(suggestion.id)
        }
    }
    
    /**
     * Belirli bir alan için tüm önerileri siler
     * 
     * @param fieldIdentifier Alan kimliği
     */
    fun deleteAllSuggestionsForField(fieldIdentifier: String) {
        if (fieldIdentifier.isBlank()) {
            return
        }
        
        viewModelScope.launch {
            try {
                Timber.d("'$fieldIdentifier' alanı için tüm öneriler siliniyor")
                repository.clearSuggestionsForField(fieldIdentifier)
                Timber.d("'$fieldIdentifier' alanı için tüm öneriler silindi")
            } catch (e: Exception) {
                Timber.e(e, "Önerileri silme hatası: ${e.message}")
            }
        }
    }
}