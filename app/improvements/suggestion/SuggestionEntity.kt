package com.asforce.asforcebrowser.suggestion.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * HTML input alanları için öneri verileri entity sınıfı
 * 
 * Her input alanı için girilen değerleri ve kullanım istatistiklerini saklar.
 * Verimli sorgular için indeksler içerir.
 */
@Entity(
    tableName = "suggestions",
    indices = [
        Index(value = ["fieldIdentifier", "value"], unique = true) // Aynı alanda aynı değerin tekrarını engelle
    ]
)
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Input alanı benzersiz tanımlayıcısı (id veya name)
     * Örnek: "username", "email", "phoneNumber" vb.
     */
    val fieldIdentifier: String,
    
    /**
     * Input alanına girilen değer
     */
    val value: String,
    
    /**
     * Son kullanım tarihi (milisaniye cinsinden timestamp)
     */
    val lastUsedTimestamp: Long,
    
    /**
     * Kullanım sayısı - daha sık kullanılan öneriler daha üst sıralarda gösterilebilir
     */
    val usageCount: Int,
    
    /**
     * İlgili input alanının tip bilgisi
     * Örnek: "text", "email", "number", "password" vb.
     */
    val fieldType: String,
    
    /**
     * Öneri kaynağı. Örneğin: 
     * - USER_INPUT: Kullanıcının kendi girdiği değer
     * - PREFILLED: Form otomatik doldurma 
     */
    val source: String,
    
    /**
     * İsteğe bağlı alan - URL tabanlı gruplama için
     * Belirli URL desenlerine özel öneri grupları oluşturmak için kullanılabilir
     */
    val urlPattern: String? = null
)
