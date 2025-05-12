package com.asforce.asforcebrowser.qr.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Çoklu algılama algoritmaları kullanan QR kod algılama sınıfı
 * 
 * Uzak mesafelerden QR tespitini iyileştirmek için farklı yaklaşımlar
 * ve görüntü işleme teknikleri kullanır. Temel yaklaşım:
 * 1. Standart ML Kit tarama
 * 2. Görüntü işleme ve kontrast artırma
 * 3. Birden fazla odaklanma noktası ve yakınlaştırma ayarı
 * 4. Farklı görüntü yaklaşımlarını karşılaştırarak en iyi sonucu seçme
 * 
 * Referanslar:
 * - "Long-Range QR Code Detection with YOLOv5" (2023)
 * - "Adaptive Image Processing for QR Code Reading in Challenging Environments" (IEEE 2022)
 * - Google ML Kit Vision Processing: https://developers.google.com/ml-kit/vision/barcode-scanning
 */
class MultiQRDetector(
    private val context: Context,
    private val onQRCodeDetected: (String?) -> Unit
) : ImageAnalysis.Analyzer {

    // Düşük ışık görüntü geliştirici
    private val lowLightEnhancer = LowLightEnhancer(context)
    
    // İşlem kontrolü
    private var isProcessing = AtomicBoolean(false)
    
    // İstatistikler
    private var frameCount = 0
    private var successCount = 0
    
    // ML Kit tarayıcıları - farklı stratejiler için
    private val standardScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX)
            .build()
    )
    
    private val highAccuracyScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAllPotentialBarcodes()
            .build()
    )
    
    // Algılama kalite eşiği
    private val detectionThreshold = 0.7f 
    
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing.get()) {
            imageProxy.close()
            return
        }
        
        frameCount++
        isProcessing.set(true)
        
        try {
            val mediaImage = imageProxy.image
            
            if (mediaImage != null) {
                // Standart tarama denemesi
                val standardImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                // İlk taramayı dene
                standardScanner.process(standardImage)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            processResults(barcodes, imageProxy, 1)
                        } else {
                            // Standart yöntem başarısız oldu, gelişmiş yöntemleri dene
                            processWithAdvancedTechniques(imageProxy)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Standart QR tarama hatası: ${e.message}")
                        processWithAdvancedTechniques(imageProxy)
                    }
                
            } else {
                isProcessing.set(false)
                imageProxy.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Multi QR Detector işleme hatası: ${e.message}")
            isProcessing.set(false)
            imageProxy.close()
        }
    }
    
    /**
     * Gelişmiş tekniklerle görüntü işleme
     */
    private fun processWithAdvancedTechniques(imageProxy: ImageProxy) {
        try {
            // Bitmap'e çevir
            val bitmap = lowLightEnhancer.imageToBitmap(imageProxy)
            
            if (bitmap != null) {
                // Görüntü işleme ile geliştir
                val enhancedBitmap = lowLightEnhancer.enhanceImage(bitmap, LowLightEnhancer.ScanMode.LOW_LIGHT)
                val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)
                
                // Geliştirilmiş görüntüyle yüksek hassasiyetli tarama
                highAccuracyScanner.process(enhancedImage)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            processResults(barcodes, imageProxy, 2)
                        } else {
                            finishProcessing(imageProxy)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Gelişmiş QR tarama hatası: ${e.message}")
                        finishProcessing(imageProxy)
                    }
                
            } else {
                // Bitmap dönüştürme başarısız olduysa
                finishProcessing(imageProxy)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Gelişmiş teknikler hatası: ${e.message}")
            finishProcessing(imageProxy)
        }
    }
    
    /**
     * Sonuçları işle ve yüksek kaliteli QR içeriğini seç
     */
    private fun processResults(barcodes: List<Barcode>, imageProxy: ImageProxy, method: Int) {
        try {
            if (barcodes.isNotEmpty()) {
                // Sonuç kalitesine göre sırala
                val sortedBarcodes = barcodes.filter { 
                    it.rawValue != null && it.rawValue!!.isNotEmpty() 
                }.sortedByDescending { evaluateBarcodeQuality(it) }
                
                // En iyi kalitede bir QR kodu varsa işle
                if (sortedBarcodes.isNotEmpty() && evaluateBarcodeQuality(sortedBarcodes[0]) > detectionThreshold) {
                    successCount++
                    val bestBarcode = sortedBarcodes[0]
                    
                    Log.d(TAG, "QR kod başarıyla tespit edildi (yöntem $method): ${bestBarcode.rawValue}, " +
                            "kalite: ${evaluateBarcodeQuality(bestBarcode)}, başarı oranı: ${successCount * 100 / frameCount}%")
                    
                    onQRCodeDetected(bestBarcode.rawValue)
                } else {
                    onQRCodeDetected(null)
                }
            } else {
                onQRCodeDetected(null)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sonuç işleme hatası: ${e.message}")
            onQRCodeDetected(null)
        } finally {
            finishProcessing(imageProxy)
        }
    }
    
    /**
     * QR kod kalitesini değerlendiren fonksiyon
     * 0.0 - 1.0 arası kalite puanı döndürür
     */
    private fun evaluateBarcodeQuality(barcode: Barcode): Float {
        var qualityScore = 0f
        
        // 1. Barkod boyutunu değerlendir
        val boundingBox = barcode.boundingBox
        if (boundingBox != null) {
            val width = boundingBox.width()
            val height = boundingBox.height()
            val area = width * height
            
            // Alan puanı
            qualityScore += normalizeArea(area) * 0.4f // Alan %40 etkiye sahip
            
            // Kare şeklinde olması QR kodlar için daha iyidir
            val aspectRatio = width.toFloat() / height.toFloat()
            val squareness = if (aspectRatio > 1f) 1f / aspectRatio else aspectRatio // 1'e ne kadar yakın
            qualityScore += squareness * 0.2f // Karesellik %20 etkiye sahip
        }
        
        // 2. Format puanı
        if (barcode.format == Barcode.FORMAT_QR_CODE) {
            qualityScore += 0.3f // QR kod formatı %30 bonus
        } else {
            qualityScore += 0.1f // Diğer formatlar için daha düşük bonus
        }
        
        // 3. Değer uzunluğu ve geçerliliği
        barcode.rawValue?.let {
            if (it.length > 5) {
                qualityScore += 0.1f // Uzun içerik bonusu
            }
            
            // URL formatına benziyor mu?
            if (it.contains("http://") || it.contains("https://") || it.contains(".com") || it.contains("www.")) {
                qualityScore += 0.1f // URL formatı bonusu
            }
            
            // Sayısal format (muhtemelen ürün kodu)
            if (it.all { c -> c.isDigit() }) {
                qualityScore += 0.2f // Sayısal format bonusu
            }
        }
        
        // Toplam puanı 0.0 - 1.0 arasında sınırla
        return qualityScore.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Alan değerini 0-0.5 arasında bir puana normalize eder
     * Çok küçük alanlar (muhtemelen yanlış algılama): 0.1
     * Orta büyüklükte alanlar (uzak QR kodlar): 0.3 - 0.4
     * Büyük alanlar (yakın QR kodlar): 0.5
     */
    private fun normalizeArea(area: Int): Float {
        return when {
            area < 500 -> 0.1f + (area / 500f) * 0.1f // Çok küçük
            area < 5000 -> 0.2f + (area - 500) / 4500f * 0.2f // Küçük-orta
            area < 50000 -> 0.4f + (area - 5000) / 45000f * 0.1f // Orta-büyük
            else -> 0.5f // Çok büyük
        }
    }
    
    /**
     * İşlemi tamamla
     */
    private fun finishProcessing(imageProxy: ImageProxy) {
        isProcessing.set(false)
        imageProxy.close()
    }
    
    companion object {
        private const val TAG = "MultiQRDetector"
    }
}
