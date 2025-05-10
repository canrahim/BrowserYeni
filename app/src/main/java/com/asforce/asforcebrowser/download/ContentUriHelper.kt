package com.asforce.asforcebrowser.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Helper class for working with content URIs.
 * Optimized with coroutines and better resource management.
 */
class ContentUriHelper(context: Context) {
    companion object {
        private const val TAG = "ContentUriHelper"
        private const val DOWNLOAD_DIRECTORY = "Downloads"
        private const val DEBUG = false // Turn off for production
    }

    private val context: Context = context.applicationContext
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Checks if a URL is a content URI.
     */
    fun isContentUri(url: String?): Boolean {
        return url != null && url.startsWith("content://")
    }

    /**
     * Extracts metadata from a content URI.
     * Returns an array containing [fileName, mimeType]
     */
    suspend fun extractContentUriMetadata(contentUri: Uri): Array<String> = withContext(Dispatchers.IO) {
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

        arrayOf(fileName, mimeType)
    }

    /**
     * Saves a content URI to a file.
     * Now uses coroutines for better performance.
     */
    fun saveContentUriToFile(contentUri: Uri, fileName: String, mimeType: String) {
        coroutineScope.launch {
            try {
                val result = performSave(contentUri, fileName, mimeType)
                if (!result && DEBUG) {
                    Log.e(TAG, "Failed to save content URI to file")
                }
            } catch (e: Exception) {
                if (DEBUG) Log.e(TAG, "Error in saveContentUriToFile coroutine", e)
            }
        }
    }

    private suspend fun performSave(contentUri: Uri, fileName: String, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            input = context.contentResolver.openInputStream(contentUri) ?: return@withContext false

            val downloadsDir = getDownloadsDirectory()
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                if (DEBUG) Log.e(TAG, "Failed to create downloads directory")
                return@withContext false
            }

            val outputFile = File(downloadsDir, fileName)
            output = FileOutputStream(outputFile)

            // Use coroutine-friendly copy
            input.copyTo(output, bufferSize = 8192)

            // Notify media scanner in background
            notifyMediaScanner(outputFile)

            true
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "Error saving content URI to file", e)
            false
        } finally {
            try {
                output?.flush()
                output?.close()
                input?.close()
            } catch (e: Exception) {
                if (DEBUG) Log.e(TAG, "Error closing streams", e)
            }
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
     * Cleanup when helper is no longer needed
     */
    fun cleanup() {
        coroutineScope.cancel()
    }
}