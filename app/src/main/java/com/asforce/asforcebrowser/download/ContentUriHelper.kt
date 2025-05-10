package com.asforce.asforcebrowser.download

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Helper class for working with content URIs.
 */
class ContentUriHelper(context: Context) {
    companion object {
        private const val TAG = "ContentUriHelper"
        private const val DOWNLOAD_DIRECTORY = "Downloads"
    }
    
    private val context: Context = context.applicationContext
    
    /**
     * Checks if a URL is a content URI.
     *
     * @param url The URL to check
     * @return True if the URL is a content URI, false otherwise
     */
    fun isContentUri(url: String?): Boolean {
        return url != null && url.startsWith("content://")
    }
    
    /**
     * Extracts metadata from a content URI.
     *
     * @param contentUri The content URI
     * @return An array containing [fileName, mimeType]
     */
    fun extractContentUriMetadata(contentUri: Uri): Array<String> {
        var fileName = ""
        var mimeType = ""
        
        try {
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
                    val nameIndex = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }

                    val mimeIndex = it.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                    if (mimeIndex != -1) {
                        val contentMimeType = it.getString(mimeIndex)
                        if (!TextUtils.isEmpty(contentMimeType)) {
                            mimeType = contentMimeType
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting content URI metadata", e)
        }
        
        return arrayOf(fileName, mimeType)
    }
    
    /**
     * Saves a content URI to a file.
     *
     * @param contentUri The content URI
     * @param fileName The file name to save as
     * @param mimeType The MIME type of the file
     * @return True if the save was successful, false otherwise
     */
    fun saveContentUriToFile(contentUri: Uri, fileName: String, mimeType: String): Boolean {
        try {
            val inputStream = context.contentResolver.openInputStream(contentUri)
                ?: run {
                    Log.e(TAG, "Failed to open input stream for content URI")
                    return false
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
                    return false
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

            // Notify the system about the new file
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val fileUri = Uri.fromFile(outputFile)
            mediaScanIntent.data = fileUri
            context.sendBroadcast(mediaScanIntent)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving content URI to file", e)
            return false
        }
    }
}