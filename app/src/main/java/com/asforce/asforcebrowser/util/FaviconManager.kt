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
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * FaviconManager - Geliştirilmiş favicon yönetimi sınıfı
 * 
 * Referans: Android Developer Guide - Bitmap and FileOutputStream usage
 * https://developer.android.com/reference/android/graphics/Bitmap
 * 
 * Yenilikler:
 * 1. Daha sessiz hata yönetimi (log spam'ini önler)
 * 2. 404 hatalarını önceden öngörerek minimize eder
 * 3. Daha akıllı önbellekleme stratejisi
 * 4. Progressive loading desteği
 */
object FaviconManager {
    private const val FAVICON_DIRECTORY = "favicons"
    private const val CONNECTION_TIMEOUT = 5000 // 5 saniye (optimize edildi)
    private const val READ_TIMEOUT = 5000 // 5 saniye
    private const val MIN_FAVICON_SIZE = 50

    // Favicon önbelleği
    private val faviconCache = ConcurrentHashMap<String, String>()
    
    // Pending favicon işlemleri
    private val pendingDownloads = ConcurrentHashMap<String, Deferred<String?>>()
    
    // Başarısız domain listesi (404 önlemek için)
    private val failedDomains = ConcurrentHashMap<String, Long>()
    private const val FAILED_DOMAIN_CACHE_DURATION = 3600_000 // 1 saat

    /**
     * Ana favicon indirme ve kaydetme fonksiyonu
     */
    suspend fun downloadAndSaveFavicon(context: Context, url: String, tabId: Long): String? {
        if (url.isBlank()) return null
        
        return withContext(Dispatchers.IO) {
            try {
                // Favicon dizinini oluştur
                val faviconDir = File(context.filesDir, FAVICON_DIRECTORY)
                if (!faviconDir.exists()) {
                    faviconDir.mkdirs()
                }

                // Domain'i çıkar
                val domain = extractDomain(url)
                
                // Başarısız domain listesini kontrol et
                if (isRecentlyFailedDomain(domain)) {
                    // Son çare: domain'den favicon oluştur
                    return@withContext generateDefaultFavicon(context, tabId, url)
                }

                // Mevcut favicon'u kontrol et
                val existingPath = "$FAVICON_DIRECTORY/favicon_${tabId}.png"
                val existingFile = File(context.filesDir, existingPath)

                if (existingFile.exists() && existingFile.length() > MIN_FAVICON_SIZE) {
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

                // Domain bazlı önbelleği kontrol et
                faviconCache[domain]?.let { cachedPath ->
                    val cachedFile = File(context.filesDir, cachedPath)
                    if (cachedFile.exists() && cachedFile.length() > MIN_FAVICON_SIZE) {
                        try {
                            val testBitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                            if (testBitmap != null && testBitmap.width > 0 && testBitmap.height > 0) {
                                testBitmap.recycle()
                                cachedFile.copyTo(existingFile, overwrite = true)
                                return@withContext existingPath
                            }
                        } catch (e: Exception) {
                            // Önbellek geçersiz
                        }
                    }
                }

                // Pending işlemleri kontrol et
                val domainKey = "download_$domain"
                val existingJob = pendingDownloads[domainKey]
                if (existingJob != null && existingJob.isActive) {
                    try {
                        val result = withTimeout(3.seconds) {
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
                    val downloadedPath = downloadJob.await()
                    
                    if (downloadedPath != null) {
                        val downloadedFile = File(context.filesDir, downloadedPath)
                        if (downloadedFile.exists() && downloadedFile != existingFile) {
                            downloadedFile.copyTo(existingFile, overwrite = true)
                        }
                        faviconCache[domain] = downloadedPath
                        return@withContext existingPath
                    }
                    
                    // İndirme başarısız, Google API'yi dene (dikkatli)
                    if (shouldTryGoogleAPI(domain)) {
                        val googleBitmap = tryGoogleFaviconAPICarefully(domain)
                        if (googleBitmap != null) {
                            val fileName = "favicon_${tabId}.png"
                            val savedPath = saveFaviconToFile(context, googleBitmap, fileName)
                            if (savedPath != null) {
                                faviconCache[domain] = "$FAVICON_DIRECTORY/$fileName"
                                return@withContext "$FAVICON_DIRECTORY/$fileName"
                            }
                        }
                    }
                    
                    // Domain'i başarısız olarak işaretle
                    markDomainAsFailed(domain)
                    
                    // Son çare: domain'den favicon oluştur
                    return@withContext generateDefaultFavicon(context, tabId, url)
                    
                } finally {
                    pendingDownloads.remove(domainKey)
                }
                
            } catch (e: Exception) {
                // Hataları sessizce handle et (log spam önlenir)
                try {
                    val domain = extractDomain(url)
                    markDomainAsFailed(domain)
                    generateDefaultFavicon(context, tabId, url)
                } catch (genEx: Exception) {
                    null
                }
            }
        }
    }

    /**
     * İyileştirilmiş favicon indirme fonksiyonu
     */
    private suspend fun downloadFaviconInternal(context: Context, url: String, domain: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // HTML'den favicon URL'ini bul
                var faviconUrl: String? = null
                
                try {
                    faviconUrl = findBestFaviconUrlQuietly(url)
                } catch (e: Exception) {
                    // Sessizce hatayı ignore et
                }
                
                if (faviconUrl.isNullOrEmpty()) {
                    val baseUrl = extractBaseUrl(url)
                    faviconUrl = "$baseUrl/favicon.ico"
                }
                
                // Favicon'u indir
                if (!faviconUrl.isNullOrEmpty()) {
                    val bitmap = downloadFaviconQuietly(faviconUrl)
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
                // Sessizce hatayı ignore et
                null
            }
        }
    }

    /**
     * Google Favicon API'yi dikkatli kullan (sadece uygun domain'ler için)
     */
    private suspend fun tryGoogleFaviconAPICarefully(domain: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                if (domain.isBlank() || isKnownProblemDomain(domain)) {
                    return@withContext null
                }
                
                val googleUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                downloadFaviconQuietly(googleUrl)
            } catch (e: Exception) {
                // Sessizce hatayı ignore et
                null
            }
        }
    }

    /**
     * Sessizce favicon URL bul (log spam olmadan)
     */
    private suspend fun findBestFaviconUrlQuietly(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = extractBaseUrl(url)
                val cleanUrl = prepareUrl(url)
                
                val connection = Jsoup.connect(cleanUrl)
                    .timeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .maxBodySize(512 * 1024) // 512KB sınırı (optimize edildi)
                    .userAgent("Mozilla/5.0 (Android 12) Chrome/120.0.0.0 Mobile")
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)

                val doc = connection.get()

                // Öncelik sırası
                findAppleTouchIcon(doc, baseUrl) ?: 
                findMsTileIcon(doc, baseUrl) ?: 
                findBestIcon(doc, baseUrl) ?: 
                "$baseUrl/favicon.ico"
            } catch (e: Exception) {
                // Sessizce ignore et
                null
            }
        }
    }

    /**
     * Sessizce favicon indir (log spam olmadan)
     */
    private fun downloadFaviconQuietly(faviconUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null

        return try {
            val url = URL(faviconUrl)
            connection = url.openConnection() as HttpURLConnection
            
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

                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    if (bitmap.width < 16 || bitmap.height < 16) {
                        val size = 32
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
                        bitmap.recycle()
                        return scaledBitmap
                    }
                    return bitmap
                }
            }
            
            null
        } catch (e: Exception) {
            // Sessizce ignore et
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Domain'in başarısız olduğunu işaretle
     */
    private fun markDomainAsFailed(domain: String) {
        failedDomains[domain] = System.currentTimeMillis()
    }

    /**
     * Domain'in yakın zamanda başarısız olup olmadığını kontrol et
     */
    private fun isRecentlyFailedDomain(domain: String): Boolean {
        val failTime = failedDomains[domain] ?: return false
        return System.currentTimeMillis() - failTime < FAILED_DOMAIN_CACHE_DURATION
    }

    /**
     * Google API için uygun domain mu kontrol et
     */
    private fun shouldTryGoogleAPI(domain: String): Boolean {
        // Bilinen problemli domain'lar için Google API'yi kullanma
        return !isKnownProblemDomain(domain) && !isRecentlyFailedDomain(domain)
    }

    /**
     * Problemli olduğu bilinen domain'leri kontrol et
     */
    private fun isKnownProblemDomain(domain: String): Boolean {
        val problemDomains = listOf(
            "localhost",
            "127.0.0.1",
            "192.168.",
            "10.0.0.",
            ".test",
            ".local",
            "app.szutest.com.tr", // Log'da görülen problemli domain
            ".internal",
            ".dev",
            ".invalid"
        )
        
        return problemDomains.any { domain.contains(it, ignoreCase = true) }
    }

    /**
     * Varsayılan favicon oluştur ve kaydet
     */
    private fun generateDefaultFavicon(context: Context, tabId: Long, url: String): String? {
        return try {
            val bitmap = generateFaviconFromDomain(url)
            val fileName = "favicon_${tabId}.png"
            saveFaviconToFile(context, bitmap, fileName)
            "$FAVICON_DIRECTORY/$fileName"
        } catch (e: Exception) {
            null
        }
    }

    // Diğer yardımcı fonksiyonlar (extractDomain, generateFaviconFromDomain, vb.)
    // bu kısımda aynı kalıyor ancak log mesajları azaltıldı

    private fun extractDomain(url: String): String {
        return try {
            val cleanUrl = prepareUrl(url)
            val urlObject = URL(cleanUrl)
            var host = urlObject.host

            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            
            host
        } catch (e: Exception) {
            val cleaned = url.replace("http://", "").replace("https://", "")
            val parts = cleaned.split("/")
            if (parts.isNotEmpty()) {
                parts[0].replace("www.", "")
            } else {
                url
            }
        }
    }

    private fun generateFaviconFromDomain(url: String): Bitmap {
        val domain = extractDomain(url)
        val initial = if (domain.isNotEmpty()) domain[0].uppercaseChar().toString() else "A"

        val colors = arrayOf(
            Color.parseColor("#2196F3"), Color.parseColor("#F44336"), 
            Color.parseColor("#4CAF50"), Color.parseColor("#FF9800"), 
            Color.parseColor("#9C27B0"), Color.parseColor("#009688"), 
            Color.parseColor("#3F51B5"), Color.parseColor("#607D8B"), 
            Color.parseColor("#E91E63"), Color.parseColor("#00BCD4")
        )

        val colorIndex = Math.abs(domain.hashCode()) % colors.size
        val backgroundColor = colors[colorIndex]

        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Arka planı çiz
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cornerRadius = size * 0.15f
        paint.color = backgroundColor
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), cornerRadius, cornerRadius, paint)

        // Parlama efekti
        paint.color = Color.WHITE
        paint.alpha = 31
        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat() * 0.4f)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        // Yazıyı çiz
        paint.color = Color.WHITE
        paint.alpha = 255
        paint.textSize = size * 0.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        val bounds = Rect()
        paint.getTextBounds(initial, 0, initial.length, bounds)

        val x = size / 2f
        val y = size / 2f + (bounds.height() / 2f) - bounds.bottom

        paint.setShadowLayer(4f, 0f, 2f, Color.parseColor("#44000000"))
        canvas.drawText(initial, x, y, paint)

        return bitmap
    }

    private fun extractBaseUrl(url: String): String {
        val cleanUrl = prepareUrl(url)
        return try {
            val urlObject = URL(cleanUrl)
            "${urlObject.protocol}://${urlObject.host}"
        } catch (e: Exception) {
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

    private fun prepareUrl(url: String): String {
        val cleanUrl = if (url.startsWith("http")) url else "https://$url"
        
        return cleanUrl
            .replace(" ", "%20")
            .replace("://www.http", "://")
            .replace("://http", "://")
    }

    private fun saveFaviconToFile(context: Context, bitmap: Bitmap, fileName: String): String? {
        return try {
            val directory = File(context.filesDir, FAVICON_DIRECTORY)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)
            if (file.exists()) {
                file.delete()
            }

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.flush()
            }

            if (file.exists() && file.length() > MIN_FAVICON_SIZE) {
                val testBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (testBitmap != null && testBitmap.width > 0 && testBitmap.height > 0) {
                    testBitmap.recycle()
                    return file.absolutePath
                }
            }
            
            file.delete()
            null
        } catch (e: IOException) {
            null
        }
    }

    fun loadFavicon(context: Context, faviconPath: String?): Bitmap? {
        if (faviconPath == null) return null

        return try {
            val file = File(context.filesDir, faviconPath)
            if (file.exists() && file.length() > MIN_FAVICON_SIZE) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    bitmap
                } else {
                    file.delete()
                    null
                }
            } else {
                if (file.exists()) {
                    file.delete()
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        faviconCache.clear()
        pendingDownloads.clear()
        failedDomains.clear()
    }

    fun cleanupInvalidFavicons(context: Context) {
        try {
            val faviconDir = File(context.filesDir, FAVICON_DIRECTORY)
            if (!faviconDir.exists()) return

            faviconDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".png")) {
                    if (file.length() <= MIN_FAVICON_SIZE) {
                        file.delete()
                        return@forEach
                    }

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
            // Sessizce ignore et
        }
    }

    // Existing helper functions from original implementation
    private fun findAppleTouchIcon(doc: org.jsoup.nodes.Document, baseUrl: String): String? {
        val elements = doc.select("link[rel~='(?i)apple-touch-icon']")
        if (elements.isEmpty()) return null

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

    private fun findMsTileIcon(doc: org.jsoup.nodes.Document, baseUrl: String): String? {
        val element = doc.select("meta[name='msapplication-TileImage']").first()
        if (element != null) {
            val content = element.attr("content")
            if (content.isNotEmpty()) {
                return resolveUrl(baseUrl, content)
            }
        }
        return null
    }

    private fun findBestIcon(doc: org.jsoup.nodes.Document, baseUrl: String): String? {
        val iconElements = doc.select("link[rel~='(?i)icon|shortcut|fluid-icon']")
        if (iconElements.isEmpty()) return null

        var bestElement = iconElements.first()
        var bestSize = 0

        for (element in iconElements) {
            val type = element.attr("type").lowercase()
            val href = element.attr("href").lowercase()

            if (type.contains("svg") || href.endsWith(".svg")) {
                return resolveUrl(baseUrl, element.attr("href"))
            }

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

    private fun extractFirstNumber(text: String): Int {
        return text.split("x", "X", ",", " ")
            .mapNotNull { it.toIntOrNull() }
            .maxOrNull() ?: 0
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isEmpty()) return baseUrl
        
        return when {
            relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://") -> relativeUrl
            relativeUrl.startsWith("//") -> "https:$relativeUrl"
            relativeUrl.startsWith("/") -> "$baseUrl$relativeUrl"
            else -> "$baseUrl/$relativeUrl"
        }
    }
}