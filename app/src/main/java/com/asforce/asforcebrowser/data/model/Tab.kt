package com.asforce.asforcebrowser.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tab Entity - Tarayıcıdaki bir sekmeyi temsil eder
 * 
 * Bu entity, tarayıcı uygulamasındaki sekmeleri ve onların durumlarını saklamak için kullanılır.
 * Referans: Clean Architecture prensipleri ve Room veritabanı entity tanımları
 */
@Entity(tableName = "tabs")
data class Tab(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val position: Int, // Sekmenin sırasını belirler
    val isActive: Boolean = false, // Aktif sekme
    val createdAt: Long = System.currentTimeMillis()
)
