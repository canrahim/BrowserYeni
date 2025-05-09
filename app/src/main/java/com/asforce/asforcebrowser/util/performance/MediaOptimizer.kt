package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * MediaOptimizer - WebView video and media performance optimization
 *
 * This class contains methods to ensure smoother playback of video content
 * (including Dolby Vision) in WebView.
 *
 * References:
 * - Android WebView Media Optimizations (Google Developers)
 * - HTML5 Video Playback API
 */
class MediaOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "MediaOptimizer"

        // Device capabilities
        private val SUPPORTS_HDR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        private val SUPPORTS_DOLBY_VISION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * Settings that optimize video playback in WebView
     */
    fun optimizeVideoPlayback(webView: WebView) {
        webView.settings.apply {
            // Critical settings for media playback
            mediaPlaybackRequiresUserGesture = false // Allow video playback without user interaction

            // Caching settings for video performance
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK // Maximize cache usage

            // Required features for web content
            useWideViewPort = true // For correct sizing
            loadWithOverviewMode = true // For correct sizing
            domStorageEnabled = true // For HTML5 Video
            javaScriptEnabled = true // Mandatory for video APIs

            // Hardware acceleration settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false // For faster loading
            }
        }

        // Keep phone awake during video playback (optional)
        webView.setKeepScreenOn(true)

        // Hardware acceleration for video playback
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Inject JavaScript for codec and hardware acceleration
        webView.evaluateJavascript(JsScripts.VIDEO_OPTIMIZATION, null)
    }

    /**
     * Special media settings for Dolby Vision and other advanced codecs
     */
    fun enableAdvancedCodecSupport(webView: WebView) {
        // Advanced codec support for video
        webView.settings.apply {
            // Mandatory settings for Dolby Vision and HDR support
            mediaPlaybackRequiresUserGesture = false
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // JavaScript that checks and configures codec support
        webView.evaluateJavascript(JsScripts.CODEC_OPTIMIZATION, null)

        Log.d(TAG, "Advanced codec support enabled")
    }

    /**
     * Creates a specialized WebChromeClient for better WebView performance
     * during video playback
     */
    fun createOptimizedMediaWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            // Support for fullscreen video
            private var customView: View? = null

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)

                // Apply video optimizations when page is loaded enough
                if (newProgress >= 50) {
                    optimizeVideoPlayback(view)
                }

                // Apply detailed media optimizations when page is fully loaded
                if (newProgress >= 90) {
                    enableAdvancedCodecSupport(view)
                }
            }
        }
    }

    /**
     * Helper method that applies all media optimizations to WebView at once
     */
    fun applyAllMediaOptimizations(webView: WebView) {
        // Optimize basic video settings
        optimizeVideoPlayback(webView)

        // Enable Dolby Vision and advanced codec support
        enableAdvancedCodecSupport(webView)

        // Assign optimized WebChromeClient
        webView.webChromeClient = createOptimizedMediaWebChromeClient()

        Log.d(TAG, "All media optimizations successfully applied")
    }

    /**
     * Collection of JavaScript snippets used for media optimization
     */
    private object JsScripts {
        const val VIDEO_OPTIMIZATION = """
            (function() {
                // Find and optimize video elements
                function optimizeVideoElements() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var video = videos[i];
                        
                        // Hardware acceleration optimizations
                        if (!video.hasAttribute('playsinline')) {
                            video.setAttribute('playsinline', '');
                        }
                        
                        if (!video.hasAttribute('webkit-playsinline')) {
                            video.setAttribute('webkit-playsinline', '');
                        }
                        
                        if (!video.hasAttribute('preload')) {
                            video.setAttribute('preload', 'auto');
                        }
                        
                        // Video performance settings
                        video.style.transform = 'translate3d(0,0,0)'; // GPU acceleration
                        
                        // Dolby Vision and HDR support settings
                        video.addEventListener('loadedmetadata', function() {
                            // Check video codec information
                            try {
                                if ('mediaCapabilities' in navigator) {
                                    // Get codec information
                                    var videoTrack = this.videoTracks && this.videoTracks[0];
                                    if (videoTrack) {
                                        console.log('Video codec:', videoTrack.label);
                                    }
                                }
                            } catch(e) {
                                console.error('Could not get codec info:', e);
                            }
                        });
                        
                        // To optimize video speed
                        video.addEventListener('canplaythrough', function() {
                            // When video loading is complete
                            this.play().catch(function(e) {
                                console.log('Auto-play blocked:', e);
                            });
                        });
                        
                        // Monitor video performance
                        video.addEventListener('waiting', function() {
                            console.log('Video waiting - loading...');
                        });
                        
                        video.addEventListener('playing', function() {
                            console.log('Video playing');
                        });
                    }
                }
                
                // First run
                optimizeVideoElements();
                
                // Monitor DOM changes
                if (window.MutationObserver) {
                    new MutationObserver(function(mutations) {
                        for (var i = 0; i < mutations.length; i++) {
                            if (mutations[i].addedNodes.length > 0) {
                                optimizeVideoElements();
                                break;
                            }
                        }
                    }).observe(document.documentElement, { childList: true, subtree: true });
                }
                
                // Run in all iframes as well
                function optimizeIframes() {
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        try {
                            var iframeDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
                            if (iframeDoc) {
                                // Optimize videos within iframes as well
                                var videos = iframeDoc.querySelectorAll('video');
                                for (var j = 0; j < videos.length; j++) {
                                    videos[j].setAttribute('playsinline', '');
                                    videos[j].setAttribute('webkit-playsinline', '');
                                    videos[j].style.transform = 'translate3d(0,0,0)';
                                }
                            }
                        } catch(e) {
                            // Access may be blocked due to same-origin policy
                            console.log('iframe access error:', e);
                        }
                    }
                }
                
                // Run iframe optimization
                optimizeIframes();
                
                console.log('AsforceBrowser: Video optimizations applied');
            })();
        """

        const val CODEC_OPTIMIZATION = """
            (function() {
                // Check and report codec support
                function checkCodecSupport() {
                    var codecs = [
                        'video/mp4; codecs="avc1.640028"', // Standard H.264 High Profile
                        'video/mp4; codecs="hev1.1.6.L93.B0"', // HEVC/H.265
                        'video/mp4; codecs="dva1.08.01"', // Dolby Vision
                        'video/mp4; codecs="hvc1.1.6.L93.B0"', // HEVC Main
                        'audio/mp4; codecs="mp4a.40.2"', // AAC LC
                        'audio/mp4; codecs="ec-3"', // Dolby Digital Plus
                        'audio/mp4; codecs="ac-3"' // Dolby Digital
                    ];
                    
                    var supportedCodecs = [];
                    var unsupportedCodecs = [];
                    
                    codecs.forEach(function(codec) {
                        var supported = MediaSource && MediaSource.isTypeSupported ? 
                                        MediaSource.isTypeSupported(codec) : 
                                        'canPlayType' in document.createElement('video') ? 
                                        document.createElement('video').canPlayType(codec) !== '' : 
                                        false;
                        
                        if (supported) {
                            supportedCodecs.push(codec);
                        } else {
                            unsupportedCodecs.push(codec);
                        }
                    });
                    
                    console.log('Supported codecs:', supportedCodecs.join(', '));
                    console.log('Unsupported codecs:', unsupportedCodecs.join(', '));
                    
                    return {
                        supported: supportedCodecs,
                        unsupported: unsupportedCodecs
                    };
                }
                
                // Check codec support
                var codecSupport = checkCodecSupport();
                
                // Optimize video elements for Dolby Vision if supported
                function optimizeDolbyVisionVideos() {
                    if (codecSupport.supported.some(function(codec) { return codec.includes('dva1'); })) {
                        console.log('Dolby Vision support available!');
                        
                        // Optimize all videos for Dolby Vision
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            videos[i].setAttribute('data-dolby-optimized', 'true');
                        }
                    }
                }
                
                // Apply Dolby Vision optimization
                optimizeDolbyVisionVideos();
                
                // Configure all video elements to minimize CPU usage
                function minimizeCPUUsage() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        // Settings that reduce CPU usage
                        video.setAttribute('poster', video.poster || ''); // Force poster image
                        video.setAttribute('preload', 'metadata'); // Load only metadata
                        
                        // Play only when visible
                        if ('IntersectionObserver' in window) {
                            var observer = new IntersectionObserver(function(entries) {
                                entries.forEach(function(entry) {
                                    if (entry.isIntersecting) {
                                        if (video.paused) video.play().catch(function() {});
                                    } else {
                                        if (!video.paused) video.pause();
                                    }
                                });
                            }, { threshold: 0.1 });
                            
                            observer.observe(video);
                        }
                    });
                }
                
                // Reduce CPU usage
                if ('IntersectionObserver' in window) {
                    minimizeCPUUsage();
                }
                
                console.log('AsforceBrowser: Codec optimizations applied');
            })();
        """
    }
}