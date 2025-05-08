package com.asforce.asforcebrowser.presentation.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.databinding.ItemTabBinding

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
                
                // Favicon ayarla (gerçek uygulamada favicon'u yükleyebilirsiniz)
                favicon.setImageResource(R.drawable.ic_add)
                
                // Aktif sekmeyi vurgula
                root.isSelected = isActive
                
                // Tıklama işleyicileri
                root.setOnClickListener {
                    onTabSelected(tab)
                }
                
                closeButton.setOnClickListener {
                    onTabClosed(tab)
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