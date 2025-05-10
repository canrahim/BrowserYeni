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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * FaviconManager - Web sitelerinin favicon'larını yönetmek için yardımcı sınıf
 * 
 * Referans: Android Developer Guide - Bitmap and FileOutputStream usage
 * https://developer.android.com/reference/android/graphics/Bitmap
 * 
 * Bu sınıf, favicon'ların indirilmesi, saklanması ve yüklenmesi işlemlerini yönetir.
 */
object FaviconManager {
    private const val FAVICON_DIRECTORY = "favicons"
    private const val CONNECTION_TIMEOUT = 10000 // 10 saniye (artırıldı)
    private const val READ_TIMEOUT = 10000 // 10 saniye (artırıldı)
    private const val MIN_FAVICON_SIZE = 50 // Minimum geçerli dosya boyutu (azaltıldı)

    // Favicon önbelleği - aynı domain için tekrar tekrar indirmemek için
    private val faviconCache = ConcurrentHashMap<String, String>()
    
    // Pending favicon indirme işlemleri - aynı favicon'u tekrar indirmemek için
    private val pendingDownloads = ConcurrentHashMap<String, Deferred<String?>>()

    /**
     * Favicon'u indirerek saklar - önce mevcut/önbellekteki versiyonu kontrol eder
     */
    suspend fun downloadAndSaveFavicon(context: Context, url: String, tabId: Long): String? {
        if (url.isBlank()) return null
        
        return withContext(Dispatchers.IO) {
            try {
                // Önce favicon dizininin olduğundan emin ol
                val faviconDir = File(context.filesDir, FAVICON_DIRECTORY)
                if (!faviconDir.exists()) {
                    faviconDir.mkdirs()
                }

                // Domain'i çıkar
                val domain = extractDomain(url)
                
                // Mevcut favicon'u kontrol et
                val existingPath = "$FAVICON_DIRECTORY/favicon_${tabId}.png"
                val existingFile = File(context.filesDir, existingPath)

                if (existingFile.exists() && existingFile.length() > MIN_FAVICON_SIZE) {
                    // Mevcut favicon geçerli, kontrol et
                    try {
                        val bitmap = BitmapFactory.decodeFile(existingFile.absolutePath)
                        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                            bitmap.recycle()
                            return@withContext existingPath
                        }
                    } catch (e: Exception) {
                        existingFile.delete()
                    }
                }

                // Domain bazlı önbellekte kontrol et
                faviconCache[domain]?.let { cachedPath ->
                    val cachedFile = File(context.filesDir, cachedPath)
                    if (cachedFile.exists() && cachedFile.length() > MIN_FAVICON_SIZE) {
                        try {
                            val testBitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                            if (testBitmap != null && testBitmap.width > 0 && testBitmap.height > 0) {
                                testBitmap.recycle()
                                // Mevcut sekme için kopyala
                                cachedFile.copyTo(existingFile, overwrite = true)
                                return@withContext existingPath
                            }
                        } catch (e: Exception) {
                            // Önbellek geçersiz, devam et
                        }
                    }
                }

                // Aynı domain için zaten indirme işlemi başlatılmışsa bekle
                val domainKey = "download_$domain"
                val existingJob = pendingDownloads[domainKey]
                if (existingJob != null && existingJob.isActive) {
                    try {
                        val result = withTimeout(5.seconds) {
                            existingJob.await()
                        }
                        result?.let {
                            val cachedFile = File(context.filesDir, it)
                            if (cachedFile.exists()) {
                                cachedFile.copyTo(existingFile, overwrite = true)
                                return@withContext existingPath
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        // Timeout olursa devam et
                    }
                }

                // Yeni indirme işlemi başlat
                val downloadJob = async {
                    downloadFaviconInternal(context, url, domain)
                }
                
                pendingDownloads[domainKey] = downloadJob
                
                try {
                    // İndirme işlemini gerçekleştir
                    val downloadedPath = downloadJob.await()
                    
                    if (downloadedPath != null) {
                        val downloadedFile = File(context.filesDir, downloadedPath)
                        if (downloadedFile.exists() && downloadedFile != existingFile) {
                            downloadedFile.copyTo(existingFile, overwrite = true)
                        }
                        // Önbelleğe ekle
                        faviconCache[domain] = downloadedPath
                        return@withContext existingPath
                    }
                    
                    // İndirme başarısız, Google API'yi dene
                    val googleBitmap = tryGoogleFaviconAPI(url)
                    if (googleBitmap != null) {
                        val fileName = "favicon_${tabId}.png"
                        val savedPath = saveFaviconToFile(context, googleBitmap, fileName)
                        if (savedPath != null) {
                            faviconCache[domain] = "$FAVICON_DIRECTORY/$fileName"
                            return@withContext "$FAVICON_DIRECTORY/$fileName"
                        }
                    }
                    
                    // Son çare: domain'den favicon oluştur
                    val generatedBitmap = generateFaviconFromDomain(url)
                    val fileName = "favicon_${tabId}.png"
                    saveFaviconToFile(context, generatedBitmap, fileName)
                    return@withContext "$FAVICON_DIRECTORY/$fileName"
                    
                } finally {
                    pendingDownloads.remove(domainKey)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Hata durumunda domain'den favicon oluştur
                try {
                    val bitmap = generateFaviconFromDomain(url)
                    val fileName = "favicon_${tabId}.png"
                    saveFaviconToFile(context, bitmap, fileName)
                    return@withContext "$FAVICON_DIRECTORY/$fileName"
                } catch (genEx: Exception) {
                    genEx.printStackTrace()
                    null
                }
            }
        }
    }

    /**
     * Favicon indirme işleminin asıl gerçekleştirildiği fonksiyon
     */
    private suspend fun downloadFaviconInternal(context: Context, url: String, domain: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Önce web sayfasından favion URL'ini bul
                var faviconUrl: String? = null
                
                // 1. Önce HTML içinden favicon araştır
                try {
                    faviconUrl = findBestFaviconUrl(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 2. HTML'den bulunamazsa standart favicon.ico'yu dene
                if (faviconUrl.isNullOrEmpty()) {
                    val baseUrl = extractBaseUrl(url)
                    faviconUrl = "$baseUrl/favicon.ico"
                }
                
                // Favicon'u indir
                if (!faviconUrl.isNullOrEmpty()) {
                    val bitmap = downloadFavicon(faviconUrl)
                    if (bitmap != null) {
                        val fileName = "favicon_${domain.hashCode()}.png"
                        val savedPath = saveFaviconToFile(context, bitmap, fileName)
                        if (savedPath != null) {
                            return@withContext "$FAVICON_DIRECTORY/$fileName"
                        }
                    }
                }
                
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Google Favicon API'sini kullanarak favicon indirir
     */
    private suspend fun tryGoogleFaviconAPI(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val domain = extractDomain(url)
                if (domain.isBlank()) return@withContext null
                
                val googleUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                downloadFavicon(googleUrl)
            } catch (e: Exception) {
                e.printStackTrace()
                null
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
                val cleanUrl = prepareUrl(url)
                
                // HTML'i kısa timeout ile indir
                val connection = Jsoup.connect(cleanUrl)
                    .timeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .maxBodySize(1024 * 1024) // 1MB sınırı
                    .userAgent("Mozilla/5.0 (Android 12) Chrome/120.0.0.0 Mobile")
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .method(org.jsoup.Connection.Method.GET)

                val doc: Document = connection.get()

                // Öncelik sırası - daha yüksek kaliteli favicon'ları tercih et
                val appleTouchIcon = findAppleTouchIcon(doc, baseUrl)
                if (appleTouchIcon != null) return@withContext appleTouchIcon

                val msIcon = findMsTileIcon(doc, baseUrl)
                if (msIcon != null) return@withContext msIcon

                val bestIcon = findBestIcon(doc, baseUrl)
                if (bestIcon != null) return@withContext bestIcon

                // Standart favicon.ico'ya deneme
                "$baseUrl/favicon.ico"
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Apple Touch Icon'u bulur
     */
    private fun findAppleTouchIcon(doc: Document, baseUrl: String): String? {
        val elements = doc.select("link[rel~='(?i)apple-touch-icon']")
        if (elements.isEmpty()) return null

        // En yüksek boyutlu Apple Touch Icon'u bul
        var bestElement = elements.first()
        var bestSize = 0

        for (element in elements) {
            val sizes = element.attr("sizes")
            if (sizes.isNotEmpty() && sizes != "any") {
                val size = extractFirstNumber(sizes)
                if (size > bestSize) {
                    bestSize = size
                    bestElement = element
                }
            }
        }

        val href = bestElement?.attr("href") ?: return null
        return if (href.isNotEmpty()) resolveUrl(baseUrl, href) else null
    }

    /**
     * Microsoft için özel tile icon'u bulur
     */
    private fun findMsTileIcon(doc: Document, baseUrl: String): String? {
        val element = doc.select("meta[name='msapplication-TileImage']").first()
        if (element != null) {
            val content = element.attr("content")
            if (content.isNotEmpty()) {
                return resolveUrl(baseUrl, content)
            }
        }
        return null
    }

    /**
     * En iyi favicon'u bulur
     */
    private fun findBestIcon(doc: Document, baseUrl: String): String? {
        val iconElements = doc.select("link[rel~='(?i)icon|shortcut|fluid-icon']")
        if (iconElements.isEmpty()) return null

        var bestElement = iconElements.first()
        var bestSize = 0

        for (element in iconElements) {
            val type = element.attr("type").lowercase()
            val href = element.attr("href").lowercase()

            // SVG'yi öncelikle tercih et
            if (type.contains("svg") || href.endsWith(".svg")) {
                return resolveUrl(baseUrl, element.attr("href"))
            }

            // En büyük boyutlu ikonu bul
            val sizes = element.attr("sizes")
            if (sizes.isNotEmpty() && sizes != "any") {
                val size = extractFirstNumber(sizes)
                if (size > bestSize) {
                    bestSize = size
                    bestElement = element
                }
            }
        }

        val href = bestElement?.attr("href") ?: return null
        return if (href.isNotEmpty()) resolveUrl(baseUrl, href) else null
    }

    /**
     * String'den ilk sayıyı çıkarır (32x32 formatından 32)
     */
    private fun extractFirstNumber(text: String): Int {
        return text.split("x", "X", ",", " ")
            .mapNotNull { it.toIntOrNull() }
            .maxOrNull() ?: 0
    }

    /**
     * URL'i temizler ve hazırlar
     */
    private fun prepareUrl(url: String): String {
        val cleanUrl = if (url.startsWith("http")) url else "https://$url"
        
        // Yaygın hataları düzelt
        return cleanUrl
            .replace(" ", "%20")
            .replace("://www.http", "://")
            .replace("://http", "://")
    }

    /**
     * URL'den domain adını çıkarır (www ve protokol olmadan)
     */
    private fun extractDomain(url: String): String {
        return try {
            val cleanUrl = prepareUrl(url)
            val urlObject = URL(cleanUrl)
            var host = urlObject.host

            // www. önekini kaldır
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            
            host
        } catch (e: Exception) {
            e.printStackTrace()
            // Geçerli bir URL değilse, sadece domain kısmını çıkarmaya çalış
            val cleaned = url.replace("http://", "").replace("https://", "")
            val parts = cleaned.split("/")
            if (parts.isNotEmpty()) {
                parts[0].replace("www.", "")
            } else {
                url
            }
        }
    }

    /**
     * Göreceli URL'leri mutlak URL'lere dönüştürür
     */
    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isEmpty()) return baseUrl
        
        return when {
            relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://") -> relativeUrl
            relativeUrl.startsWith("//") -> "https:$relativeUrl"
            relativeUrl.startsWith("/") -> "$baseUrl$relativeUrl"
            else -> "$baseUrl/$relativeUrl"
        }
    }

    /**
     * Domaine dayalı otomatik favicon oluşturur
     */
    private fun generateFaviconFromDomain(url: String): Bitmap {
        val domain = extractDomain(url)
        val initial = if (domain.isNotEmpty()) domain[0].uppercaseChar().toString() else "A"

        // Daha fazla renk seçeneği
        val colors = arrayOf(
            Color.parseColor("#2196F3"), // Mavi
            Color.parseColor("#F44336"), // Kırmızı  
            Color.parseColor("#4CAF50"), // Yeşil
            Color.parseColor("#FF9800"), // Turuncu
            Color.parseColor("#9C27B0"), // Mor
            Color.parseColor("#009688"), // Turkuaz
            Color.parseColor("#3F51B5"), // Indigo
            Color.parseColor("#607D8B"), // Blue Grey
            Color.parseColor("#E91E63"), // Pink
            Color.parseColor("#00BCD4")  // Cyan
        )

        // Domain adına göre tutarlı bir renk seç
        val colorIndex = Math.abs(domain.hashCode()) % colors.size
        val backgroundColor = colors[colorIndex]

        // Daha büyük boyut - netlik için
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Arka planı çiz (yuvarlak köşeler)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cornerRadius = size * 0.15f
        paint.color = backgroundColor
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), cornerRadius, cornerRadius, paint)

        // Hafif parlama efekti (üstten)
        paint.color = Color.WHITE
        paint.alpha = 31 // %12 opaklık
        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat() * 0.4f)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        // Yazıyı çiz
        paint.color = Color.WHITE
        paint.alpha = 255
        paint.textSize = size * 0.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        // Yazıyı ortala
        val bounds = Rect()
        paint.getTextBounds(initial, 0, initial.length, bounds)

        val x = size / 2f
        val y = size / 2f + (bounds.height() / 2f) - bounds.bottom

        // Hafif gölge efekti
        paint.setShadowLayer(4f, 0f, 2f, Color.parseColor("#44000000"))
        canvas.drawText(initial, x, y, paint)

        return bitmap
    }

    /**
     * URL'den temel domain adresini çıkarır
     */
    private fun extractBaseUrl(url: String): String {
        val cleanUrl = prepareUrl(url)
        return try {
            val urlObject = URL(cleanUrl)
            "${urlObject.protocol}://${urlObject.host}"
        } catch (e: Exception) {
            e.printStackTrace()
            if (cleanUrl.contains("://")) {
                val parts = cleanUrl.split("://", limit = 2)
                if (parts.size >= 2) {
                    "${parts[0]}://${parts[1].split("/")[0]}"
                } else {
                    "https://$cleanUrl"
                }
            } else {
                "https://$cleanUrl"
            }
        }
    }

    /**
     * Favicon'u belirtilen URL'den indirir
     */
    private fun downloadFavicon(faviconUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null

        return try {
            val url = URL(faviconUrl)
            connection = url.openConnection() as HttpURLConnection
            
            // Connection ayarları
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Android 12) Chrome/120.0.0.0 Mobile")
            connection.setRequestProperty("Accept", "image/webp,image/*,*/*;q=0.8")
            connection.doInput = true
            connection.useCaches = true
            
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // Bitmap başarıyla oluşturuldu mu?
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    // Çok küçük bir favicon ise büyüt
                    if (bitmap.width < 16 || bitmap.height < 16) {
                        val size = 32 // Minimum boyut
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
                        bitmap.recycle() // Orijinali serbest bırak
                        return scaledBitmap
                    }
                    return bitmap
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
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

            // Önceki dosyayı sil (varsa)
            val file = File(directory, fileName)
            if (file.exists()) {
                file.delete()
            }

            // Dosyayı kaydet
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) // 90% kalite
                out.flush()
            }

            // Dosya validasyonu
            if (file.exists() && file.length() > MIN_FAVICON_SIZE) {
                // Double-check: dosyayı okuyup geçerli olduğundan emin ol
                val testBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (testBitmap != null && testBitmap.width > 0 && testBitmap.height > 0) {
                    testBitmap.recycle()
                    return file.absolutePath
                }
            }
            
            // Geçersiz dosyayı sil
            file.delete()
            null
        } catch (e: IOException) {
            e.printStackTrace()
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
            if (file.exists() && file.length() > MIN_FAVICON_SIZE) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                // Bitmap geçerliliğini kontrol et
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    bitmap
                } else {
                    // Geçersiz dosyayı sil
                    file.delete()
                    null
                }
            } else {
                // Dosya yoksa veya çok küçükse sil
                if (file.exists()) {
                    file.delete()
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Önbelleği temizler - düşük bellek durumlarında çağrılabilir
     */
    fun clearCache() {
        faviconCache.clear()
        pendingDownloads.clear()
    }

    /**
     * Eski/bozuk favicon dosyalarını temizler
     */
    fun cleanupInvalidFavicons(context: Context) {
        try {
            val faviconDir = File(context.filesDir, FAVICON_DIRECTORY)
            if (!faviconDir.exists()) return

            faviconDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".png")) {
                    // Dosya boyutu kontrolü
                    if (file.length() <= MIN_FAVICON_SIZE) {
                        file.delete()
                        return@forEach
                    }

                    // Bitmap geçerliliği kontrolü
                    try {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
                            file.delete()
                        } else {
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}