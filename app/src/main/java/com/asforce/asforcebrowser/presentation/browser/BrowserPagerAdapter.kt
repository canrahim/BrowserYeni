package com.asforce.asforcebrowser.presentation.browser

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.util.viewpager.FragmentCache

/**
 * BrowserPagerAdapter - Sekmeler arası geçiş için ViewPager2 adapter'ı
 *
 * Her sekme için FragmentCache üzerinden bir WebViewFragment yönetir.
 */
class BrowserPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val tabs: MutableList<Tab> = mutableListOf()
) : FragmentStateAdapter(fragmentActivity) {

    // Adapter yeniden oluştuğunda fragmentların yeniden oluşmasını önlemek için bir anahtar kullanıyoruz
    private val uniqueId = System.currentTimeMillis()

    override fun getItemId(position: Int): Long {
        return if (position < tabs.size) tabs[position].id else position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        // Fragment önbellekte varsa her zaman true döndür
        val containsInCache = FragmentCache.getFragment(itemId) != null

        // Tabs listesinde var mı kontrol et
        val containsInTabs = tabs.any { it.id == itemId }

        // Sonucu döndür
        return containsInCache || containsInTabs
    }

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        val tab = if (position < tabs.size) tabs[position] else return Fragment()

        // FragmentCache'den mevcut fragment'ı al veya yeni oluştur
        return FragmentCache.getOrCreateFragment(tab.id, tab.url)
    }

    /**
     * Sekme listesini günceller
     */
    fun updateTabs(newTabs: List<Tab>) {
        // Sekme ID'lerini değişimden önce ve sonra kaydet
        val oldTabIds = tabs.map { it.id }
        val newTabIds = newTabs.map { it.id }

        // Silinen sekmeleri tespit et
        val removedTabIds = oldTabIds.filterNot { it in newTabIds }

        // Silinen sekmelerin fragment kayıtlarını temizle
        removedTabIds.forEach { tabId ->
            FragmentCache.removeFragment(tabId)
        }

        tabs.clear()
        tabs.addAll(newTabs)

        // Sadece gerekli öğelerin güncellenmesini sağla - tüm listeyi yenileme
        if (removedTabIds.isNotEmpty() || oldTabIds.size != newTabIds.size) {
            notifyDataSetChanged()
        }
    }

    /**
     * ID'ye göre fragment'ı döndürür
     */
    fun getFragmentByTabId(tabId: Long): WebViewFragment? {
        return FragmentCache.getFragment(tabId)
    }

    /**
     * Pozisyona göre sekmeyi döndürür
     */
    fun getTabAt(position: Int): Tab? {
        return if (position in tabs.indices) tabs[position] else null
    }

    /**
     * Sekme ID'sine göre pozisyon döndürür
     */
    fun getPositionForTabId(tabId: Long): Int {
        return tabs.indexOfFirst { it.id == tabId }
    }
}