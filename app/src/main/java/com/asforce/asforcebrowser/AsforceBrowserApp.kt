package com.asforce.asforcebrowser

import android.app.Application
import android.webkit.WebView
import android.os.Build
import android.view.ViewGroup
import com.asforce.asforcebrowser.util.performance.PerformanceOptimizer
import dagger.hilt.android.HiltAndroidApp

/**
 * AsforceBrowserApp - Uygulama sınıfı
 *
 * Hilt Dependency Injection'ı başlatmak ve uygulama genelinde
 * performans optimizasyonlarını ayarlamak için kullanılır.
 */
@HiltAndroidApp
class AsforceBrowserApp : Application() {

    // Üretim ortamı için bu değişkeni false yapın
    private val isDebugMode = false

    override fun onCreate() {
        super.onCreate()

        // Uygulama başlangıcında optimizasyonları yap
        initializeOptimizations()
    }

    /**
     * Uygulama çapında performans optimizasyonlarını başlatır
     */
    private fun initializeOptimizations() {
        // WebView optimizasyonları için ön hazırlık
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = Application.getProcessName()
            if (packageName != processName) {
                // WebView çoklu süreç ayarı için - sadece ana işlemde yap
                WebView.setDataDirectorySuffix(processName)
            }
        }

        // Sadece debug modunda Chrome DevTools entegrasyonunu etkinleştir
        if (isDebugMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(isDebugMode)
        }

        // Önbellek ve bellek yönetimi optimize et
        optimizeMemoryUsage()
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
            // Hata durumunda sessizce devam et
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
                // Hata durumunda sessizce devam et
            }
        }
    }
}