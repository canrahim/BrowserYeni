package com.asforce.asforcebrowser.download

import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.JavascriptInterface

/**
 * Helper class to set up download functionality for WebViews.
 */
class WebViewDownloadHelper(context: Context) {
    companion object {
        private const val TAG = "WebViewDownloadHelper"
    }
    
    private val downloadManager: DownloadManager
    private val context: Context = context.applicationContext
    
    init {
        this.downloadManager = DownloadManager.getInstance(context)
    }
    
    /**
     * Sets up WebView downloads.
     *
     * @param webView The WebView to set up downloads for
     */
    fun setupWebViewDownloads(webView: WebView) {
        // Standart indirme dinleyicisi
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            // Better filename extraction
            var fileName: String? = null
            
            Log.d(TAG, "Download initiated - URL: $url")
            Log.d(TAG, "Content-Disposition: $contentDisposition")
            Log.d(TAG, "Original MIME type: $mimeType")
            
            // 1. First try to extract from Content-Disposition
            if (!contentDisposition.isNullOrEmpty()) {
                fileName = downloadManager.extractFilenameFromContentDisposition(contentDisposition)
                Log.d(TAG, "Filename from Content-Disposition: $fileName")
            }
            
            // 2. If that fails, try URL-based extraction
            if (fileName.isNullOrEmpty()) {
                fileName = downloadManager.extractFilenameFromUrl(url)
                Log.d(TAG, "Filename extracted from URL: $fileName")
            }
            
            // 3. As a last resort, use URLUtil
            if (fileName.isNullOrEmpty()) {
                fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                Log.d(TAG, "Filename from URLUtil: $fileName")
            }
            
            // Fix MIME type for PDF files, JPGs and other binary files
            var finalMimeType = mimeType
            
            // SoilContinuity için özel kontrol
            if (url.contains("SoilContinuity", ignoreCase = true) || 
                fileName?.contains("SoilContinuity", ignoreCase = true) == true) {
                finalMimeType = "image/jpeg"
                if (fileName != null && !fileName.lowercase().endsWith(".jpg") && 
                    !fileName.lowercase().endsWith(".jpeg")) {
                    // Dosya adından .bin gibi yanlus uzanti kaldır ve .jpg ekle
                    var name = fileName
                    val lastDot = name.lastIndexOf(".")
                    if (lastDot > 0) {
                        name = name.substring(0, lastDot)
                    }
                    fileName = "$name.jpg"
                }
                Log.d(TAG, "Fixed SoilContinuity - MIME: $finalMimeType, filename: $fileName")
            }
            else if (finalMimeType.isNullOrEmpty() || finalMimeType == "application/octet-stream") {
                // Check URL for PDF indicators
                if (url.lowercase().contains(".pdf")) {
                    finalMimeType = "application/pdf"
                    if (fileName != null && !fileName.lowercase().endsWith(".pdf")) {
                        // Make sure filename has .pdf extension
                        var name = fileName
                        val lastDot = name.lastIndexOf(".")
                        if (lastDot > 0) {
                            name = name.substring(0, lastDot)
                        }
                        fileName = "$name.pdf"
                    }
                    Log.d(TAG, "Fixed PDF MIME type and filename: $fileName")
                }
                // Check for JPEG files
                else if (url.lowercase().contains(".jpg") || url.lowercase().contains(".jpeg")) {
                    finalMimeType = "image/jpeg"
                    // Make sure filename has .jpg extension
                    if (fileName != null && !fileName.lowercase().endsWith(".jpg") && !fileName.lowercase().endsWith(".jpeg")) {
                        var name = fileName
                        val lastDot = name.lastIndexOf(".")
                        if (lastDot > 0) {
                            name = name.substring(0, lastDot)
                        }
                        fileName = "$name.jpg"
                    }
                    Log.d(TAG, "Fixed JPG MIME type and filename: $fileName")
                }
                // Check for PNG files
                else if (url.lowercase().contains(".png")) {
                    finalMimeType = "image/png"
                    // Ensure extension
                    if (fileName != null && !fileName.lowercase().endsWith(".png")) {
                        var name = fileName
                        val lastDot = name.lastIndexOf(".")
                        if (lastDot > 0) {
                            name = name.substring(0, lastDot)
                        }
                        fileName = "$name.png"
                    }
                    Log.d(TAG, "Fixed PNG MIME type and filename: $fileName")
                }
            }
            
            // Log download parameters
            Log.d(TAG, "Download - URL: $url")
            Log.d(TAG, "Filename: $fileName")
            Log.d(TAG, "MIME type: $finalMimeType")
            Log.d(TAG, "Content length: $contentLength")
            
            // Hız optimizasyonu: Büyük dosyaları doğrudan indirmeye başla
            if (contentLength > 10 * 1024 * 1024) { // 10MB'dan büyük dosyalar
                // Büyük dosyalar için direkt indirme başlat
                downloadManager.downloadFile(url, fileName, finalMimeType, userAgent, contentDisposition)
                return@setDownloadListener
            }
            
            // Boyut bilgisini göster
            val sizeInfo = if (contentLength > 0) {
                val sizeMB = contentLength / (1024f * 1024f)
                if (sizeMB >= 1) {
                    String.format("%.1f MB", sizeMB)
                } else {
                    val sizeKB = contentLength / 1024f
                    String.format("%.0f KB", sizeKB)
                }
            } else null
            
            // Resim ise veya içerik türü resim ise ona göre bildirim göster
            val isImage = finalMimeType?.startsWith("image/") == true
            
            // İndirme onayı göster
            downloadManager.showDownloadConfirmationDialog(
                url, fileName ?: "download", finalMimeType, userAgent, contentDisposition, sizeInfo, isImage
            )
        }
        
        // JavaScript indirme arayüzünü ekle
        setupJavaScriptInterface(webView)
        
        // Custom WebViewClient ile indirilebilir linkleri yakala 
        setupCustomWebViewClient(webView)
    }
    
    /**
     * JavaScript arayüzünü WebView'a ekler
     */
    private fun setupJavaScriptInterface(webView: WebView) {
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun downloadImage(imageUrl: String) {
                Log.d(TAG, "JS image download request: $imageUrl")
                val imageDownloader = ImageDownloader(context)
                imageDownloader.downloadImage(imageUrl, webView)
            }
            
            @JavascriptInterface
            fun handleDownloadUrl(url: String) {
                Log.d(TAG, "JS download URL: $url")
                if (isDownloadUrl(url)) {
                    handleSpecialDownloadUrl(url)
                } else {
                    // Normal indirme
                    val fileName = downloadManager.extractFilenameFromUrl(url)
                    val userAgent = webView.settings.userAgentString
                    downloadManager.downloadFile(url, fileName, null, userAgent, null)
                }
            }
        }, "NativeDownloader")
        
        // İndirme butonlarını yakala
        injectDownloadButtonHandler(webView)
    }
    
    /**
     * İndirme butonlarını yakalamak için JavaScript enjekte eder
     */
    private fun injectDownloadButtonHandler(webView: WebView) {
        val js = """
            javascript:(function() {
                console.log('Injecting download button handler');
                var downloadLinks = document.querySelectorAll('a[title="İndir"], a.btn-success, a:contains("İndir"), button:contains("İndir")');
                console.log('Found download buttons: ' + downloadLinks.length);
                for (var i = 0; i < downloadLinks.length; i++) {
                    var link = downloadLinks[i];
                    if (!link.hasAttribute('data-download-handled')) {
                        link.setAttribute('data-download-handled', 'true');
                        var originalOnClick = link.onclick;
                        link.onclick = function(e) {
                            e.preventDefault();
                            var url = this.href || this.getAttribute('data-url') || this.getAttribute('href');
                            if (url) {
                                window.NativeDownloader.handleDownloadUrl(url);
                                return false;
                            }
                            if (originalOnClick) {
                                return originalOnClick.call(this, e);
                            }
                        };
                    }
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }
    
    /**
     * URL'nin indirilebilir bir bağlantı olup olmadığını kontrol eder
     */
    private fun isDownloadUrl(url: String?): Boolean {
        if (url == null) return false
        return url.contains("/EXT/PKControl/DownloadFile") ||
                url.contains("/DownloadFile") ||
                (url.contains("download") && url.contains("id="))
    }
    
    /**
     * Özel indirme URL'lerini işler
     */
    private fun handleSpecialDownloadUrl(url: String) {
        Log.d(TAG, "Handling special download URL: $url")
        try {
            val uri = android.net.Uri.parse(url)
            val type = uri.getQueryParameter("type")
            val id = uri.getQueryParameter("id")
            val format = uri.getQueryParameter("format")
            var fileName = "download_${System.currentTimeMillis()}"
            var mimeType: String? = null
            var isPdf = false
            var isImage = false
            
            // Special handling for URLs containing SoilContinuity which we know should be JPG
            if (url.contains("SoilContinuity", ignoreCase = true)) {
                isImage = true
                mimeType = "image/jpeg"
                Log.d(TAG, "Detected SoilContinuity JPG image from URL")
            }
            
            // Check content disposition from URL if available
            var contentDisposition: String? = null
            try {
                val urlObj = java.net.URL(url)
                val connection = urlObj.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connect()
                contentDisposition = connection.getHeaderField("Content-Disposition")
                connection.disconnect()
                if (contentDisposition != null) {
                    Log.d(TAG, "Found Content-Disposition: $contentDisposition")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not perform HEAD request: ${e.message}")
            }
            
            // Determine file type from URL and parameters
            if (url.lowercase().contains(".pdf") || 
                url.lowercase().contains("pdf=true") ||
                (format != null && format.lowercase() == "pdf")) {
                isPdf = true
                mimeType = "application/pdf"
            } else if (url.lowercase().contains(".jpg") || 
                url.lowercase().contains(".jpeg") ||
                (format != null && (format.lowercase() == "jpg" || format.lowercase() == "jpeg"))) {
                isImage = true
                mimeType = "image/jpeg"
            } else if (url.lowercase().contains(".png") ||
                (format != null && format.lowercase() == "png")) {
                isImage = true
                mimeType = "image/png"
            }
            
            // Try to extract filename from Content-Disposition if available
            if (!contentDisposition.isNullOrEmpty()) {
                val extractedName = downloadManager.extractFilenameFromContentDisposition(contentDisposition)
                if (!extractedName.isNullOrEmpty()) {
                    fileName = extractedName
                    Log.d(TAG, "Using filename from Content-Disposition: $fileName")
                    
                    // Try to determine MIME type from the filename extension
                    when {
                        fileName.lowercase().endsWith(".jpg") || fileName.lowercase().endsWith(".jpeg") -> {
                            isImage = true
                            mimeType = "image/jpeg"
                        }
                        fileName.lowercase().endsWith(".png") -> {
                            isImage = true
                            mimeType = "image/png"
                        }
                        fileName.lowercase().endsWith(".pdf") -> {
                            isPdf = true
                            mimeType = "application/pdf"
                        }
                    }
                }
            }
            
            // Extract filename from type parameter if not found in Content-Disposition
            if (type != null && type.startsWith("F") && type.length > 1) {
                val fileNameBase = type.substring(1)
                fileName = fileNameBase
                Log.d(TAG, "Using filename from type parameter: $fileName")
                
                // Special handling for SoilContinuity - make sure it's saved as JPG
                if (fileName.contains("SoilContinuity", ignoreCase = true)) {
                    isImage = true
                    mimeType = "image/jpeg"
                    fileName = if (!id.isNullOrEmpty()) {
                        "${id}_${fileName}.jpg"
                    } else {
                        "${fileName}.jpg"
                    }
                    Log.d(TAG, "Set SoilContinuity as JPG: $fileName")
                }
            } else if (!id.isNullOrEmpty() && (contentDisposition == null || fileName == "download_${System.currentTimeMillis()}")) {
                fileName = "download_$id"
                Log.d(TAG, "Using filename from id parameter: $fileName")
            }
            
            // Ensure proper extension based on type
            if (isPdf) {
                if (!fileName.lowercase().endsWith(".pdf")) {
                    fileName = fileName.replace("\\.[^.]*$".toRegex(), "") + ".pdf"
                }
            } else if (isImage) {
                // Use appropriate image extension
                if (mimeType != null && mimeType == "image/jpeg") {
                    if (!fileName.lowercase().endsWith(".jpg") && !fileName.lowercase().endsWith(".jpeg")) {
                        // Remove existing extension if any, but preserve SoilContinuity special case with .jpg
                        if (!fileName.contains("SoilContinuity.jpg", ignoreCase = true)) {
                            fileName = fileName.replace("\\.[^.]*$".toRegex(), "") + ".jpg"
                        }
                    }
                } else if (mimeType != null && mimeType == "image/png") {
                    if (!fileName.lowercase().endsWith(".png")) {
                        fileName = fileName.replace("\\.[^.]*$".toRegex(), "") + ".png"
                    }
                } else {
                    // Default to jpg for other image types
                    if (!fileName.lowercase().endsWith(".jpg") && !fileName.lowercase().endsWith(".jpeg")) {
                        fileName = fileName.replace("\\.[^.]*$".toRegex(), "") + ".jpg"
                    }
                    if (mimeType == null) {
                        mimeType = "image/jpeg"
                    }
                }
            }
            
            Log.d(TAG, "Final filename: $fileName")
            Log.d(TAG, "MIME type: $mimeType")
            
            // Use custom download with proper MIME type
            // Special handling for SoilContinuity since we know it's a JPG image
            if (fileName.contains("SoilContinuity", ignoreCase = true) && 
                !fileName.lowercase().endsWith(".jpg")) {
                fileName = "$fileName.jpg"
                mimeType = "image/jpeg"
                isImage = true
                Log.d(TAG, "Enforced JPG for SoilContinuity: $fileName")
            }
            
            when {
                mimeType != null -> {
                    // Daha güvenilir indirme için ImageDownloader kullan (görsel dosyalar için)
                    if (isImage && mimeType.startsWith("image/")) {
                        // For SoilContinuity, use ImageDownloader for best results
                        if (fileName.contains("SoilContinuity", ignoreCase = true)) {
                            val imageDownloader = ImageDownloader(context)
                            imageDownloader.downloadImage(url, null)
                            Log.d(TAG, "Using ImageDownloader for SoilContinuity with image/jpeg MIME type")
                        } else {
                            val imageDownloader = ImageDownloader(context)
                            imageDownloader.downloadImage(url, null)
                        }
                    } else {
                        // Diğer dosya türleri için standart indirme yöntemini kullan
                        downloadManager.downloadFile(url, fileName, mimeType, "Mozilla/5.0", contentDisposition)
                    }
                }
                else -> {
                    // Use custom download for other cases
                    downloadManager.startCustomDownload(url, fileName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing download URL", e)
            // Fallback to simpler download
            val fileName = downloadManager.extractFilenameFromUrl(url)
            downloadManager.downloadFile(url, fileName, null, "Mozilla/5.0", null)
        }
    }
    
    /**
     * WebView için özel istemci kurar
     */
    private fun setupCustomWebViewClient(webView: WebView) {
        val originalClient = webView.webViewClient
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                originalClient?.onPageFinished(view, url)
                // Sayfa yüklendiğinde JavaScript enjekte et
                injectDownloadButtonHandler(view)
            }
            
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                Log.d(TAG, "shouldOverrideUrlLoading: $url")
                if (isDownloadUrl(url)) {
                    handleSpecialDownloadUrl(url)
                    return true
                }
                
                return if (originalClient != null && originalClient.shouldOverrideUrlLoading(view, url)) {
                    true
                } else {
                    false
                }
            }
            
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (isDownloadUrl(url)) {
                    // Ana thread'de çalıştır
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        handleSpecialDownloadUrl(url)
                    }
                }
                
                return originalClient?.shouldInterceptRequest(view, request)
                    ?: super.shouldInterceptRequest(view, request)
            }
        }
    }
    
    /**
     * Returns the DownloadManager instance.
     *
     * @return The DownloadManager instance
     */
    fun getDownloadManager(): DownloadManager {
        return downloadManager
    }
    
    /**
     * Clean up resources when the helper is no longer needed.
     */
    fun cleanup() {
        downloadManager.unregisterDownloadReceiver()
    }
}