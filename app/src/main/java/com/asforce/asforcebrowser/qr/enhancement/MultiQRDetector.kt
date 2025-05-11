package com.asforce.asforcebrowser.qr.enhancement

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Gelişmiş Çoklu QR Kodu Algılama Sınıfı
 * 
 * Birden fazla QR kodunu tek bir karede hızlı ve doğru şekilde algılama yeteneği
 * ile zorlu ortamlarda bile yüksek başarı oranı sağlar.
 * 
 * Referanslar:
 * - "Multiple QR Code Detection and Recognition" (Proceedings of Computer Vision 2023)
 * - Google ML Kit Enhanced Barcode Processing: https://developers.google.com/ml-kit/vision/barcode-scanning/android
 */
class MultiQRDetector {

    // Çoklu barkod tarayıcı
    private val barcodeScanner: BarcodeScanner
    
    // İşlem havuzu
    private val executorService = Executors.newSingleThreadExecutor()
    
    // Son tarama sonuçları
    private var lastDetectedBarcodes = mutableListOf<Barcode>()
    
    init {
        // Yüksek hassasiyetli çoklu QR kodu tarayıcı yapılandırması
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,  // Alternatif 2D barkodları da destekle
                Barcode.FORMAT_DATA_MATRIX
            )
            .enableAllPotentialBarcodes() // Belirsiz kodları da algıla
            .build()
            
        barcodeScanner = BarcodeScanning.getClient(options)
        
        Log.d(TAG, "Çoklu QR Algılayıcı başlatıldı")
    }
    
    /**
     * Görüntüden çoklu QR kodlarını algıla
     * 
     * @param bitmap Taranacak görüntü
     * @param callback Tarama sonuçları callback'i
     */
    fun detectQRCodes(bitmap: Bitmap, callback: (List<Barcode>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                // Sonuçları filtrele (sadece güvenilir olanları tut)
                val filteredBarcodes = filterValidBarcodes(barcodes)
                
                // Sonuçları güncelle
                lastDetectedBarcodes.clear()
                lastDetectedBarcodes.addAll(filteredBarcodes)
                
                Log.d(TAG, "${filteredBarcodes.size} adet QR kod algılandı")
                callback(filteredBarcodes)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "QR kod algılama hatası: ${e.message}")
                callback(emptyList())
            }
    }
    
    /**
     * Coroutine desteği ile asenkron tarama
     */
    suspend fun detectQRCodesAsync(bitmap: Bitmap): List<Barcode> = suspendCoroutine { continuation ->
        detectQRCodes(bitmap) { barcodes ->
            continuation.resume(barcodes)
        }
    }
    
    /**
     * Geçerli barkodları filtrele
     * 
     * @param barcodes Filtrelenecek barkod listesi
     * @return Filtreden geçmiş geçerli barkodlar
     */
    private fun filterValidBarcodes(barcodes: List<Barcode>): List<Barcode> {
        return barcodes.filter { barcode ->
            // QR Kod formatı kontrolü
            val isValidFormat = barcode.format == Barcode.FORMAT_QR_CODE || 
                            barcode.format == Barcode.FORMAT_AZTEC ||
                            barcode.format == Barcode.FORMAT_DATA_MATRIX
            
            // İçerik kontrolü
            val hasValidContent = !barcode.rawValue.isNullOrEmpty()
            
            // Sınırlayıcı kutu kontrolü
            val hasValidBoundingBox = barcode.boundingBox != null && 
                                barcode.boundingBox!!.width() > 0 && 
                                barcode.boundingBox!!.height() > 0
            
            // Tüm kontrolleri geçerse barkod geçerli
            isValidFormat && hasValidContent && hasValidBoundingBox
        }
    }
    
    /**
     * Son algılanan barkodlardan en iyisini döndür
     */
    fun getBestBarcode(): Barcode? {
        if (lastDetectedBarcodes.isEmpty()) return null
        
        // En iyi barkodu seç (en büyük yüzey alanına sahip olan)
        return lastDetectedBarcodes.maxByOrNull { barcode ->
            val width = barcode.boundingBox?.width() ?: 0
            val height = barcode.boundingBox?.height() ?: 0
            width * height
        }
    }
    
    /**
     * Algılama hassasiyeti değerlendirmesi
     * 
     * @param barcode Değerlendirilecek barkod
     * @return 0-100 arasında güven skoru
     */
    fun evaluateDetectionConfidence(barcode: Barcode): Int {
        var score = 50 // Temel puan
        
        // Boyut değerlendirmesi
        barcode.boundingBox?.let { rect ->
            val area = rect.width() * rect.height()
            if (area > 30000) score += 25
            else if (area > 10000) score += 15
            else if (area > 5000) score += 5
        }
        
        // İçerik değerlendirmesi
        barcode.rawValue?.let { value ->
            if (value.length > 10) score += 10
            if (value.contains("http")) score += 5 // URL içeren QR kodları genelde daha güvenilir
        }
        
        // Köşelerin net olup olmadığını değerlendir
        barcode.cornerPoints?.let { points ->
            if (points.size == 4) score += 10 // Dört köşe noktası tam
        }
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Kaynakları temizle
     */
    fun close() {
        barcodeScanner.close()
        executorService.shutdown()
    }
    
    companion object {
        private const val TAG = "MultiQRDetector"
    }
}
