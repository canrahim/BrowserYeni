package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.CookieManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.graphics.Bitmap
import android.util.Log

/**
 * ScrollOptimizer - WebView kaydırma performansı optimizasyonu
 * 
 * Bu sınıf, WebView için kaydırma performansını optimize eden ve 
 * animasyonlu kaydırma davranışlarını düzenleyen yardımcı metotlar içerir.
 * 
 * Referans: 
 * - Android WebView Performans Optimizasyonu (Google Developers)
 * - Modern JavaScript DOM Manipülasyonu Teknikleri
 */
class ScrollOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "ScrollOptimizer"
        private const val MINIMAL_JS = true // Minimal JavaScript kullan
    }
    
    /**
     * WebView için optimize edilmiş donanım ve render ayarlarını yapar
     */
    fun optimizeWebViewHardwareRendering(webView: WebView) {
        // Donanım hızlandırma ayarları
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Render performansını artıran ayarlar
        webView.settings.apply {
            // Kritik render ayarları
            setRenderPriority(WebSettings.RenderPriority.HIGH) 
            
            // Önbellek ayarları - ağ erişimini optimize eder
            cacheMode = WebSettings.LOAD_DEFAULT // Önbellek kullanımını etkinleştir
            
            // JavaScript ve render optimizasyonu
            javaScriptEnabled = true
            domStorageEnabled = true // DOM Depolama etkin
            databaseEnabled = true
            
            // HTML5 Depolama için
            databaseEnabled = true
            domStorageEnabled = true
            
            // Render performans ayarları
            blockNetworkImage = false
            loadsImagesAutomatically = true
            
            // Günümüz standartlarına uygun
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // Kaydırma sırasında performans için gerekli ayarlar
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // Görüntü yükleme optimizasyonu
            blockNetworkImage = false
            loadsImagesAutomatically = true
        }
        
        // WebView optimize edilmiş çizim ayarları
        webView.apply {
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY // Kaydırma çubuğunu içerik alanının dışına yerleştir
            isVerticalScrollBarEnabled = false // Kaydırma çubuklarını gizle
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER // Kaydırma sınırı efektini kaldır
        }
        
        // Çerez yöneticisini optimize et (RAM kullanımını azaltır)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }
    
    /**
     * Kaydırma optimizasyonu için JavaScript kodunu enjekte eder
     * En minimal ve verimli kodu kullanır
     */
    fun injectOptimizedScrollingScript(webView: WebView) {
        // JavaScript Bridge oluşturma - Native WebView ile JavaScript arasında köprü
        class JSInterface {
            @JavascriptInterface
            fun reportScrollPerformance(message: String) {
                Log.d(TAG, "Scroll Performansı: $message")
            }
        }
        
        // JavaScript arayüzünü ekle
        webView.addJavascriptInterface(JSInterface(), "ScrollOptimizer")
        
        // Sayfada kaydırma performansını artıran JavaScripti çalıştır
        // Minimal veya kapsamlı seçeneği kullan
        val script = if (MINIMAL_JS) {
            // Minimal script - performans için
            """
            (function() {
                // Kaydırma davranışını düzelt
                document.documentElement.style.setProperty('scroll-behavior', 'auto', 'important');
                
                // Genel stil enjeksiyonu
                var style = document.createElement('style');
                style.textContent = '* { scroll-behavior: auto !important; scroll-snap-type: none !important; }';
                document.head.appendChild(style);
                
                // Scrollable elementleri izle
                function optimizeScrollables() {
                    var elements = document.querySelectorAll('[class*="scroll"],[class*="carousel"],[class*="slider"]');
                    for(var i = 0; i < elements.length; i++) {
                        if(elements[i].style) {
                            elements[i].style.setProperty('scroll-behavior', 'auto', 'important');
                            elements[i].style.setProperty('transition', 'none', 'important');
                        }
                    }
                }
                
                // İlk çalıştırma
                optimizeScrollables();
                
                // DOM değişikliklerini izle
                if (window.MutationObserver) {
                    new MutationObserver(optimizeScrollables).observe(
                        document.documentElement, { childList: true, subtree: true }
                    );
                }
                
                // Scroll API düzeltmeleri
                if (window.scrollTo) {
                    var originalScrollTo = window.scrollTo;
                    window.scrollTo = function() {
                        if (arguments[0] && arguments[0].behavior) {
                            arguments[0].behavior = 'auto';
                        }
                        return originalScrollTo.apply(this, arguments);
                    };
                }
                
                // Rapor ver
                if (window.ScrollOptimizer) {
                    ScrollOptimizer.reportScrollPerformance("Optimizasyon uygulandı");
                }
                
                console.log('AsforceBrowser: Optimize kaydırma etkinleştirildi');
            })();
            """
        } else {
            // Daha kapsamlı script - tam müdahale
            """
            (function() {
                // Stil enjeksiyonu için ana fonksiyon
                function injectStyles() {
                    var style = document.createElement('style');
                    style.textContent = `
                        html, body, * {
                            scroll-behavior: auto !important;
                            scroll-snap-type: none !important;
                            -webkit-overflow-scrolling: auto !important;
                            overflow-scrolling: auto !important;
                            transition: none !important;
                            animation: none !important;
                            overscroll-behavior: none !important;
                        }
                        
                        /* Kaydırma çubuklarını gizle */
                        ::-webkit-scrollbar {
                            width: 0 !important; 
                            height: 0 !important;
                            background: transparent !important;
                        }
                        
                        /* Kaydırma çubuğu stilleri */
                        * {
                            -ms-overflow-style: none !important;
                            scrollbar-width: none !important;
                        }
                        
                        /* Yaygın bileşenlere müdahale */
                        .scrollable, [class*="scroll"], [id*="scroll"], 
                        [class*="slider"], [id*="slider"], 
                        [class*="carousel"], [id*="carousel"] {
                            scroll-behavior: auto !important;
                            transition: none !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
                
                // Smooth Scroll API'yi devre dışı bırak
                function overrideScrollAPIs() {
                    // scrollTo API düzeltme
                    if (window.scrollTo) {
                        var originalScrollTo = window.scrollTo;
                        window.scrollTo = function() {
                            if (arguments.length === 1 && typeof arguments[0] === 'object') {
                                if ('behavior' in arguments[0]) {
                                    var opts = Object.assign({}, arguments[0], { behavior: 'auto' });
                                    return originalScrollTo.call(this, opts);
                                }
                            }
                            return originalScrollTo.apply(this, arguments);
                        };
                    }
                    
                    // scrollBy API düzeltme
                    if (window.scrollBy) {
                        var originalScrollBy = window.scrollBy;
                        window.scrollBy = function() {
                            if (arguments.length === 1 && typeof arguments[0] === 'object') {
                                if ('behavior' in arguments[0]) {
                                    var opts = Object.assign({}, arguments[0], { behavior: 'auto' });
                                    return originalScrollBy.call(this, opts);
                                }
                            }
                            return originalScrollBy.apply(this, arguments);
                        };
                    }
                    
                    // scrollIntoView API düzeltme
                    if (Element.prototype.scrollIntoView) {
                        var originalScrollIntoView = Element.prototype.scrollIntoView;
                        Element.prototype.scrollIntoView = function() {
                            if (arguments.length === 0 || (arguments.length === 1 && typeof arguments[0] === 'boolean')) {
                                return originalScrollIntoView.apply(this, arguments);
                            } else if (arguments.length === 1 && typeof arguments[0] === 'object') {
                                if ('behavior' in arguments[0]) {
                                    var opts = Object.assign({}, arguments[0], { behavior: 'auto' });
                                    return originalScrollIntoView.call(this, opts);
                                }
                            }
                            return originalScrollIntoView.apply(this, arguments);
                        };
                    }
                }
                
                // Scroll stilleri içeren elementleri optimize et
                function optimizeScrollableElements() {
                    // Tüm kaydırılabilir elementleri bul
                    var scrollers = document.querySelectorAll('[class*="scroller"], [class*="scroll"], [class*="slider"], [id*="carousel"]');
                    for (var i = 0; i < scrollers.length; i++) {
                        if (scrollers[i].style) {
                            scrollers[i].style.transition = 'none';
                            scrollers[i].style.scrollBehavior = 'auto';
                        }
                    }
                    
                    // Animasyonlu elementleri durdur
                    var animatedElements = document.querySelectorAll('[style*="animation"], [style*="transition"]');
                    for (var i = 0; i < animatedElements.length; i++) {
                        if (animatedElements[i].style) {
                            animatedElements[i].style.animation = 'none';
                            animatedElements[i].style.transition = 'none';
                        }
                    }
                }
                
                // DOM değişikliklerini izle
                function setupMutationObserver() {
                    if (window.MutationObserver) {
                        var observer = new MutationObserver(function() {
                            optimizeScrollableElements();
                        });
                        
                        observer.observe(document.documentElement, {
                            childList: true,
                            subtree: true,
                            attributes: true,
                            attributeFilter: ['style', 'class']
                        });
                    }
                }
                
                // Ana terapilerini uygula
                injectStyles();
                overrideScrollAPIs();
                optimizeScrollableElements();
                setupMutationObserver();
                
                // Natif uygulamaya performans verisi gönder
                if (window.ScrollOptimizer) {
                    ScrollOptimizer.reportScrollPerformance("Kapsamlı optimizasyon uygulandı");
                }
                
                console.log('AsforceBrowser: Gelişmiş kaydırma optimizasyonu etkinleştirildi');
            })();
            """
        }
        
        // Sayfaya script enjekte et
        webView.evaluateJavascript(script, null)
    }
    
    /**
     * Render performansını artırmak için gelişmiş WebViewClient
     */
    fun createOptimizedWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Sayfa yüklenmeye başladığında uygulanacak optimizasyonlar
                optimizeWebViewHardwareRendering(view)
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Sayfa tamamen yüklendiğinde kaydırma optimizasyonu yap
                injectOptimizedScrollingScript(view)
            }
            
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.e(TAG, "Render process crashed: ${detail.didCrash()}")
                // Render işlemi çöktüğünde kurtarma stratejisi
                if (detail.didCrash()) {
                    // Crashing durumunda webview'i yenile
                    view.reload()
                    return true
                }
                return false
            }
        }
    }
    
    /**
     * WebView için tüm optimizasyonları tek seferde uygulayan yardımcı metod
     */
    fun applyAllOptimizations(webView: WebView) {
        // Donanım hızlandırma optimizasyonunu uygula
        optimizeWebViewHardwareRendering(webView)
        
        // Optimize edilmiş WebViewClient ata
        webView.webViewClient = createOptimizedWebViewClient()
        
        // JavaScript'i çalıştır (eğer sayfa zaten yüklenmişse)
        if (webView.url != null) {
            injectOptimizedScrollingScript(webView)
        }
        
        Log.d(TAG, "Tüm WebView optimizasyonları uygulandı")
    }
}