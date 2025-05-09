package com.asforce.asforcebrowser.presentation.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.databinding.ItemTabBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.File

/**
 * TabAdapter - Sekme listesi için RecyclerView adapter'ı
 *
 * Sekmelerin yatay listesini yönetir ve sekme seçimi, kapatma gibi işlemleri işler.
 */
class TabAdapter(
    private val onTabSelected: (Tab) -> Unit,
    private val onTabClosed: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    private val tabs = mutableListOf<Tab>()
    private var activeTabId: Long = -1
    private val requestOptions = RequestOptions()
        .skipMemoryCache(false)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .centerCrop()
        .override(32, 32)
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

                // Favicon'u yükle
                loadFavicon(tab, isActive)

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

        private fun loadFavicon(tab: Tab, isActive: Boolean) {
            // Önce varsayılan simgeyi göster
            binding.favicon.setImageResource(R.drawable.ic_globe)
            setIconColorFilter(isActive)

            // Favicon URL kontrolü
            if (tab.faviconUrl != null && tab.faviconUrl!!.isNotEmpty()) {
                try {
                    // Favicon dosyasının tam yolu
                    val faviconFile = File(binding.root.context.filesDir, tab.faviconUrl)

                    if (faviconFile.exists() && faviconFile.length() > 0) {
                        // Dosya mevcutsa yükle
                        Glide.with(binding.root.context.applicationContext)
                            .load(faviconFile)
                            .apply(requestOptions)
                            .into(binding.favicon)

                        // Renk filtresini temizle
                        binding.favicon.clearColorFilter()
                    } else {
                        // Dosya yoksa Google favicon servisini dene
                        tryLoadFromGoogle(tab, isActive)
                    }
                } catch (e: Exception) {
                    // Hata durumunda Google favicon servisini dene
                    tryLoadFromGoogle(tab, isActive)
                }
            } else if (tab.url.isNotEmpty()) {
                // Favicon URL yoksa ama sayfa URL'i varsa Google servisini dene
                tryLoadFromGoogle(tab, isActive)
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
                            .apply(requestOptions)
                            .into(binding.favicon)

                        // Renk filtresini temizle
                        binding.favicon.clearColorFilter()
                    } catch (e: Exception) {
                        // Varsayılan simgeyi göster
                        binding.favicon.setImageResource(R.drawable.ic_globe)
                        setIconColorFilter(isActive)
                    }
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