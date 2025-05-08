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
 * 
 * WebView için gerekli temel ayarları yükler, özellikle kademeli kaydırmayı engelleyecek şekilde ayarlar.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.configure() {
    // Kritik - donanım hızlandırma katmanını ayarla (kaydırma için önemli)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)

    // Tüm kaydırma çubuklarını ve taraygıcı kenar efektlerini gizle
    isHorizontalScrollBarEnabled = false
    isVerticalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY

    // Düşük seviyeli geliştirme ayarları
    isFocusable = true
    isLongClickable = false // Uzun dokunmayı devre dışı bırak

    // WebView ayarları
    settings.apply {
        // En önemli - JavaScript'i etkinleştir - çeşitli optimizasyonlar için gerekli
        javaScriptEnabled = true

        // Önbellek ve hız ayarları
        cacheMode = WebSettings.LOAD_NO_CACHE // Önbellekten yükleme yapma, doğrudan network kullan
        domStorageEnabled = true // Yerel depolama etkinleştir
        
        // Kaydırma hızını etkileyen özellikler
        blockNetworkImage = false // Resimleri yükle
        loadsImagesAutomatically = true // Resimleri otomatik yükle

        // Yakınlaştırma ayarları - genelde yavaşlamaya neden olur
        setSupportZoom(false) // Yakınlaştırma özelliği kapalı
        builtInZoomControls = false // Yakınlaştırma kontrolleri kapalı
        displayZoomControls = false // Yakınlaştırma düğmeleri kapalı
        
        // Görünüm ayarları
        useWideViewPort = false // Geniş görünüm devre dışı
        loadWithOverviewMode = false // Genel görünüm modu devre dışı
        
        // Tepki hızı için ayarlar
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // Karışık içeriğe izin ver
        allowContentAccess = true // İçerik erişimi aç
        setNeedInitialFocus(false) // Başlangıç odaklaması gereksiz
        
        // Hız optimizasyonu
        setGeolocationEnabled(false) // Konum özelliği kapalı
        javaScriptCanOpenWindowsAutomatically = false // Otomatik açılan pencereleri kapat
        databaseEnabled = true // Veritabanını etkinleştir
        
        // Uyumluluk modu kapat
        @Suppress("DEPRECATION")
        setSaveFormData(false) // Form verilerini kaydetmeyi kapat
        
        // Çoklu pencere desteğini kapat
        setSupportMultipleWindows(false) // Gereksiz pencereleri kapat
    }
    
    // WebView'ın donanım tarafından hızlandırılmasını sağla (kritik kaydırma performansı için)
    @Suppress("DEPRECATION")
    setDrawingCacheEnabled(false) // Çizim önbelleğini kapat (artık donanım hızlandırma var)
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