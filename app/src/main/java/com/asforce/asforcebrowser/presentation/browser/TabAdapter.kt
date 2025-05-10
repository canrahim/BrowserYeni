package com.asforce.asforcebrowser.presentation.browser

import android.graphics.drawable.Drawable
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
import com.bumptech.glide.request.target.Target
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TabAdapter - İyileştirilmiş sekme adapter'ı
 * 
 * Referans: Android RecyclerView Adapter Guide
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * Önemli İyileştirmeler:
 * 1. Akıllı favicon yükleme stratejisi (404 hatalarını minimize eder)
 * 2. Daha az log spam ile daha sessiz hata yönetimi
 * 3. Önbellek bazlı favicon yönetimi
 * 4. Progressive loading desteği
 */
class TabAdapter(
    private val onTabSelected: (Tab) -> Unit,
    private val onTabClosed: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    private val tabs = mutableListOf<Tab>()
    var activeTabId: Long = -1
    
    // Glide için optimize edilmiş request seçenekleri
    private val requestOptions = RequestOptions()
        .skipMemoryCache(false)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .centerCrop()
        .override(48, 48)
        .placeholder(R.drawable.ic_globe)
        .error(R.drawable.ic_globe)
        // Hata loglarını azalt
        .dontTransform()
        .encodeFormat(Bitmap.CompressFormat.PNG)

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

        // Aktif sekmeyi belirle
        val activeTab = newTabs.find { it.isActive }
        activeTab?.let { this.activeTabId = it.id }

        tabs.clear()
        tabs.addAll(newTabs)

        diffResult.dispatchUpdatesTo(this)
    }

    inner class TabViewHolder(
        private val binding: ItemTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: Tab, isActive: Boolean) {
            binding.apply {
                // Sekme başlığını ayarla
                tabTitle.text = tab.title.ifEmpty { "Yeni Sekme" }

                // Aktif sekme durumunu ayarla
                root.isSelected = isActive
                root.background = ContextCompat.getDrawable(root.context, R.drawable.tab_background)
                root.post { root.refreshDrawableState() }

                // Metin ve icon renklerini ayarla
                if (isActive) {
                    tabTitle.setTextColor(ContextCompat.getColor(root.context, R.color.tabTextActive))
                    closeButton.setColorFilter(ContextCompat.getColor(root.context, R.color.iconTintActive))
                } else {
                    tabTitle.setTextColor(ContextCompat.getColor(root.context, R.color.tabTextInactive))
                    closeButton.setColorFilter(ContextCompat.getColor(root.context, R.color.iconTint))
                }

                // İyileştirilmiş favicon yükleme
                loadFaviconSmartly(tab, isActive)

                // Tıklama işleyicileri
                root.setOnClickListener { onTabSelected(tab) }
                closeButton.setOnClickListener { onTabClosed(tab) }
            }
        }

        /**
         * Akıllı favicon yükleme - 404 hatalarını minimize eder
         * Strateji: Local → FaviconManager → Google (son çare)
         */
        private fun loadFaviconSmartly(tab: Tab, isActive: Boolean) {
            // 1. Önce local faviconu kontrol et
            if (tab.faviconUrl != null && tab.faviconUrl!!.isNotEmpty()) {
                loadFromLocalCache(tab, isActive)
            } else if (tab.url.isNotEmpty()) {
                // 2. Local yoksa FaviconManager'dan yükle
                loadFromFaviconManager(tab, isActive)
            } else {
                // 3. URL yoksa varsayılan icon
                showDefaultIcon(isActive)
            }
        }

        /**
         * Local cache'ten favicon yükler
         */
        private fun loadFromLocalCache(tab: Tab, isActive: Boolean) {
            try {
                val faviconFile = File(binding.root.context.filesDir, tab.faviconUrl!!)
                if (faviconFile.exists() && faviconFile.length() > 50) {
                    Glide.with(binding.root.context.applicationContext)
                        .load(faviconFile)
                        .apply(requestOptions)
                        .listener(createQuietRequestListener { success ->
                            if (!success) {
                                loadFromFaviconManager(tab, isActive)
                            }
                        })
                        .into(binding.favicon)
                } else {
                    // Dosya yoksa FaviconManager'dan yükle
                    loadFromFaviconManager(tab, isActive)
                }
            } catch (e: Exception) {
                loadFromFaviconManager(tab, isActive)
            }
        }

        /**
         * FaviconManager aracılığıyla favicon yükler
         */
        private fun loadFromFaviconManager(tab: Tab, isActive: Boolean) {
            (binding.root.context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val faviconPath = com.asforce.asforcebrowser.util.FaviconManager
                            .downloadAndSaveFavicon(binding.root.context, tab.url, tab.id)
                        
                        withContext(Dispatchers.Main) {
                            if (faviconPath != null) {
                                // Başarılı, local cache'ten yeniden yükle
                                loadFromLocalCache(tab.copy(faviconUrl = faviconPath), isActive)
                            } else {
                                // FaviconManager başarısız, Google'ı dene (ama dikkatli)
                                loadFromGoogleApiCarefully(tab, isActive)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            loadFromGoogleApiCarefully(tab, isActive)
                        }
                    }
                }
            }
        }

        /**
         * Google API'yi dikkatli bir şekilde kullanır (sadece son çare olarak)
         */
        private fun loadFromGoogleApiCarefully(tab: Tab, isActive: Boolean) {
            val domain = extractDomain(tab.url)
            
            if (domain.isNotEmpty() && !isKnownProblemDomain(domain)) {
                val googleFaviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                
                Glide.with(binding.root.context.applicationContext)
                    .load(googleFaviconUrl)
                    .apply(requestOptions)
                    .listener(createQuietRequestListener { _ ->
                        // Başarılı veya başarısız, logları minimize et
                    })
                    .into(binding.favicon)
            } else {
                // Problemli domain veya geçersiz URL, varsayılan icon
                showDefaultIcon(isActive)
            }
        }

        /**
         * Domain'i URL'den çıkarır
         */
        private fun extractDomain(url: String): String {
            return try {
                val uri = android.net.Uri.parse(url)
                uri.host?.replace("www.", "") ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * Problemli olduğu bilinen domain'leri kontrol eder
         */
        private fun isKnownProblemDomain(domain: String): Boolean {
            // Bilinen problemli domain'lar
            val problemDomains = listOf(
                "localhost",
                "127.0.0.1",
                "192.168.",
                "10.0.0.",
                ".test",
                ".local",
                "app.szutest.com.tr" // Log'da görülen problemli domain
            )
            
            return problemDomains.any { domain.contains(it, ignoreCase = true) }
        }

        /**
         * Sessiz request listener oluşturur (log spam'ini önler)
         */
        private fun createQuietRequestListener(onComplete: (Boolean) -> Unit): RequestListener<Drawable> {
            return object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    // Hata loglarını bastır
                    onComplete(false)
                    return false
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
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                    return false
                }
            }
        }

        /**
         * Varsayılan ikonu gösterir
         */
        private fun showDefaultIcon(isActive: Boolean) {
            binding.favicon.setImageResource(R.drawable.ic_globe)
            val colorResId = if (isActive) R.color.iconTintActive else R.color.iconTint
            binding.favicon.setColorFilter(ContextCompat.getColor(binding.root.context, colorResId))
        }
    }

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