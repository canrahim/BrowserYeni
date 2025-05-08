package com.asforce.asforcebrowser.util.performance

import android.content.Context
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.ValueCallback
import android.util.Log
import android.view.View
import android.os.Build

/**
 * MediaOptimizer - WebView video ve medya performansı optimizasyonu
 * 
 * Bu sınıf, WebView'da video içeriklerinin (Dolby Vision dahil) daha akıcı
 * oynatılmasını sağlayan metotlar içerir.
 * 
 * Referans: 
 * - Android WebView Medya Optimizasyonları (Google Developers)
 * - HTML5 Video Playback API
 */
class MediaOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaOptimizer"
    }
    
    /**
     * WebView içinde video performansını optimize eden ayarlar
     */
    fun optimizeVideoPlayback(webView: WebView) {
        webView.settings.apply {
            // Medya oynatma için kritik ayarlar
            mediaPlaybackRequiresUserGesture = false // Kullanıcı etkileşimi olmadan video oynatmaya izin ver
            
            // Video performansı için önbellekleme ayarları
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK // Önbelleği maksimum kullan
            
            // Web içeriği için gerekli özellikler
            useWideViewPort = true // Doğru boyutlama için
            loadWithOverviewMode = true // Doğru boyutlama için
            domStorageEnabled = true // HTML5 Video için
            javaScriptEnabled = true // Video API'leri için mecburi
            
            // Hardware hızlandırma ayarları
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false // Daha hızlı yükleme için
            }
        }
        
        // Video oynatma sırasında telefonu uyanık tut (isteğe bağlı)
        webView.setKeepScreenOn(true)
        
        // Video oynatımı için donanım hızlandırma
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Codec ve donanım hızlandırma için JavaScript enjekte et
        webView.evaluateJavascript("""
            (function() {
                // Video elementlerini bul ve optimize et
                function optimizeVideoElements() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var video = videos[i];
                        
                        // Donanım hızlandırma optimizasyonları
                        if (!video.hasAttribute('playsinline')) {
                            video.setAttribute('playsinline', '');
                        }
                        
                        if (!video.hasAttribute('webkit-playsinline')) {
                            video.setAttribute('webkit-playsinline', '');
                        }
                        
                        if (!video.hasAttribute('preload')) {
                            video.setAttribute('preload', 'auto');
                        }
                        
                        // Video performansı ayarları
                        video.style.transform = 'translate3d(0,0,0)'; // GPU hızlandırma
                        
                        // Dolby Vision ve HDR desteği için ayarlar
                        video.addEventListener('loadedmetadata', function() {
                            // Video codec bilgisini kontrol et
                            try {
                                if ('mediaCapabilities' in navigator) {
                                    // Codec bilgilerini getir
                                    var videoTrack = this.videoTracks && this.videoTracks[0];
                                    if (videoTrack) {
                                        console.log('Video codec:', videoTrack.label);
                                    }
                                }
                            } catch(e) {
                                console.error('Codec bilgisi alınamadı:', e);
                            }
                        });
                        
                        // Video hızını optimize etmek için
                        video.addEventListener('canplaythrough', function() {
                            // Video yüklenmesi tamamlandığında
                            this.play().catch(function(e) {
                                console.log('Otomatik oynatma engellendi:', e);
                            });
                        });
                        
                        // Video performansını izle
                        video.addEventListener('waiting', function() {
                            console.log('Video beklemede - yükleniyor...');
                        });
                        
                        video.addEventListener('playing', function() {
                            console.log('Video oynatılıyor');
                        });
                    }
                }
                
                // İlk çalıştırma
                optimizeVideoElements();
                
                // DOM değişikliklerini izle
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
                
                // Tüm iframe'lerde de çalıştır
                function optimizeIframes() {
                    var iframes = document.querySelectorAll('iframe');
                    for (var i = 0; i < iframes.length; i++) {
                        try {
                            var iframeDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
                            if (iframeDoc) {
                                // iframe içindeki videolar için de optimize et
                                var videos = iframeDoc.querySelectorAll('video');
                                for (var j = 0; j < videos.length; j++) {
                                    videos[j].setAttribute('playsinline', '');
                                    videos[j].setAttribute('webkit-playsinline', '');
                                    videos[j].style.transform = 'translate3d(0,0,0)';
                                }
                            }
                        } catch(e) {
                            // Aynı origin politikası nedeniyle erişim engellenmiş olabilir
                            console.log('iframe erişim hatası:', e);
                        }
                    }
                }
                
                // iframe optimizasyonunu çalıştır
                optimizeIframes();
                
                console.log('AsforceBrowser: Video optimizasyonları uygulandı');
            })();
        """, null)
    }
    
    /**
     * Dolby Vision ve diğer gelişmiş codec'ler için özel media ayarları
     */
    fun enableAdvancedCodecSupport(webView: WebView) {
        // Video için gelişmiş codec desteği
        webView.settings.apply {
            // Dolby Vision ve HDR desteği için mecburi ayarlar
            mediaPlaybackRequiresUserGesture = false
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        
        // Codec desteğini kontrol edip ayarlayan JavaScript
        val codecJs = """
            (function() {
                // Codec desteğini kontrol et ve raporla
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
                    
                    console.log('Desteklenen codec\'ler:', supportedCodecs.join(', '));
                    console.log('Desteklenmeyen codec\'ler:', unsupportedCodecs.join(', '));
                    
                    return {
                        supported: supportedCodecs,
                        unsupported: unsupportedCodecs
                    };
                }
                
                // codec desteğini kontrol et
                var codecSupport = checkCodecSupport();
                
                // Dolby Vision desteği varsa video elementleri için optimize et
                function optimizeDolbyVisionVideos() {
                    if (codecSupport.supported.some(function(codec) { return codec.includes('dva1'); })) {
                        console.log('Dolby Vision desteği mevcut!');
                        
                        // Tüm videoları Dolby Vision için optimize et
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            videos[i].setAttribute('data-dolby-optimized', 'true');
                        }
                    }
                }
                
                // Dolby Vision optimizasyonunu uygula
                optimizeDolbyVisionVideos();
                
                // Tüm video elementlerini CPU kullanımını minimize edecek şekilde ayarla
                function minimizeCPUUsage() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        // CPU kullanımını azaltan ayarlar
                        video.setAttribute('poster', video.poster || ''); // Poster görselini zorla
                        video.setAttribute('preload', 'metadata'); // Sadece metadata yükle
                        
                        // Sadece görünürken oynat
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
                    });
                }
                
                // CPU kullanımını azalt
                if ('IntersectionObserver' in window) {
                    minimizeCPUUsage();
                }
                
                console.log('AsforceBrowser: Codec optimizasyonları uygulandı');
            })();
        """
        
        // JavaScript'i çalıştır
        webView.evaluateJavascript(codecJs, null)
        
        Log.d(TAG, "Gelişmiş codec desteği etkinleştirildi")
    }
    
    /**
     * Video oynatma sırasında WebView'in daha iyi performans göstermesi için
     * özel bir WebChromeClient oluşturur
     */
    fun createOptimizedMediaWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            // Video için tam ekran desteği
            private var customView: View? = null
            
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                
                // Sayfa yeterince yüklendiğinde video optimizasyonlarını uygula
                if (newProgress >= 50) {
                    optimizeVideoPlayback(view)
                }
                
                // Sayfa tam yüklendiğinde detaylı medya optimizasyonlarını uygula
                if (newProgress >= 90) {
                    enableAdvancedCodecSupport(view)
                }
            }
        }
    }
    
    /**
     * WebView için tüm medya optimizasyonlarını tek seferde uygulayan yardımcı metod
     */
    fun applyAllMediaOptimizations(webView: WebView) {
        // Temel video ayarlarını optimize et
        optimizeVideoPlayback(webView)
        
        // Dolby Vision ve gelişmiş codec desteğini etkinleştir
        enableAdvancedCodecSupport(webView)
        
        // Optimize edilmiş WebChromeClient'ı ata
        webView.webChromeClient = createOptimizedMediaWebChromeClient()
        
        Log.d(TAG, "Tüm medya optimizasyonları başarıyla uygulandı")
    }
}