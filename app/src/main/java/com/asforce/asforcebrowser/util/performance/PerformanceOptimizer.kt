package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * PerformanceOptimizer - Main performance optimization class for AsforceBrowser
 *
 * This class combines all other optimizer classes (ScrollOptimizer, MediaOptimizer,
 * PageLoadOptimizer) and provides a single interface.
 *
 * References:
 * - Android WebView Optimization Best Practices
 * - Modern Web Browser Performance Techniques
 */
class PerformanceOptimizer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceOptimizer"

        // Singleton instance
        @Volatile
        private var instance: PerformanceOptimizer? = null

        /**
         * Get the singleton instance of PerformanceOptimizer
         */
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
     * Creates a fully optimized WebViewClient that combines all optimization features
     */
    fun createSuperOptimizedWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // 1. Page loading optimizations (should be applied earliest)
                pageLoadOptimizer.optimizePageLoadSettings(view)

                // 2. Prepare scrolling optimizations
                scrollOptimizer.optimizeWebViewHardwareRendering(view)

                Log.d(TAG, "Page load started optimizations applied: $url")
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // 1. Complete page loading optimizations
                pageLoadOptimizer.injectLoadOptimizationScript(view)

                // 2. Inject scrolling optimizations
                scrollOptimizer.injectOptimizedScrollingScript(view)

                // 3. Inject media optimizations
                mediaOptimizer.optimizeVideoPlayback(view)

                // 4. Enable advanced codec support
                mediaOptimizer.enableAdvancedCodecSupport(view)

                // 5. Optimize render performance
                optimizeRenderPerformance(view)

                Log.d(TAG, "All page optimizations completed: $url")
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)

                // Apply special optimizations when video or audio resource is detected
                if (isMediaResource(url)) {
                    // Media resource detected, optimize
                    mediaOptimizer.optimizeVideoPlayback(view)
                }
            }

            /**
             * Check if a URL points to a media resource
             */
            private fun isMediaResource(url: String): Boolean {
                return url.contains(".mp4") || url.contains(".m3u8") ||
                        url.contains(".ts") || url.contains("video") ||
                        url.contains("audio") || url.contains(".mp3")
            }
        }
    }

    /**
     * Creates a fully optimized WebChromeClient
     */
    fun createSuperOptimizedWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)

                // Progressive optimizations based on loading progress
                when {
                    newProgress in 31..69 -> {
                        // Apply early optimizations when page structure starts forming
                        pageLoadOptimizer.optimizePageLoadSettings(view)
                    }
                    newProgress >= 70 -> {
                        // Apply scrolling optimizations when page is largely loaded
                        scrollOptimizer.optimizeWebViewHardwareRendering(view)
                    }
                    newProgress >= 90 -> {
                        // Inject all optimizations when almost all resources are loaded
                        view.evaluateJavascript("""
                            console.log('AsforceBrowser: Starting full optimization mode...');
                        """, null)
                    }
                }
            }
        }
    }

    /**
     * Settings that optimize render performance
     */
    private fun optimizeRenderPerformance(webView: WebView) {
        // Inject render thread optimization
        webView.evaluateJavascript(JsScripts.RENDER_OPTIMIZATION, null)

        // Additional hardware optimizations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                true
            )
        }
    }

    /**
     * Applies all performance optimizations to WebView at once
     */
    fun optimizeWebView(webView: WebView) {
        Log.d(TAG, "Starting all WebView optimizations...")

        // 1. Basic WebView settings
        configureBasicWebViewSettings(webView)

        // 2. Hardware acceleration optimizations
        scrollOptimizer.optimizeWebViewHardwareRendering(webView)

        // 3. Assign performance-focused WebViewClient and WebChromeClient
        webView.webViewClient = createSuperOptimizedWebViewClient()
        webView.webChromeClient = createSuperOptimizedWebChromeClient()

        // 4. Debugging and reporting
        enableDebugModeIfNeeded()

        Log.d(TAG, "All WebView optimizations completed")
    }

    /**
     * Configure basic WebView settings for optimal performance
     */
    private fun configureBasicWebViewSettings(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(false) // Turn off location support if not needed
            mediaPlaybackRequiresUserGesture = false

            // Cache settings
            databaseEnabled = true
            domStorageEnabled = true

            // Faster transitions between pages
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

            // Other performance improvements
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }
    }

    /**
     * Enable WebView debugging if needed
     */
    private fun enableDebugModeIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /**
     * Collect performance metrics after page is loaded
     */
    fun collectPerformanceMetrics(webView: WebView, callback: ValueCallback<String>) {
        webView.evaluateJavascript(JsScripts.PERFORMANCE_METRICS, callback)
    }

    /**
     * Collection of JavaScript snippets used for optimization
     */
    private object JsScripts {
        const val RENDER_OPTIMIZATION = """
            (function() {
                // Reduce render thread load
                
                // 1. Optimize page composition layers
                var potentialElements = document.querySelectorAll(
                    '.fixed, .sticky, [style*="position: fixed"], [style*="position: sticky"], ' +
                    '[style*="transform"], [style*="filter"], [style*="opacity"], ' +
                    '[style*="will-change"], video, canvas, [style*="animation"], ' +
                    '[style*="z-index"]'
                );
                
                for (var i = 0; i < potentialElements.length; i++) {
                    var el = potentialElements[i];
                    // Set will-change property
                    if (el.nodeName === 'VIDEO' || el.nodeName === 'CANVAS') {
                        el.style.willChange = 'transform';
                    } else {
                        // Optimize based on properties
                        var style = window.getComputedStyle(el);
                        if (style.position === 'fixed' || style.position === 'sticky') {
                            el.style.willChange = 'transform';
                        } else if (style.transform !== 'none' || style.filter !== 'none' || 
                                  (style.opacity !== '1' && style.opacity !== '')) {
                            el.style.willChange = 'transform, opacity';
                        }
                    }
                    
                    // For GPU-accelerated rendering:
                    el.style.transform = 'translateZ(0)';
                }
                
                // 2. Prevent excessive layout changes
                var mutationCount = 0;
                var layoutTriggeringProps = [
                    'width', 'height', 'top', 'left', 'right', 'bottom',
                    'margin', 'padding', 'display', 'position', 'float'
                ];
                
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function(mutations) {
                        // Many DOM changes detected
                        mutationCount += mutations.length;
                        
                        // When exceeding a certain threshold, block expensive DOM changes
                        if (mutationCount > 100) {
                            for (var i = 0; i < mutations.length; i++) {
                                var mutation = mutations[i];
                                if (mutation.type === 'attributes') {
                                    var attributeName = mutation.attributeName.toLowerCase();
                                    // For layout-triggering styles
                                    for (var j = 0; j < layoutTriggeringProps.length; j++) {
                                        if (attributeName === 'style' && 
                                            mutation.target.style && 
                                            mutation.target.style[layoutTriggeringProps[j]]) {
                                            // Remove/balance layout-triggering styles
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
                    
                    // Clear observer after 5 seconds (post page loading)
                    setTimeout(function() {
                        observer.disconnect();
                    }, 5000);
                }
                
                console.log('AsforceBrowser: Render performance optimizations applied');
            })();
        """

        const val PERFORMANCE_METRICS = """
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
                
                // Page loading metrics
                var pageLoadTime = performance.timing.loadEventEnd - performance.timing.navigationStart;
                var domReadyTime = performance.timing.domComplete - performance.timing.domLoading;
                var networkTime = performance.timing.responseEnd - performance.timing.fetchStart;
                
                // Calculate FPS performance
                var fps = 0;
                var frameCount = 0;
                var lastTime = performance.now();
                
                // Start FPS measurement
                function countFrames() {
                    frameCount++;
                    var now = performance.now();
                    
                    // Calculate FPS every second
                    if (now - lastTime >= 1000) {
                        fps = Math.round(frameCount * 1000 / (now - lastTime));
                        frameCount = 0;
                        lastTime = now;
                    }
                    
                    window.requestAnimationFrame(countFrames);
                }
                
                // Start FPS counter
                countFrames();
                
                // Report metrics after 1.5 seconds
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
        """
    }
}