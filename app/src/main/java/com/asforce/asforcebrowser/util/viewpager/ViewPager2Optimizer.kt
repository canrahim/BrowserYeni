package com.asforce.asforcebrowser.util.viewpager

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
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
            // Fragment'ların tamponlanmasını artır - maxValue kullanarak tüm sekmeleri hafızada tut
            offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
            
            // Sayfa geçişlerini sıfırla - daha hızlı olması için
            setPageTransformer(null)
            
            // Yatay kaydırmayı kapat - bu şekilde manuel sekme değişiminde daha az yenileme olur
            isUserInputEnabled = false
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
            // ViewPager2'nin durumunu kontrol edelim
            val currentPosition = viewPager.currentItem
            
            if (currentPosition == position) {
                Log.d(TAG, "setCurrentTabForceRefresh: Mevcut pozisyon zaten $position, yenileme yapılmayacak")
                return
            }
            
            try {
                // ViewPager2'nin internal state'ini değiştirmeden geçiş yapalım
                // Bu özel yaklaşım, çok önceden yapılmıştı ama recyclerView alanı değişmiş olabilir
                
                // Normal yöntemle geçiş yap, ama isUserInputEnabled false ise refresh olmaz
                viewPager.setCurrentItem(position, false)
                
                // Fragment'lerin durumunu kontrol et
                Log.d(TAG, "setCurrentTabForceRefresh: $currentPosition pozisyonundan $position pozisyonuna standart geçiş yapılıyor")
            } catch (e: Exception) {
                Log.e(TAG, "Standart geçiş metodu hata", e)
                // Herhangi bir sorun olursa, son çare olarak position ayarla
                viewPager.post {
                    viewPager.setCurrentItem(position, false)
                }
            }
            
            // Geçiş tamamlandı mesajı
            Log.d(TAG, "setCurrentTabForceRefresh: Geçiş tamamlandı, pozisyon $position")
        }
    }
    
    /**
     * Fragment'lar arası geçişleri izler ve WebView durumunu kontrol eder
     * 
     * @param viewPager İzlenecek ViewPager2
     * @param fragments Tüm fragmentlar haritasi
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