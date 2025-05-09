package com.asforce.asforcebrowser.util.viewpager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.asforce.asforcebrowser.presentation.browser.WebViewFragment

/**
 * FragmentCache - Fragment örneğini korumak için yardımcı sınıf
 *
 * ViewPager2'nin fragment yönetimini geçersiz kılarak fragmentleri korur.
 * Böylece sekmeler arası geçişte fragmentlar yeniden oluşturulmaz.
 */
object FragmentCache {
    private val fragmentsMap = mutableMapOf<Long, WebViewFragment>()
    private val fragmentStates = mutableMapOf<Long, Fragment.SavedState?>()
    private val fragmentUrls = mutableMapOf<Long, String>()

    /**
     * Verilen fragment ID için varsa fragment örneğini döndürür, yoksa yeni oluşturur
     */
    @Synchronized
    fun getOrCreateFragment(id: Long, url: String): WebViewFragment {
        // URL'i kaydet
        fragmentUrls[id] = url

        val existingFragment = fragmentsMap[id]
        return if (existingFragment != null) {
            existingFragment.apply {
                // Fragment'ın durumunu doğrula ve gerekirse güncelle
                if (!isAdded && !isDetached) {
                    // Fragment eklenmemişse veya ayrılmamışsa, durumunu yeniden yükle
                    fragmentStates[id]?.let { _ ->
                        // Kaydedilmiş durum var ise yüklenebilir
                    }
                }
            }
        } else {
            val newFragment = WebViewFragment.newInstance(id, url)
            fragmentsMap[id] = newFragment
            newFragment
        }
    }

    /**
     * Tüm fragmentları temizler
     */
    fun clearFragments() {
        fragmentsMap.clear()
        fragmentStates.clear()
        fragmentUrls.clear()
    }

    /**
     * Belirli bir fragment'ı kaldırır
     */
    fun removeFragment(id: Long) {
        fragmentsMap.remove(id)
        fragmentStates.remove(id)
        fragmentUrls.remove(id)
    }

    /**
     * Önbelleğe alınmış fragment'ı alır
     */
    fun getFragment(id: Long): WebViewFragment? = fragmentsMap[id]

    /**
     * Fragment durumunu kaydeder
     */
    fun saveFragmentState(id: Long, fragmentManager: FragmentManager) {
        fragmentsMap[id]?.let { fragment ->
            try {
                if (fragment.isAdded) {
                    val state = fragmentManager.saveFragmentInstanceState(fragment)
                    fragmentStates[id] = state
                }
            } catch (e: Exception) {
                // Silent exception handling for production
            }
        }
    }

    /**
     * Tüm fragmentları döndürür
     */
    fun getAllFragments(): Map<Long, WebViewFragment> = fragmentsMap.toMap()

    /**
     * Fragment URL'ini döndürür
     */
    fun getFragmentUrl(id: Long): String = fragmentUrls[id] ?: ""
}