package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import android.graphics.Bitmap
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * PageLoadOptimizer - WebView sayfa yükleme performansı optimizasyonu
 * 
 * Bu sınıf, WebView'da sayfa yükleme hızını artırmaya yönelik 
 * optimizasyonlar içerir.
 * 
 * Referans:
 * - Android WebView Performans Optimizasyonu (Google Developers)
 * - Modern Web İçerik Yükleme Teknikleri
 */
class PageLoadOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "PageLoadOptimizer"
    }
    
    /**
     * Sayfa yükleme performansını artıran temel ayarları uygular
     */
    fun optimizePageLoadSettings(webView: WebView) {
        webView.settings.apply {
            // Yükleme performansını etkileyen temel ayarlar
            blockNetworkImage = false // Resimleri yükle (false değeri çoğu durumda daha iyi)
            loadsImagesAutomatically = true // Resimleri otomatik yükle
            
            // Önbellek kullanımı
            domStorageEnabled = true // Yerel depolama kullan
            databaseEnabled = true // Veritabanı desteğini etkinleştir
            
            // Genel performans ayarları
            javaScriptEnabled = true // Modern siteler için gerekli
            useWideViewPort = true // Tam sayfa genişliği kullan
            loadWithOverviewMode = true // Sayfayı tam olarak göster
            
            // Dosya erişimi ve güvenlik
            allowFileAccess = true // Dosya erişimini aktif et
            
            // Tepki süresi optimizasyonu
            setNeedInitialFocus(false) // Başlangıç odaklamasını devre dışı bırak
            
            // Network kaynak kullanımını dengele
            setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT) // Önbellek kullan
        }
        
        // Service Worker desteğini etkinleştir (Android 8.0+)
        // Not: Bu özellik bazı sürümlerde desteklenmeyebilir, bu yüzden try-catch içinde
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                // ServiceWorker desteği için alternatif yöntem
                Log.d(TAG, "Service Worker desteği kontrol ediliyor")
                
                // ServiceWorker API'sinin dolaylı kullanımı - özel sınıfı olmadan da çalışır
                webView.settings.apply {
                    // Service Worker için gerekli ayarlar
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptEnabled = true
                }
                
                // ServiceWorker davranışını ayarlamak için JavaScript kullan
                webView.evaluateJavascript("""
                    (function() {
                        // ServiceWorker testi
                        if ('serviceWorker' in navigator) {
                            console.log('AsforceBrowser: ServiceWorker desteği mevcut');
                        }
                    })();
                """.trimIndent(), null)
                
                Log.d(TAG, "Service Worker için tarafında ayarlar yapıldı")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service Worker ayarlanırken hata: ${e.message}")
        }
    }
    
    /**
     * Sayfa yükleme sırasında kritik olmayan kaynakları erteleyen
     * optimizasyon kodunu enjekte eder
     */
    fun injectLoadOptimizationScript(webView: WebView) {
        val optimizationScript = """
            (function() {
                // Sayfa yükleme performansını artıran JavaScript
                
                // 1. Görünür alanda olmayan resimlerin yüklenmesini ertele
                function lazyLoadImages() {
                    // IntersectionObserver destekleniyorsa kullanalım
                    if ('IntersectionObserver' in window) {
                        var imageObserver = new IntersectionObserver(function(entries, observer) {
                            entries.forEach(function(entry) {
                                if (entry.isIntersecting) {
                                    var lazyImage = entry.target;
                                    if (lazyImage.dataset.src) {
                                        lazyImage.src = lazyImage.dataset.src;
                                        lazyImage.removeAttribute('data-src');
                                        observer.unobserve(lazyImage);
                                    }
                                }
                            });
                        });
                        
                        // Tüm resimleri tara ve lazy-load uygula
                        var images = document.querySelectorAll('img[data-src]');
                        images.forEach(function(image) {
                            imageObserver.observe(image);
                        });
                    }
                }
                
                // 2. Kritik olmayan JavaScript yüklemelerini ertele
                function deferNonCriticalJS() {
                    var scripts = document.querySelectorAll('script[defer], script[async]');
                    scripts.forEach(function(script) {
                        // Script elementini kaldır ve sonra geri ekleyerek tarayıcıyı yeniden yüklemeye zorla
                        if (script.parentNode) {
                            var parent = script.parentNode;
                            var nextSibling = script.nextSibling;
                            parent.removeChild(script);
                            
                            setTimeout(function() {
                                if (nextSibling) {
                                    parent.insertBefore(script, nextSibling);
                                } else {
                                    parent.appendChild(script);
                                }
                            }, 50); // 50ms gecikme
                        }
                    });
                }
                
                // 3. Sayfa yüklendikten sonra preconnect işlemleri
                function optimizeNetworkConnections() {
                    // Sık kullanılan domainler için preconnect önerileleri ekle
                    var domains = [];
                    var links = document.querySelectorAll('a[href^="http"], img[src^="http"], script[src^="http"], link[href^="http"]');
                    
                    links.forEach(function(link) {
                        try {
                            var url;
                            if (link.href) url = new URL(link.href);
                            else if (link.src) url = new URL(link.src);
                            
                            if (url && url.hostname && !domains.includes(url.hostname)) {
                                domains.push(url.hostname);
                            }
                        } catch(e) {}
                    });
                    
                    // En yaygın 3 domain için preconnect ekle
                    domains.slice(0, 3).forEach(function(domain) {
                        var link = document.createElement('link');
                        link.rel = 'preconnect';
                        link.href = 'https://' + domain;
                        document.head.appendChild(link);
                    });
                }
                
                // 4. Stil ve fontları optimize et
                function optimizeStyles() {
                    // Kritik olmayan stil sayfalarını asenkron yükle
                    var styles = document.querySelectorAll('link[rel="stylesheet"]');
                    styles.forEach(function(style, index) {
                        // İlk stil sayfasını kritik kabul et, diğerlerini asenkron yükle
                        if (index > 0) {
                            style.setAttribute('media', 'print');
                            style.setAttribute('onload', "this.media='all'");
                        }
                    });
                }
                
                // 5. Sayfa yükleme metriklerini topla
                function collectMetrics() {
                    if (window.performance && window.performance.timing) {
                        window.addEventListener('load', function() {
                            setTimeout(function() {
                                var timing = performance.timing;
                                var pageLoadTime = timing.loadEventEnd - timing.navigationStart;
                                var domReadyTime = timing.domComplete - timing.domLoading;
                                
                                console.log('Sayfa yükleme süresi: ' + pageLoadTime + 'ms');
                                console.log('DOM hazırlanma süresi: ' + domReadyTime + 'ms');
                            }, 0);
                        });
                    }
                }
                
                // Uygulamaya başla
                // DOMContentLoaded olayını bekle
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        deferNonCriticalJS();
                        optimizeStyles();
                        collectMetrics();
                    });
                } else {
                    deferNonCriticalJS();
                    optimizeStyles();
                    collectMetrics();
                }
                
                // window.load olayını bekle (tüm kaynaklar yüklendikten sonra)
                window.addEventListener('load', function() {
                    lazyLoadImages();
                    optimizeNetworkConnections();
                });
                
                console.log('AsforceBrowser: Sayfa yükleme optimizasyonları uygulandı');
            })();
        """
        
        // JavaScript'i çalıştır
        webView.evaluateJavascript(optimizationScript, null)
    }
    
    /**
     * Sayfa yükleme performansını artırmak için optimize edilmiş WebViewClient
     */
    fun createOptimizedLoadingWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            // Yükleme başladığında - erken optimizasyonlar
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                // Temel ayarları optimize et
                optimizePageLoadSettings(view)
                
                // Sayfa yüklenmeye başladığında yükleme optimizasyonları
                view.evaluateJavascript("""
                    (function() {
                        // Önce kritik CSS ve JS'yi yükle
                        document.documentElement.style.visibility = 'visible';
                        
                        // Console mesajlarını azalt (performansı artırır)
                        if (window.console) {
                            var originalLog = console.log;
                            console.log = function() {
                                // Sadece önemli mesajları geçir
                                if (arguments[0] && typeof arguments[0] === 'string' && 
                                    (arguments[0].includes('error') || 
                                     arguments[0].includes('AsforceBrowser'))) {
                                    return originalLog.apply(console, arguments);
                                }
                            };
                        }
                    })();
                """, null)
            }
            
            // Sayfa yükleme tamamlandığında
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                
                // Sayfa tamamen yüklendiğinde optimizasyon kodunu çalıştır
                injectLoadOptimizationScript(view)
                
                // CPU kullanımını azalt
                view.evaluateJavascript("""
                    (function() {
                        // CPU kullanımını optimize eden ayarlar
                        // Gereksiz zamanlayıcıları ve animasyonları temizle
                        var highCPUEvents = ['mousemove', 'touchmove', 'scroll'];
                        highCPUEvents.forEach(function(eventType) {
                            // Pasif olay dinleyicilerini kullan
                            document.addEventListener(eventType, function() {}, { passive: true });
                        });
                        
                        // Zamanlayıcıları optimize et
                        if (window.performance && window.performance.now) {
                            var start = performance.now();
                            window._requestAnimationFrame = window.requestAnimationFrame;
                            window.requestAnimationFrame = function(callback) {
                                return window._requestAnimationFrame(function(timestamp) {
                                    if (performance.now() - start < 500) { // İlk 500ms boyunca normal çalış
                                        callback(timestamp);
                                    } else {
                                        // 500ms sonra CPU'yu korumak için yavaşla
                                        setTimeout(function() {
                                            callback(performance.now());
                                        }, 16); // ~60fps yerine daha düşük hızda çalıştır
                                    }
                                });
                            };
                        }
                    })();
                """, null)
                
                Log.d(TAG, "Sayfa yükleme optimizasyonları başarıyla uygulandı: $url")
            }
            
            // WebView kaynak yükleme engelleme ve optimizasyon
            override fun shouldInterceptRequest(
                view: WebView, 
                request: WebResourceRequest
            ): WebResourceResponse? {
                // İstenmeyen kaynakları engelle
                val url = request.url.toString()
                
                // Belirli reklam veya gereksiz kaynakları engelle (sayfa yükleme hızı için)
                if (url.contains("ads.") || 
                    url.contains("analytics.") || 
                    url.contains("tracker.") || 
                    url.endsWith(".gif")) {
                    // Boş bir yanıt döndür
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }
                
                return super.shouldInterceptRequest(view, request)
            }
        }
    }
    
    /**
     * WebView için tüm sayfa yükleme optimizasyonlarını tek seferde uygulayan yardımcı metod
     */
    fun applyAllLoadOptimizations(webView: WebView) {
        // Temel ayarları optimize et
        optimizePageLoadSettings(webView)
        
        // Optimize edilmiş WebViewClient ata
        webView.webViewClient = createOptimizedLoadingWebViewClient()
        
        // Eğer sayfa zaten yüklenmişse optimizasyonları hemen uygula
        if (webView.url != null) {
            injectLoadOptimizationScript(webView)
        }
        
        Log.d(TAG, "Tüm sayfa yükleme optimizasyonları başarıyla uygulandı")
    }
}