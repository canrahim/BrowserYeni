package com.asforce.asforcebrowser.util.viewpager

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.asforce.asforcebrowser.data.model.Tab
import com.asforce.asforcebrowser.presentation.browser.WebViewFragment
import java.lang.reflect.Field

/**
 * ViewPager2Optimizer - ViewPager2 ile fragment'ları optimize etmek için yardımcı sınıf
 * 
 * ViewPager2'de fragment'lar arası geçişleri ve WebView yönetimini optimize eder.
 * Referans: ViewPager2 implementasyonları ve fragment yönetimi
 */
class ViewPager2Optimizer(
    private val activity: FragmentActivity
) {
    private val TAG = "ViewPager2Optimizer"
    
    /**
     * ViewPager2'yi optimize eder ve WebView'ları daha iyi yönetir
     * 
     * @param viewPager Optimize edilecek ViewPager2
     * @param adapter Kullanılan adapter
     */
    fun optimizeViewPager(viewPager: ViewPager2, adapter: FragmentStateAdapter) {
        Log.d(TAG, "ViewPager2 optimizasyonu başladı")
        
        // Kaydırma hassasiyetini ayarla
        viewPager.apply {
            // Fragment'ların tamponlanmasını artır
            offscreenPageLimit = 2
            
            // Sayfa geçişlerini sıfırla - daha hızlı olması için
            setPageTransformer(null)
        }
        
        // Fragment Değiştirme Animasyonu Disable Et (reflection ile)
        try {
            val recyclerViewField: Field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(viewPager)
            
            val touchSlopField: Field = recyclerView.javaClass.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val touchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, touchSlop * 3) // Kaydırmayı daha az hassas hale getir
            
            Log.d(TAG, "ViewPager2 kaydırma hassasiyeti azaltıldı")
        } catch (e: Exception) {
            Log.e(TAG, "ViewPager2 optimizasyonu başarısız oldu", e)
        }
    }
    
    /**
     * ViewPager2'nin yeni fragment oluşturmasını zorlar
     * 
     * @param viewPager Optimize edilecek ViewPager2
     */
    fun refreshViewPager(viewPager: ViewPager2) {
        viewPager.adapter?.notifyDataSetChanged()
    }
    
    /**
     * ViewPager2'nin geçerli sekmeye geçişi zorla
     * 
     * @param viewPager Optimize edilecek ViewPager2
     * @param position Geçilecek pozisyon
     */
    fun setCurrentTabForceRefresh(viewPager: ViewPager2, position: Int) {
        if (position >= 0 && position < viewPager.adapter?.itemCount ?: 0) {
            // Önce yeni pozisyona git
            viewPager.setCurrentItem(position, false)
            
            // Yeniden yüklenmeye zorla
            viewPager.post {
                viewPager.adapter?.notifyItemChanged(position)
            }
        }
    }
    
    /**
     * Fragment'lar arası geçişleri izler ve WebView durumunu kontrol eder
     * 
     * @param viewPager İzlenecek ViewPager2
     * @param tabId Aktif sekme ID'si
     * @param onPageSelected Sayfa seçildiğinde çağrılacak callback
     */
    fun monitorFragmentSwitching(
        viewPager: ViewPager2,
        fragments: Map<Long, WebViewFragment>,
        tabId: Long,
        onPageSelected: (WebViewFragment) -> Unit
    ) {
        // İlgili fragment'ı bul
        val fragment = fragments[tabId]
        if (fragment != null) {
            Log.d(TAG, "Fragment bulundu, işleniyor: TabID=$tabId")
            onPageSelected(fragment)
        } else {
            Log.e(TAG, "Fragment bulunamadı: TabID=$tabId")
            // Fragment bulunamadıysa, adapter'ı güncelleme ihtiyacı olabilir
            refreshViewPager(viewPager)
        }
    }
}