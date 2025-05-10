package com.asforce.asforcebrowser.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for downloading images.
 * Optimized with coroutines and better file handling.
 */
class ImageDownloader(context: Context) {
    companion object {
        private const val TAG = "ImageDownloader"
        private const val DOWNLOAD_DIRECTORY = "Downloads"
        private const val DEBUG = false // Turn off for production
    }

    private val context: Context = context.applicationContext
    private val downloadManager: DownloadManager = DownloadManager.getInstance(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Downloads an image from the given URL with improved async handling.
     */
    fun downloadImage(imageUrl: String, webView: WebView?) {
        if (DEBUG) Log.d(TAG, "downloadImage: $imageUrl")

        coroutineScope.launch {
            try {
                processImageDownload(imageUrl, webView)
            } catch (e: Exception) {
                if (DEBUG) Log.e(TAG, "Error in downloadImage coroutine", e)
                // Fallback to regular download manager
                downloadManager.downloadFile(imageUrl, null, "image/jpeg",
                    webView?.settings?.userAgentString, null)
            }
        }
    }

    private suspend fun processImageDownload(imageUrl: String, webView: WebView?) = withContext(Dispatchers.IO) {
        val (fileName, mimeType) = determineFileNameAndMimeType(imageUrl)

        when {
            imageUrl.startsWith("content://") -> {
                saveContentUriToFile(Uri.parse(imageUrl), fileName, mimeType)
            }
            else -> {
                startSystemDownload(imageUrl, fileName, mimeType, webView)
            }
        }
    }

    private suspend fun determineFileNameAndMimeType(imageUrl: String): Pair<String, String> = withContext(Dispatchers.Default) {
        var fileName = ""
        var mimeType = "image/jpeg" // Default MIME type

        if (imageUrl.startsWith("content://")) {
            // Extract metadata from content URI
            val metadata = extractContentUriMetadata(Uri.parse(imageUrl))
            fileName = metadata.first
            mimeType = metadata.second.ifEmpty { mimeType }
        } else {
            // Extract filename from regular URL
            fileName = extractFileNameFromUrl(imageUrl)

            // Special handling for SoilContinuity
            if (imageUrl.contains("SoilContinuity", ignoreCase = true)) {
                val uri = Uri.parse(imageUrl)
                val id = uri.getQueryParameter("id")
                fileName = if (!id.isNullOrEmpty()) {
                    "${id}_SoilContinuity"
                } else {
                    "SoilContinuity"
                }
                mimeType = "image/jpeg"
            }
        }

        // Generate default filename if needed
        if (fileName.isEmpty()) {
            fileName = generateDefaultImageFileName()
        }

        // Ensure proper extension
        fileName = ensureImageExtension(fileName, mimeType)

        // Normalize MIME type
        if (mimeType == "image/jpg") {
            mimeType = "image/jpeg"
        }

        if (DEBUG) Log.d(TAG, "Determined filename: $fileName, MIME type: $mimeType")

        Pair(fileName, mimeType)
    }

    private suspend fun extractContentUriMetadata(contentUri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        var fileName = ""
        var mimeType = ""

        try {
            context.contentResolver.query(
                contentUri,
                arrayOf(
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(
                        cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    ) ?: ""
                    mimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                    ) ?: ""
                }
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error extracting content URI metadata", e)
        }

        Pair(fileName, mimeType)
    }

    private fun extractFileNameFromUrl(imageUrl: String): String {
        try {
            val uri = Uri.parse(imageUrl)
            val lastPathSegment = uri.lastPathSegment

            return if (!lastPathSegment.isNullOrEmpty()) {
                val queryIndex = lastPathSegment.indexOf('?')
                if (queryIndex > 0) {
                    lastPathSegment.substring(0, queryIndex)
                } else {
                    lastPathSegment
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error extracting filename from URL", e)
            return ""
        }
    }

    private fun generateDefaultImageFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_$timeStamp"
    }

    private fun ensureImageExtension(fileName: String, mimeType: String): String {
        var result = fileName
        val extension = getFileExtension(fileName)

        val hasImageExtension = extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

        // Special handling for SoilContinuity
        if (fileName.contains("SoilContinuity", ignoreCase = true)) {
            result = removeExtension(fileName) + ".jpg"
            if (DEBUG) Log.d(TAG, "SoilContinuity file - enforced JPG: $result")
        }
        // Add extension if needed
        else if (!hasImageExtension || extension == "bin") {
            val properExtension = when (mimeType) {
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/png" -> ".png"
                "image/gif" -> ".gif"
                "image/bmp" -> ".bmp"
                "image/webp" -> ".webp"
                else -> ".jpg" // Default to jpg
            }

            result = removeExtension(fileName) + properExtension
            if (DEBUG) Log.d(TAG, "Added extension to filename: $result")
        }

        return result
    }

    private fun getFileExtension(fileName: String): String {
        val lastDotPos = fileName.lastIndexOf(".")
        return if (lastDotPos > 0 && lastDotPos < fileName.length - 1) {
            fileName.substring(lastDotPos + 1).lowercase()
        } else {
            ""
        }
    }

    private fun removeExtension(fileName: String): String {
        val lastDotPos = fileName.lastIndexOf(".")
        return if (lastDotPos > 0) {
            fileName.substring(0, lastDotPos)
        } else {
            fileName
        }
    }

    private suspend fun startSystemDownload(
        imageUrl: String,
        fileName: String,
        mimeType: String,
        webView: WebView?
    ) = withContext(Dispatchers.IO) {
        val cleanUrl = imageUrl.replace(" ", "%20")
        val userAgent = webView?.settings?.userAgentString ?: "Mozilla/5.0"

        val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager

        try {
            val request = createDownloadRequest(cleanUrl, fileName, mimeType, userAgent, webView)
            val downloadId = systemDownloadManager.enqueue(request)

            // Register completion listener
            downloadManager.registerDownloadCompleteReceiver(
                context, downloadId, fileName, mimeType
            )

            // Add to active downloads
            downloadManager.addActiveDownload(downloadId, fileName)

            if (DEBUG) Log.d(TAG, "Started image download: $fileName (ID: $downloadId)")

        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error starting image download", e)
            // Fallback to regular download manager
            downloadManager.downloadFile(imageUrl, fileName, mimeType, userAgent, null)
        }
    }

    private fun createDownloadRequest(
        cleanUrl: String,
        fileName: String,
        mimeType: String,
        userAgent: String,
        webView: WebView?
    ): android.app.DownloadManager.Request {
        val request = android.app.DownloadManager.Request(Uri.parse(cleanUrl))

        // Set MIME type
        request.setMimeType(mimeType)

        // Set destination
        setDownloadDestination(request, fileName)

        // Set request headers
        if (userAgent.isNotEmpty()) {
            request.addRequestHeader("User-Agent", userAgent)
        }

        // Add cookies if WebView is provided
        webView?.let {
            val cookies = CookieManager.getInstance().getCookie(cleanUrl)
            if (!cookies.isNullOrEmpty()) {
                request.addRequestHeader("Cookie", cookies)
            }
        }

        // Set notification visibility
        request.setNotificationVisibility(
            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )

        return request
    }

    private fun setDownloadDestination(request: android.app.DownloadManager.Request, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // For Android 10+, use MediaStore for better visibility
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$DOWNLOAD_DIRECTORY")
                }

                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )

                if (uri != null) {
                    request.setDestinationUri(uri)
                    return
                }
            } catch (e: Exception) {
                if (DEBUG) Log.e(TAG, "Error setting MediaStore destination", e)
            }
        }

        // Fallback for older versions or if MediaStore fails
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_PICTURES,
            "$DOWNLOAD_DIRECTORY${File.separator}$fileName"
        )
    }

    /**
     * Saves a content URI to a file using coroutines.
     */
    private suspend fun saveContentUriToFile(contentUri: Uri, fileName: String, mimeType: String) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                val downloadsDir = getDownloadsDirectory()
                if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                    if (DEBUG) Log.e(TAG, "Failed to create downloads directory")
                    return@withContext
                }

                val outputFile = File(downloadsDir, fileName)
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }

                // Notify media scanner
                notifyMediaScanner(outputFile)

                if (DEBUG) Log.d(TAG, "Saved content URI to file: ${outputFile.absolutePath}")
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error saving content URI to file", e)
        }
    }

    private fun getDownloadsDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DOWNLOAD_DIRECTORY
        )
    }

    private fun notifyMediaScanner(file: File) {
        try {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val fileUri = Uri.fromFile(file)
            mediaScanIntent.data = fileUri
            context.sendBroadcast(mediaScanIntent)
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error notifying media scanner", e)
        }
    }

    /**
     * Clean up resources when downloader is no longer needed
     */
    fun cleanup() {
        coroutineScope.cancel()
    }
}