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

    private val fragments = mutableMapOf<Long, WebViewFragment>()

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        val fragment = WebViewFragment.newInstance(tab.id, tab.url)
        
        // Fragment referansını sakla
        fragments[tab.id] = fragment
        
        return fragment
    }

    /**
     * Sekme listesini günceller
     */
    fun updateTabs(newTabs: List<Tab>) {
        tabs.clear()
        tabs.addAll(newTabs)
        notifyDataSetChanged()
    }

    /**
     * ID'ye göre fragment'ı döndürür
     */
    fun getFragmentByTabId(tabId: Long): WebViewFragment? {
        return fragments[tabId]
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