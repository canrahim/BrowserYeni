package com.asforce.asforcebrowser.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
    private const val CONNECTION_TIMEOUT = 10000 // 10 saniye
    private const val READ_TIMEOUT = 10000 // 10 saniye
    
    /**
     * Favicon'u indirerek saklar
     */
    suspend fun downloadAndSaveFavicon(context: Context, url: String, tabId: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                // En iyi favicon URL'ini bul
                val faviconUrl = findBestFaviconUrl(url)
                if (faviconUrl.isNullOrEmpty()) {
                    Log.w(TAG, "Favicon URL bulunamadı: $url")
                    // Alternatif favicon kaynaklarını dene
                    val alternativeFaviconUrl = tryAlternativeFaviconSources(url)
                    if (alternativeFaviconUrl.isNullOrEmpty()) {
                        // Yine bulunamazsa, domain baş harfinden favicon oluştur
                        val generatedFavicon = generateFaviconFromDomain(url)
                        val fileName = "favicon_${tabId}.png"
                        saveFaviconToFile(context, generatedFavicon, fileName)
                        return@withContext "$FAVICON_DIRECTORY/$fileName"
                    } else {
                        // Alternatif favicon'u indir
                        val bitmap = downloadFavicon(alternativeFaviconUrl)
                        if (bitmap != null) {
                            val fileName = "favicon_${tabId}.png"
                            saveFaviconToFile(context, bitmap, fileName)
                            return@withContext "$FAVICON_DIRECTORY/$fileName"
                        } else {
                            // Yine indirilemezse, domain baş harfinden favicon oluştur
                            val generatedFavicon = generateFaviconFromDomain(url)
                            val fileName = "favicon_${tabId}.png"
                            saveFaviconToFile(context, generatedFavicon, fileName)
                            return@withContext "$FAVICON_DIRECTORY/$fileName"
                        }
                    }
                }
                
                // Favicon'u indir
                val bitmap = downloadFavicon(faviconUrl)
                if (bitmap == null) {
                    Log.w(TAG, "Favicon indirilemedi: $faviconUrl")
                    // İndiremezse, domain baş harfinden favicon oluştur
                    val generatedFavicon = generateFaviconFromDomain(url)
                    val fileName = "favicon_${tabId}.png"
                    saveFaviconToFile(context, generatedFavicon, fileName)
                    return@withContext "$FAVICON_DIRECTORY/$fileName"
                }
                
                // Favicon'u sakla
                val fileName = "favicon_${tabId}.png"
                saveFaviconToFile(context, bitmap, fileName)
                
                "$FAVICON_DIRECTORY/$fileName"
            } catch (e: Exception) {
                Log.e(TAG, "Favicon indirme hatası: ${e.message}")
                // Hata durumunda bile bir favicon oluştur
                try {
                    val generatedFavicon = generateFaviconFromDomain(url)
                    val fileName = "favicon_${tabId}.png"
                    saveFaviconToFile(context, generatedFavicon, fileName)
                    return@withContext "$FAVICON_DIRECTORY/$fileName"
                } catch (genEx: Exception) {
                    Log.e(TAG, "Favicon oluşturma hatası: ${genEx.message}")
                    null
                }
            }
        }
    }
    
    /**
     * Web sayfasını parse ederek en iyi favicon URL'ini bulur
     */
    private suspend fun findBestFaviconUrl(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = extractBaseUrl(url)
                val cleanUrl = if (url.startsWith("http")) url else "https://$url"
                
                // HTML'i indir ve parse et
                val connection = Jsoup.connect(cleanUrl)
                    .timeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                
                val doc: Document = connection.get()
                
                // Öncelik sırası - daha yüksek kaliteli favicon'ları tercih et
                val appleTouchIconEl = doc.select("link[rel='apple-touch-icon'],link[rel='apple-touch-icon-precomposed']").firstOrNull()
                if (appleTouchIconEl != null) {
                    val iconHref = appleTouchIconEl.attr("href")
                    return@withContext resolveUrl(baseUrl, iconHref)
                }
                
                // Web uygulaması favori ikonları
                val msIconEl = doc.select("meta[name='msapplication-TileImage']").firstOrNull()
                if (msIconEl != null) {
                    val iconHref = msIconEl.attr("content")
                    return@withContext resolveUrl(baseUrl, iconHref)
                }
                
                // Diğer favicon türleri
                val iconEls = doc.select("link[rel='icon'],link[rel='shortcut icon'],link[rel='fluid-icon']").toList()
                if (iconEls.isNotEmpty()) {
                    // En yüksek boyutlu favicon'u tercih et
                    var bestIcon = iconEls.first()
                    var bestSize = 0
                    
                    for (icon in iconEls) {
                        val sizes = icon.attr("sizes")
                        var currentSize = 0
                        
                        if (sizes.isNotEmpty() && sizes != "any") {
                            // 32x32 gibi boyut formatlarını işle
                            val parts = sizes.split("x", limit = 2)
                            if (parts.size == 2) {
                                currentSize = parts[0].toIntOrNull() ?: 0
                            }
                        }
                        
                        // SVG veya PNG formatını tercih et
                        val type = icon.attr("type")
                        val href = icon.attr("href")
                        
                        if (type.contains("svg") || href.endsWith(".svg")) {
                            // SVG'yi her zaman tercih et
                            bestIcon = icon
                            break
                        } else if (currentSize > bestSize) {
                            bestSize = currentSize
                            bestIcon = icon
                        }
                    }
                    
                    val iconHref = bestIcon.attr("href")
                    return@withContext resolveUrl(baseUrl, iconHref)
                }
                
                // Hiçbir ikon bulunamazsa, standart favicon.ico'ya dön
                "$baseUrl/favicon.ico"
            } catch (e: Exception) {
                Log.e(TAG, "Favicon URL bulma hatası: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Alternatif favicon kaynaklarını dener
     */
    private suspend fun tryAlternativeFaviconSources(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val domain = extractDomain(url)
                
                // Google Favicon API'sini dene
                "https://www.google.com/s2/favicons?domain=$domain&sz=64"
            } catch (e: Exception) {
                Log.e(TAG, "Alternatif favicon kaynakları hatası: ${e.message}")
                null
            }
        }
    }
    
    /**
     * URL'den domain adını çıkarır (www ve protokol olmadan)
     */
    private fun extractDomain(url: String): String {
        return try {
            val cleanUrl = if (url.startsWith("http")) url else "https://$url"
            val urlObject = URL(cleanUrl)
            val host = urlObject.host
            
            // www. önekini kaldır
            if (host.startsWith("www.")) {
                host.substring(4)
            } else {
                host
            }
        } catch (e: Exception) {
            // Geçerli bir URL değilse, giriş değerini döndür
            url
        }
    }
    
    /**
     * Göreceli URL'leri mutlak URL'lere dönüştürür
     */
    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return when {
            relativeUrl.startsWith("http") -> relativeUrl // Zaten mutlak URL
            relativeUrl.startsWith("//") -> "https:$relativeUrl" // Protocol-relative URL
            relativeUrl.startsWith("/") -> "$baseUrl$relativeUrl" // Root-relative URL
            else -> "$baseUrl/$relativeUrl" // Göreceli URL
        }
    }
    
    /**
     * Domaine dayalı otomatik favicon oluşturur
     */
    private fun generateFaviconFromDomain(url: String): Bitmap {
        val domain = extractDomain(url)
        val initial = if (domain.isNotEmpty()) domain[0].uppercase() else "A"
        
        // Rastgele renk seçimi (domain adına göre tutarlı)
        val colors = arrayOf(
            Color.parseColor("#2196F3"), // Mavi
            Color.parseColor("#F44336"), // Kırmızı
            Color.parseColor("#4CAF50"), // Yeşil
            Color.parseColor("#FF9800"), // Turuncu
            Color.parseColor("#9C27B0"), // Mor
            Color.parseColor("#009688"), // Turkuaz
            Color.parseColor("#3F51B5")  // Indigo
        )
        
        // Domain adına göre tutarlı bir renk seç
        val colorIndex = domain.hashCode().rem(colors.size).let { if (it < 0) it + colors.size else it }
        val backgroundColor = colors[colorIndex]
        
        // 64x64 boyutunda bitmap oluştur
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Arka planı çiz (yuvarlak)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = backgroundColor
        canvas.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        
        // Yazıyı çiz
        paint.color = Color.WHITE
        paint.textSize = size * 0.5f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        
        // Yazıyı ortala
        val bounds = Rect()
        paint.getTextBounds(initial, 0, initial.length, bounds)
        
        val x = size / 2f
        val y = size / 2f + (bounds.height() / 2f) - bounds.bottom
        
        canvas.drawText(initial, x, y, paint)
        
        return bitmap
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
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                
                // Bitmap başarıyla oluşturuldu mu?
                if (bitmap != null) {
                    // Çok küçük bir favicon ise büyüt
                    if (bitmap.width < 16 || bitmap.height < 16) {
                        val size = 32 // Minimum boyut
                        return Bitmap.createScaledBitmap(bitmap, size, size, true)
                    }
                    bitmap
                } else {
                    Log.w(TAG, "Favicon decode edilemedi: $faviconUrl")
                    null
                }
            } else {
                Log.w(TAG, "Favicon indirme başarısız - HTTP ${connection.responseCode}: $faviconUrl")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Favicon indirme hatası: ${e.message} - URL: $faviconUrl")
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