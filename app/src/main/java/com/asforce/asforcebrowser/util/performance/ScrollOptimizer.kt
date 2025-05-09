package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * ScrollOptimizer - WebView scrolling performance optimization
 *
 * This class contains helper methods that optimize scrolling performance
 * for WebView and adjust animated scrolling behaviors.
 *
 * References:
 * - Android WebView Performance Optimization (Google Developers)
 * - Modern JavaScript DOM Manipulation Techniques
 */
class ScrollOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "ScrollOptimizer"

        // Scrolling optimization mode
        private enum class ScriptMode {
            MINIMAL, // Minimal performance-focused optimizations
            COMPREHENSIVE // Full optimization suite
        }

        // Default configuration
        private val DEFAULT_SCRIPT_MODE = ScriptMode.MINIMAL
    }

    /**
     * Configures optimized hardware and render settings for WebView
     */
    fun optimizeWebViewHardwareRendering(webView: WebView) {
        // Hardware acceleration settings
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Settings that increase render performance
        webView.settings.apply {
            // Critical render settings
            setRenderPriority(WebSettings.RenderPriority.HIGH)

            // Cache settings - optimizes network access
            cacheMode = WebSettings.LOAD_DEFAULT

            // JavaScript and render optimization
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            // HTML5 Storage
            databaseEnabled = true
            domStorageEnabled = true

            // Render performance settings
            blockNetworkImage = false
            loadsImagesAutomatically = true

            // Compliant with modern standards
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Settings necessary for performance during scrolling
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // WebView optimized drawing settings
        webView.apply {
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        // Optimize cookie manager (reduces RAM usage)
        optimizeCookieManager(webView)
    }

    /**
     * Optimizes cookie manager settings for better performance
     */
    private fun optimizeCookieManager(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
    }

    /**
     * Injects JavaScript code for scrolling optimization
     * Uses the most minimal and efficient code
     */
    fun injectOptimizedScrollingScript(webView: WebView) {
        // Create JavaScript Bridge - bridge between Native WebView and JavaScript
        class JSInterface {
            @JavascriptInterface
            fun reportScrollPerformance(message: String) {
                Log.d(TAG, "Scroll Performance: $message")
            }
        }

        // Add JavaScript interface
        webView.addJavascriptInterface(JSInterface(), "ScrollOptimizer")

        // Run JavaScript that improves scrolling performance on the page
        // Use minimal or comprehensive option
        val scriptMode = DEFAULT_SCRIPT_MODE
        val script = when (scriptMode) {
            ScriptMode.MINIMAL -> JsScripts.MINIMAL_OPTIMIZATION
            ScriptMode.COMPREHENSIVE -> JsScripts.COMPREHENSIVE_OPTIMIZATION
        }

        // Inject script into page
        webView.evaluateJavascript(script, null)
    }

    /**
     * Creates a WebViewClient with enhanced render performance
     */
    fun createOptimizedWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Optimizations to apply when page starts loading
                optimizeWebViewHardwareRendering(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Apply scrolling optimization when page is fully loaded
                injectOptimizedScrollingScript(view)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.e(TAG, "Render process crashed: ${detail.didCrash()}")
                // Recovery strategy when render process crashes
                if (detail.didCrash()) {
                    // Refresh the webview in case of crashing
                    view.reload()
                    return true
                }
                return false
            }
        }
    }

    /**
     * Helper method that applies all optimizations to WebView at once
     */
    fun applyAllOptimizations(webView: WebView) {
        // Apply hardware acceleration optimization
        optimizeWebViewHardwareRendering(webView)

        // Assign optimized WebViewClient
        webView.webViewClient = createOptimizedWebViewClient()

        // Run JavaScript (if page is already loaded)
        if (webView.url != null) {
            injectOptimizedScrollingScript(webView)
        }

        Log.d(TAG, "All WebView optimizations applied")
    }

    /**
     * Collection of JavaScript snippets used for scrolling optimization
     */
    private object JsScripts {
        /**
         * Minimal optimization script - focused on performance
         */
        const val MINIMAL_OPTIMIZATION = """
            (function() {
                // Fix scrolling behavior
                document.documentElement.style.setProperty('scroll-behavior', 'auto', 'important');
                
                // General style injection
                var style = document.createElement('style');
                style.textContent = '* { scroll-behavior: auto !important; scroll-snap-type: none !important; }';
                document.head.appendChild(style);
                
                // Monitor scrollable elements
                function optimizeScrollables() {
                    var elements = document.querySelectorAll('[class*="scroll"],[class*="carousel"],[class*="slider"]');
                    for(var i = 0; i < elements.length; i++) {
                        if(elements[i].style) {
                            elements[i].style.setProperty('scroll-behavior', 'auto', 'important');
                            elements[i].style.setProperty('transition', 'none', 'important');
                        }
                    }
                }
                
                // First run
                optimizeScrollables();
                
                // Monitor DOM changes
                if (window.MutationObserver) {
                    new MutationObserver(optimizeScrollables).observe(
                        document.documentElement, { childList: true, subtree: true }
                    );
                }
                
                // Scroll API fixes
                if (window.scrollTo) {
                    var originalScrollTo = window.scrollTo;
                    window.scrollTo = function() {
                        if (arguments[0] && arguments[0].behavior) {
                            arguments[0].behavior = 'auto';
                        }
                        return originalScrollTo.apply(this, arguments);
                    };
                }
                
                // Report back
                if (window.ScrollOptimizer) {
                    ScrollOptimizer.reportScrollPerformance("Optimization applied");
                }
                
                console.log('AsforceBrowser: Optimized scrolling enabled');
            })();
        """

        /**
         * Comprehensive optimization script - complete intervention
         */
        const val COMPREHENSIVE_OPTIMIZATION = """
            (function() {
                // Main function for style injection
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
                        
                        /* Hide scrollbars */
                        ::-webkit-scrollbar {
                            width: 0 !important; 
                            height: 0 !important;
                            background: transparent !important;
                        }
                        
                        /* Scrollbar styles */
                        * {
                            -ms-overflow-style: none !important;
                            scrollbar-width: none !important;
                        }
                        
                        /* Intervene in common components */
                        .scrollable, [class*="scroll"], [id*="scroll"], 
                        [class*="slider"], [id*="slider"], 
                        [class*="carousel"], [id*="carousel"] {
                            scroll-behavior: auto !important;
                            transition: none !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
                
                // Disable Smooth Scroll API
                function overrideScrollAPIs() {
                    // scrollTo API fix
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
                    
                    // scrollBy API fix
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
                    
                    // scrollIntoView API fix
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
                
                // Optimize elements with scroll styles
                function optimizeScrollableElements() {
                    // Find all scrollable elements
                    var scrollers = document.querySelectorAll('[class*="scroller"], [class*="scroll"], [class*="slider"], [id*="carousel"]');
                    for (var i = 0; i < scrollers.length; i++) {
                        if (scrollers[i].style) {
                            scrollers[i].style.transition = 'none';
                            scrollers[i].style.scrollBehavior = 'auto';
                        }
                    }
                    
                    // Stop animated elements
                    var animatedElements = document.querySelectorAll('[style*="animation"], [style*="transition"]');
                    for (var i = 0; i < animatedElements.length; i++) {
                        if (animatedElements[i].style) {
                            animatedElements[i].style.animation = 'none';
                            animatedElements[i].style.transition = 'none';
                        }
                    }
                }
                
                // Monitor DOM changes
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
                
                // Apply main therapies
                injectStyles();
                overrideScrollAPIs();
                optimizeScrollableElements();
                setupMutationObserver();
                
                // Send performance data to native app
                if (window.ScrollOptimizer) {
                    ScrollOptimizer.reportScrollPerformance("Comprehensive optimization applied");
                }
                
                console.log('AsforceBrowser: Advanced scrolling optimization enabled');
            })();
        """
    }
}