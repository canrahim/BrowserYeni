package com.asforce.asforcebrowser.presentation.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.databinding.ItemTabBinding
import com.asforce.asforcebrowser.util.FaviconManager
import com.bumptech.glide.Glide
import java.io.File

/**
 * TabAdapter - Sekme listesi için RecyclerView adapter'ı
 * 
 * Sekmelerin yatay listesini yönetir ve sekme seçimi, kapatma gibi işlemleri işler.
 * Referans: RecyclerView ve Custom Adapter kullanımı
 */
class TabAdapter(
    private val onTabSelected: (Tab) -> Unit,
    private val onTabClosed: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    private val tabs = mutableListOf<Tab>()
    private var activeTabId: Long = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab, tab.id == activeTabId)
    }

    override fun getItemCount(): Int = tabs.size

    fun updateTabs(newTabs: List<Tab>) {
        val diffCallback = TabDiffCallback(tabs, newTabs)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        tabs.clear()
        tabs.addAll(newTabs)
        
        diffResult.dispatchUpdatesTo(this)
        
        // Aktif sekmeyi belirle
        val activeTab = newTabs.find { it.isActive }
        activeTab?.let { this.activeTabId = it.id }
    }

    inner class TabViewHolder(
        private val binding: ItemTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: Tab, isActive: Boolean) {
            binding.apply {
                // Sekme başlığını ayarla
                tabTitle.text = tab.title.ifEmpty { "Yeni Sekme" }
                
                android.util.Log.d("TabAdapter", "Sekme için favori ikonu yükleniyor: TabID=${tab.id}, FaviconURL=${tab.faviconUrl}")
                
            // Favicon'u yükle
            if (tab.faviconUrl != null && tab.faviconUrl!!.isNotEmpty()) {
                try {
                    // Favicon dosyasının tam yolu
                    val faviconFile = File(root.context.filesDir, tab.faviconUrl)
                    
                    if (faviconFile.exists() && faviconFile.length() > 0) {
                        android.util.Log.d("TabAdapter", "Favicon dosyası mevcut: ${faviconFile.absolutePath}, boyut=${faviconFile.length()}")
                        
                        // Glide ayarlarını optimize edelim
                        Glide.with(root.context.applicationContext) // ApplicationContext kullan - memory leak önlemek için
                            .load(faviconFile)
                            .skipMemoryCache(false)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .centerCrop()
                            .override(32, 32)
                            .placeholder(R.drawable.ic_globe)
                            .error(R.drawable.ic_globe)
                            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                                override fun onResourceReady(
                                    resource: android.graphics.drawable.Drawable,
                                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                                ) {
                                    // Kaynak hazır olduğunda görseli ayarla ve filtre temizle
                                    favicon.setImageDrawable(resource)
                                    favicon.clearColorFilter() // Filtre temizleme
                                    android.util.Log.d("TabAdapter", "Favicon başarıyla uygulandı: TabID=${tab.id}")
                                }
                                
                                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                    // Yükleme iptal edildiğinde varsayılan simgeyi göster
                                    favicon.setImageResource(R.drawable.ic_globe)
                                    setIconColorFilter(isActive)
                                }
                                
                                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                    // Yükleme başarısız olduğunda varsayılan simgeyi göster
                                    favicon.setImageResource(R.drawable.ic_globe)
                                    setIconColorFilter(isActive)
                                    android.util.Log.e("TabAdapter", "Favicon yükleme başarısız: TabID=${tab.id}")
                                    
                                    // Google'un favicon servisini dene
                                    tryLoadFromGoogle(tab, isActive)
                                }
                            })
                    } else {
                        android.util.Log.d("TabAdapter", "Favicon dosyası bulunamadı veya boş: ${faviconFile.absolutePath}")
                        // Dosya yoksa varsayılan simgeyi göster
                        favicon.setImageResource(R.drawable.ic_globe)
                        setIconColorFilter(isActive)
                        
                        // Google'un favicon servisini dene
                        tryLoadFromGoogle(tab, isActive)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TabAdapter", "Favicon yükleme hatası: ${e.message}")
                    // Hata durumunda varsayılan simgeyi göster
                    favicon.setImageResource(R.drawable.ic_globe)
                    setIconColorFilter(isActive)
                    
                    // Google'un favicon servisini dene
                    tryLoadFromGoogle(tab, isActive)
                }
            } else {
                android.util.Log.d("TabAdapter", "Sekmenin favicon URL'i yok: TabID=${tab.id}")
                // Favicon yoksa varsayılan simgeyi göster
                favicon.setImageResource(R.drawable.ic_globe)
                setIconColorFilter(isActive)
                
                // URL mevcut ise Google'un favicon servisini dene
                if (tab.url.isNotEmpty()) {
                    tryLoadFromGoogle(tab, isActive)
                }
            }
                
                // Aktif sekme durumunu ayarla
                root.isSelected = isActive
                
                // Aktif sekme için metin ve icon renklerini ayarla
                if (isActive) {
                    tabTitle.setTextColor(root.context.getColor(R.color.tabTextActive))
                    closeButton.setColorFilter(root.context.getColor(R.color.iconTintActive))
                } else {
                    tabTitle.setTextColor(root.context.getColor(R.color.tabTextInactive))
                    closeButton.setColorFilter(root.context.getColor(R.color.iconTint))
                }
                
                // Tıklama işleyicileri
                root.setOnClickListener {
                    onTabSelected(tab)
                }
                
                closeButton.setOnClickListener {
                    onTabClosed(tab)
                }
            }
        }
        
        /**
         * Favicon için renk filtresini ayarlar
         */
        private fun setIconColorFilter(isActive: Boolean) {
            if (isActive) {
                binding.favicon.setColorFilter(binding.root.context.getColor(R.color.iconTintActive))
            } else {
                binding.favicon.setColorFilter(binding.root.context.getColor(R.color.iconTint))
            }
        }
        
        /**
         * Google'un favicon servisinden favicon yüklemeyi dener
         */
        private fun tryLoadFromGoogle(tab: Tab, isActive: Boolean) {
            if (tab.url.isNotEmpty()) {
                // Web sitesinin ana alan adını çıkar
                val domain = try {
                    val uri = android.net.Uri.parse(tab.url)
                    uri.host ?: ""
                } catch (e: Exception) {
                    ""
                }
                
                if (domain.isNotEmpty()) {
                    // Google'un favicon servisini kullan
                    val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                    
                    // Favicon'u Glide ile indir
                    try {
                        Glide.with(binding.root.context.applicationContext)
                            .load(faviconUrl)
                            .skipMemoryCache(false)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_globe)
                            .error(R.drawable.ic_globe)
                            .override(32, 32)
                            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                                override fun onResourceReady(
                                    resource: android.graphics.drawable.Drawable,
                                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                                ) {
                                    binding.favicon.setImageDrawable(resource)
                                    binding.favicon.clearColorFilter()
                                    android.util.Log.d("TabAdapter", "Google Favicon başarıyla yüklendi: TabID=${tab.id}, Domain=$domain")
                                }
                                
                                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                    binding.favicon.setImageResource(R.drawable.ic_globe)
                                    setIconColorFilter(isActive)
                                }
                                
                                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                    binding.favicon.setImageResource(R.drawable.ic_globe)
                                    setIconColorFilter(isActive)
                                    android.util.Log.e("TabAdapter", "Google Favicon yükleme başarısız: TabID=${tab.id}, Domain=$domain")
                                }
                            })
                    } catch (e: Exception) {
                        android.util.Log.e("TabAdapter", "Google Favicon yükleme hatası: ${e.message}")
                        binding.favicon.setImageResource(R.drawable.ic_globe)
                        setIconColorFilter(isActive)
                    }
                    
                    android.util.Log.d("TabAdapter", "Google Favicon servisi kullanılıyor: $faviconUrl")
                }
            }
        }
    }

    /**
     * TabDiffCallback - Sekme listesi değişimlerini verimli şekilde işlemek için
     */
    private class TabDiffCallback(
        private val oldList: List<Tab>,
        private val newList: List<Tab>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            return oldItem.title == newItem.title &&
                   oldItem.url == newItem.url &&
                   oldItem.isActive == newItem.isActive &&
                   oldItem.faviconUrl == newItem.faviconUrl
        }
    }
}