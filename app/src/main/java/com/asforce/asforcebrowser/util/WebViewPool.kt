package com.asforce.asforcebrowser.util

import android.content.Context
import android.webkit.WebView
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * WebView havuz sınıfı
 * WebView oluşturma ve yok etme maliyetini azaltmak için WebView'leri yeniden kullanır
 */
class WebViewPool private constructor(private val context: Context) {
    private val availableWebViews = ConcurrentLinkedQueue<WebView>()
    private val maxPoolSize = 3 // Maksimum havuz boyutu

    companion object {
        @Volatile
        private var instance: WebViewPool? = null

        fun getInstance(context: Context): WebViewPool {
            return instance ?: synchronized(this) {
                instance ?: WebViewPool(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Havuzdan bir WebView alır
     * Eğer havuzda WebView yoksa yeni bir tane oluşturur
     */
    fun acquireWebView(): WebView {
        return availableWebViews.poll() ?: createNewWebView()
    }

    /**
     * WebView'i havuza geri verir
     * Havuz dolu ise WebView'i yok eder
     */
    fun releaseWebView(webView: WebView) {
        // WebView'i temizle
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.clearCache(true)
        
        // Eğer havuz dolu değilse WebView'i havuza ekle
        if (availableWebViews.size < maxPoolSize) {
            availableWebViews.offer(webView)
        } else {
            // Havuz dolu, WebView'i yok et
            webView.destroy()
        }
    }

    /**
     * Yeni bir WebView oluşturur
     */
    private fun createNewWebView(): WebView {
        return WebView(context).apply {
            // Temel ayarları yapılandır
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }
    }

    /**
     * Tüm WebView'leri yok eder
     */
    fun destroyAll() {
        while (availableWebViews.isNotEmpty()) {
            availableWebViews.poll()?.destroy()
        }
    }
}