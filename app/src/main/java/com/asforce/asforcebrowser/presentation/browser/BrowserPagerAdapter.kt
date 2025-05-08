package com.asforce.asforcebrowser.presentation.browser

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.asforce.asforcebrowser.data.model.Tab

/**
 * BrowserPagerAdapter - Sekmeler arası geçiş için ViewPager2 adapter'ı
 * 
 * Her sekme için bir WebViewFragment oluşturur ve yönetir.
 * Referans: ViewPager2 ile Fragment yönetimi
 */
class BrowserPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val tabs: MutableList<Tab> = mutableListOf()
) : FragmentStateAdapter(fragmentActivity) {
    
    // İzleme için log tag'i
    private val TAG = "BrowserPagerAdapter"

    // Fragment'lara dışarıdan da erişilebilmesi için public
val fragments = mutableMapOf<Long, WebViewFragment>()
    
    // Fragment'ın yeniden oluşturulmasını kontrol etmek için
    override fun getItemId(position: Int): Long {
        return if (position < tabs.size) tabs[position].id else position.toLong()
    }
    
    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id == itemId }
    }

    override fun getItemCount(): Int {
        val count = tabs.size
        android.util.Log.d(TAG, "getItemCount: Count=$count")
        return count
    }

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        val fragment = WebViewFragment.newInstance(tab.id, tab.url)
        
        // Fragment referansını sakla
        fragments[tab.id] = fragment
        
        android.util.Log.d(TAG, "createFragment: Pozisyon=$position, TabID=${tab.id}, URL=${tab.url}")
        return fragment
    }

    /**
     * Sekme listesini günceller
     */
    fun updateTabs(newTabs: List<Tab>) {
        // Değişimleri izlemek için
        android.util.Log.d(TAG, "updateTabs: Önceki boyut=${tabs.size}, Yeni boyut=${newTabs.size}")
        
        // Sekme ID'lerini değişimden önce ve sonra kaydet
        val oldTabIds = tabs.map { it.id }
        val newTabIds = newTabs.map { it.id }
        
        tabs.clear()
        tabs.addAll(newTabs)
        
        // ViewPager2'yi yenile
        notifyDataSetChanged()
        
        android.util.Log.d(TAG, "updateTabs: Eski ID'ler=$oldTabIds, Yeni ID'ler=$newTabIds")
    }

    /**
     * ID'ye göre fragment'ı döndürür
     */
    fun getFragmentByTabId(tabId: Long): WebViewFragment? {
        val fragment = fragments[tabId]
        android.util.Log.d(TAG, "getFragmentByTabId: ID=$tabId, Fragment ${if (fragment != null) "bulundu" else "bulunamadı"}")
        return fragment
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
        val position = tabs.indexOfFirst { it.id == tabId }
        android.util.Log.d(TAG, "getPositionForTabId: ID=$tabId, Pozisyon=$position")
        return position
    }
}