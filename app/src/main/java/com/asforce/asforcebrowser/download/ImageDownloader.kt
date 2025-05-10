package com.asforce.asforcebrowser.download

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for downloading images.
 */
class ImageDownloader(context: Context) {
    companion object {
        private const val TAG = "ImageDownloader"
        private const val DOWNLOAD_DIRECTORY = "Downloads"
    }
    
    private val context: Context = context.applicationContext
    private val downloadManager: DownloadManager = DownloadManager.getInstance(context)
    
    /**
     * Removes the file extension from a filename
     * @param fileName Filename with extension
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
     * Downloads an image from the given URL.
     *
     * @param imageUrl The URL of the image to download
     * @param webView Optional WebView to get user agent and cookies from (can be null)
     */
    fun downloadImage(imageUrl: String, webView: WebView?) {
        Log.d(TAG, "downloadImage: $imageUrl")
        val isContentUri = imageUrl.startsWith("content://")
        var fileName = ""
        var mimeType = "image/jpeg" // Default MIME type
        
        // Log the image download request
        Log.d(TAG, "Starting image download from URL: $imageUrl")

        if (isContentUri) {
            try {
                val contentUri = Uri.parse(imageUrl)

                val cursor = context.contentResolver.query(
                    contentUri,
                    arrayOf(
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        android.provider.MediaStore.MediaColumns.MIME_TYPE
                    ),
                    null, null, null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex =
                            it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = it.getString(nameIndex)
                        }

                        val mimeIndex =
                            it.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                        if (mimeIndex != -1) {
                            val contentMimeType = it.getString(mimeIndex)
                            if (!TextUtils.isEmpty(contentMimeType)) {
                                mimeType = contentMimeType
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing content URI", e)
            }
        } else {
            try {
                val uri = Uri.parse(imageUrl)
                val lastPathSegment = uri.lastPathSegment
                
                // SoilContinuity için özel dosya adı oluşturma
                if (imageUrl.contains("SoilContinuity", ignoreCase = true)) {
                    val id = uri.getQueryParameter("id")
                    if (!id.isNullOrEmpty()) {
                        fileName = "${id}_SoilContinuity"
                    } else {
                        fileName = "SoilContinuity"
                    }
                    Log.d(TAG, "SoilContinuity detected, filename will be: $fileName")
                } else if (!lastPathSegment.isNullOrEmpty()) {
                    fileName = lastPathSegment
                    val queryIndex = fileName.indexOf('?')
                    if (queryIndex > 0) {
                        fileName = fileName.substring(0, queryIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting filename from URL", e)
            }
        }

        if (TextUtils.isEmpty(fileName)) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(Date())
            fileName = "IMG_$timeStamp"
            Log.d(TAG, "Generated timestamp filename: $fileName")
        }
        
        // Log extracted filename
        Log.d(TAG, "Image filename before extension check: $fileName")

        var extension = ""
        val lastDotPos = fileName.lastIndexOf(".")
        if (lastDotPos > 0 && lastDotPos < fileName.length - 1) {
            extension = fileName.substring(lastDotPos + 1).lowercase()
        }

        val hasImageExtension = extension == "jpg" ||
                extension == "jpeg" ||
                extension == "png" ||
                extension == "gif" ||
                extension == "bmp" ||
                extension == "webp" ||
                extension == "bin" // .bin dosyası olsa bile uzantıyı değiştireceğiz

        // SoilContinuity için her zaman JPG uzantısı kullan
        if (imageUrl.contains("SoilContinuity", ignoreCase = true)) {
            fileName = removeExtension(fileName)
            fileName += ".jpg"
            mimeType = "image/jpeg"
            Log.d(TAG, "SoilContinuity file - enforced JPG: $fileName")
        }
        else if (!hasImageExtension || extension == "bin") {
            when {
                mimeType == "image/jpeg" || mimeType == "image/jpg" -> {
                    // Önce mevcut uzantıyı kaldır
                    fileName = removeExtension(fileName)
                    fileName += ".jpg"
                    // Ensure consistent MIME type
                    mimeType = "image/jpeg"
                }
                mimeType == "image/png" -> {
                    fileName = removeExtension(fileName)
                    fileName += ".png"
                }
                mimeType == "image/gif" -> {
                    fileName = removeExtension(fileName)
                    fileName += ".gif"
                }
                mimeType == "image/bmp" -> {
                    fileName = removeExtension(fileName)
                    fileName += ".bmp"
                }
                mimeType == "image/webp" -> {
                    fileName = removeExtension(fileName)
                    fileName += ".webp"
                }
                else -> {
                    // Default to jpg for unknown image types
                    fileName = removeExtension(fileName)
                    fileName += ".jpg"
                    mimeType = "image/jpeg"
                }
            }
            Log.d(TAG, "Added extension to filename: $fileName")
        } else if (extension != "bin") {
            // If file already has extension (but not .bin), ensure the MIME type matches
            when {
                fileName.lowercase().endsWith(".jpg") || fileName.lowercase().endsWith(".jpeg") -> 
                    mimeType = "image/jpeg"
                fileName.lowercase().endsWith(".png") -> 
                    mimeType = "image/png"
                fileName.lowercase().endsWith(".gif") -> 
                    mimeType = "image/gif"
                fileName.lowercase().endsWith(".bmp") -> 
                    mimeType = "image/bmp"
                fileName.lowercase().endsWith(".webp") -> 
                    mimeType = "image/webp"
            }
        } else {
            // .bin uzantısı var, doğru uzantıyla değiştir
            fileName = removeExtension(fileName)
            when {
                mimeType == "image/jpeg" || mimeType == "image/jpg" -> {
                    fileName += ".jpg"
                    mimeType = "image/jpeg"
                }
                mimeType == "image/png" -> {
                    fileName += ".png"
                }
                else -> {
                    // Varsayılan olarak jpg kullan
                    fileName += ".jpg"
                    mimeType = "image/jpeg"
                }
            }
            Log.d(TAG, "Using existing extension, MIME type: $mimeType")
        }

        if (isContentUri) {
            saveContentUriToFile(Uri.parse(imageUrl), fileName, mimeType)
            return
        }

        var cleanUrl = imageUrl
        if (cleanUrl.contains(" ")) {
            cleanUrl = cleanUrl.replace(" ", "%20")
        }
        
        Log.d(TAG, "Clean URL for download: $cleanUrl")

        val userAgent = webView?.settings?.userAgentString ?: "Mozilla/5.0"

        val systemDownloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        
        try {
            val request = android.app.DownloadManager.Request(Uri.parse(cleanUrl))
        
            // Ensure consistent MIME type for JPGs
            var finalMimeType = mimeType
            if (finalMimeType == "image/jpg") {
                finalMimeType = "image/jpeg"
            }
            
            // Log final MIME type
            Log.d(TAG, "Final MIME type for download: $finalMimeType")
            request.setMimeType(finalMimeType)
            
            // Handle different Android versions appropriately
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    // For Android 10+, use MediaStore for better visibility
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                        // Ensure MIME type is consistent for JPGs
                        if (finalMimeType == "image/jpg") {
                            finalMimeType = "image/jpeg"
                        }
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, finalMimeType)
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, 
                            "${Environment.DIRECTORY_PICTURES}/$DOWNLOAD_DIRECTORY")
                    }
                            
                    val uri = context.contentResolver.insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    
                    if (uri != null) {
                        Log.d(TAG, "Using MediaStore destination for Android 10+: $uri")
                        request.setDestinationUri(uri)
                    } else {
                        // Fallback to legacy method
                        Log.d(TAG, "MediaStore URI is null, using legacy destination")
                        request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_PICTURES,
                            "$DOWNLOAD_DIRECTORY${File.separator}$fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting MediaStore destination", e)
                    // Fallback to legacy method
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_PICTURES,
                        "$DOWNLOAD_DIRECTORY${File.separator}$fileName")
                }
            } else {
                // For older Android versions, use the legacy method
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    "$DOWNLOAD_DIRECTORY${File.separator}$fileName")
            }
            
            // Set user agent if available
            if (userAgent.isNotEmpty()) {
                request.addRequestHeader("User-Agent", userAgent)
            }
            
            // Get cookies if WebView is provided
            if (webView != null) {
                val cookies = CookieManager.getInstance().getCookie(imageUrl)
                if (!cookies.isNullOrEmpty()) {
                    request.addRequestHeader("Cookie", cookies)
                }
            }
            
            // Set notification visibility
            request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            val finalFileName = fileName
            
            // Enqueue the download and get the ID
            val downloadId = systemDownloadManager.enqueue(request)
            
            // Register BroadcastReceiver to listen for download completion
            downloadManager.registerDownloadCompleteReceiver(
                context,
                downloadId,
                finalFileName,
                finalMimeType)
                
            // Log the download ID
            Log.d(TAG, "Image download request enqueued with ID: $downloadId")
            
            // Add to our download manager's active downloads
            this.downloadManager.addActiveDownload(downloadId, fileName)
            
            Log.d(TAG, "Started image download: $fileName (ID: $downloadId)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting image download", e)
            // Fallback to regular download manager if there's an error
            downloadManager.downloadFile(imageUrl, fileName, mimeType, userAgent, null)
        }
    }

    /**
     * Saves a content URI to a file.
     */
    private fun saveContentUriToFile(contentUri: Uri, fileName: String, mimeType: String) {
        try {
            val inputStream = context.contentResolver.openInputStream(contentUri)
                ?: run {
                    Log.e(TAG, "Failed to open input stream for content URI")
                    return
                }

            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ), DOWNLOAD_DIRECTORY
            )
            
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create downloads directory")
                    inputStream.close()
                    return
                }
            }

            val outputFile = File(downloadsDir, fileName)
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(1024)
            var length: Int
            
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val fileUri = Uri.fromFile(outputFile)
            mediaScanIntent.data = fileUri
            context.sendBroadcast(mediaScanIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving content URI to file", e)
        }
    }
}