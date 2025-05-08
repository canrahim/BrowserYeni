package com.asforce.asforcebrowser

import android.app.Application
import android.webkit.WebView
import android.os.Build
import android.view.ViewGroup
import android.util.Log
import com.asforce.asforcebrowser.util.performance.PerformanceOptimizer
import dagger.hilt.android.HiltAndroidApp

/**
 * AsforceBrowserApp - Uygulama sınıfı
 * 
 * Hilt Dependency Injection'ı başlatmak ve uygulama genelinde
 * performans optimizasyonlarını ayarlamak için kullanılır.
 * 
 * Referans: 
 * - Hilt Application Sınıfı
 * - Android WebView Performans Optimizasyonu
 */
@HiltAndroidApp
class AsforceBrowserApp : Application() {

    companion object {
        private const val TAG = "AsforceBrowserApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Uygulama başlangıcında optimizasyonları yap
        initializeOptimizations()
    }
    
    /**
     * Uygulama çapında performans optimizasyonlarını başlatır
     */
    private fun initializeOptimizations() {
        Log.d(TAG, "Uygulama optimizasyonları başlatılıyor")
        
        // WebView optimizasyonları için ön hazırlık
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = Application.getProcessName()
            if (packageName != processName) {
                // WebView çoklu süreç ayarı için - sadece ana işlemde yap
                WebView.setDataDirectorySuffix(processName)
            }
        }
        
        // Debug için Chrome DevTools entegrasyonunu etkinleştir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        // Önbellek ve bellek yönetimi optimize et
        optimizeMemoryUsage()
        
        Log.d(TAG, "Uygulama optimizasyonları tamamlandı")
    }
    
    /**
     * Bellek kullanımı ve önbellek optimizasyonlarını yapar
     */
    private fun optimizeMemoryUsage() {
        // WebView önbellek yönetimi için cache temizliği
        try {
            WebView(applicationContext).apply {
                clearCache(false) // önbelleği temizle ama dosyaları koru
                destroy() // kaynakları serbest bırak
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView önbellek temizleme hatası: ${e.message}")
        }
        
        // Düşük bellek durumlarında önbelleği temizle
        registerComponentCallbacks(LowMemoryHandler(this))
    }
    
    /**
     * Mevcut bir WebView'ı optimize etmek için yardımcı metod
     */
    fun optimizeWebView(webView: WebView) {
        // PerformanceOptimizer sınıfını kullanarak optimizasyonları uygula
        val optimizer = PerformanceOptimizer.getInstance(applicationContext)
        optimizer.optimizeWebView(webView)
    }
    
    /**
     * Düşük bellek durumları için ComponentCallbacks2 implementasyonu
     */
    private class LowMemoryHandler(private val app: Application) : android.content.ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                // Bellek kullanımını azalt
                clearWebViewCache(app)
            }
        }
        
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
            // Konfigürasyon değişikliklerinde bir şey yapma
        }
        
        override fun onLowMemory() {
            // Düşük bellek durumunda WebView önbelleğini temizle
            clearWebViewCache(app)
        }
        
        private fun clearWebViewCache(context: android.content.Context) {
            try {
                // Gizli bir WebView kullanarak önbelleği temizle
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1) // Minimum boyut
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    destroy()
                }
            } catch (e: Exception) {
                Log.e("LowMemoryHandler", "WebView temizleme hatası: ${e.message}")
            }
        }
    }
}