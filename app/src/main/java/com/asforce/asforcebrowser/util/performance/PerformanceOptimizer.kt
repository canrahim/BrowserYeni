package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.graphics.Bitmap
import android.util.Log
import android.os.Build
import android.webkit.ValueCallback

/**
 * PerformanceOptimizer - AsforceBrowser için ana performans optimizasyon sınıfı
 * 
 * Bu sınıf, diğer tüm optimizer sınıflarını (ScrollOptimizer, MediaOptimizer, PageLoadOptimizer)
 * birleştirir ve tek bir arayüz sunar.
 * 
 * Referans:
 * - Android WebView Optimization Best Practices
 * - Modern Web Browser Performance Techniques
 */
class PerformanceOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "PerformanceOptimizer"
        
        // Singleton örnek
        @Volatile
        private var instance: PerformanceOptimizer? = null
        
        fun getInstance(context: Context): PerformanceOptimizer {
            return instance ?: synchronized(this) {
                instance ?: PerformanceOptimizer(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Alt optimizer sınıfları
    private val scrollOptimizer = ScrollOptimizer(context)
    private val mediaOptimizer = MediaOptimizer(context)
    private val pageLoadOptimizer = PageLoadOptimizer(context)
    
    /**
     * Tam optimize edilmiş WebViewClient oluşturur 
     * Tüm optimizasyon özelliklerini birleştirir
     */
    fun createSuperOptimizedWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                // 1. Sayfa yükleme optimizasyonları (en erken uygulanmalı)
                pageLoadOptimizer.optimizePageLoadSettings(view)
                
                // 2. Kaydırma optimizasyonları için hazırlık
                scrollOptimizer.optimizeWebViewHardwareRendering(view)
                
                Log.d(TAG, "Sayfa yükleme başladı optimizasyonları uygulandı: $url")
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                
                // 1. Sayfa yükleme optimizasyonlarını tamamla
                pageLoadOptimizer.injectLoadOptimizationScript(view)
                
                // 2. Kaydırma optimizasyonlarını enjekte et
                scrollOptimizer.injectOptimizedScrollingScript(view)
                
                // 3. Medya optimizasyonlarını enjekte et
                mediaOptimizer.optimizeVideoPlayback(view)
                
                // 4. Gelişmiş codec desteğini etkinleştir
                mediaOptimizer.enableAdvancedCodecSupport(view)
                
                // 5. Render performansını optimize et
                optimizeRenderPerformance(view)
                
                Log.d(TAG, "Tüm sayfa optimizasyonları tamamlandı: $url")
            }
            
            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
                
                // Video veya ses kaynağı algılandığında özel optimizasyonlar uygula
                if (url.contains(".mp4") || url.contains(".m3u8") || 
                    url.contains(".ts") || url.contains("video") ||
                    url.contains("audio") || url.contains(".mp3")) {
                    // Medya kaynağı algılandı, optimize et
                    mediaOptimizer.optimizeVideoPlayback(view)
                }
            }
        }
    }
    
    /**
     * Tam optimize edilmiş WebChromeClient oluşturur
     */
    fun createSuperOptimizedWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                
                // İlerleme durumuna göre aşamalı optimizasyonlar
                if (newProgress > 30 && newProgress < 70) {
                    // Sayfa yapısı oluşmaya başladığında erken optimizasyonlar
                    pageLoadOptimizer.optimizePageLoadSettings(view)
                }
                
                if (newProgress >= 70) {
                    // Sayfa büyük ölçüde yüklendiğinde kaydırma optimizasyonları
                    scrollOptimizer.optimizeWebViewHardwareRendering(view)
                }
                
                if (newProgress >= 90) {
                    // Tüm kaynaklar neredeyse yüklendiğinde 
                    // tüm optimizasyonları enjekte et
                    view.evaluateJavascript("""
                        console.log('AsforceBrowser: Tam optimizasyon modu başlatılıyor...');
                    """, null)
                }
            }
        }
    }
    
    /**
     * Render performansını optimize eden ayarlar
     */
    private fun optimizeRenderPerformance(webView: WebView) {
        // Render thread optimizasyonu
        webView.evaluateJavascript("""
            (function() {
                // Render thread yükünü azalt
                
                // 1. Sayfa kompozisyon katmanlarını optimize et
                var potentialElements = document.querySelectorAll(
                    '.fixed, .sticky, [style*="position: fixed"], [style*="position: sticky"], ' +
                    '[style*="transform"], [style*="filter"], [style*="opacity"], ' +
                    '[style*="will-change"], video, canvas, [style*="animation"], ' +
                    '[style*="z-index"]'
                );
                
                for (var i = 0; i < potentialElements.length; i++) {
                    var el = potentialElements[i];
                    // will-change özelliğini ayarla
                    if (el.nodeName === 'VIDEO' || el.nodeName === 'CANVAS') {
                        el.style.willChange = 'transform';
                    } else {
                        // Özelliklere göre optimize et
                        var style = window.getComputedStyle(el);
                        if (style.position === 'fixed' || style.position === 'sticky') {
                            el.style.willChange = 'transform';
                        } else if (style.transform !== 'none' || style.filter !== 'none' || 
                                  (style.opacity !== '1' && style.opacity !== '')) {
                            el.style.willChange = 'transform, opacity';
                        }
                    }
                    
                    // GPU hızlandırmalı render için:
                    el.style.transform = 'translateZ(0)';
                }
                
                // 2. Fazla layout değişikliğini önle
                var mutationCount = 0;
                var layoutTriggeringProps = [
                    'width', 'height', 'top', 'left', 'right', 'bottom',
                    'margin', 'padding', 'display', 'position', 'float'
                ];
                
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function(mutations) {
                        // Birçok DOM değişikliği algılandı
                        mutationCount += mutations.length;
                        
                        // Belirli bir eşiği aştığında, pahalı DOM değişikliklerini engelle
                        if (mutationCount > 100) {
                            for (var i = 0; i < mutations.length; i++) {
                                var mutation = mutations[i];
                                if (mutation.type === 'attributes') {
                                    var attributeName = mutation.attributeName.toLowerCase();
                                    // Layout tetikleyen stiller için
                                    for (var j = 0; j < layoutTriggeringProps.length; j++) {
                                        if (attributeName === 'style' && 
                                            mutation.target.style && 
                                            mutation.target.style[layoutTriggeringProps[j]]) {
                                            // Layout tetikleyici stilleri kaldır/dengele
                                            mutation.target.style.willChange = 'transform';
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    });
                    
                    observer.observe(document.body, {
                        attributes: true,
                        childList: true,
                        subtree: true,
                        attributeFilter: ['style', 'class']
                    });
                    
                    // 5 saniye sonra observer'ı temizle (sayfa yükleme sonrası)
                    setTimeout(function() {
                        observer.disconnect();
                    }, 5000);
                }
                
                console.log('AsforceBrowser: Render performans optimizasyonları uygulandı');
            })();
        """, null)
        
        // Ek donanım optimizasyonları
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                true
            )
        }
    }
    
    /**
     * Tüm performans optimizasyonlarını tek seferde WebView'a uygular
     */
    fun optimizeWebView(webView: WebView) {
        Log.d(TAG, "Tüm WebView optimizasyonları başlatılıyor...")
        
        // 1. Temel WebView ayarları
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(false) // Konum desteğine gerek yoksa kapat
            mediaPlaybackRequiresUserGesture = false
            
            // Önbellek ayarları
            databaseEnabled = true
            domStorageEnabled = true
            
            // Sayfalar arasında daha hızlı geçiş
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            
            // Diğer performans iyileştirmeleri
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }
        
        // 2. Donanım hızlandırma optimizasyonları
        scrollOptimizer.optimizeWebViewHardwareRendering(webView)
        
        // 3. Performans odaklı WebViewClient ve WebChromeClient ata
        webView.webViewClient = createSuperOptimizedWebViewClient()
        webView.webChromeClient = createSuperOptimizedWebChromeClient()
        
        // 4. Hata ayıklama ve raporlama
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        Log.d(TAG, "Tüm WebView optimizasyonları tamamlandı")
    }
    
    /**
     * Sayfa yüklendikten sonra performans metriklerini topla
     */
    fun collectPerformanceMetrics(webView: WebView, callback: ValueCallback<String>) {
        webView.evaluateJavascript("""
            (function() {
                var metrics = {
                    'navigationType': performance.navigation.type,
                    'navigationStart': performance.timing.navigationStart,
                    'unloadEventStart': performance.timing.unloadEventStart,
                    'unloadEventEnd': performance.timing.unloadEventEnd,
                    'redirectStart': performance.timing.redirectStart,
                    'redirectEnd': performance.timing.redirectEnd,
                    'fetchStart': performance.timing.fetchStart,
                    'domainLookupStart': performance.timing.domainLookupStart,
                    'domainLookupEnd': performance.timing.domainLookupEnd,
                    'connectStart': performance.timing.connectStart,
                    'connectEnd': performance.timing.connectEnd,
                    'secureConnectionStart': performance.timing.secureConnectionStart,
                    'requestStart': performance.timing.requestStart,
                    'responseStart': performance.timing.responseStart,
                    'responseEnd': performance.timing.responseEnd,
                    'domLoading': performance.timing.domLoading,
                    'domInteractive': performance.timing.domInteractive,
                    'domContentLoadedEventStart': performance.timing.domContentLoadedEventStart,
                    'domContentLoadedEventEnd': performance.timing.domContentLoadedEventEnd,
                    'domComplete': performance.timing.domComplete,
                    'loadEventStart': performance.timing.loadEventStart,
                    'loadEventEnd': performance.timing.loadEventEnd
                };
                
                // Sayfa yükleme metrikleri
                var pageLoadTime = performance.timing.loadEventEnd - performance.timing.navigationStart;
                var domReadyTime = performance.timing.domComplete - performance.timing.domLoading;
                var networkTime = performance.timing.responseEnd - performance.timing.fetchStart;
                
                // FPS performansını hesapla
                var fps = 0;
                var frameCount = 0;
                var lastTime = performance.now();
                
                // FPS ölçümünü başlat
                function countFrames() {
                    frameCount++;
                    var now = performance.now();
                    
                    // Her saniyede FPS hesapla
                    if (now - lastTime >= 1000) {
                        fps = Math.round(frameCount * 1000 / (now - lastTime));
                        frameCount = 0;
                        lastTime = now;
                    }
                    
                    window.requestAnimationFrame(countFrames);
                }
                
                // FPS sayacını başlat
                countFrames();
                
                // 1.5 saniye sonra metrikleri raporla
                setTimeout(function() {
                    var report = {
                        'pageLoadTime': pageLoadTime + ' ms',
                        'domReadyTime': domReadyTime + ' ms',
                        'networkTime': networkTime + ' ms',
                        'fps': fps + ' FPS',
                        'detailedMetrics': metrics
                    };
                    
                    return JSON.stringify(report);
                }, 1500);
            })();
        """, callback)
    }
}