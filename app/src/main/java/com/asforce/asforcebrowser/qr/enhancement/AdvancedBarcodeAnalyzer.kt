package com.asforce.asforcebrowser.qr.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Gelişmiş QR kod analiz sınıfı
 * 
 * Düşük ışık koşullarında dahil olmak üzere, zorlu koşullarda daha iyi QR kodu algılama
 * çoklu algılama stratejileri ile güvenilir hale getirilmiştir.
 * 
 * Referanslar:
 * - "A Robust QR Code Detection Algorithm Using Image Processing" (IEEE 2019)
 * - Google ML Kit Vision Processing: https://developers.google.com/ml-kit/vision/barcode-scanning
 * - "Adaptive QR Code Detection in Low-Contrast Environments" (2023)
 */
class AdvancedBarcodeAnalyzer(
    private val context: Context,
    private val onBarcodeDetected: (List<Barcode>) -> Unit
) : ImageAnalysis.Analyzer {

    // Düşük ışık görüntü geliştirici
    private val lowLightEnhancer = LowLightEnhancer(context)

    // ML Kit barcode scanner - yüksek doğrulukta tarama
    private val highAccuracyScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes() // Olası tüm barkodları etkinleştir
            .build()
    )

    // ML Kit barcode scanner - yüksek hızda tarama
    private val highSpeedScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    // Tarama durumu ve istatistikleri
    private var frameCount = 0
    private var successfulScanCount = 0
    private var isProcessing = AtomicBoolean(false)
    private var currentScanMode = LowLightEnhancer.ScanMode.NORMAL
    private var shouldUseEnhancement = false

    // İşlem istatistikleri (debugging)
    private var lastProcessingStartTime = 0L
    private var processingTimes = mutableListOf<Long>()

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        // Halihazırda bir görüntü işleniyor mu kontrolü
        if (isProcessing.get()) {
            // Skip this frame if we're still processing
            imageProxy.close()
            return
        }

        lastProcessingStartTime = System.currentTimeMillis()
        frameCount++

        try {
            isProcessing.set(true)
            val mediaImage = imageProxy.image

            if (mediaImage != null) {
                // Işık seviyesini analiz et
                val lightLevel = lowLightEnhancer.analyzeLightLevel(imageProxy)
                
                // Işık seviyesine göre tarama modunu belirle
                val newScanMode = lowLightEnhancer.determineScanMode(lightLevel)
                
                // Mod değişti mi?
                if (newScanMode != currentScanMode) {
                    currentScanMode = newScanMode
                    Log.d(TAG, "Tarama modu değişti: $currentScanMode (Işık: $lightLevel)")
                }
                
                // Düşük ışık koşullarında gelişmiş tarama kullan
                shouldUseEnhancement = currentScanMode == LowLightEnhancer.ScanMode.LOW_LIGHT ||
                        currentScanMode == LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT

                // Standart tarama yaklaşımı
                processWithStandardApproach(imageProxy)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Barkod analiz hatası: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    @ExperimentalGetImage
    private fun processWithStandardApproach(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // Düşük ışık koşullarında hassas tarama, normal koşullarda hızlı tarama kullan
        val scanner = if (shouldUseEnhancement) highAccuracyScanner else highSpeedScanner

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    successfulScanCount++
                    Log.d(TAG, "QR kod algılandı: ${barcodes.size} adet, başarı oranı: ${successfulScanCount * 100 / frameCount}%")
                    
                    // QR kodları kalite puanına göre sıralanır
                    val sortedBarcodes = barcodes.sortedByDescending { evaluateBarcodeQuality(it) }
                    onBarcodeDetected(sortedBarcodes)
                }
                
                val processingTime = System.currentTimeMillis() - lastProcessingStartTime
                processingTimes.add(processingTime)
                
                // Her 100 karedeki ortalama işlem süresini log'a yaz
                if (frameCount % 100 == 0 && processingTimes.isNotEmpty()) {
                    val avgTime = processingTimes.average()
                    Log.d(TAG, "Ortalama işlem süresi: $avgTime ms (mod: $currentScanMode)")
                    processingTimes.clear()
                }
                
                isProcessing.set(false)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barkod tarama hatası: ${e.message}")
                isProcessing.set(false)
            }
    }

    @ExperimentalGetImage
    private fun processWithEnhancedApproach(imageProxy: ImageProxy) {
        // Düşük ışık koşullarında ek görüntü işleme
        val bitmap = lowLightEnhancer.imageToBitmap(imageProxy) ?: return
        
        // Görüntü geliştirme
        val enhancedBitmap = lowLightEnhancer.enhanceImage(bitmap, currentScanMode)
        
        // Geliştirilmiş görüntüden QR kod tespiti
        val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)
        
        highAccuracyScanner.process(enhancedImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    successfulScanCount++
                    Log.d(TAG, "Geliştirilmiş görüntüde QR kod algılandı: ${barcodes.size} adet")
                    onBarcodeDetected(barcodes)
                }
                isProcessing.set(false)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Geliştirilmiş görüntü tarama hatası: ${e.message}")
                isProcessing.set(false)
            }
    }

    /**
     * QR kod kalitesini değerlendiren fonksiyon
     * Kalite değerlendirmesi, QR kodunun boyutu, metriklerinin netliği ve çevreleyen alana
     * göre yapılır. Daha yüksek puan daha kaliteli bir QR kodunu gösterir.
     * 
     * @param barcode Değerlendirilecek barkod
     * @return 0-100 arasında kalite puanı
     */
    private fun evaluateBarcodeQuality(barcode: Barcode): Int {
        var qualityScore = 0
        
        // 1. Barkod boyutunu değerlendir (daha büyük = daha iyi)
        val boundingBox = barcode.boundingBox
        if (boundingBox != null) {
            val area = boundingBox.width() * boundingBox.height()
            qualityScore += normalizeArea(area)
        }
        
        // 2. Değerin varlığı ve uzunluğu
        barcode.rawValue?.let {
            qualityScore += 10  // Değer varsa +10 puan
            
            // Uzunluk değerlendirmesi
            if (it.length > 3) qualityScore += 10  // Anlamlı içerik için +10 puan
        }
        
        // 3. Biçim - QR kodu ise ek puan
        if (barcode.format == Barcode.FORMAT_QR_CODE) {
            qualityScore += 20
        }
        
        // Maksimum 100 ile sınırla
        return qualityScore.coerceAtMost(100)
    }
    
    /**
     * Alan değerini 0-60 arasında bir puana normalize eder
     * Küçük QR kodları (1000px² altı) - düşük puan
     * Orta QR kodları (1000-10000px²) - orta puan
     * Büyük QR kodları (10000px² üstü) - yüksek puan
     */
    private fun normalizeArea(area: Int): Int {
        return when {
            area < 1000 -> area / 20  // Maks 50 puan
            area < 10000 -> 50 + (area - 1000) / 200  // 50-95 arası puan
            else -> 95  // Maksimum alan puanı
        }.coerceAtMost(60)  // 60 puanla sınırla
    }

    /**
     * Flash kullanılmalı mı kontrol eder
     */
    fun shouldUseFlash(): Boolean {
        return lowLightEnhancer.shouldUseFlash()
    }
    
    /**
     * Mevcut tarama modunu verir
     */
    fun getCurrentMode(): LowLightEnhancer.ScanMode {
        return currentScanMode
    }

    companion object {
        private const val TAG = "AdvancedBarcodeAnalyzer"
    }
}
