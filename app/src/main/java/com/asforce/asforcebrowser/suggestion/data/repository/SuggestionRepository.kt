package com.asforce.asforcebrowser.suggestion.data.repository

import com.asforce.asforcebrowser.suggestion.data.local.SuggestionDao
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Öneri verileri için Repository sınıfı
 * 
 * Veritabanı işlemlerini yönetir ve iş mantığını veri katmanından soyutlar.
 * Hilt ile bağımlılık enjeksiyonu kullanılarak oluşturulmuştur.
 */
@Singleton
class SuggestionRepository @Inject constructor(
    private val suggestionDao: SuggestionDao
) {
    /**
     * Yeni öneri ekler veya günceller, ve kullanım sayacını artırır
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Öneri değeri
     * @param fieldType Alan tipi
     * @param source Öneri kaynağı
     * @param urlPattern URL deseni (opsiyonel)
     * @return İşlem sonucu
     */
    suspend fun addOrUpdateSuggestion(
        fieldIdentifier: String,
        value: String,
        fieldType: String = "text",
        source: String = "USER_INPUT",
        urlPattern: String? = null
    ): Long = withContext(Dispatchers.IO) {
        // Önce önerinin var olup olmadığını kontrol et
        val existingSuggestion = suggestionDao.getSuggestion(fieldIdentifier, value)
        
        if (existingSuggestion != null) {
            // Varsa kullanım sayacını artır ve güncelle
            suggestionDao.incrementUsageCount(existingSuggestion.id)
            existingSuggestion.id
        } else {
            // Yoksa yeni öneri ekle
            val newSuggestion = SuggestionEntity(
                fieldIdentifier = fieldIdentifier,
                value = value,
                lastUsedTimestamp = System.currentTimeMillis(),
                usageCount = 1,
                fieldType = fieldType,
                source = source,
                urlPattern = urlPattern
            )
            suggestionDao.insertOrUpdateSuggestion(newSuggestion)
        }
    }
    
    /**
     * Belirli bir alan için önerileri getirir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param limit Maksimum sonuç sayısı
     * @return Önerilerin Flow listesi
     */
    fun getSuggestionsForField(fieldIdentifier: String, limit: Int = 15): Flow<List<SuggestionEntity>> {
        return suggestionDao.getSuggestionsForField(fieldIdentifier, limit)
    }
    
    /**
     * Belirli bir alan ve URL deseni için önerileri getirir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param urlPattern URL deseni
     * @param limit Maksimum sonuç sayısı
     * @return Önerilerin Flow listesi
     */
    fun getSuggestionsForFieldAndUrl(
        fieldIdentifier: String, 
        urlPattern: String?, 
        limit: Int = 15
    ): Flow<List<SuggestionEntity>> {
        return suggestionDao.getSuggestionsForFieldAndUrl(fieldIdentifier, urlPattern, limit)
    }
    
    /**
     * Belirli bir öneriyi siler
     * 
     * @param id Silinecek önerinin id'si
     */
    suspend fun deleteSuggestion(id: Long) = withContext(Dispatchers.IO) {
        suggestionDao.deleteSuggestion(id)
    }
    
    /**
     * Belirli bir alan ve değere sahip öneriyi siler
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Silinecek değer
     */
    suspend fun deleteSuggestionByValue(fieldIdentifier: String, value: String) = withContext(Dispatchers.IO) {
        suggestionDao.deleteSuggestionByValue(fieldIdentifier, value)
    }
    
    /**
     * Belirli bir alan için tüm önerileri siler
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     */
    suspend fun clearSuggestionsForField(fieldIdentifier: String) = withContext(Dispatchers.IO) {
        suggestionDao.deleteSuggestionsForField(fieldIdentifier)
    }
    
    /**
     * En sık kullanılan önerileri getirir
     * 
     * @param limit Maksimum sonuç sayısı
     * @return Önerilerin Flow listesi
     */
    fun getMostUsedSuggestions(limit: Int = 50): Flow<List<SuggestionEntity>> {
        return suggestionDao.getMostUsedSuggestions(limit)
    }
}