package com.asforce.asforcebrowser.download

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for identifying and handling special download URLs.
 * Optimized with caching and improved pattern matching.
 */
object DownloadUrlHelper {
    private const val TAG = "DownloadUrlHelper"
    private const val DEBUG = false // Turn off for production

    // Cache for common URL patterns and MIME types
    private val downloadPatternCache = ConcurrentHashMap<String, Boolean>()
    private val mimeTypeCache = ConcurrentHashMap<String, String?>()
    private val fileNameCache = ConcurrentHashMap<String, String?>()

    // Cached regex patterns for better performance
    private val fileExtensionPattern by lazy {
        Pattern.compile(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|txt|csv)($|\\?.*)")
    }

    private val contentDispositionPattern by lazy {
        Pattern.compile("filename\\*?=['\"]?(?:UTF-\\d['\"]*)?([^;\\r\\n\"']*)['\"]?;?", Pattern.CASE_INSENSITIVE)
    }

    private val fallbackPattern by lazy {
        Pattern.compile("filename=['\"]?([^;\\r\\n\"']*)['\"]?", Pattern.CASE_INSENSITIVE)
    }

    // Common download URL patterns - GÜNCELLENMIŞ (Rapor URL'leri eklendi)
    private val downloadUrlPatterns = listOf(
        "/EXT/PKControl/DownloadFile",
        "/DownloadFile",
        "/download.php",
        "/filedownload",
        "/file_download",
        "/getfile",
        "/get_file",
        "/EXT/PKControl/PdfForEK",  // Yeni eklenen rapor URL'si
        "/PdfForEK"                 // Yeni eklenen rapor URL'si kısayolu
    )

    /**
     * Checks if a URL is a download URL using caching.
     */
    @JvmStatic
    fun isDownloadUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false

        // Check cache first
        downloadPatternCache[url]?.let { return it }

        val isDownload = checkDownloadPattern(url)
        downloadPatternCache[url] = isDownload
        return isDownload
    }

    private fun checkDownloadPattern(url: String): Boolean {
        val lowerUrl = url.lowercase()

        // Check common download patterns
        for (pattern in downloadUrlPatterns) {
            if (lowerUrl.contains(pattern)) return true
        }

        // Check for download with id parameter
        if (lowerUrl.contains("download") && lowerUrl.contains("id=")) return true

        // YENI EKLENEN: PdfForEK için özel kontrol
        if (lowerUrl.contains("/pdfforek") && lowerUrl.contains("customerid=")) return true

        // Check file extension pattern
        if (fileExtensionPattern.matcher(url).matches()) return true

        return false
    }

    /**
     * Gets the file name from a URL with improved caching.
     */
    @JvmStatic
    fun getFileNameFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null

        // Check cache first
        fileNameCache[url]?.let { return it }

        val fileName = extractFileName(url)

        // Cache the result
        fileNameCache[url] = fileName
        return fileName
    }

    private fun extractFileName(url: String): String? {
        try {
            val uri = Uri.parse(url)

            // YENI EKLENEN: PdfForEK için özel işleme
            if (url.contains("/PdfForEK")) {
                val customerId = uri.getQueryParameter("customerId")
                val type = uri.getQueryParameter("type")
                val fileName = when {
                    customerId != null && type != null -> "Rapor_${customerId}_${type}_${System.currentTimeMillis()}.pdf"
                    customerId != null -> "Rapor_${customerId}_${System.currentTimeMillis()}.pdf"
                    else -> "Rapor_${System.currentTimeMillis()}.pdf"
                }
                return fileName
            }

            // Try to extract file name from query parameters
            var fileName = extractFileNameFromParams(uri)

            // Special handling for specific download URLs
            if (fileName == null && (url.contains("/EXT/PKControl/DownloadFile") || url.contains("/DownloadFile"))) {
                fileName = handleSpecialDownloadUrls(uri)
            }

            // Fallback to URLUtil
            if (fileName == null) {
                fileName = URLUtil.guessFileName(url, null, null)
            }

            // Ensure file name has extension
            if (fileName != null && !fileName.contains(".")) {
                val mimeType = getMimeTypeFromUrl(url)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                fileName = if (extension != null) {
                    "$fileName.$extension"
                } else {
                    "$fileName.bin"
                }
            }

            return fileName
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error extracting filename from URL: $url", e)
            return null
        }
    }

    private fun extractFileNameFromParams(uri: Uri): String? {
        // Check common parameter names
        listOf("file", "name", "fn").forEach { param ->
            uri.getQueryParameter(param)?.let { return it }
        }
        return null
    }

    private fun handleSpecialDownloadUrls(uri: Uri): String? {
        val type = uri.getQueryParameter("type")
        val id = uri.getQueryParameter("id")

        return when {
            type != null && type.startsWith("F") && type.length > 1 -> type.substring(1)
            !id.isNullOrEmpty() -> "download_$id"
            else -> null
        }
    }

    /**
     * Gets the MIME type from a URL with caching.
     */
    @JvmStatic
    fun getMimeTypeFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null

        // Check cache first
        mimeTypeCache[url]?.let { return it }

        val mimeType = determineMimeType(url)
        mimeTypeCache[url] = mimeType
        return mimeType
    }

    private fun determineMimeType(url: String): String? {
        val lowerUrl = url.lowercase()

        // Define MIME type mappings
        val extensionToMimeMap = mapOf(
            ".pdf" to "application/pdf",
            ".doc" to "application/msword",
            ".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".xls" to "application/vnd.ms-excel",
            ".xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ".ppt" to "application/vnd.ms-powerpoint",
            ".pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ".zip" to "application/zip",
            ".rar" to "application/x-rar-compressed",
            ".7z" to "application/x-7z-compressed",
            ".txt" to "text/plain",
            ".csv" to "text/csv",
            ".jpg" to "image/jpeg",
            ".jpeg" to "image/jpeg",
            ".png" to "image/png",
            ".gif" to "image/gif",
            ".mp3" to "audio/mpeg",
            ".mp4" to "video/mp4",
            ".webm" to "video/webm"
        )

        // Check for extensions in URL
        for ((extension, mimeType) in extensionToMimeMap) {
            if (lowerUrl.endsWith(extension)) {
                return mimeType
            }
        }

        // Try to extract extension from URL using URLUtil
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (!extension.isNullOrEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let {
                return it
            }
        }

        // Check URL patterns for format hints
        return checkUrlPatternsForMimeType(lowerUrl)
    }

    private fun checkUrlPatternsForMimeType(lowerUrl: String): String {
        val patternToMimeMap = mapOf(
            "pdf=true" to "application/pdf",
            "format=pdf" to "application/pdf",
            "format=jpg" to "image/jpeg",
            "format=jpeg" to "image/jpeg",
            "format=png" to "image/png",
            "format=gif" to "image/gif"
        )

        for ((pattern, mimeType) in patternToMimeMap) {
            if (lowerUrl.contains(pattern)) {
                return mimeType
            }
        }

        return "application/octet-stream"
    }

    /**
     * Extracts file name from content disposition header with improved regex.
     */
    @JvmStatic
    fun extractFileNameFromContentDisposition(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrEmpty()) return null

        try {
            // First try with the main pattern
            contentDispositionPattern.matcher(contentDisposition).let { matcher ->
                if (matcher.find()) {
                    return processExtractedFileName(matcher.group(1))
                }
            }

            // Fallback pattern
            fallbackPattern.matcher(contentDisposition).let { matcher ->
                if (matcher.find()) {
                    return processExtractedFileName(matcher.group(1))
                }
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error parsing content disposition: $contentDisposition", e)
        }

        return null
    }

    private fun processExtractedFileName(fileName: String?): String? {
        if (fileName.isNullOrEmpty()) return null

        var processed = fileName.trim()

        // Decode URL-encoded parts
        processed = decodeUrlEncodedString(processed)

        // Remove surrounding quotes
        if (processed.startsWith("\"") && processed.endsWith("\"")) {
            processed = processed.substring(1, processed.length - 1)
        }

        // Replace invalid file name characters
        processed = processed.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

        return processed.ifEmpty { null }
    }

    private fun decodeUrlEncodedString(input: String): String {
        var decoded = input

        try {
            decoded = java.net.URLDecoder.decode(input, "UTF-8")
        } catch (e: Exception) {
            // Manual replacement for common Turkish characters
            val replacements = mapOf(
                "%20" to " ",
                "%C4%B0" to "İ",
                "%C5%9E" to "Ş",
                "%C3%9C" to "Ü",
                "%C4%9E" to "Ğ",
                "%C3%87" to "Ç",
                "%C3%96" to "Ö",
                "%C4%B1" to "ı",
                "%C5%9F" to "ş",
                "%C3%BC" to "ü",
                "%C4%9F" to "ğ",
                "%C3%A7" to "ç",
                "%C3%B6" to "ö"
            )

            // Değişken ismi değiştirilerek çözüldü
            var result = decoded
            for ((encoded, decodedChar) in replacements) {
                result = result.replace(encoded, decodedChar)
            }
            decoded = result
        }

        return decoded
    }

    /**
     * Clears all caches to free memory
     */
    @JvmStatic
    fun clearCaches() {
        downloadPatternCache.clear()
        mimeTypeCache.clear()
        fileNameCache.clear()
    }
}