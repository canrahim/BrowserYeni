package com.asforce.asforcebrowser.presentation.browser

import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.databinding.ItemTabBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TabAdapter - Sekme listesi için RecyclerView adapter'ı
 * 
 * Referans: Android RecyclerView Adapter Guide
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * Sekmelerin yatay listesini yönetir ve sekme seçimi, kapatma gibi işlemleri işler.
 * 
 * Düzelti: Agresif favicon yükleme stratejisi
 */
class TabAdapter(
    private val onTabSelected: (Tab) -> Unit,
    private val onTabClosed: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    private val tabs = mutableListOf<Tab>()
    var activeTabId: Long = -1
    private val requestOptions = RequestOptions()
        .skipMemoryCache(false)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .centerCrop()
        .override(48, 48) // Daha büyük boyut için
        .placeholder(R.drawable.ic_globe)
        .error(R.drawable.ic_globe)

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

    /**
     * Sekme güncellemelerini daha güvenilir hale getirdik
     */
    fun updateTabs(newTabs: List<Tab>) {
        val diffCallback = TabDiffCallback(tabs, newTabs)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        // Önce aktif sekmeyi belirle
        val activeTab = newTabs.find { it.isActive }
        activeTab?.let { this.activeTabId = it.id }

        tabs.clear()
        tabs.addAll(newTabs)

        diffResult.dispatchUpdatesTo(this)

        // UI güncellemesini zorla
        notifyDataSetChanged()
    }

    inner class TabViewHolder(
        private val binding: ItemTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Sekme seçili/aktif durumunu daha güvenilir şekilde ayarlıyoruz
         */
        fun bind(tab: Tab, isActive: Boolean) {
            binding.apply {
                // Sekme başlığını ayarla
                tabTitle.text = tab.title.ifEmpty { "Yeni Sekme" }

                // Aktif sekme durumunu hem root view hem de selected state ile ayarla
                root.isSelected = isActive
                
                // Arka plan rengini direkt ayarla
                root.background = ContextCompat.getDrawable(root.context, R.drawable.tab_background)
                
                // Sekme durumu değiştikten hemen sonra arka planı yenile
                root.post {
                    root.refreshDrawableState()
                }

                // Aktif sekme için metin ve icon renklerini ayarla
                if (isActive) {
                    tabTitle.setTextColor(ContextCompat.getColor(root.context, R.color.tabTextActive))
                    closeButton.setColorFilter(ContextCompat.getColor(root.context, R.color.iconTintActive))
                } else {
                    tabTitle.setTextColor(ContextCompat.getColor(root.context, R.color.tabTextInactive))
                    closeButton.setColorFilter(ContextCompat.getColor(root.context, R.color.iconTint))
                }

                // Favicon'u agresif olarak yükle
                loadFaviconAggressively(tab, isActive)

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
         * Agresif favicon yükleme stratejisi
         * 1. Önce Google API'yi dene
         * 2. Sonra savedlı favicon'u kontrol et
         * 3. FaviconManager'dan indir
         * 4. Son çare: varsayılan icon
         */
        private fun loadFaviconAggressively(tab: Tab, isActive: Boolean) {
            // Her zaman Google favicon API'yi önce dene
            if (tab.url.isNotEmpty()) {
                tryLoadFromGoogleDirectly(tab, isActive)
            } else {
                // URL yoksa varsayılan ikonu göster
                binding.favicon.setImageResource(R.drawable.ic_globe)
                setIconColorFilter(isActive)
            }
        }

        /**
         * Doğrudan Google Favicon API'sinden favicon yüklemeyi dener
         */
        private fun tryLoadFromGoogleDirectly(tab: Tab, isActive: Boolean) {
            if (tab.url.isEmpty()) return

            // Web sitesinin domain'ini çıkar
            val domain = try {
                val uri = android.net.Uri.parse(tab.url)
                uri.host ?: ""
            } catch (e: Exception) {
                ""
            }

            if (domain.isNotEmpty()) {
                // 1. Önce Google API'yi dene
                val googleFaviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                
                Glide.with(binding.root.context.applicationContext)
                    .load(googleFaviconUrl)
                    .apply(requestOptions)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            // Google API başarısız olursa, kaydedilmiş favicon'u dene
                            tryLoadSavedFavicon(tab, isActive)
                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            if (resource != null) {
                                // Başarılı, renk filtresini temizle
                                binding.favicon.clearColorFilter()
                                
                                // Ayrıca veritabanına kaydet (arka planda)
                                if (tab.faviconUrl == null || tab.faviconUrl.isEmpty()) {
                                    saveGoogleFaviconToDatabase(tab, domain)
                                }
                                return false // Glide'ın normal işleyişini devam ettir
                            }
                            return true
                        }
                    })
                    .into(binding.favicon)
            } else {
                // Domain bulunamazsa varsayılan ikonu göster
                binding.favicon.setImageResource(R.drawable.ic_globe)
                setIconColorFilter(isActive)
            }
        }

        /**
         * Kaydedilmiş favicon'u yüklemeyi dener
         */
        private fun tryLoadSavedFavicon(tab: Tab, isActive: Boolean) {
            if (tab.faviconUrl != null && tab.faviconUrl!!.isNotEmpty()) {
                try {
                    val faviconFile = File(binding.root.context.filesDir, tab.faviconUrl)
                    if (faviconFile.exists() && faviconFile.length() > 50) {
                        Glide.with(binding.root.context.applicationContext)
                            .load(faviconFile)
                            .apply(requestOptions)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    // Kaydedilmiş favicon başarısız, FaviconManager'ı dene
                                    downloadFaviconInBackground(tab, isActive)
                                    return true
                                }

                                override fun onResourceReady(
                                    resource: Drawable?,
                                    model: Any?,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    if (resource != null) {
                                        binding.favicon.clearColorFilter()
                                        return false
                                    }
                                    return true
                                }
                            })
                            .into(binding.favicon)
                    } else {
                        // Dosya yok veya çok küçük, arka planda indir
                        downloadFaviconInBackground(tab, isActive)
                    }
                } catch (e: Exception) {
                    // Hata durumunda arka planda indir
                    downloadFaviconInBackground(tab, isActive)
                }
            } else {
                // Favicon URL yoksa arka planda indir
                downloadFaviconInBackground(tab, isActive)
            }
        }

        /**
         * Arka planda FaviconManager aracılığıyla favicon indirir
         */
        private fun downloadFaviconInBackground(tab: Tab, isActive: Boolean) {
            // Arka planda favicon indir
            (binding.root.context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val faviconPath = com.asforce.asforcebrowser.util.FaviconManager
                            .downloadAndSaveFavicon(binding.root.context, tab.url, tab.id)
                        
                        withContext(Dispatchers.Main) {
                            if (faviconPath != null) {
                                // Başarılı, yeniden yükle
                                tryLoadSavedFavicon(tab.copy(faviconUrl = faviconPath), isActive)
                            } else {
                                // Son çare: varsayılan icon
                                binding.favicon.setImageResource(R.drawable.ic_globe)
                                setIconColorFilter(isActive)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            binding.favicon.setImageResource(R.drawable.ic_globe)
                            setIconColorFilter(isActive)
                        }
                    }
                }
            }
        }

        /**
         * Google'dan indirilen favicon'u veritabanına kaydeder
         */
        private fun saveGoogleFaviconToDatabase(tab: Tab, domain: String) {
            (binding.root.context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                withContext(Dispatchers.IO) {
                    try {
                        // FaviconManager aracılığıyla Google'dan indirip kaydet
                        com.asforce.asforcebrowser.util.FaviconManager
                            .downloadAndSaveFavicon(binding.root.context, tab.url, tab.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        /**
         * Favicon için renk filtresini ayarlar
         */
        private fun setIconColorFilter(isActive: Boolean) {
            val colorResId = if (isActive) R.color.iconTintActive else R.color.iconTint
            binding.favicon.setColorFilter(ContextCompat.getColor(binding.root.context, colorResId))
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