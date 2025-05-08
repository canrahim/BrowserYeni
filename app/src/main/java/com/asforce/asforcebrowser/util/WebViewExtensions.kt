package com.asforce.asforcebrowser.util

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * WebView için yardımcı uzantı fonksiyonları
 * 
 * WebView'ın yapılandırılması ve özelleştirilmesi için kullanışlı metotlar içerir.
 * Referans: Kotlin Extension Functions
 */

/**
 * WebView'ın temel konfigürasyonunu yapar
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.configure() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        javaScriptCanOpenWindowsAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
        
        // Kaydırma performansı optimizasyonları
        // Not: LayoutAlgorithm.NORMAL Android'in yeni sürümlerinde önerilmiyor
        // Bunun yerine varsayılan WebView davranışı daha iyi
        
        // Donanım hızlandırma ve performans ayarları
        databaseEnabled = true
        allowContentAccess = true
        setNeedInitialFocus(false) // Gereksiz odaklanmayı önle
        blockNetworkImage = false // Resimleri bloklama
        
        // Bellek kullanımı optimizasyonu
        // Not: setAppCacheEnabled metodu Android'in yeni sürümlerinde kaldırılmıştır
        // Modern tarayıcı önbelleği için cacheMode ve domStorage kullanılır
        cacheMode = WebSettings.LOAD_DEFAULT // Önbelleği etkin bir şekilde kullan
        domStorageEnabled = true // LocalStorage desteği 
        
        // Kaydırma optimizasyonu ayarları
        // Not: setSaveFormData Android'in yeni sürümlerinde önerilmiyor
        allowFileAccess = true
        
        // CSS hızlandırma
        // Not: setRenderPriority Android'in yeni sürümlerinde kaldırılmıştır
        // Bunun yerine GPU ve donanım hızlandırma özellikleri kullanılır

        // Koyu mod desteği (varsa)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_AUTO)
        }
    }
    
    // Donanım hızlandırma ayarları - kritik kaydırma performansı için
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    
    // Kaydırma gecikmesini azaltmak için overscroll ayarları
    overScrollMode = View.OVER_SCROLL_NEVER
    
    // Kaydırma kenar efektlerini devre dışı bırak
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
}

/**
 * WebView için SwipeRefreshLayout ile sayfa yenileme
 */
fun WebView.setupWithSwipeRefresh(swipeRefresh: SwipeRefreshLayout) {
    swipeRefresh.setOnRefreshListener {
        this.reload()
    }

    this.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            swipeRefresh.isRefreshing = true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            swipeRefresh.isRefreshing = false
        }
    }
}

/**
 * URL'i normalize eder
 */
fun String.normalizeUrl(): String {
    return if (!this.startsWith("http://") && !this.startsWith("https://")) {
        "https://$this"
    } else {
        this
    }
}