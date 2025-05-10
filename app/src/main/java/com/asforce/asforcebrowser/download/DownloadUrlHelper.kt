package com.asforce.asforcebrowser.download

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.util.regex.Pattern
import java.util.regex.Matcher

/**
 * Utility class for identifying and handling special download URLs.
 */
object DownloadUrlHelper {
    private const val TAG = "DownloadUrlHelper"
    
    /**
     * Checks if a URL is a download URL.
     *
     * @param url The URL to check
     * @return True if the URL is a download URL, false otherwise
     */
    fun isDownloadUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) {
            return false
        }
        
        // Common download path patterns
        return url.contains("/EXT/PKControl/DownloadFile") ||
               url.contains("/DownloadFile") ||
               url.contains("/download.php") ||
               url.contains("/filedownload") ||
               url.contains("/file_download") ||
               url.contains("/getfile") ||
               url.contains("/get_file") ||
               (url.contains("download") && url.contains("id=")) ||
               Pattern.compile(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|txt|csv)($|\\?.*)").matcher(url).matches()
    }
    
    /**
     * Gets the file name from a URL.
     *
     * @param url The URL to get the file name from
     * @return The file name
     */
    fun getFileNameFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) {
            return null
        }
        
        // Try to extract file name from URL
        var fileName = URLUtil.guessFileName(url, null, null)
        
        // If URL has query parameters, try to extract file name from them
        val uri = Uri.parse(url)
        val fileParam = uri.getQueryParameter("file")
        if (!fileParam.isNullOrEmpty()) {
            fileName = fileParam
        }
        
        val nameParam = uri.getQueryParameter("name")
        if (!nameParam.isNullOrEmpty()) {
            fileName = nameParam
        }
        
        val fnParam = uri.getQueryParameter("fn")
        if (!fnParam.isNullOrEmpty()) {
            fileName = fnParam
        }
        
        // Special handling for specific URLs
        if (url.contains("/EXT/PKControl/DownloadFile") || url.contains("/DownloadFile")) {
            val type = uri.getQueryParameter("type")
            val id = uri.getQueryParameter("id")
            
            if (type != null && type.startsWith("F") && type.length > 1) {
                fileName = type.substring(1)
            } else if (!id.isNullOrEmpty()) {
                fileName = "download_$id"
            }
        }
        
        // Ensure file name has extension
        if (fileName != null && !fileName.contains(".")) {
            val mimeType = getMimeTypeFromUrl(url)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            fileName = if (extension != null) {
                "$fileName.$extension"
            } else {
                // Default to .bin if can't determine extension
                "$fileName.bin"
            }
        }
        
        return fileName
    }
    
    /**
     * Gets the MIME type from a URL.
     *
     * @param url The URL to get the MIME type from
     * @return The MIME type
     */
    fun getMimeTypeFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) {
            return null
        }
        
        // Check for common file extensions
        return when {
            url.lowercase().endsWith(".pdf") -> "application/pdf"
            url.lowercase().endsWith(".doc") -> "application/msword"
            url.lowercase().endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            url.lowercase().endsWith(".xls") -> "application/vnd.ms-excel"
            url.lowercase().endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            url.lowercase().endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            url.lowercase().endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            url.lowercase().endsWith(".zip") -> "application/zip"
            url.lowercase().endsWith(".rar") -> "application/x-rar-compressed"
            url.lowercase().endsWith(".7z") -> "application/x-7z-compressed"
            url.lowercase().endsWith(".txt") -> "text/plain"
            url.lowercase().endsWith(".csv") -> "text/csv"
            url.lowercase().endsWith(".jpg") || url.lowercase().endsWith(".jpeg") -> "image/jpeg"
            url.lowercase().endsWith(".png") -> "image/png"
            url.lowercase().endsWith(".gif") -> "image/gif"
            url.lowercase().endsWith(".mp3") -> "audio/mpeg"
            url.lowercase().endsWith(".mp4") -> "video/mp4"
            url.lowercase().endsWith(".webm") -> "video/webm"
            else -> {
                // Try to extract extension from URL
                val extension = MimeTypeMap.getFileExtensionFromUrl(url)
                if (!extension.isNullOrEmpty()) {
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    if (mimeType != null) {
                        return mimeType
                    }
                }
                // Default to octet-stream
                "application/octet-stream"
            }
        }
    }
    
    /**
     * Extracts file name from content disposition header.
     *
     * @param contentDisposition The content disposition header
     * @return The file name
     */
    fun extractFileNameFromContentDisposition(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrEmpty()) {
            return null
        }
        
        try {
            val pattern = Pattern.compile(
                "filename\\*?=['\"]?(?:UTF-\\d['\"]*)?([^;\\r\\n\"']*)['\"]?;?", 
                Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(contentDisposition)
            
            if (matcher.find()) {
                var fileName = matcher.group(1)
                
                // Decode URL-encoded parts
                fileName = fileName.replace("%20".toRegex(), " ")
                    .replace("%[0-9a-fA-F]{2}".toRegex(), "")
                    .trim()
                
                // Remove quotes if present
                if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                    fileName = fileName.substring(1, fileName.length - 1)
                }
                
                // Replace invalid file name characters
                fileName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                
                return fileName
            }
            
            // Try another pattern as fallback
            val fallbackPattern = Pattern.compile(
                "filename=['\"]?([^;\\r\\n\"']*)['\"]?", 
                Pattern.CASE_INSENSITIVE
            )
            val fallbackMatcher = fallbackPattern.matcher(contentDisposition)
            
            if (fallbackMatcher.find()) {
                var fileName = fallbackMatcher.group(1).trim()
                fileName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                return fileName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing content disposition: $contentDisposition", e)
        }
        
        return null
    }
}