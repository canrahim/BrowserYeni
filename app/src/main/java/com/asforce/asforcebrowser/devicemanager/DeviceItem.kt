package com.asforce.asforcebrowser.devicemanager

/**
 * Cihaz listesi öğesini temsil eden model sınıfı
 * 
 * @property id Cihazın benzersiz ID'si
 * @property name Cihazın görünen adı
 * @property isSelected Listede seçili olup olmadığı
 * @property isFavorite Favori olarak işaretlenip işaretlenmediği
 * 
 * Referans: Modern Kotlin veri sınıfı (data class) özellikleri
 * URL: https://kotlinlang.org/docs/data-classes.html
 */
data class DeviceItem(
    val id: String,
    val name: String,
    var isSelected: Boolean = false,
    var isFavorite: Boolean = false
) {
    // Filtreleme için kullanılacak sıra bilgisi
    // Favoriler önce gelmesi için kullanılır
    val sortOrder: Int get() = if (isFavorite) 0 else 1
    
    // İsim ve favori durumuna göre filtreleme için kullanılır
    val displayText: String get() = name.toLowerCase()
}
