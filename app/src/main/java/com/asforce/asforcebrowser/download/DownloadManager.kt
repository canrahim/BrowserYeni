package com.asforce.asforcebrowser.download

import com.asforce.asforcebrowser.R

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Manages download operations for the browser.
 */
class DownloadManager private constructor(context: Context) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val DOWNLOAD_DIRECTORY = "Downloads"
        private const val DOWNLOAD_NOTIFICATION_THRESHOLD = 2 * 1024 * 1024 // 2MB

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context).also { instance = it }
            }
        }
    }

    private val applicationContext: Context = context.applicationContext
    private var currentActivityContext: Context = context // Store the initial context (might be an Activity)
    private val activeDownloads = HashMap<Long, String>()
    private var downloadReceiver: BroadcastReceiver? = null
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val contextRef = ThreadLocal<Context>()

    init {
        contextRef.set(applicationContext)
        registerDownloadReceiver()
    }
    
    /**
     * Updates the current activity context.
     * Should be called in Activity.onResume() to ensure we always have a valid Activity context.
     *
     * @param context The new context (should be an Activity)
     */
    fun updateContext(context: Context?) {
        if (context != null) {
            this.currentActivityContext = context
            Log.d(TAG, "Context updated to: ${context.javaClass.simpleName}")
        }
    }
    
    /**
     * Adds a download to the active downloads map.
     *
     * @param downloadId The download ID
     * @param fileName The file name
     */
    fun addActiveDownload(downloadId: Long, fileName: String) {
        activeDownloads[downloadId] = fileName
    }

    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
                    val downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId != -1L) {
                        handleDownloadCompleted(downloadId)
                    }
                }
            }
        }

        val filter = IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        // Specify RECEIVER_NOT_EXPORTED flag for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            applicationContext.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            applicationContext.registerReceiver(downloadReceiver, filter)
        }
    }

    fun unregisterDownloadReceiver() {
        downloadReceiver?.let {
            try {
                applicationContext.unregisterReceiver(it)
                downloadReceiver = null
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering download receiver", e)
            }
        }
    }

    private fun handleDownloadCompleted(downloadId: Long) {
        val fileName = activeDownloads[downloadId]
        activeDownloads.remove(downloadId)

        val downloadManager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        try {
            val query = android.app.DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)

                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    val uriIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI)
                    val uriString = cursor.getString(uriIndex)
                    
                    // Get MIME type index
                    val mimeTypeIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_MEDIA_TYPE)
                    var mimeType: String? = null
                    
                    if (mimeTypeIndex != -1) {
                        mimeType = cursor.getString(mimeTypeIndex)
                    }
                    
                    // If no MIME type from download, try to determine from filename
                    if (mimeType.isNullOrEmpty() || mimeType == "application/octet-stream") {
                        // Check if this is a JPG file first based on the original filename
                        if (fileName != null && (fileName.lowercase().endsWith(".jpg") || fileName.lowercase().endsWith(".jpeg"))) {
                            mimeType = "image/jpeg"
                            Log.d(TAG, "Setting MIME type to image/jpeg based on filename")
                        } else {
                            mimeType = getMimeTypeFromFileName(fileName)
                        }
                    }
                    
                    // Additional logging
                    Log.d(TAG, "Download completed - URI: $uriString, MIME: $mimeType")
                    
                    // Ensure we have a URI to work with
                    if (uriString != null) {
                        // Handle file opening on UI thread
                        val finalMimeType = mimeType
                        Handler(Looper.getMainLooper()).post {
                            // Show toast only, no dialog here to avoid duplicates
                            // The dialog will be shown by the BroadcastReceiver instead
                            showToast(applicationContext.getString(R.string.download_completed, fileName))
                        }
                    } else {
                        Log.e(TAG, "Downloaded file URI is null")
                        showToast(applicationContext.getString(R.string.download_completed, fileName))
                    }
                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                    val reasonColumnIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON)
                    if (reasonColumnIndex != -1) {
                        val reason = cursor.getInt(reasonColumnIndex)
                        Log.e(TAG, "Download failed with reason: $reason")
                    }
                    showToast(applicationContext.getString(R.string.download_failed))
                }
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling download completion", e)
            showToast(applicationContext.getString(R.string.download_failed))
        }
    }

    fun downloadFile(url: String, fileName: String?, mimeType: String?, userAgent: String?, contentDisposition: String?) {
        // Log download parameters for debugging
        Log.d(TAG, "Download request - URL: $url")
        Log.d(TAG, "Original filename: $fileName")
        Log.d(TAG, "Original MIME type: $mimeType")
        Log.d(TAG, "Content-Disposition: $contentDisposition")
        
        // Permission check
        if (!checkStoragePermission()) {
            Log.e(TAG, "Storage permission not granted, can't download")
            showToastOnUiThread("İndirme için depolama izni gerekli")
            return
        }
        
        // İndirme işlemini arkaplanda yap
        val finalUrl = url
        val finalFileName = fileName
        val finalUserAgent = userAgent
        val finalContentDisposition = contentDisposition
        val finalMimeTypeOuter = mimeType
        
        executor.execute {
            try {
                var context = contextRef.get()
                if (context == null) {
                    context = applicationContext
                    contextRef.set(context)
                }
                
                val finalContext = context
                
                // Define variables for filename processing
                var processedFileName = finalFileName
                var fileNameModified = false
                
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val request = android.app.DownloadManager.Request(Uri.parse(finalUrl))
                
                // İndirme performansı ayarları
                request.setAllowedNetworkTypes(
                    android.app.DownloadManager.Request.NETWORK_WIFI or 
                    android.app.DownloadManager.Request.NETWORK_MOBILE
                )
                request.setAllowedOverMetered(true)  // Metered ağlarda indirmeye izin ver
                request.setAllowedOverRoaming(true)  // Roaming'de indirmeye izin ver
                
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // Dosya adını doğru şekilde ayarla
                var localFinalFileName: String
                var localMimeType = finalMimeTypeOuter
                
                // İlk olarak Content-Disposition'dan dosya adını çıkarmaya çalış
                val extractedName = extractFilenameFromContentDisposition(finalContentDisposition)
                
                if (!extractedName.isNullOrEmpty()) {
                    localFinalFileName = extractedName
                    Log.d(TAG, "Using filename from Content-Disposition: $localFinalFileName")
                } else if (!finalFileName.isNullOrEmpty()) {
                    localFinalFileName = finalFileName
                    Log.d(TAG, "Using provided filename: $localFinalFileName")
                } else {
                    // Use URLUtil as last resort
                    localFinalFileName = URLUtil.guessFileName(finalUrl, finalContentDisposition, localMimeType)
                    Log.d(TAG, "Using URLUtil guessed filename: $localFinalFileName")
                }
                
                // Handle MIME types correctly
                if (localMimeType != null) {
                    if (localMimeType == "application/pdf" &&
                        !localFinalFileName.lowercase().endsWith(".pdf")) {
                        localFinalFileName = "$localFinalFileName.pdf"
                        Log.d(TAG, "Added .pdf extension to filename: $localFinalFileName")
                    } else if ((localMimeType == "image/jpeg" || localMimeType == "image/jpg") &&
                        !(localFinalFileName.lowercase().endsWith(".jpg") || localFinalFileName.lowercase().endsWith(".jpeg"))) {
                        localFinalFileName = "$localFinalFileName.jpg"
                        Log.d(TAG, "Added .jpg extension to filename: $localFinalFileName")
                        
                        // Normalize MIME type to image/jpeg
                        localMimeType = "image/jpeg"
                    } else if (localMimeType == "image/png" &&
                        !localFinalFileName.lowercase().endsWith(".png")) {
                        localFinalFileName = "$localFinalFileName.png"
                        Log.d(TAG, "Added .png extension to filename: $localFinalFileName")
                    }
                }
                
                // Try to improve the MIME type if it's not specific enough
                if (localMimeType.isNullOrEmpty() || localMimeType == "application/octet-stream") {
                    val betterMimeType = determineBetterMimeType(finalUrl, localFinalFileName)
                    
                    if (!betterMimeType.isNullOrEmpty()) {
                        localMimeType = betterMimeType
                        
                        Log.d(TAG, "Improved MIME type to: $localMimeType")
                    }
                }
                
                localFinalFileName = ensureCorrectFileExtension(localFinalFileName, localMimeType)
                
                // Force specific MIME types for common file extensions
                if (localFinalFileName.lowercase().endsWith(".pdf")) {
                    localMimeType = "application/pdf"
                } else if (localFinalFileName.lowercase().endsWith(".jpg") || localFinalFileName.lowercase().endsWith(".jpeg")) {
                    localMimeType = "image/jpeg"
                } else if (localFinalFileName.lowercase().endsWith(".png")) {
                    localMimeType = "image/png"
                }
                
                Log.d(TAG, "Final filename for download: $localFinalFileName")
                Log.d(TAG, "Final MIME type for download: $localMimeType")
                
                // Set filename for download
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, localFinalFileName)
                } else {
                    request.setDestinationInExternalPublicDir(DOWNLOAD_DIRECTORY, localFinalFileName)
                }
                
                // Set MIME type if available
                if (!localMimeType.isNullOrEmpty()) {
                    // Convert "image/jpg" to "image/jpeg" for consistency
                    if (localMimeType == "image/jpg") {
                        localMimeType = "image/jpeg"
                    }
                    
                    request.setMimeType(localMimeType)
                    Log.d(TAG, "Set download MIME type: $localMimeType")
                }
                
                // Set user agent if available
                if (!finalUserAgent.isNullOrEmpty()) {
                    request.addRequestHeader("User-Agent", finalUserAgent)
                    Log.d(TAG, "Set User-Agent: $finalUserAgent")
                }
                
                // Get cookies for the URL
                val cookies = CookieManager.getInstance().getCookie(finalUrl)
                if (!cookies.isNullOrEmpty()) {
                    request.addRequestHeader("Cookie", cookies)
                    Log.d(TAG, "Added cookies to request")
                }
                
                // If available, use provided Accept header
                if (localMimeType?.startsWith("image/") == true) {
                    request.addRequestHeader("Accept", "image/*")
                } else if (!localMimeType.isNullOrEmpty()) {
                    request.addRequestHeader("Accept", localMimeType)
                } else {
                    request.addRequestHeader("Accept", "*/*")
                }
                
                // Start download
                val downloadId = downloadManager.enqueue(request)
                Log.d(TAG, "Download enqueued with ID: $downloadId")
                
                // Keep track of active downloads
                addActiveDownload(downloadId, localFinalFileName)
                
                val finalLocalMimeType = localMimeType
                val finalLocalFileName = localFinalFileName
                
                // Show toast on UI thread
                Handler(Looper.getMainLooper()).post {
                    showToast(finalContext.getString(R.string.download_started, finalLocalFileName))
                }
                
                // Notify for media files
                if (finalLocalMimeType?.startsWith("image/") == true) {
                    registerDownloadCompleteReceiver(
                        finalContext,
                        downloadId,
                        finalLocalFileName, finalLocalMimeType
                    )
                } else if (finalLocalMimeType?.startsWith("video/") == true) {
                    registerDownloadCompleteReceiver(
                        finalContext,
                        downloadId,
                        finalLocalFileName, finalLocalMimeType
                    )
                } else if (finalLocalMimeType?.startsWith("audio/") == true) {
                    registerDownloadCompleteReceiver(
                        finalContext,
                        downloadId,
                        finalLocalFileName, finalLocalMimeType
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                
                // Show error on UI thread
                Handler(Looper.getMainLooper()).post {
                    showToast("İndirme başlatılamadı: ${e.message}")
                }
            }
        }
    }

    /**
     * Content-Disposition'dan dosya adı çıkarır
     */
    fun extractFilenameFromContentDisposition(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrEmpty()) {
            return null
        }

        try {
            // Loglansın
            Log.d(TAG, "Parsing Content-Disposition: $contentDisposition")
            
            // Önce standart "filename=" formatını ara
            val simplePattern = Pattern.compile(
                "filename\\s*=\\s*['\"]?([^;\\r\\n\"']*)['\"]?", 
                Pattern.CASE_INSENSITIVE
            )
            val simpleMatcher = simplePattern.matcher(contentDisposition)
            
            if (simpleMatcher.find()) {
                var fileName = simpleMatcher.group(1)?.trim() ?: ""
                Log.d(TAG, "Found filename with simple pattern: $fileName")
                
                // Ekstra temizlik
                fileName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                
                if (fileName.isNotEmpty()) {
                    return fileName
                }
            }
            
            // UTF-8 kodlanmış filename* formatını ara
            val utfPattern = Pattern.compile(
                "filename\\*\\s*=\\s*UTF-8''(.*?)(?:['\"]?$|\\s|;)", 
                Pattern.CASE_INSENSITIVE
            )
            val utfMatcher = utfPattern.matcher(contentDisposition)
            
            if (utfMatcher.find()) {
                var fileName = utfMatcher.group(1)
                Log.d(TAG, "Found filename with UTF-8 pattern: $fileName")
                
                // URL decode - Türkçe karakterler için daha kapsamlı dekodlama
                try {
                    fileName = java.net.URLDecoder.decode(fileName, "UTF-8")
                    Log.d(TAG, "URL decoded filename: $fileName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding filename: $fileName", e)
                    // Decode başarısız olursa, basit replace'leri dene
                    fileName = fileName.replace("%20", " ")
                        .replace("%C4%B0", "İ")
                        .replace("%C4%B0", "İ")
                        .replace("%C5%9E", "Ş")
                        .replace("%C3%9C", "Ü")
                        .replace("%C4%9E", "Ğ")
                        .replace("%C3%87", "Ç")
                        .replace("%C3%96", "Ö")
                        .replace("%C4%B1", "ı")
                        .replace("%C5%9F", "ş")
                        .replace("%C3%BC", "ü")
                        .replace("%C4%9F", "ğ")
                        .replace("%C3%A7", "ç")
                        .replace("%C3%B6", "ö")
                    Log.d(TAG, "Manual decoded filename: $fileName")
                }
                
                // Tırnak işaretlerini temizle
                if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                    fileName = fileName.substring(1, fileName.length - 1)
                }
                
                // Dosya sistemi için güvenli karakter setine dönüştür
                fileName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                
                Log.d(TAG, "Final filename: $fileName")
                return fileName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing content disposition: $contentDisposition", e)
        }

        return null
    }

    /**
     * URL'den dosya adı çıkarır
     */
    fun extractFilenameFromUrl(url: String?): String {
        if (url.isNullOrEmpty()) {
            return "download"
        }

        try {
            var cleanUrl = url
            val queryIndex = url.indexOf('?')
            if (queryIndex > 0) {
                cleanUrl = url.substring(0, queryIndex)
            }

            val segments = cleanUrl.split("/")
            if (segments.isNotEmpty()) {
                val lastSegment = segments.last()
                if (lastSegment.isNotEmpty()) {
                    return lastSegment.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting filename from URL: $url", e)
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "download_$timeStamp"
    }

    /**
     * MIME tipi belirler
     */
    fun determineMimeType(url: String, providedMimeType: String?, fileName: String?): String {
        if (providedMimeType != null && providedMimeType.isNotEmpty() &&
            providedMimeType != "application/octet-stream" &&
            providedMimeType != "application/force-download") {
            return providedMimeType
        }

        val extension = getFileExtension(fileName)
        if (extension.isNotEmpty()) {
            val mimeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            if (mimeFromExt != null) {
                return mimeFromExt
            }
        }

        val urlExtension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (urlExtension != null && urlExtension.isNotEmpty()) {
            val mimeFromUrl = MimeTypeMap.getSingleton().getMimeTypeFromExtension(urlExtension)
            if (mimeFromUrl != null) {
                return mimeFromUrl
            }
        }

        if (url.lowercase().contains(".pdf") ||
            (fileName != null && fileName.lowercase().endsWith(".pdf"))) {
            return "application/pdf"
        } else if (url.lowercase().contains(".doc") ||
            (fileName != null && fileName.lowercase().endsWith(".doc"))) {
            return "application/msword"
        } else if (url.lowercase().contains(".docx") ||
            (fileName != null && fileName.lowercase().endsWith(".docx"))) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        } else if (url.lowercase().contains(".xls") ||
            (fileName != null && fileName.lowercase().endsWith(".xls"))) {
            return "application/vnd.ms-excel"
        } else if (url.lowercase().contains(".xlsx") ||
            (fileName != null && fileName.lowercase().endsWith(".xlsx"))) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        } else if (url.lowercase().contains(".zip") ||
            (fileName != null && fileName.lowercase().endsWith(".zip"))) {
            return "application/zip"
        }

        return "application/octet-stream"
    }
    
    /**
     * Dosya uzantısını alır
     */
    fun getFileExtension(fileName: String?): String {
        if (fileName.isNullOrEmpty() || !fileName.contains(".")) {
            return ""
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).lowercase(Locale.ROOT)
    }
    
    /**
     * Removes the file extension from a filename
     * @param fileName Filename with or without extension
     * @return Filename without extension
     */
    private fun removeExtension(fileName: String?): String {
        if (fileName == null) return ""
        val lastDotPos = fileName.lastIndexOf(".")
        if (lastDotPos > 0) {
            return fileName.substring(0, lastDotPos)
        }
        return fileName
    }

    /**
     * Try to determine a better MIME type from URL or filename for binary files
     */
    private fun determineBetterMimeType(url: String?, fileName: String?): String? {
        // Önce dosya adına bakarak MIME type belirle
        if (fileName != null) {
            val lowerFileName = fileName.lowercase()
            
            // SoilContinuity dosyaları için özel kontrol
            if (lowerFileName.contains("soilcontinuity")) {
                Log.d(TAG, "Detected SoilContinuity file, setting MIME type to image/jpeg")
                return "image/jpeg"
            }
            
            // Diğer resim uzantıları
            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
                Log.d(TAG, "Using image/jpeg MIME type based on .jpg extension in filename")
                return "image/jpeg"
            } else if (lowerFileName.endsWith(".png")) {
                return "image/png"
            } else if (lowerFileName.endsWith(".gif")) {
                return "image/gif"
            } else if (lowerFileName.endsWith(".pdf")) {
                return "application/pdf"
            }
        }
        
        // URL'ye bakarak MIME type belirle
        val lowerUrl = url?.lowercase() ?: ""
        
        // SoilContinuity URL'leri için özel kontrol
        if (lowerUrl.contains("soilcontinuity")) {
            Log.d(TAG, "Detected SoilContinuity in URL, setting MIME type to image/jpeg")
            return "image/jpeg"
        }
        
        // PDF file indicators
        if (lowerUrl.contains(".pdf") || lowerUrl.contains("pdf=true") || 
            lowerUrl.contains("format=pdf")) {
            return "application/pdf"
        } 
        // Image file indicators
        else if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ||
                 lowerUrl.contains("format=jpg") || lowerUrl.contains("format=jpeg")) {
            return "image/jpeg"
        } 
        else if (lowerUrl.contains(".png") || lowerUrl.contains("format=png")) {
            return "image/png"
        } 
        else if (lowerUrl.contains(".gif") || lowerUrl.contains("format=gif")) {
            return "image/gif"
        }
        // Document format indicators
        else if (lowerUrl.contains(".doc")) {
            return "application/msword"
        } 
        else if (lowerUrl.contains(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }
        else if (lowerUrl.contains(".xls")) {
            return "application/vnd.ms-excel"
        }
        else if (lowerUrl.contains(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        
        // Additional check for special URL patterns that often contain PDFs
        if (lowerUrl.contains("/document/") || lowerUrl.contains("/viewdoc") || 
            lowerUrl.contains("/download/") || lowerUrl.contains("/attachments/")) {
            return "application/pdf"
        }
        
        // Default fallback
        return "application/octet-stream"
    }
    
    /**
     * Dosya uzantısını MIME türüne göre düzenler
     */
    fun ensureCorrectFileExtension(fileName: String, mimeType: String?): String {
        var tempFileName = fileName
        if (tempFileName.isEmpty()) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(Date())
            tempFileName = "download_$timeStamp"
        }

        val mimeToExtMap = mapOf(
            "application/pdf" to ".pdf",
            "application/msword" to ".doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to ".docx",
            "application/vnd.ms-excel" to ".xls",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to ".xlsx",
            "application/vnd.ms-powerpoint" to ".ppt",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to ".pptx",
            "text/plain" to ".txt",
            "text/html" to ".html",
            "image/jpeg" to ".jpg",
            "image/png" to ".png",
            "image/gif" to ".gif",
            "application/zip" to ".zip",
            "application/x-rar-compressed" to ".rar",
            "audio/mpeg" to ".mp3",
            "video/mp4" to ".mp4"
        )

        // First, if the file ends with .bin, we should try to determine a better extension
        if (tempFileName.lowercase().endsWith(".bin")) {
            // Remove the .bin extension first
            tempFileName = tempFileName.substring(0, tempFileName.length - 4)
            Log.d(TAG, "Removed .bin extension: $tempFileName")
            
            // Check if the original file was supposed to be a JPG from the MIME type
            // This is the main fix for the jpg->bin conversion issue
            if (mimeType != null && (mimeType == "image/jpeg" || mimeType == "image/jpg")) {
                tempFileName = "$tempFileName.jpg"
                Log.d(TAG, "Restored JPG extension after .bin removal: $tempFileName")
                return tempFileName
            }
        }
        
        val expectedExtension = mimeToExtMap[mimeType] ?: run {
            if (mimeType != null) {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (ext != null) {
                    ".$ext"
                } else null
            } else null
        }
        
        // For image types, ensure proper extension
        val imageExtension = if (mimeType != null && mimeType.startsWith("image/")) {
            when (mimeType) {
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/png" -> ".png"
                "image/gif" -> ".gif"
                else -> null
            }
        } else null

        // Apply the extension if needed
        if (expectedExtension != null || imageExtension != null) {
            val finalExtension = imageExtension ?: expectedExtension
            // Check if file already has the correct extension
            if (finalExtension != null && !tempFileName.lowercase().endsWith(finalExtension)) {
                // Remove existing extension if any
                val lastDotIndex = tempFileName.lastIndexOf(".")
                if (lastDotIndex > 0) {
                    tempFileName = tempFileName.substring(0, lastDotIndex)
                }
                
                // Add the proper extension
                tempFileName += finalExtension
                Log.d(TAG, "Applied proper extension: $tempFileName")
            }
        }

        return tempFileName
    }
    
    /**
     * İndirme onay dialogu - güvenli context işleme ile
     */
    fun showDownloadConfirmationDialog(
        url: String,
        fileName: String,
        mimeType: String?,
        userAgent: String?,
        contentDisposition: String?,
        sizeInfo: String?,
        isImage: Boolean
    ) {
        // Get a valid activity context
        val dialogContext = getValidActivityContext()
        if (dialogContext == null) {
            // If no valid activity context, download directly without showing dialog
            Log.d(TAG, "No valid activity context, downloading directly: $fileName")
            downloadFile(url, fileName, mimeType, userAgent, contentDisposition)
            return
        }
        
        // UI thread üzerinde dialog göster
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val title = if (isImage) 
                    dialogContext.getString(R.string.download_image) 
                else 
                    dialogContext.getString(R.string.download_file)
                
                val message = if (sizeInfo != null) 
                    "$fileName ($sizeInfo)" 
                else 
                    fileName
                
                // Use standard AlertDialog since we have a valid Activity context
                val builder = AlertDialog.Builder(dialogContext)
                builder.setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(dialogContext.getString(R.string.download_file)) { dialog, which ->
                        if (isImage) {
                            val imageDownloader = ImageDownloader(dialogContext)
                            imageDownloader.downloadImage(url, null)
                        } else {
                            downloadFile(url, fileName, mimeType, userAgent, contentDisposition)
                        }
                    }
                    .setNegativeButton(dialogContext.getString(R.string.download_cancel)) { dialog, which ->
                        dialog.dismiss()
                    }
                
                // Görselse resmi göster
                if (isImage) {
                    try {
                        val imageView = ImageView(dialogContext)
                        imageView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        imageView.adjustViewBounds = true
                        
                        // Glide ile önizleme göster
                        Glide.with(dialogContext)
                            .load(url)
                            .centerCrop()
                            .into(imageView)
                        
                        builder.setView(imageView)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting image view", e)
                    }
                }
                
                val dialog = builder.create()
                dialog.show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing download dialog: ${e.message}")
                // If dialog fails, just download directly
                downloadFile(url, fileName, mimeType, userAgent, contentDisposition)
            }
        }
    }
    
    /**
     * Get a valid activity context for showing dialogs
     */
    private fun getValidActivityContext(): Context? {
        // Check if the currentActivityContext is valid
        if (currentActivityContext is Activity) {
            val activity = currentActivityContext as Activity
            if (!activity.isFinishing && !activity.isDestroyed) {
                return currentActivityContext
            }
        }
        
        // No valid activity context available
        return null
    }
    
    /**
     * Özel HTTP bağlantısı ile dosya indirme
     */
    fun startCustomDownload(url: String, fileName: String) {
        // Check permissions first
        if (!checkStoragePermission()) {
            Log.e(TAG, "Storage permission not granted, can't download")
            showToastOnUiThread("İndirme için depolama izni gerekli")
            return
        }
        
        val userAgent = "Mozilla/5.0 (Linux; Android 10; Mobile)"
        val referer = ""

        Thread {
            var connection: HttpURLConnection? = null
            var input: InputStream? = null
            var output: FileOutputStream? = null
            
            // Define variables for filename processing
            var processedFileName = fileName
            var fileNameModified = false

            try {
                val urlObj = URL(url)
                connection = urlObj.openConnection() as HttpURLConnection

                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null) {
                    connection.setRequestProperty("Cookie", cookies)
                }

                connection.setRequestProperty("User-Agent", userAgent)
                if (referer.isNotEmpty()) {
                    connection.setRequestProperty("Referer", referer)
                }

                connection.setRequestProperty("Accept", "image/*, */*")
                connection.instanceFollowRedirects = true
                connection.connect()
                Log.d(TAG, "HTTP Response code: ${connection.responseCode}")

                val headers = connection.headerFields
                for ((key, value) in headers) {
                    if (key != null) {
                        Log.d(TAG, "Header: $key = $value")
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    showToastOnUiThread("İndirme hatası: $responseCode")
                    return@Thread
                }

                val contentType = connection.contentType
                Log.d(TAG, "Content Type: $contentType")

                val contentLength = connection.contentLength
                Log.d(TAG, "Content Length: $contentLength")

                val contentDisposition = connection.getHeaderField("Content-Disposition")
                var fileNameFromHeader: String? = null
                if (contentDisposition != null) {
                    Log.d(TAG, "Content-Disposition: $contentDisposition")
                    val fileNameIndex = contentDisposition.indexOf("filename")
                    if (fileNameIndex >= 0) {
                        val equalsIndex = contentDisposition.indexOf("=", fileNameIndex)
                        if (equalsIndex > 0) {
                            fileNameFromHeader =
                                contentDisposition.substring(equalsIndex + 1).trim()
                            fileNameFromHeader =
                                fileNameFromHeader.replace("^\"|\"$|^\\s+|\\s+$|;$".toRegex(), "")
                            Log.d(TAG, "File name from Content-Disposition: $fileNameFromHeader")
                        }
                    }
                }

                // Determine proper filename with correct extension
                val finalFileName: String
                
                // First try to get filename from header
                if (!fileNameFromHeader.isNullOrEmpty()) {
                    Log.d(TAG, "Using filename from Content-Disposition: $fileNameFromHeader")
                    
                    // Special handling for JPG files in Content-Disposition
                    finalFileName = if (contentDisposition?.lowercase()?.contains(".jpg") == true || 
                                  contentDisposition?.lowercase()?.contains(".jpeg") == true) {
                        var baseName = fileNameFromHeader
                        val dotIndex = baseName.lastIndexOf(".")
                        if (dotIndex > 0) {
                            baseName = baseName.substring(0, dotIndex)
                        }
                        "$baseName.jpg"
                    } else {
                        ensureProperFileExtension(fileNameFromHeader, contentType, url)
                    }
                    Log.d(TAG, "Fixed JPG filename from Content-Disposition: $finalFileName")
                }
                // For binary content, use provided filename with extension correction
                else if (contentType?.contains("application/octet-stream") == true) {
                    // Try to determine a better MIME type based on URL and filename
                    val betterMimeType = determineBetterMimeType(url, fileName)
                    val finalMimeType = if (betterMimeType != null && betterMimeType != "application/octet-stream") 
                                        betterMimeType else contentType
                    
                    Log.d(TAG, "For octet-stream: detected better MIME type: $finalMimeType")
                    finalFileName = ensureProperFileExtension(fileName, finalMimeType, url)
                }
                // Special handling for PDFs (often misidentified)
                else if ((contentType?.contains("application/pdf") == true) || 
                         url.lowercase().contains(".pdf")) {
                    finalFileName = "${removeExtension(fileName)}.pdf"
                    Log.d(TAG, "Using PDF filename: $finalFileName")
                } 
                // For images, ensure proper extension
                else if (contentType?.contains("image/") == true) {
                    finalFileName = when {
                        contentType.contains("jpeg") || contentType.contains("jpg") -> 
                            "${removeExtension(fileName)}.jpg"
                        contentType.contains("png") -> 
                            "${removeExtension(fileName)}.png"
                        contentType.contains("gif") -> 
                            "${removeExtension(fileName)}.gif"
                        else -> {
                            // Other image types
                            val extension = MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(contentType)
                            if (extension != null) {
                                "${removeExtension(fileName)}.$extension"
                            } else {
                                fileName
                            }
                        }
                    }
                    Log.d(TAG, "Using image filename: $finalFileName")
                } 
                // For other content types, try to get extension from MIME type
                else if (contentType != null) {
                    val extension = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(contentType)
                    finalFileName = if (extension != null) {
                        "${removeExtension(fileName)}.$extension"
                    } else {
                        fileName
                    }
                } 
                // If all else fails, use the provided filename
                else {
                    finalFileName = fileName
                }

                // If the determined MIME type is for an image file but the extension doesn't match, fix it
                var effectiveFileName = finalFileName
                val mimeType = getMimeTypeFromFileName(effectiveFileName)
                var isFileNameModified = false
                
                if (mimeType?.startsWith("image/") == true && 
                    !(effectiveFileName.lowercase().endsWith(".jpg") || 
                      effectiveFileName.lowercase().endsWith(".jpeg") || 
                      effectiveFileName.lowercase().endsWith(".png") || 
                      effectiveFileName.lowercase().endsWith(".gif"))) {
                    
                    effectiveFileName = when (mimeType) {
                        "image/jpeg" -> {
                            isFileNameModified = true
                            "${removeExtension(effectiveFileName)}.jpg"
                        }
                        "image/png" -> {
                            isFileNameModified = true
                            "${removeExtension(effectiveFileName)}.png"
                        }
                        "image/gif" -> {
                            isFileNameModified = true
                            "${removeExtension(effectiveFileName)}.gif"
                        }
                        else -> effectiveFileName
                    }
                    
                    if (isFileNameModified) {
                        Log.d(TAG, "Fixed image filename extension: $effectiveFileName")
                    }
                }
                
                Log.d(TAG, "Final file name with extension: $finalFileName")

                // Handle different storage access methods based on Android version
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // For Android 10 and above, use MediaStore
                    val finalEffectiveFileName = effectiveFileName
                    try {
                        val values = ContentValues()
                        values.put(MediaStore.Downloads.DISPLAY_NAME, finalEffectiveFileName)
                        
                        // Get precise mime type from filename
                        val usedMimeType = getMimeTypeFromFileName(finalEffectiveFileName)
                        Log.d(TAG, "MIME type for MediaStore: $usedMimeType")
                        values.put(MediaStore.Downloads.MIME_TYPE, usedMimeType)
                        
                        // Choose the right collection based on mime type
                        val collectionUri: Uri
                        if (usedMimeType?.startsWith("image/") == true) {
                            Log.d(TAG, "Using MediaStore Images collection")
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, 
                                "${Environment.DIRECTORY_PICTURES}/$DOWNLOAD_DIRECTORY")
                            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        } else {
                            Log.d(TAG, "Using MediaStore Downloads collection")
                            values.put(MediaStore.Downloads.RELATIVE_PATH, 
                                "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIRECTORY")
                            collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        }
                        
                        val uri = applicationContext.contentResolver.insert(
                            collectionUri, values)
                        
                        if (uri != null) {
                            Log.d(TAG, "MediaStore URI created: $uri")
                            input = connection.inputStream
                            val mediaStoreOutput = applicationContext.contentResolver.openOutputStream(uri)
                            
                            if (mediaStoreOutput != null) {
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytesRead: Long = 0

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    mediaStoreOutput.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                }
                                
                                // Ensure output is properly flushed and closed
                                mediaStoreOutput.flush()
                                mediaStoreOutput.close()
                                
                                Log.d(TAG, "Download completed using MediaStore. Total bytes: $totalBytesRead")
                                
                                // Offer to open the file
                                val mainHandler = Handler(Looper.getMainLooper())
                                val finalUri = uri
                                val finalMimeType = mimeType
                                mainHandler.post {
                                    offerToOpenFile(finalUri.toString(), finalMimeType)
                                }
                                
                                return@Thread
                            } else {
                                Log.e(TAG, "Failed to open MediaStore output stream")
                            }
                        } else {
                            Log.e(TAG, "Failed to create MediaStore URI")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error using MediaStore for download: ${e.message}", e)
                        // Fall back to legacy method if MediaStore fails
                    }
                }
                
                // Legacy method for older Android versions or as fallback
                val downloadsDir: File
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // For Android 10+ use app-specific directory as fallback
                    downloadsDir = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
                        DOWNLOAD_DIRECTORY)
                } else {
                    // For older versions, use public directory
                    downloadsDir = File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIRECTORY)
                }
                
                if (!downloadsDir.exists()) {
                    val dirCreated = downloadsDir.mkdirs()
                    if (!dirCreated) {
                        Log.e(TAG, "Failed to create download directory: ${downloadsDir.absolutePath}")
                    }
                }

                // Use the potentially modified filename
                val outputFile = File(downloadsDir, effectiveFileName)
                Log.d(TAG, "Output file path: ${outputFile.absolutePath}")

                input = connection.inputStream
                output = FileOutputStream(outputFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                }

                Log.d(TAG, "Download completed. Total bytes: $totalBytesRead")

                // Process the downloaded file
                // Check for the right MIME type and file extension based on content type
                val finalMimeType: String
                
                // Use a conditional check for JPEG files instead of redeclaring variables
                finalMimeType = when {
                    finalFileName.lowercase().endsWith(".jpg") ||
                    finalFileName.lowercase().endsWith(".jpeg") -> {
                        "image/jpeg"
                    }
                    finalFileName.lowercase().endsWith(".png") -> {
                        "image/png"
                    }
                    finalFileName.lowercase().endsWith(".gif") -> {
                        "image/gif"
                    }
                    finalFileName.lowercase().endsWith(".pdf") -> {
                        "application/pdf"
                    }
                    contentType != null &&
                    contentType != "application/octet-stream" -> {
                        contentType
                    }
                    else -> {
                        getMimeTypeFromFileName(effectiveFileName) ?: "application/octet-stream"
                    }
                }

                // Now prepare the actual file for output, using our processed name if it was modified
                val finalOutputFile: File = if (isFileNameModified) {
                    File(downloadsDir, effectiveFileName).also {
                        Log.d(TAG, "Using modified filename: $effectiveFileName")
                    }
                } else {
                    File(downloadsDir, finalFileName).also {
                        Log.d(TAG, "Using original filename: $finalFileName")
                    }
                }

                notifyMediaScanner(effectiveFileName, finalOutputFile, finalMimeType)

                showToastOnUiThread("$effectiveFileName başarıyla indirildi")
                showSuccessNotification(effectiveFileName, finalOutputFile)

            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                showToastOnUiThread("İndirme hatası: ${e.message}")
            } finally {
                try {
                    output?.close()
                    input?.close()
                    connection?.disconnect()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing resources", e)
                }
            }
        }.start()
    }
    
    /**
     * MediaScanner'a yeni dosyayı bildirir
     */
    fun notifyMediaScanner(fileName: String, file: File, mimeType: String) {
        try {
            android.media.MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(file.absolutePath),
                arrayOf(mimeType)
            ) { path, uri -> 
                Log.d(TAG, "Media scan completed: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying media scanner", e)
        }
    }
    
    /**
     * İndirme tamamlandığında bildirim göstermek için alıcı kaydeder
     * ve dosyayı açma seçeneği sunar
     */
    fun registerDownloadCompleteReceiver(context: Context, downloadId: Long, fileName: String, mimeType: String) {
        val downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val receivedDownloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == receivedDownloadId) {
                    try {
                        // Get the downloaded file's URI
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                        val fileUri = dm.getUriForDownloadedFile(downloadId)
                        
                        if (fileUri != null) {
                            // Log the successful download
                            Log.d(TAG, "Download completed, URI: $fileUri, MIME: $mimeType")
                            
                            // Offer to open the file on the UI thread
                            Handler(Looper.getMainLooper()).post {
                                offerToOpenFile(fileUri.toString(), mimeType)
                            }
                        } else {
                            // If URI is null, just show a toast
                            Toast.makeText(
                                context, 
                                context.getString(R.string.download_completed, fileName), 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling download completion", e)
                        // Show a simple toast on error
                        Toast.makeText(
                            context, 
                            context.getString(R.string.download_completed, fileName), 
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        // Always unregister the receiver
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering receiver", e)
                        }
                    }
                }
            }
        }
        
        // İndirme tamamlandığında bildirimi almak için filtre
        val filter = IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        // Specify RECEIVER_NOT_EXPORTED flag for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    /**
     * Başarılı indirme bildirimi gösterir
     */
    private fun showSuccessNotification(fileName: String, file: File) {
        try {
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel =
                    android.app.NotificationChannel(
                        "download_channel",
                        "İndirme Bildirimleri",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                notificationManager.createNotificationChannel(channel)
            }

            val openFileIntent = Intent(Intent.ACTION_VIEW)
            val fileUri: Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    file
                ).also { 
                    openFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Uri.fromFile(file)
            }

            val mimeType = getMimeTypeFromFileName(fileName)
            openFileIntent.setDataAndType(fileUri, mimeType)

            val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.app.PendingIntent.getActivity(
                    applicationContext,
                    0,
                    openFileIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                android.app.PendingIntent.getActivity(
                    applicationContext,
                    0,
                    openFileIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            val builder =
                NotificationCompat.Builder(applicationContext, "download_channel")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("İndirme Tamamlandı")
                    .setContentText(fileName)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())

        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
    
    /**
     * Ensures that a filename has the proper extension based on content type and URL
     * 
     * @param fileName The original filename
     * @param contentType The content type from the HTTP response
     * @param url The URL being downloaded
     * @return A filename with the proper extension
     */
    private fun ensureProperFileExtension(fileName: String?, contentType: String?, url: String): String {
        var baseName = if (fileName.isNullOrEmpty()) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            "download_$timeStamp"
        } else {
            fileName
        }
        
        // Helper to remove existing extension
        val dotIndex = baseName.lastIndexOf(".")
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex)
        }
        
        // Handle .bin extensions - always replace them
        if (fileName?.lowercase()?.endsWith(".bin") == true) {
            Log.d(TAG, "Removed .bin extension: $baseName")
        }
        
        // Check for PDF files (often misidentified)
        if ((contentType?.contains("application/pdf") == true) || 
            url.lowercase().contains(".pdf")) {
            return "$baseName.pdf"
        }
        
        // Check for binary content but with clues in the URL
        if (contentType?.contains("application/octet-stream") == true) {
            // Look for extension clues in the URL
            return when {
                url.lowercase().contains(".jpg") || url.lowercase().contains(".jpeg") -> 
                    "$baseName.jpg"
                url.lowercase().contains(".png") -> 
                    "$baseName.png"
                url.lowercase().contains(".pdf") -> 
                    "$baseName.pdf"
                else -> 
                    baseName
            }
        }
        
        // Handle image types specifically
        if (contentType != null) {
            return when {
                contentType.contains("image/jpeg") || contentType.contains("image/jpg") -> 
                    "$baseName.jpg"
                contentType.contains("image/png") -> 
                    "$baseName.png"
                contentType.contains("image/gif") -> 
                    "$baseName.gif"
                contentType.contains("application/pdf") -> 
                    "$baseName.pdf"
                else -> {
                    // Try to get extension from MIME type
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)
                    if (extension != null && extension.isNotEmpty()) {
                        "$baseName.$extension"
                    } else {
                        baseName
                    }
                }
            }
        }
        
        // If we couldn't determine a better extension, keep the original filename
        // but make sure it's not .bin
        return if (fileName?.lowercase()?.endsWith(".bin") == true) {
            baseName
        } else {
            fileName ?: baseName
        }
    }

    /**
     * Dosya adından MIME türünü tahmin eder
     */
    private fun getMimeTypeFromFileName(fileName: String?): String? {
        if (fileName == null) {
            return null
        }
        
        // Normalize to lowercase for comparison
        val lowerFileName = fileName.lowercase()
        
        // Image formats
        return when {
            lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") -> 
                "image/jpeg"
            lowerFileName.endsWith(".png") -> 
                "image/png"
            lowerFileName.endsWith(".gif") -> 
                "image/gif"
            lowerFileName.endsWith(".webp") -> 
                "image/webp"
            lowerFileName.endsWith(".bmp") -> 
                "image/bmp"
            // Document formats
            lowerFileName.endsWith(".pdf") -> 
                "application/pdf"
            lowerFileName.endsWith(".doc") -> 
                "application/msword"
            lowerFileName.endsWith(".docx") -> 
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lowerFileName.endsWith(".xls") -> 
                "application/vnd.ms-excel"
            lowerFileName.endsWith(".xlsx") -> 
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lowerFileName.endsWith(".ppt") -> 
                "application/vnd.ms-powerpoint"
            lowerFileName.endsWith(".pptx") -> 
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            // Text and other common formats
            lowerFileName.endsWith(".txt") -> 
                "text/plain"
            lowerFileName.endsWith(".html") || lowerFileName.endsWith(".htm") -> 
                "text/html"
            lowerFileName.endsWith(".css") -> 
                "text/css"
            lowerFileName.endsWith(".js") -> 
                "application/javascript"
            // Archive formats
            lowerFileName.endsWith(".zip") -> 
                "application/zip"
            lowerFileName.endsWith(".rar") -> 
                "application/x-rar-compressed"
            lowerFileName.endsWith(".7z") -> 
                "application/x-7z-compressed"
            // Try to use the extension lookup as fallback
            else -> {
                try {
                    // First try to get extension directly from the filename
                    val lastDot = lowerFileName.lastIndexOf(".")
                    if (lastDot > 0) {
                        val extension = lowerFileName.substring(lastDot + 1)
                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        if (mimeType != null && mimeType.isNotEmpty()) {
                            Log.d(TAG, "Found MIME type from extension: $mimeType")
                            return mimeType
                        }
                    }
                    
                    // Then try using the URL method
                    val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
                    if (extension != null) {
                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                        if (mimeType != null) {
                            return mimeType
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error determining MIME type from filename: $fileName", e)
                }
                
                // Default fallback
                "application/octet-stream"
            }
        }
    }
    
    /**
     * İndirme tamamlandığında dosyayı açmayı teklif eder
     */
    private fun offerToOpenFile(uriString: String?, mimeType: String?) {
        if (uriString == null) {
            return
        }
        
        Log.d(TAG, "Offering to open file - URI: $uriString")
        Log.d(TAG, "File MIME type: $mimeType")
        
        try {
            val uri = Uri.parse(uriString)
            
            // Get file name from URI
            val fileName = uri.lastPathSegment ?: "İndirilen dosya"
            
            // Get valid activity context
            val dialogContext = getValidActivityContext()
            if (dialogContext != null) {
                // Show dialog asking if user wants to open the file
                AlertDialog.Builder(dialogContext)
                    .setTitle(R.string.download_completed_title)
                    .setMessage(dialogContext.getString(R.string.download_open_prompt, fileName))
                    .setPositiveButton(R.string.download_open) { dialog, which ->
                        openFile(uri, mimeType)
                    }
                    .setNegativeButton(R.string.download_cancel, null)
                    .show()
            } else {
                // If no valid context, just show toast
                Toast.makeText(
                    applicationContext, 
                    applicationContext.getString(R.string.download_completed, fileName), 
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error offering to open downloaded file", e)
            // Show toast on error
            Toast.makeText(
                applicationContext, 
                applicationContext.getString(R.string.download_completed_generic), 
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * Actually open the file with the proper intent
     */
    private fun openFile(uri: Uri, mimeType: String?) {
        Log.d(TAG, "Opening file - URI: $uri")
        Log.d(TAG, "Opening with MIME type: $mimeType")
        
        // Fix MIME type if needed
        var finalMimeType = mimeType
        if (finalMimeType.isNullOrEmpty() || finalMimeType == "application/octet-stream") {
            // Try to determine better mime type from URI
            val path = uri.path
            if (path != null) {
                val betterMime = getMimeTypeFromFileName(path)
                if (betterMime != "application/octet-stream") {
                    finalMimeType = betterMime
                    Log.d(TAG, "Improved MIME type for opening: $finalMimeType")
                }
            }
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, finalMimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(applicationContext.packageManager) != null) {
                applicationContext.startActivity(intent)
            } else {
                // No app found to open this file type
                Toast.makeText(
                    applicationContext, 
                    R.string.download_no_app_to_open, 
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file: ${e.message}")
            Toast.makeText(
                applicationContext, 
                R.string.download_open_error, 
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Yardımcı metotlar
    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showToastOnUiThread(message: String) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Check storage permissions and request if needed
     * @return true if permissions are already granted
     */
    fun checkStoragePermission(): Boolean {
        if (currentActivityContext !is Activity) {
            Log.e(TAG, "No valid activity context to request permissions")
            return false
        }
        
        // Android 13+ (API 33+) uses more granular permissions
        if (android.os.Build.VERSION.SDK_INT >= 33) { // Android.os.Build.VERSION_CODES.TIRAMISU
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    currentActivityContext, 
                    android.Manifest.permission.READ_MEDIA_IMAGES) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true
            } else {
                androidx.core.app.ActivityCompat.requestPermissions(
                    currentActivityContext as Activity,
                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 100)
                return false
            }
        } 
        // Android 10+ (API 29+) with scoped storage
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // With scoped storage, we can use MediaStore or app-specific directories without permission
            return true
        } 
        // Android 6.0-9.0 (API 23-28) need runtime permissions
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    currentActivityContext, 
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true
            } else {
                androidx.core.app.ActivityCompat.requestPermissions(
                    currentActivityContext as Activity,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
                return false
            }
        } 
        // Android 5.1 and below (API 22-) - permissions granted at install time
        else {
            return true
        }
    }

    /**
     * İndirilen dosyaları yönetici uygulamasını açar
     */
    fun showDownloadsManager(context: Context) {
        try {
            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    val uri = Uri.parse(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).toString())
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent,
                    "İndirilenler Klasörünü Aç"))
            } catch (ex: Exception) {
                Toast.makeText(context, "İndirilenler klasörü açılamadı", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error opening downloads folder", ex)
            }
        }
    }

    /**
     * Create MediaStore content URI for inserting a file into shared storage
     * Used for Android 10+ (API 29+)
     * 
     * @param context Context for ContentResolver
     * @param collection The MediaStore collection URI (Images, Video, etc.)
     * @param fileName The desired filename
     * @param mimeType The MIME type of the file
     * @return The content URI or null if creation fails
     */
    private fun insertMediaFile(context: Context, collection: Uri, fileName: String, mimeType: String?): Uri? {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                
                // For Android 10+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Store in the Downloads directory
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + 
                            File.separator + DOWNLOAD_DIRECTORY)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            // Insert the URI
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null
            
            // Mark as not pending if on Android 10+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }
            
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Error creating MediaStore file", e)
            return null
        }
    }
}