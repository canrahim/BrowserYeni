package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * PageLoadOptimizer - WebView page loading performance optimization
 *
 * This class contains optimizations to improve page loading speed in WebView.
 *
 * References:
 * - Android WebView Performance Optimization (Google Developers)
 * - Modern Web Content Loading Techniques
 */
class PageLoadOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "PageLoadOptimizer"

        // JavaScript optimization script constants
        private val LAZY_LOAD_IMAGES_SCRIPT = JsScripts.LAZY_LOAD_IMAGES
        private val DEFER_NON_CRITICAL_JS_SCRIPT = JsScripts.DEFER_NON_CRITICAL_JS
        private val OPTIMIZE_NETWORK_CONNECTIONS_SCRIPT = JsScripts.OPTIMIZE_NETWORK_CONNECTIONS
        private val OPTIMIZE_STYLES_SCRIPT = JsScripts.OPTIMIZE_STYLES
        private val COLLECT_METRICS_SCRIPT = JsScripts.COLLECT_METRICS

        // Resource blocking patterns
        private val BLOCKED_RESOURCE_PATTERNS = listOf(
            "ads.", "analytics.", "tracker.", ".gif"
        )
    }

    /**
     * Apply basic settings that improve page loading performance
     */
    fun optimizePageLoadSettings(webView: WebView) {
        webView.settings.apply {
            // Basic settings affecting loading performance
            blockNetworkImage = false
            loadsImagesAutomatically = true

            // Cache usage
            domStorageEnabled = true
            databaseEnabled = true

            // General performance settings
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true

            // File access and security
            allowFileAccess = true

            // Response time optimization
            setNeedInitialFocus(false)

            // Network resource usage balance
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Enable Service Worker support (Android 8.0+)
        enableServiceWorkerIfSupported(webView)
    }

    /**
     * Enable service worker support if the device supports it
     */
    private fun enableServiceWorkerIfSupported(webView: WebView) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                Log.d(TAG, "Checking Service Worker support")

                // Required settings for Service Worker
                webView.settings.apply {
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptEnabled = true
                }

                // Test Service Worker support using JavaScript
                webView.evaluateJavascript(JsScripts.TEST_SERVICE_WORKER, null)

                Log.d(TAG, "Settings for Service Worker applied")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Service Worker: ${e.message}")
        }
    }

    /**
     * Injects optimization code that defers non-critical resources
     * during page loading
     */
    fun injectLoadOptimizationScript(webView: WebView) {
        val optimizationScript = buildString {
            append("(function() {\n")

            // Add optimization scripts
            append("// 1. Lazy load images that are not in the viewport\n")
            append(LAZY_LOAD_IMAGES_SCRIPT)
            append("\n\n")

            append("// 2. Defer non-critical JavaScript loading\n")
            append(DEFER_NON_CRITICAL_JS_SCRIPT)
            append("\n\n")

            append("// 3. Preconnect to frequently used domains\n")
            append(OPTIMIZE_NETWORK_CONNECTIONS_SCRIPT)
            append("\n\n")

            append("// 4. Optimize styles and fonts\n")
            append(OPTIMIZE_STYLES_SCRIPT)
            append("\n\n")

            append("// 5. Collect page loading metrics\n")
            append(COLLECT_METRICS_SCRIPT)
            append("\n\n")

            // Add initialization code
            append(JsScripts.INIT_OPTIMIZATION)

            append("})();")
        }

        // Execute the script
        webView.evaluateJavascript(optimizationScript, null)
    }

    /**
     * Creates an optimized WebViewClient to improve page loading performance
     */
    fun createOptimizedLoadingWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            // When loading starts - early optimizations
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // Optimize basic settings
                optimizePageLoadSettings(view)

                // Early page load optimizations
                view.evaluateJavascript(JsScripts.EARLY_PAGE_LOAD_OPTIMIZATION, null)
            }

            // When loading completes
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Run optimization code when page is fully loaded
                injectLoadOptimizationScript(view)

                // Reduce CPU usage
                view.evaluateJavascript(JsScripts.REDUCE_CPU_USAGE, null)

                Log.d(TAG, "Page loading optimizations successfully applied: $url")
            }

            // WebView resource loading blocking and optimization
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()

                // Block certain ad or unnecessary resources (for page loading speed)
                if (BLOCKED_RESOURCE_PATTERNS.any { url.contains(it) }) {
                    // Return an empty response
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }

                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    /**
     * Helper method that applies all page loading optimizations to WebView at once
     */
    fun applyAllLoadOptimizations(webView: WebView) {
        // Optimize basic settings
        optimizePageLoadSettings(webView)

        // Assign optimized WebViewClient
        webView.webViewClient = createOptimizedLoadingWebViewClient()

        // Apply optimizations immediately if page is already loaded
        if (webView.url != null) {
            injectLoadOptimizationScript(webView)
        }

        Log.d(TAG, "All page loading optimizations successfully applied")
    }

    /**
     * Collection of JavaScript snippets used for optimization
     */
    private object JsScripts {
        const val TEST_SERVICE_WORKER = """
            (function() {
                // ServiceWorker test
                if ('serviceWorker' in navigator) {
                    console.log('AsforceBrowser: ServiceWorker support available');
                }
            })();
        """

        const val LAZY_LOAD_IMAGES = """
            function lazyLoadImages() {
                // Use IntersectionObserver if supported
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
                    
                    // Scan all images and apply lazy-loading
                    var images = document.querySelectorAll('img[data-src]');
                    images.forEach(function(image) {
                        imageObserver.observe(image);
                    });
                }
            }
        """

        const val DEFER_NON_CRITICAL_JS = """
            function deferNonCriticalJS() {
                var scripts = document.querySelectorAll('script[defer], script[async]');
                scripts.forEach(function(script) {
                    // Remove and re-add the script element to force the browser to reload
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
                        }, 50); // 50ms delay
                    }
                });
            }
        """

        const val OPTIMIZE_NETWORK_CONNECTIONS = """
            function optimizeNetworkConnections() {
                // Add preconnect suggestions for frequently used domains
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
                
                // Add preconnect for the top 3 most common domains
                domains.slice(0, 3).forEach(function(domain) {
                    var link = document.createElement('link');
                    link.rel = 'preconnect';
                    link.href = 'https://' + domain;
                    document.head.appendChild(link);
                });
            }
        """

        const val OPTIMIZE_STYLES = """
            function optimizeStyles() {
                // Load non-critical stylesheets asynchronously
                var styles = document.querySelectorAll('link[rel="stylesheet"]');
                styles.forEach(function(style, index) {
                    // Consider the first stylesheet critical, load others asynchronously
                    if (index > 0) {
                        style.setAttribute('media', 'print');
                        style.setAttribute('onload', "this.media='all'");
                    }
                });
            }
        """

        const val COLLECT_METRICS = """
            function collectMetrics() {
                if (window.performance && window.performance.timing) {
                    window.addEventListener('load', function() {
                        setTimeout(function() {
                            var timing = performance.timing;
                            var pageLoadTime = timing.loadEventEnd - timing.navigationStart;
                            var domReadyTime = timing.domComplete - timing.domLoading;
                            
                            console.log('Page loading time: ' + pageLoadTime + 'ms');
                            console.log('DOM ready time: ' + domReadyTime + 'ms');
                        }, 0);
                    });
                }
            }
        """

        const val INIT_OPTIMIZATION = """
            // Start application
            // Wait for DOMContentLoaded event
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
            
            // Wait for window.load event (after all resources are loaded)
            window.addEventListener('load', function() {
                lazyLoadImages();
                optimizeNetworkConnections();
            });
            
            console.log('AsforceBrowser: Page loading optimizations applied');
        """

        const val EARLY_PAGE_LOAD_OPTIMIZATION = """
            (function() {
                // First load critical CSS and JS
                document.documentElement.style.visibility = 'visible';
                
                // Reduce console messages (improves performance)
                if (window.console) {
                    var originalLog = console.log;
                    console.log = function() {
                        // Only pass important messages
                        if (arguments[0] && typeof arguments[0] === 'string' && 
                            (arguments[0].includes('error') || 
                             arguments[0].includes('AsforceBrowser'))) {
                            return originalLog.apply(console, arguments);
                        }
                    };
                }
            })();
        """

        const val REDUCE_CPU_USAGE = """
            (function() {
                // Settings that optimize CPU usage
                // Clean up unnecessary timers and animations
                var highCPUEvents = ['mousemove', 'touchmove', 'scroll'];
                highCPUEvents.forEach(function(eventType) {
                    // Use passive event listeners
                    document.addEventListener(eventType, function() {}, { passive: true });
                });
                
                // Optimize timers
                if (window.performance && window.performance.now) {
                    var start = performance.now();
                    window._requestAnimationFrame = window.requestAnimationFrame;
                    window.requestAnimationFrame = function(callback) {
                        return window._requestAnimationFrame(function(timestamp) {
                            if (performance.now() - start < 500) { // Run normally for the first 500ms
                                callback(timestamp);
                            } else {
                                // Slow down after 500ms to preserve CPU
                                setTimeout(function() {
                                    callback(performance.now());
                                }, 16); // Run at lower rate than ~60fps
                            }
                        });
                    };
                }
            })();
        """
    }
}