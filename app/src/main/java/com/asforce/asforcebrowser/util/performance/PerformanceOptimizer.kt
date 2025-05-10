package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.asforce.asforcebrowser.BuildConfig

/**
 * PerformanceOptimizer - Geliştirilmiş performans optimizasyonu sınıfı
 *
 * Referans: Android WebView Optimization Best Practices
 * https://developer.android.com/reference/android/webkit/WebSettings
 *
 * Yenilikler:
 * 1. Android T+ için setForceDark() uyarısını ortadan kaldırır
 * 2. Daha temiz log çıktısı
 * 3. API seviyesine göre akıllı uyumluluk
 */
class PerformanceOptimizer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceOptimizer"

        @Volatile
        private var instance: PerformanceOptimizer? = null

        fun getInstance(context: Context): PerformanceOptimizer {
            return instance ?: synchronized(this) {
                instance ?: PerformanceOptimizer(context.applicationContext).also { instance = it }
            }
        }
    }

    // Sub-optimizer classes
    private val scrollOptimizer = ScrollOptimizer(context)
    private val mediaOptimizer = MediaOptimizer(context)
    private val pageLoadOptimizer = PageLoadOptimizer(context)

    /**
     * Tüm optimizasyonları içeren WebViewClient oluşturur
     */
    fun createSuperOptimizedWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // 1. Sayfa yükleme optimizasyonları
                pageLoadOptimizer.optimizePageLoadSettings(view)

                // 2. Scroll optimizasyonlarını hazırla
                scrollOptimizer.optimizeWebViewHardwareRendering(view)

                Log.d(TAG, "Optimizasyonlar uygulandı: $url")
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // 1. Sayfa yükleme optimizasyonlarını tamamla
                pageLoadOptimizer.injectLoadOptimizationScript(view)

                // 2. Scroll optimizasyonlarını enjekte et
                scrollOptimizer.injectOptimizedScrollingScript(view)

                // 3. Medya optimizasyonlarını enjekte et
                mediaOptimizer.optimizeVideoPlayback(view)
                mediaOptimizer.enableAdvancedCodecSupport(view)

                // 4. Render performansını optimize et
                optimizeRenderPerformance(view)

                Log.d(TAG, "Tüm optimizasyonlar tamamlandı: $url")
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)

                // Medya kaynağı tespit edildiğinde özel optimizasyonlar uygula
                if (isMediaResource(url)) {
                    mediaOptimizer.optimizeVideoPlayback(view)
                }
            }

            private fun isMediaResource(url: String): Boolean {
                return url.contains(".mp4") || url.contains(".m3u8") ||
                        url.contains(".ts") || url.contains("video") ||
                        url.contains("audio") || url.contains(".mp3")
            }
        }
    }

    /**
     * Fully optimized WebChromeClient oluşturur
     */
    fun createSuperOptimizedWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)

                // Yükleme durumuna göre aşamalı optimizasyonlar
                when {
                    newProgress in 31..69 -> {
                        pageLoadOptimizer.optimizePageLoadSettings(view)
                    }
                    newProgress >= 70 -> {
                        scrollOptimizer.optimizeWebViewHardwareRendering(view)
                    }
                }
            }
        }
    }

    /**
     * Render performansını optimize eder
     */
    private fun optimizeRenderPerformance(webView: WebView) {
        // Render thread optimizasyonu
        webView.evaluateJavascript(JsScripts.RENDER_OPTIMIZATION, null)

        // Donanım optimizasyonları
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                true
            )
        }
    }

    /**
     * WebView'e tüm performans optimizasyonlarını uygular
     */
    fun optimizeWebView(webView: WebView) {
        Log.d(TAG, "WebView optimizasyonları başlatılıyor...")

        // 1. Temel WebView ayarları (API seviyesi farkını dikkate alarak)
        configureBasicWebViewSettings(webView)

        // 2. Donanım ivmesi optimizasyonları
        scrollOptimizer.optimizeWebViewHardwareRendering(webView)

        // 3. Performans odaklı client'ları ata
        webView.webViewClient = createSuperOptimizedWebViewClient()
        webView.webChromeClient = createSuperOptimizedWebChromeClient()

        // 4. Debug modunu etkinleştir (gerekirse)
        enableDebugModeIfNeeded()

        Log.d(TAG, "Tüm WebView optimizasyonları tamamlandı")
    }

    /**
     * API seviyesine göre uyumlu ayarlar yapılandırır
     */
    private fun configureBasicWebViewSettings(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = false

            // Cache ayarları
            cacheMode = WebSettings.LOAD_DEFAULT

            // Performans iyileştirmeleri
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false

            // API seviyesine göre güvenlik ve dark mode ayarları
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }

            // Android T+ için setForceDark() çağrılmıyor (uyarıyı önlemek için)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_AUTO
            }
        }
    }

    /**
     * Debug modunu etkinleştir
     */
    private fun enableDebugModeIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Sadece debug build'lerde etkinleştir
            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
    }

    /**
     * Performans metriklerini toplar
     */
    fun collectPerformanceMetrics(webView: WebView, callback: ValueCallback<String>) {
        webView.evaluateJavascript(JsScripts.PERFORMANCE_METRICS, callback)
    }

    /**
     * JavaScript parçacıkları
     */
    private object JsScripts {
        const val RENDER_OPTIMIZATION = """
            (function() {
                // Render thread yükünü azalt
                
                // 1. Kompozisyon katmanlarını optimize et
                const potentialElements = document.querySelectorAll(
                    '.fixed, .sticky, [style*="position: fixed"], [style*="position: sticky"], ' +
                    '[style*="transform"], [style*="filter"], [style*="opacity"], ' +
                    '[style*="will-change"], video, canvas, [style*="animation"], ' +
                    '[style*="z-index"]'
                );
                
                for (let el of potentialElements) {
                    // will-change özelliğini ayarla
                    if (el.nodeName === 'VIDEO' || el.nodeName === 'CANVAS') {
                        el.style.willChange = 'transform';
                    } else {
                        const style = window.getComputedStyle(el);
                        if (style.position === 'fixed' || style.position === 'sticky') {
                            el.style.willChange = 'transform';
                        } else if (style.transform !== 'none' || style.filter !== 'none' || 
                                  (style.opacity !== '1' && style.opacity !== '')) {
                            el.style.willChange = 'transform, opacity';
                        }
                    }
                    
                    // GPU-hızlandırılmış render için:
                    el.style.transform = 'translateZ(0)';
                }
                
                console.log('AsforceBrowser: Render performans optimizasyonları uygulandı');
            })();
        """

        const val PERFORMANCE_METRICS = """
            (function() {
                const metrics = {
                    navigationType: performance.navigation.type,
                    navigationStart: performance.timing.navigationStart,
                    unloadEventStart: performance.timing.unloadEventStart,
                    unloadEventEnd: performance.timing.unloadEventEnd,
                    redirectStart: performance.timing.redirectStart,
                    redirectEnd: performance.timing.redirectEnd,
                    fetchStart: performance.timing.fetchStart,
                    domainLookupStart: performance.timing.domainLookupStart,
                    domainLookupEnd: performance.timing.domainLookupEnd,
                    connectStart: performance.timing.connectStart,
                    connectEnd: performance.timing.connectEnd,
                    secureConnectionStart: performance.timing.secureConnectionStart,
                    requestStart: performance.timing.requestStart,
                    responseStart: performance.timing.responseStart,
                    responseEnd: performance.timing.responseEnd,
                    domLoading: performance.timing.domLoading,
                    domInteractive: performance.timing.domInteractive,
                    domContentLoadedEventStart: performance.timing.domContentLoadedEventStart,
                    domContentLoadedEventEnd: performance.timing.domContentLoadedEventEnd,
                    domComplete: performance.timing.domComplete,
                    loadEventStart: performance.timing.loadEventStart,
                    loadEventEnd: performance.timing.loadEventEnd
                };
                
                // Sayfa yükleme metrikleri
                const pageLoadTime = performance.timing.loadEventEnd - performance.timing.navigationStart;
                const domReadyTime = performance.timing.domComplete - performance.timing.domLoading;
                const networkTime = performance.timing.responseEnd - performance.timing.fetchStart;
                
                // FPS performansını hesapla
                let fps = 0;
                let frameCount = 0;
                let lastTime = performance.now();
                
                function countFrames() {
                    frameCount++;
                    const now = performance.now();
                    
                    if (now - lastTime >= 1000) {
                        fps = Math.round(frameCount * 1000 / (now - lastTime));
                        frameCount = 0;
                        lastTime = now;
                    }
                    
                    requestAnimationFrame(countFrames);
                }
                
                // FPS sayacını başlat
                countFrames();
                
                // 1.5 saniye sonra metrikleri raporla
                setTimeout(function() {
                    const report = {
                        pageLoadTime: pageLoadTime + ' ms',
                        domReadyTime: domReadyTime + ' ms',
                        networkTime: networkTime + ' ms',
                        fps: fps + ' FPS',
                        detailedMetrics: metrics
                    };
                    
                    return JSON.stringify(report);
                }, 1500);
            })();
        """
    }
}