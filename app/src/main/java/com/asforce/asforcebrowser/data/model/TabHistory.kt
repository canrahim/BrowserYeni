package com.asforce.asforcebrowser.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TabHistory Entity - Her bir sekmenin gezinme geçmişini temsil eder
 * 
 * Her bir sekme için ileri/geri gezinme geçmişini saklar. Bu entity sayesinde
 * kullanıcı sekmelere döndüğünde gezinme geçmişini koruyabiliriz.
 * Referans: Room veritabanı ilişkisel entity tanımları
 */
@Entity(
    tableName = "tab_history",
    foreignKeys = [
        ForeignKey(
            entity = Tab::class,
            parentColumns = ["id"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tabId")]
)
data class TabHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tabId: Long, // Hangi sekmeye ait olduğunu belirtir
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val position: Int // Geçmiş listesindeki sıra (ileri/geri işlemleri için)
)
