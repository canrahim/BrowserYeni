package com.asforce.asforcebrowser.download

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class to set up download functionality for WebViews.
 * Optimized with coroutines and better JavaScript injection management.
 */
class WebViewDownloadHelper(context: Context) {
    companion object {
        private const val TAG = "WebViewDownloadHelper"
        private const val DEBUG = false // Turn off for production

        // JavaScript template for download button handler - GÜNCELLENMIŞ
        private const val DOWNLOAD_BUTTON_HANDLER_JS = """
            (function() {
                console.log('Injecting download button handler');
                
                // Rapor butonlarını da yakalamak için
                var downloadLinks = document.querySelectorAll(
                    'a[title="İndir"], a.btn-success, a:contains("İndir"), button:contains("İndir"),' +
                    'a[title="Rapor"], a.btn-danger[href*="PdfForEK"], a[href*="PdfForEK"]'
                );
                
                console.log('Found download buttons: ' + downloadLinks.length);
                
                for (var i = 0; i < downloadLinks.length; i++) {
                    var link = downloadLinks[i];
                    
                    // Rapor linklerini özel olarak işle
                    if (link.href && link.href.includes('PdfForEK')) {
                        if (!link.hasAttribute('data-download-handled')) {
                            link.setAttribute('data-download-handled', 'true');
                            link.addEventListener('click', function(e) {
                                e.preventDefault();
                                console.log('Rapor indirme URL\'si yakalandı: ' + this.href);
                                window.NativeDownloader.handleDownloadUrl(this.href);
                                return false;
                            });
                        }
                    }
                    // Diğer linkler için eski işleme
                    else if (!link.hasAttribute('data-download-handled')) {
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
        """
    }

    private val downloadManager: DownloadManager = DownloadManager.getInstance(context)
    private val context: Context = context.applicationContext
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Cache for processed URLs to avoid duplicate processing
    private val processedUrls = ConcurrentHashMap<String, Boolean>()

    /**
     * Sets up WebView downloads with optimized event handling.
     */
    fun setupWebViewDownloads(webView: WebView) {
        // Set download listener with improved processing
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            coroutineScope.launch {
                handleDownloadRequest(url, userAgent, contentDisposition, mimeType, contentLength)
            }
        }

        // Add JavaScript interface
        setupJavaScriptInterface(webView)

        // Custom WebViewClient setup
        setupCustomWebViewClient(webView)
    }

    private suspend fun handleDownloadRequest(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        if (DEBUG) {
            Log.d(TAG, "Download initiated - URL: $url")
            Log.d(TAG, "Content-Disposition: $contentDisposition")
            Log.d(TAG, "Original MIME type: $mimeType")
        }

        var fileName = extractBestFileName(url, contentDisposition)
        val finalMimeType: String = improveMimeType(url, fileName, mimeType ?: "application/octet-stream")
        fileName = ensureProperExtension(fileName, finalMimeType)

        if (DEBUG) {
            Log.d(TAG, "Final filename: $fileName")
            Log.d(TAG, "Final MIME type: $finalMimeType")
        }

        // YENI EKLENEN: PdfForEK için direk indirme
        if (url.contains("/PdfForEK")) {
            withContext(Dispatchers.Main) {
                downloadManager.downloadFile(url, fileName, finalMimeType, userAgent, contentDisposition)
            }
            return
        }

        // Check if large file for direct download
        if (contentLength > 10 * 1024 * 1024) { // 10MB+
            withContext(Dispatchers.Main) {
                downloadManager.downloadFile(url, fileName, finalMimeType, userAgent, contentDisposition)
            }
            return
        }

        // Format size info
        val sizeInfo = formatFileSize(contentLength)
        val isImage = finalMimeType.startsWith("image/")

        // Show download confirmation dialog
        withContext(Dispatchers.Main) {
            downloadManager.showDownloadConfirmationDialog(
                url, fileName, finalMimeType, userAgent,
                contentDisposition, sizeInfo, isImage
            )
        }
    }

    private fun extractBestFileName(url: String, contentDisposition: String?): String {
        // 1. Try Content-Disposition first
        var fileName = downloadManager.extractFilenameFromContentDisposition(contentDisposition)

        // 2. Extract from URL if needed
        if (fileName.isNullOrEmpty()) {
            fileName = downloadManager.extractFilenameFromUrl(url)
        }

        // 3. Fallback to URLUtil
        if (fileName.isNullOrEmpty()) {
            fileName = URLUtil.guessFileName(url, contentDisposition, null)
        }

        return fileName ?: "download"
    }

    private fun improveMimeType(url: String, fileName: String?, mimeType: String): String {
        // Special handling for SoilContinuity
        if (url.contains("SoilContinuity", ignoreCase = true) ||
            fileName?.contains("SoilContinuity", ignoreCase = true) == true) {
            return "image/jpeg"
        }

        // Fix generic MIME types
        if (mimeType.isEmpty() || mimeType == "application/octet-stream") {
            return when {
                url.contains(".pdf", ignoreCase = true) -> "application/pdf"
                url.contains(".jpg", ignoreCase = true) || url.contains(".jpeg", ignoreCase = true) -> "image/jpeg"
                url.contains(".png", ignoreCase = true) -> "image/png"
                else -> mimeType
            }
        }

        return mimeType
    }

    private fun ensureProperExtension(fileName: String?, mimeType: String?): String {
        var result = fileName ?: "download"

        // Special handling for SoilContinuity
        if (result.contains("SoilContinuity", ignoreCase = true)) {
            if (!result.lowercase().endsWith(".jpg") && !result.lowercase().endsWith(".jpeg")) {
                val baseName = result.substringBeforeLast('.')
                result = "$baseName.jpg"
            }
            return result
        }

        // General extension fixing
        return when {
            mimeType == "application/pdf" && !result.lowercase().endsWith(".pdf") -> {
                "${result.substringBeforeLast('.')}.pdf"
            }
            (mimeType == "image/jpeg" || mimeType == "image/jpg") &&
                    !result.lowercase().run { endsWith(".jpg") || endsWith(".jpeg") } -> {
                "${result.substringBeforeLast('.')}.jpg"
            }
            mimeType == "image/png" && !result.lowercase().endsWith(".png") -> {
                "${result.substringBeforeLast('.')}.png"
            }
            else -> result
        }
    }

    private fun formatFileSize(contentLength: Long): String? {
        if (contentLength <= 0) return null

        val sizeMB = contentLength / (1024f * 1024f)
        return if (sizeMB >= 1) {
            String.format("%.1f MB", sizeMB)
        } else {
            val sizeKB = contentLength / 1024f
            String.format("%.0f KB", sizeKB)
        }
    }

    /**
     * JavaScript interface setup with optimized methods
     */
    private fun setupJavaScriptInterface(webView: WebView) {
        val jsInterface = object {
            @JavascriptInterface
            fun downloadImage(imageUrl: String) {
                if (DEBUG) Log.d(TAG, "JS image download request: $imageUrl")
                coroutineScope.launch {
                    val imageDownloader = ImageDownloader(context)
                    imageDownloader.downloadImage(imageUrl, webView)
                }
            }

            @JavascriptInterface
            fun handleDownloadUrl(url: String) {
                if (DEBUG) Log.d(TAG, "JS download URL: $url")
                coroutineScope.launch {
                    if (isDownloadUrl(url)) {
                        handleSpecialDownloadUrl(url)
                    } else {
                        val fileName = downloadManager.extractFilenameFromUrl(url)
                        val userAgent = webView.settings.userAgentString
                        downloadManager.downloadFile(url, fileName, null, userAgent, null)
                    }
                }
            }
        }

        webView.addJavascriptInterface(jsInterface, "NativeDownloader")

        // Inject download button handler
        injectDownloadButtonHandler(webView)
    }

    /**
     * Injects download button handler JavaScript
     */
    private fun injectDownloadButtonHandler(webView: WebView) {
        webView.evaluateJavascript("javascript:$DOWNLOAD_BUTTON_HANDLER_JS", null)
    }

    /**
     * Custom WebViewClient setup with optimized URL handling
     */
    private fun setupCustomWebViewClient(webView: WebView) {
        val originalClient = webView.webViewClient

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                originalClient.onPageFinished(view, url)
                // Inject JavaScript after page load
                injectDownloadButtonHandler(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (isDownloadUrl(url)) {
                    coroutineScope.launch {
                        handleSpecialDownloadUrl(url)
                    }
                    true
                } else {
                    originalClient.shouldOverrideUrlLoading(view, request)
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (isDownloadUrl(url) && processedUrls.putIfAbsent(url, true) == null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        coroutineScope.launch {
                            handleSpecialDownloadUrl(url)
                        }
                    }
                }

                return originalClient.shouldInterceptRequest(view, request)
                    ?: super.shouldInterceptRequest(view, request)
            }
        }
    }

    /**
     * Checks if URL is a download URL - GÜNCELLENMIŞ
     */
    private fun isDownloadUrl(url: String?): Boolean {
        if (url == null) return false
        return url.contains("/EXT/PKControl/DownloadFile") ||
                url.contains("/DownloadFile") ||
                url.contains("/PdfForEK") ||  // YENİ EKLEME
                url.contains("/EXT/PKControl/PdfForEK") ||  // YENİ EKLEME
                (url.contains("download") && url.contains("id="))
    }

    /**
     * Handles special download URLs with improved async processing - GÜNCELLENMIŞ
     */
    private suspend fun handleSpecialDownloadUrl(url: String) {
        if (DEBUG) Log.d(TAG, "Handling special download URL: $url")

        try {
            val (fileName, mimeType, isImage) = processSpecialUrl(url)

            withContext(Dispatchers.Main) {
                when {
                    // YENİ EKLENEN: PdfForEK için özel işleme
                    url.contains("/PdfForEK") -> {
                        // Rapor dosyalarını hemen indirmeye başla
                        downloadManager.downloadFile(url, fileName, mimeType, "Mozilla/5.0", null)
                    }
                    isImage && fileName.contains("SoilContinuity", ignoreCase = true) -> {
                        val imageDownloader = ImageDownloader(context)
                        imageDownloader.downloadImage(url, null)
                    }
                    isImage && mimeType.startsWith("image/") -> {
                        val imageDownloader = ImageDownloader(context)
                        imageDownloader.downloadImage(url, null)
                    }
                    else -> {
                        val contentDisposition = fetchContentDisposition(url)
                        downloadManager.downloadFile(url, fileName, mimeType, "Mozilla/5.0", contentDisposition)
                    }
                }
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error processing download URL", e)
            // Fallback to simpler download
            val fileName = downloadManager.extractFilenameFromUrl(url)
            withContext(Dispatchers.Main) {
                downloadManager.downloadFile(url, fileName, null, "Mozilla/5.0", null)
            }
        }
    }

    private suspend fun processSpecialUrl(url: String): Triple<String, String, Boolean> = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(url)
        val type = uri.getQueryParameter("type")
        val id = uri.getQueryParameter("id")
        val customerId = uri.getQueryParameter("customerId")
        val format = uri.getQueryParameter("format")
        var fileName = "download_${System.currentTimeMillis()}"
        var mimeType: String? = null
        var isImage = false

        // YENİ EKLENEN: PdfForEK için özel işleme
        if (url.contains("/PdfForEK")) {
            mimeType = "application/pdf"
            fileName = when {
                customerId != null && type != null -> "Rapor_${customerId}_${type}_${System.currentTimeMillis()}.pdf"
                customerId != null -> "Rapor_${customerId}_${System.currentTimeMillis()}.pdf"
                else -> "Rapor_${System.currentTimeMillis()}.pdf"
            }
            return@withContext Triple(fileName, mimeType, false)
        }

        // Special handling for SoilContinuity
        if (url.contains("SoilContinuity", ignoreCase = true)) {
            isImage = true
            mimeType = "image/jpeg"
        }

        // Determine file type from URL parameters
        when {
            url.contains(".pdf", ignoreCase = true) || format == "pdf" -> {
                mimeType = "application/pdf"
            }
            url.contains(".jpg", ignoreCase = true) || url.contains(".jpeg", ignoreCase = true) ||
                    format == "jpg" || format == "jpeg" -> {
                isImage = true
                mimeType = "image/jpeg"
            }
            url.contains(".png", ignoreCase = true) || format == "png" -> {
                isImage = true
                mimeType = "image/png"
            }
        }

        // Extract filename from parameters
        if (type != null && type.startsWith("F") && type.length > 1) {
            fileName = type.substring(1)

            // Special handling for SoilContinuity
            if (fileName.contains("SoilContinuity", ignoreCase = true)) {
                isImage = true
                mimeType = "image/jpeg"
                fileName = if (!id.isNullOrEmpty()) {
                    "${id}_${fileName}.jpg"
                } else {
                    "${fileName}.jpg"
                }
            }
        } else if (!id.isNullOrEmpty()) {
            fileName = "download_$id"
        }

        // Ensure proper extension
        fileName = downloadManager.ensureCorrectFileExtension(fileName, mimeType)

        Triple(fileName, mimeType ?: "application/octet-stream", isImage)
    }

    private suspend fun fetchContentDisposition(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val urlObj = java.net.URL(url)
            val connection = urlObj.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connect()
            val disposition = connection.getHeaderField("Content-Disposition")
            connection.disconnect()
            disposition
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Could not fetch Content-Disposition: ${e.message}")
            null
        }
    }

    /**
     * Returns the DownloadManager instance
     */
    fun getDownloadManager(): DownloadManager {
        return downloadManager
    }

    /**
     * Clean up resources when helper is no longer needed
     */
    fun cleanup() {
        coroutineScope.cancel()
        processedUrls.clear()
        downloadManager.unregisterDownloadReceiver()
    }
}