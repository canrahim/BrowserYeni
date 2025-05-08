package com.asforce.asforcebrowser.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

/**
 * FaviconManager - Web sitelerinin favicon'larını yönetmek için yardımcı sınıf
 *
 * Bu sınıf, favicon'ların indirilmesi, saklanması ve yüklenmesi işlemlerini yönetir.
 * Referans: Android I/O işlemleri ve dosya yönetimi
 */
object FaviconManager {
    private const val TAG = "FaviconManager"
    private const val FAVICON_DIRECTORY = "favicons"
    
    /**
     * Favicon'u indirerek saklar
     */
    suspend fun downloadAndSaveFavicon(context: Context, url: String, tabId: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Web sitesinden favicon URL'ini çıkar
                val faviconUrl = extractFaviconUrl(url)
                if (faviconUrl.isNullOrEmpty()) {
                    Log.w(TAG, "Favicon URL bulunamadı: $url")
                    return@withContext null
                }
                
                // Favicon'u indir
                val bitmap = downloadFavicon(faviconUrl)
                if (bitmap == null) {
                    Log.w(TAG, "Favicon indirilemedi: $faviconUrl")
                    return@withContext null
                }
                
                // Favicon'u sakla
                val fileName = "favicon_${tabId}.png"
                saveFaviconToFile(context, bitmap, fileName)
                
                "$FAVICON_DIRECTORY/$fileName"
            } catch (e: Exception) {
                Log.e(TAG, "Favicon indirme hatası: ${e.message}")
                null
            }
        }
    }
    
    /**
     * URL'den favicon URL'ini çıkarır
     */
    private fun extractFaviconUrl(url: String): String? {
        return try {
            val baseUrl = extractBaseUrl(url)
            // Öncelikle standart favicon.ico konumunu dene
            // Alternatif olarak, gelecekte HTML'i parse edip
            // <link rel="icon"...> tag'inden çekme özelliği eklenebilir
            "$baseUrl/favicon.ico"
        } catch (e: Exception) {
            Log.e(TAG, "Favicon URL çıkarma hatası: ${e.message}")
            null
        }
    }
    
    /**
     * URL'den temel domain adresini çıkarır
     */
    private fun extractBaseUrl(url: String): String {
        val cleanUrl = if (url.startsWith("http")) url else "https://$url"
        val urlObject = URL(cleanUrl)
        
        return "${urlObject.protocol}://${urlObject.host}"
    }
    
    /**
     * Favicon'u belirtilen URL'den indirir
     */
    private fun downloadFavicon(faviconUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null
        
        return try {
            connection = URL(faviconUrl).openConnection() as HttpURLConnection
            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BitmapFactory.decodeStream(connection.inputStream)
            } else {
                Log.w(TAG, "Favicon indirme başarısız - HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Favicon indirme hatası: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Favicon'u cihaza kaydeder
     */
    private fun saveFaviconToFile(context: Context, bitmap: Bitmap, fileName: String): String? {
        return try {
            // Favicon klasörünü oluştur (yoksa)
            val directory = File(context.filesDir, FAVICON_DIRECTORY)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            // Dosyayı kaydet
            val file = File(directory, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Favicon kaydetme hatası: ${e.message}")
            null
        }
    }
    
    /**
     * Favicon'u dosyadan yükler
     */
    fun loadFavicon(context: Context, faviconPath: String?): Bitmap? {
        if (faviconPath == null) return null
        
        return try {
            val file = File(context.filesDir, faviconPath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Favicon yükleme hatası: ${e.message}")
            null
        }
    }
}