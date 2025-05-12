package com.asforce.asforcebrowser.devicemanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.google.android.material.card.MaterialCardView
import java.util.*

/**
 * RecyclerView için cihaz listesi adaptörü
 * Arama filtreleme ve favori filtreleme özellikleri içerir
 * 
 * @property context Uygulama context'i
 * @property devices Cihaz öğeleri listesi
 * 
 * Referans: Android RecyclerView ve Filtreleme
 * URL: https://developer.android.com/develop/ui/views/layout/recyclerview
 */
class DeviceAdapter(
    private val context: Context,
    private val devices: List<DeviceItem>
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>(), Filterable {

    // Tam cihaz listesi ve filtrelenmiş cihaz listesi
    private val allDevices = ArrayList(devices)
    private val filteredDevices = ArrayList(devices)
    
    // Sadece favorileri göster/gösterme
    private var showOnlyFavorites = false
    
    // Filtreleme özellikleri için
    private var currentSearchText = ""
    
    /**
     * Favori gösterim durumunu değiştirir
     * @param onlyFavorites Sadece favorileri göster
     */
    fun toggleFavorites(onlyFavorites: Boolean) {
        showOnlyFavorites = onlyFavorites
        filter.filter(currentSearchText)
    }
    
    /**
     * Tüm öğeleri seçer veya tüm seçimleri kaldırır
     * @param selectAll True ise tüm öğeleri seç, false ise seçimi kaldır
     */
    fun toggleSelectAll(selectAll: Boolean) {
        for (device in filteredDevices) {
            device.isSelected = selectAll
        }
        notifyDataSetChanged()
    }
    
    /**
     * Seçili cihazların listesini döndürür
     * @return Seçili tüm cihazlar
     */
    fun getSelectedDevices(): List<DeviceItem> {
        return filteredDevices.filter { it.isSelected }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = filteredDevices[position]
        
        // Cihaz adını ayarla
        holder.deviceName.text = device.name
        
        // Seçim durumunu görselleştir
        holder.itemView.isSelected = device.isSelected
        holder.cardView.strokeWidth = if (device.isSelected) 2 else 0
        
        // Favori durumunu görselleştir
        holder.favoriteIcon.setImageResource(
            if (device.isFavorite) R.drawable.ic_favorite_filled 
            else R.drawable.ic_favorite_border
        )
        
        // Öğe tıklama dinleyicisi - seçim durumunu değiştirir
        holder.itemView.setOnClickListener {
            device.isSelected = !device.isSelected
            notifyItemChanged(holder.adapterPosition)
        }
        
        // Favori ikonu tıklama dinleyicisi
        holder.favoriteIcon.setOnClickListener {
            device.isFavorite = !device.isFavorite
            
            // Favori durumunu kalıcı olarak kaydet (DeviceManager'da)
            saveFavoriteState(device.id, device.isFavorite)
            
            // Görünümü güncelle
            notifyItemChanged(holder.adapterPosition)
            
            // Eğer sadece favorileri gösteriyorsak ve favori kaldırıldıysa, listeyi güncelle
            if (showOnlyFavorites && !device.isFavorite) {
                filter.filter(currentSearchText)
            }
        }
    }

    override fun getItemCount(): Int = filteredDevices.size

    /**
     * Favori durumunu kaydet
     * @param deviceId Cihaz ID'si
     * @param isFavorite Favori durumu
     */
    private fun saveFavoriteState(deviceId: String, isFavorite: Boolean) {
        val prefs = context.getSharedPreferences("DeviceFavorites", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(deviceId, isFavorite).apply()
    }

    /**
     * Arama ve favorilere göre filtreleme filtrelemesi
     */
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchText = constraint?.toString()?.toLowerCase(Locale.getDefault()) ?: ""
                currentSearchText = searchText
                
                val filteredList = ArrayList<DeviceItem>()
                
                // Tüm cihazları kontrol et
                for (device in allDevices) {
                    // Favori filtresi
                    if (showOnlyFavorites && !device.isFavorite) {
                        continue
                    }
                    
                    // Metin filtresi
                    if (searchText.isEmpty() || device.displayText.contains(searchText)) {
                        filteredList.add(device)
                    }
                }
                
                // Favorileri başa al
                filteredList.sortWith(compareBy { it.sortOrder })
                
                val results = FilterResults()
                results.values = filteredList
                results.count = filteredList.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredDevices.clear()
                if (results != null && results.count > 0) {
                    filteredDevices.addAll(results.values as List<DeviceItem>)
                }
                notifyDataSetChanged()
            }
        }
    }

    /**
     * ViewHolder - cihaz listesi öğesi görünümü
     */
    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        val cardView: MaterialCardView = itemView.findViewById(R.id.deviceCardView)
    }
}