package com.asforce.asforcebrowser.suggestion.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Öneri verileri için Data Access Object
 * 
 * Veritabanı işlemlerini yönetir: ekleme, güncelleme, silme ve sorgulama.
 */
@Dao
interface SuggestionDao {

    /**
     * Yeni öneri ekler veya mevcut öneriyi günceller
     * OnConflictStrategy.REPLACE ile aynı alan için aynı değer eklendiğinde güncelleme yapılır
     * 
     * @param suggestion Eklenecek öneri
     * @return Eklenen veya güncellenen kaydın id'si
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSuggestion(suggestion: SuggestionEntity): Long
    
    /**
     * Belirli bir alan için tüm önerileri kullanım sayısı ve son kullanım tarihine göre sıralı getirir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     * @return Önerilerin Flow listesi
     */
    @Query("""
        SELECT * FROM suggestions 
        WHERE fieldIdentifier = :fieldIdentifier 
        ORDER BY usageCount DESC, lastUsedTimestamp DESC 
        LIMIT :limit
    """)
    fun getSuggestionsForField(fieldIdentifier: String, limit: Int = 15): Flow<List<SuggestionEntity>>
    
    /**
     * Belirli bir alan için ve belirli bir URL deseni için önerileri getirir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param urlPattern URL deseni (null değilse eşleştirilir)
     * @return Önerilerin Flow listesi
     */
    @Query("""
        SELECT * FROM suggestions 
        WHERE fieldIdentifier = :fieldIdentifier
        AND (urlPattern IS NULL OR urlPattern = :urlPattern)
        ORDER BY usageCount DESC, lastUsedTimestamp DESC 
        LIMIT :limit
    """)
    fun getSuggestionsForFieldAndUrl(
        fieldIdentifier: String, 
        urlPattern: String?, 
        limit: Int = 15
    ): Flow<List<SuggestionEntity>>
    
    /**
     * Belirli bir alanda öneri olup olmadığını kontrol eder
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Kontrol edilecek değer
     * @return Öneri varsa true, yoksa false
     */
    @Query("""
        SELECT EXISTS (
            SELECT 1 FROM suggestions 
            WHERE fieldIdentifier = :fieldIdentifier 
            AND value = :value
        )
    """)
    suspend fun suggestionExists(fieldIdentifier: String, value: String): Boolean
    
    /**
     * Belirli bir alan için belirli bir değere sahip öneriyi getirir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Öneri değeri
     * @return Eşleşen öneri veya null
     */
    @Query("""
        SELECT * FROM suggestions 
        WHERE fieldIdentifier = :fieldIdentifier 
        AND value = :value 
        LIMIT 1
    """)
    suspend fun getSuggestion(fieldIdentifier: String, value: String): SuggestionEntity?
    
    /**
     * Belirli bir öneriyi siler
     * 
     * @param id Silinecek önerinin id'si
     */
    @Query("DELETE FROM suggestions WHERE id = :id")
    suspend fun deleteSuggestion(id: Long)
    
    /**
     * Belirli bir alan için tüm önerileri siler
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     */
    @Query("DELETE FROM suggestions WHERE fieldIdentifier = :fieldIdentifier")
    suspend fun deleteSuggestionsForField(fieldIdentifier: String)
    
    /**
     * Belirli bir değere sahip öneriyi alan tanımlayıcısına göre siler
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Silinecek değer
     */
    @Query("""
        DELETE FROM suggestions 
        WHERE fieldIdentifier = :fieldIdentifier 
        AND value = :value
    """)
    suspend fun deleteSuggestionByValue(fieldIdentifier: String, value: String)
    
    /**
     * Öneri kullanım sayacını artırır ve son kullanım tarihini günceller
     * 
     * @param id Güncellenecek önerinin id'si
     * @param timestamp Yeni zaman damgası (null ise mevcut zaman kullanılır)
     */
    @Query("""
        UPDATE suggestions 
        SET usageCount = usageCount + 1, 
            lastUsedTimestamp = :timestamp 
        WHERE id = :id
    """)
    suspend fun incrementUsageCount(id: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Tüm önerileri getirir
     * 
     * @return Tüm önerilerin listesi
     */
    @Query("SELECT * FROM suggestions ORDER BY lastUsedTimestamp DESC")
    fun getAllSuggestions(): Flow<List<SuggestionEntity>>
    
    /**
     * En sık kullanılan önerileri getirir
     * 
     * @param limit Maksimum sonuç sayısı
     * @return Sık kullanılan önerilerin listesi
     */
    @Query("""
        SELECT * FROM suggestions 
        ORDER BY usageCount DESC 
        LIMIT :limit
    """)
    fun getMostUsedSuggestions(limit: Int = 50): Flow<List<SuggestionEntity>>
}
