package com.asforce.asforcebrowser.util.viewpager

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.asforce.asforcebrowser.presentation.browser.WebViewFragment

/**
 * FragmentCache - Fragment örneğini korumak için yardımcı sınıf
 *
 * ViewPager2'nin fragment yönetimini geçersiz kılarak fragmentleri korur.
 * Böylece sekmeler arası geçişte fragmentlar yeniden oluşturulmaz.
 * 
 * Referans: Fragment Lifecycle yönetimi ve ViewPager2'nin fragment retention stratejileri
 */
object FragmentCache {
    private val fragmentsMap = mutableMapOf<Long, WebViewFragment>()
    private val fragmentStates = mutableMapOf<Long, Fragment.SavedState?>()
    private val fragmentUrls = mutableMapOf<Long, String>()
    private val TAG = "FragmentCache"

    /**
     * Verilen fragment ID için varsa fragment örneğini döndürür, yoksa yeni oluşturur
     */
    @Synchronized
    fun getOrCreateFragment(id: Long, url: String): WebViewFragment {
        android.util.Log.d(TAG, "getOrCreateFragment çağrıldı: ID=$id, URL=$url")
        
        // URL'i kaydet
        fragmentUrls[id] = url
        
        val existingFragment = fragmentsMap[id]
        return if (existingFragment != null) {
            android.util.Log.d(TAG, "Mevcut fragment kullanılıyor: ID=$id")
            existingFragment.apply {
                // Fragment'ın durumunu doğrula ve gerekirse güncelle
                if (!isAdded && !isDetached) {
                    android.util.Log.d(TAG, "Fragment ekli değil, durumu sıfırlanıyor: ID=$id")
                    // Fragment eklenmemişse veya ayrılmamışsa, durumunu yeniden yükle
                    fragmentStates[id]?.let { savedState ->
                        android.util.Log.d(TAG, "Fragment kaydedilmiş durumdan yenileniyor: ID=$id")
                    }
                } else {
                    android.util.Log.d(TAG, "Fragment durumu normal: isAdded=$isAdded, isDetached=$isDetached")
                }
            }
        } else {
            android.util.Log.d(TAG, "Yeni fragment oluşturuluyor: ID=$id, URL=$url")
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
        android.util.Log.d(TAG, "Tüm fragmentlar temizlendi")
    }

    /**
     * Belirli bir fragment'ı kaldırır
     */
    fun removeFragment(id: Long) {
        fragmentsMap.remove(id)
        fragmentStates.remove(id)
        fragmentUrls.remove(id)
        android.util.Log.d(TAG, "Fragment kaldırıldı: ID=$id")
    }

    /**
     * Önbelleğe alınmış fragment'ı alır
     */
    fun getFragment(id: Long): WebViewFragment? {
        return fragmentsMap[id].also {
            android.util.Log.d(TAG, "Fragment sorgulandı: ID=$id, Bulundu=${it != null}")
        }
    }

    /**
     * Fragment durumunu kaydeder
     */
    fun saveFragmentState(id: Long, fragmentManager: androidx.fragment.app.FragmentManager) {
        fragmentsMap[id]?.let { fragment ->
            try {
                if (fragment.isAdded) {
                    val state = fragmentManager.saveFragmentInstanceState(fragment)
                    fragmentStates[id] = state
                    android.util.Log.d(TAG, "Fragment durumu kaydedildi: ID=$id")
                } else {
                    android.util.Log.d(TAG, "Fragment durumu kaydedilemedi çünkü ekli değil: ID=$id")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Fragment durumu kaydedilemedi: ID=$id, Hata: ${e.message}")
            }
        }
    }

    /**
     * Tüm fragmentları döndürür
     */
    fun getAllFragments(): Map<Long, WebViewFragment> {
        return fragmentsMap.toMap()
    }
    
    /**
     * Fragment URL'ini döndürür
     */
    fun getFragmentUrl(id: Long): String {
        return fragmentUrls[id] ?: ""
    }
}