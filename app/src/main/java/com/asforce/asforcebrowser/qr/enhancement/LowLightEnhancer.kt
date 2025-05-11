package com.asforce.asforcebrowser.qr.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.Image
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Düşük ışık koşullarında QR tarama performansını artırmak için görüntü geliştirme sınıfı
 * 
 * GPU hızlandırmalı görüntü işleme, kontrast iyileştirme ve uyarlanabilir parlaklık ayarı
 * ile QR kodlarının zorlu ışık koşullarında bile yüksek hızda tespit edilmesini sağlar.
 *
 * Referanslar:
 * - "Low Light Image Enhancement: A Survey" - Yang Yu, et al. (2021)
 * - "Efficient Contrast Enhancement for Low-Light Image Processing" - Li M., Liu J. (2022)
 * - GPU Image: https://github.com/cats-oss/android-gpuimage
 */
class LowLightEnhancer(private val context: Context) {

    // GPU işleme için kullanılacak sınıf
    private val gpuImage = GPUImage(context)
    
    // Görüntü filtrelerini tutan grup
    private val filterGroup = GPUImageFilterGroup().apply {
        // Histogram eşitleme ve kontrast filtresi
        addFilter(GPUImageContrastFilter(1.5f))
        
        // Parlaklık filtresi - başlangıçta normal
        addFilter(GPUImageBrightnessFilter(0.1f))
    }
    
    // Önceki ışık seviyesi değerleri
    private var lastLightLevel = 0f
    private var consecutiveDarkFrames = 0
    
    init {
        // Filtreleri GPU Image'a ayarla
        gpuImage.setFilter(filterGroup)
        Log.d(TAG, "Düşük ışık görüntü geliştirici başlatıldı")
    }
    
    /**
     * Kamera görüntüsünü analiz ederek ortam ışığını değerlendirir
     * ve uygun filtreleme ayarlarını belirler
     * 
     * @param imageProxy CameraX'ten gelen görüntü
     * @return Işık seviyesi (0.0 - çok karanlık, 1.0 - çok aydınlık)
     */
    @ExperimentalGetImage
    fun analyzeLightLevel(imageProxy: ImageProxy): Float {
        val image = imageProxy.image ?: return lastLightLevel
        
        // Görüntü örnek noktaları alınarak ışık seviyesi tespit edilir (performans optimizasyonu)
        val buffer = image.planes[0].buffer
        val sampleCount = 10
        val pixelSkip = image.width * image.height / sampleCount
        
        var totalBrightness = 0f
        var samples = 0
        
        // Tüm görüntüyü işlemek yerine örnek piksel noktalarını analiz et
        for (i in 0 until buffer.remaining() step pixelSkip) {
            if (samples >= sampleCount) break
            
            // Y düzlemindeki piksel değeri (YUV formatında Y parlaklık değeridir)
            val pixelValue = buffer.get(i).toInt() and 0xFF
            totalBrightness += pixelValue
            samples++
        }
        
        // Ortalama parlaklık değeri (0-255 aralığından 0-1 aralığına normalize edilir)
        val avgBrightness = (totalBrightness / samples) / 255f
        
        // Hızlı değişimleri engelle (yumuşatma)
        lastLightLevel = lastLightLevel * 0.7f + avgBrightness * 0.3f
        
        // Karanlık bir ortam tespit edildiğinde sayaç artırılır
        if (lastLightLevel < DARK_THRESHOLD) {
            consecutiveDarkFrames++
        } else {
            consecutiveDarkFrames = 0
        }
        
        return lastLightLevel
    }
    
    /**
     * Görüntü için en uygun QR tarama modunu belirler
     * 
     * @param lightLevel Işık seviyesi (0.0 - 1.0)
     * @return QR tarama için gerekli mod
     */
    fun determineScanMode(lightLevel: Float): ScanMode {
        return when {
            lightLevel < VERY_DARK_THRESHOLD -> ScanMode.EXTREME_LOW_LIGHT
            lightLevel < DARK_THRESHOLD -> ScanMode.LOW_LIGHT
            lightLevel < NORMAL_THRESHOLD -> ScanMode.NORMAL
            else -> ScanMode.BRIGHT
        }
    }
    
    /**
     * Belirlenen moda göre filtreleri günceller
     * 
     * @param mode Tarama modu
     */
    fun updateFiltersForMode(mode: ScanMode) {
        val filters = (filterGroup.filters as MutableList<GPUImageFilter>)
        
        // Kontrast filtresi
        (filters[0] as GPUImageContrastFilter).setContrast(
            when (mode) {
                ScanMode.EXTREME_LOW_LIGHT -> 2.0f  // Yüksek kontrast
                ScanMode.LOW_LIGHT -> 1.8f  // Geliştirilmiş kontrast
                ScanMode.NORMAL -> 1.4f     // Normal kontrast
                ScanMode.BRIGHT -> 1.2f     // Düşük kontrast
                ScanMode.AUTO -> 1.5f       // Otomatik mod - varsayılan değer
            }
        )
        
        // Parlaklık filtresi
        (filters[1] as GPUImageBrightnessFilter).setBrightness(
            when (mode) {
                ScanMode.EXTREME_LOW_LIGHT -> 0.25f // Yüksek parlaklık artışı
                ScanMode.LOW_LIGHT -> 0.15f         // Orta parlaklık artışı
                ScanMode.NORMAL -> 0.05f            // Hafif parlaklık artışı
                ScanMode.BRIGHT -> 0.0f             // Parlaklık değiştirme
                ScanMode.AUTO -> 0.1f               // Otomatik mod - varsayılan değer
            }
        )
        
        Log.d(TAG, "Filtreler güncellendi: Mod = $mode")
    }
    
    /**
     * Görüntüyü geliştirme işlemi
     * 
     * @param imageBitmap İşlenecek görüntü
     * @param mode Tarama modu
     * @return Geliştirilmiş görüntü
     */
    fun enhanceImage(imageBitmap: Bitmap, mode: ScanMode): Bitmap {
        updateFiltersForMode(mode)
        return gpuImage.getBitmapWithFilterApplied(imageBitmap)
    }
    
    /**
     * ImageProxy nesnesini Bitmap'e dönüştürür 
     * (Gelişmiş düşük ışık işleme algoritmaları için)
     */
    @ExperimentalGetImage
    fun imageToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        
        return try {
            // YUV_420_888 formatından Bitmap'e dönüştürme
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            // Y
            yBuffer.get(nv21, 0, ySize)
            
            // UV düzlemlerini birleştirme
            val uvPixelStride = image.planes[1].pixelStride
            
            if (uvPixelStride == 2) { // NV21 formatı tam olarak desteklenir
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
            } else {
                // Farklı formattan NV21'e dönüştürme gerekebilir
                val uBegin = ySize
                val vBegin = ySize + vSize
                
                for (i in 0 until vSize) {
                    nv21[vBegin + i] = vBuffer.get(i * uvPixelStride)
                }
                
                for (i in 0 until uSize) {
                    nv21[uBegin + i] = uBuffer.get(i * uvPixelStride)
                }
            }
            
            // NV21'den Bitmap'e
            val yuvImage = android.graphics.YuvImage(
                nv21, 
                android.graphics.ImageFormat.NV21, 
                image.width, 
                image.height, 
                null
            )
            
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, image.width, image.height), 
                100, 
                out
            )
            
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.e(TAG, "Görüntü dönüştürme hatası: ${e.message}")
            null
        }
    }
    
    /**
     * Karanlık koşullarda flash kullanılmalı mı kontrol eder
     */
    fun shouldUseFlash(): Boolean {
        return consecutiveDarkFrames > DARK_FRAME_THRESHOLD
    }
    
    companion object {
        private const val TAG = "LowLightEnhancer"
        
        // Işık eşik değerleri
        private const val VERY_DARK_THRESHOLD = 0.15f
        private const val DARK_THRESHOLD = 0.30f
        private const val NORMAL_THRESHOLD = 0.65f
        
        // Flash otomatik açılması için karanlık kare sayacı eşiği
        private const val DARK_FRAME_THRESHOLD = 10
    }
    
    /**
     * QR tarama modları
     */
    enum class ScanMode {
        EXTREME_LOW_LIGHT,  // Çok düşük ışık (gece, karanlık ortam)
        LOW_LIGHT,          // Düşük ışık (loş ortam)
        NORMAL,             // Normal ışık (iç mekan)
        BRIGHT,             // Parlak ışık (dış mekan, güneşli)
        AUTO                // Otomatik mod - ışık seviyesine göre uyarlanır
    }
}
