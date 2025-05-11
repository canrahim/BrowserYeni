package com.asforce.asforcebrowser.qr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.asforce.asforcebrowser.MainActivity
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.databinding.DialogQrScannerBinding
import com.asforce.asforcebrowser.qr.enhancement.AdvancedBarcodeAnalyzer
import com.asforce.asforcebrowser.qr.enhancement.LowLightEnhancer
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR Tarama Dialog'u - Pro Edition 2025
 * 
 * Düşük ışık koşullarında bile yüksek performanslı QR kod taraması:
 * - Otomatik parlaklık ve kontrast ayarları
 * - Karanlık ortamlarda geliştirilmiş tarama algoritmaları
 * - Akıllı işleme ile zorlu QR kodlarını bile algılama
 * - GPU hızlandırmalı görüntü iyileştirme
 * - Zeka ile ışık koşullarına uyum sağlama
 * 
 * Referanslar:
 * - Google ML Kit Barcode Scanning: https://developers.google.com/ml-kit/vision/barcode-scanning/android
 * - CameraX Extensions: https://developer.android.com/training/camerax/vendor-extensions
 * - Computer Vision Low-Light Enhancement: "Low-Light Image Enhancement using Deep CNNs" (CVPR 2023)
 * - Camera2 API Low-Light Features: https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics
 * - GPU Image Processing: https://github.com/cats-oss/android-gpuimage
 */
class QRScannerDialog(
    private val context: Context,
    private val onQRCodeScanned: (String) -> Unit,
    private val onDismiss: () -> Unit = {}
) : Dialog(context, R.style.Theme_Dialog_QRScannerEnhanced) {

    private lateinit var binding: DialogQrScannerBinding
    
    // CameraX bileşenleri
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // Gelişmiş QR barkod analizci
    private lateinit var advancedBarcodeAnalyzer: AdvancedBarcodeAnalyzer
    
    // Düşük ışık modu kontrolü
    private var lowLightMode = LowLightEnhancer.ScanMode.NORMAL
    private var isLowLightModeAuto = true
    
    // Kontrol değişkenleri
    private var isFlashOn = false
    private var currentZoomRatio = 1.0f
    private var isScanning = true  // Tarama durumu kontrolü
    private var lastScanTime = 0L  // Çift tarama önleme
    
    // Kamera ayarları
    private var hasDualCamera = false
    private var hasLowLightMode = false
    private var hasTorch = false
    private var supportsNightMode = false
    
    // Vibration
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dialog özelliklerini ayarla
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // View Binding'i başlat
        binding = DialogQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Kamera thread pool'unu başlat
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Vibrator'u başlat
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        
        // Gelişmiş QR analiz algoritması başlat
        advancedBarcodeAnalyzer = AdvancedBarcodeAnalyzer(context) { barcodes ->
            if (barcodes.isNotEmpty()) {
                processBarcodes(barcodes.first())
            }
        }
        
        // Kamera donanım özelliklerini kontrol et
        checkCameraCapabilities()
        
        // UI'yi başlat
        setupUI()
        
        // Kamera iznini kontrol et ve başlat
        checkCameraPermission()
    }

    /**
     * UI bileşenlerini ayarla
     */
    private fun setupUI() {
        // Kapatma butonu
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        // Flash toggle butonu
        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }
        
        // Zoom butonu
        binding.btnZoom.setOnClickListener {
            toggleZoom()
        }
        
        // Düşük ışık modu butonu
        binding.btnLowLightMode.setOnClickListener {
            toggleLowLightMode()
        }
        updateLowLightModeButton()
        
        // Overlay animasyonlarını başlat
        binding.scannerOverlay.startScanAnimation()
        
        // Kamera önizlemesine dokunarak odaklanma
        binding.cameraPreview.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val factory = binding.cameraPreview.meteringPointFactory
                val autoFocusPoint = factory.createPoint(event.x, event.y)
                
                camera?.cameraControl?.startFocusAndMetering(
                    FocusMeteringAction.Builder(autoFocusPoint)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                )
                
                // Odaklanma için vizual feedback
                binding.scannerOverlay.showFocusIndicator(event.x, event.y)
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Kamera donanım özelliklerini kontrol et
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun checkCameraCapabilities() {
        try {
            // Daha basit kamera özellik kontrolü
            val pm = context.packageManager
            
            // Otomatik odaklama (focus) özelliği kontrolü
            hasLowLightMode = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)
            
            // Flash kontrolü
            hasTorch = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
            
            // Kamera sayısı kontrolü
            hasDualCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) &&
                           pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
            
            // Gece modu varsayılan olarak desteklenir varsay
            supportsNightMode = true
            
            Log.d("QRScanner", "Kamera özellikleri: Düşük ışık: $hasLowLightMode, Torch: $hasTorch, Dual: $hasDualCamera, Night mode: $supportsNightMode")
            
        } catch (e: Exception) {
            Log.e("QRScanner", "Kamera donanım bilgileri alınamadı: ${e.message}")
        }
    }

    /**
     * Kamera iznini kontrol et
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            // İzin yoksa MainActivity üzerinden izin iste
            // İzin yoksa dialog'u kapat ve MainActivity'de izin iste
            Toast.makeText(context, "Kamera izni gerekli! Lütfen izin verin.", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    /**
     * Kamerayı başlat
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                // Kamera sağlayıcısını al
                cameraProvider = cameraProviderFuture.get()
                
                // Kamerayı başlat
                bindCameraUseCases()
                
            } catch (e: Exception) {
                Log.e("QRScanner", "Kamera başlatma hatası: ${e.message}")
                Toast.makeText(context, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Kamera özelliklerini bağla (Preview, ImageAnalysis)
     * Düşük ışık performansına optimize edildi
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        try {
            // Önceki tüm kullanım durumlarını kaldır
            cameraProvider.unbindAll()
            
            // QR tarama için optimize edilmiş barkod seçenekleri
            val barcodeOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .enableAllPotentialBarcodes() // Tüm olası barkodlar için
                .build()
                
            // Analyzer'a barkod seçeneklerini ayarla
            advancedBarcodeAnalyzer.setBarcodeOptions(barcodeOptions)
            
            // Preview oluştur
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3) // Daha geniş görüş alanı için 4:3 oranı kullan
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
            
            // Image Analysis için analiz ayarları
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720)) // Yüksek çözünürlük
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // En son görüntüyü analiz et
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // YUV formatı
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, advancedBarcodeAnalyzer)
                }
            
            // Kamera seçimi (arka kamera) 
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            // Kamera uygulamalarını bağla
            camera = cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            // Kamera ayarları
            camera?.let { cam ->
                // Otomatik odaklama modunu ayarla
                cam.cameraControl?.enableTorch(false) // Flash'i kapat
                
                // Otomatik odaklanma ayarı 
                val meteringPoint = SurfaceOrientedMeteringPointFactory(1f, 1f)
                    .createPoint(0.5f, 0.5f) // Merkeze odaklan
                
                val focusMeteringAction = FocusMeteringAction.Builder(meteringPoint)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                // Sürekli otomatik odaklanmayı etkinleştir
                cam.cameraControl?.startFocusAndMetering(focusMeteringAction)
                
                // Zoom değerlerini ayarla
                cam.cameraInfo?.zoomState?.observe(context) { zoomState ->
                    currentZoomRatio = zoomState.zoomRatio
                }
            }
            
            // Kamera başarıyla başlatıldı
            binding.qrStatusText.text = "QR taramaya hazır"
            
            // Işık durumunu periyodik olarak kontrol et
            startLightLevelChecking()
        } catch (exc: Exception) {
            Log.e("QRScanner", "Kamera özellikleri bağlanamadı: ${exc.message}")
            Toast.makeText(context, "Kamera başlatılamadı. Lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Işık seviyesini düzenli olarak kontrol eden fonksiyon
     */
    private fun startLightLevelChecking() {
        // Kameranın ışık seviyesini periyodik olarak kontrol et
        binding.root.postDelayed(object : Runnable {
            override fun run() {
                if (!isScanning) return
                
                // Otomatik moddaysa, ışık seviyesine göre düşük ışık modunu ayarla
                if (isLowLightModeAuto) {
                    val scanMode = advancedBarcodeAnalyzer.getCurrentMode()
                    if (scanMode != lowLightMode) {
                        lowLightMode = scanMode
                        updateLowLightMode()
                    }
                    
                    // Çok düşük ışık durumunda flash'ı aç
                    if (advancedBarcodeAnalyzer.shouldUseFlash() && hasTorch && !isFlashOn) {
                        toggleFlash()
                    }
                }
                
                // Devam eden işlem
                binding.root.postDelayed(this, 1000) // Her saniye kontrol et
            }
        }, 1000) // İlk kontrol 1 saniye sonra
    }

    /**
     * Barcode'ları işle - Gelişmiş algılama ve çift tarama engelleyici
     */
    private fun processBarcodes(barcode: Barcode) {
        // Çift tarama önleme
        val currentTime = System.currentTimeMillis()
        if (!isScanning || (currentTime - lastScanTime) < 1000) {
            return
        }
        
        // QR Kod içeriğini al
        val rawValue = barcode.rawValue
        if (rawValue != null && rawValue.isNotEmpty()) {
            // Log ekle - hata ayıklama için
            Log.d("QRScanner", "QR Kod algılandı: $rawValue")
            
            // Taramayı durdur
            isScanning = false
            lastScanTime = currentTime
            
            // Başarılı tarama işaretle
            binding.scannerOverlay.updateStatusMessage("QR Kod Algılandı!")
            
            // Titreşim feedback'i - Güvenli çağırım
            try {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } catch (e: SecurityException) {
                // Titreşim izni yoksa sessizce devam et
            } catch (e: Exception) {
                // Diğer hatalar da sessizce devam et
            }
            
            // Ana thread'de güncelle
            (context as? MainActivity)?.runOnUiThread {
                // Sonucu önizlemede göster
                binding.qrResultPreview.visibility = View.VISIBLE
                binding.qrResultPreview.text = "Bulunan: ${if (rawValue.length > 20) rawValue.take(20) + "..." else rawValue}"
                
                // Tarama animasyonunu durdur
                binding.scannerOverlay.stopScanAnimation()
                
                // 800ms sonra işle (daha hızlı yanıt)
                binding.root.postDelayed({
                    processScannedQR(rawValue)
                }, 800)
            }
        } else {
            // Boş QR kod içeriği - hata mesajı günlüğe yaz
            Log.e("QRScanner", "QR Kod algılandı fakat içerik boş!")
            
            // Taramaya devam et
            (context as? MainActivity)?.runOnUiThread {
                binding.qrStatusText.text = "Tekrar deneyin..."
            }
        }
    }

    /**
     * Taranan QR kodu işle
     */
    private fun processScannedQR(qrContent: String) {
        // URL kontrolü yap
        val formattedQrContent = formatQrContentIfNeeded(qrContent)
        
        // Callback fonksiyonunu çağır
        onQRCodeScanned(formattedQrContent)
        
        // Dialog'u kapat
        dismiss()
    }
    
    /**
     * QR kod içeriğini işleyerek URL formatına dönüştürür
     * 
     * QR içeriği bir URL değilse veya http(s) protokolüne sahip değilse
     * https:// öneki ekler. Özellikle sayısal QR kodlar için szutest.com.tr'ye yönlendirme yapar.
     * 
     * @param qrContent QR kod içeriği
     * @return URL formatında düzenlenmiş içerik
     */
    private fun formatQrContentIfNeeded(qrContent: String): String {
        // Boş içeriği kontrol et
        if (qrContent.isBlank()) return qrContent
        
        // QR kod içeriğindeki boşlukları temizle
        val trimmedContent = qrContent.trim()
        
        // Zaten http/https protokolü içeriyorsa değiştirme
        if (trimmedContent.startsWith("http://") || trimmedContent.startsWith("https://")) {
            return trimmedContent
        }
        
        // Sayısal QR kodları kontrol et (szutest.com.tr'özel)
        if (trimmedContent.all { it.isDigit() }) {
            val baseUrl = "https://app.szutest.com.tr/EXT/PKControl/Equipment/"
            
            // Log ekle - hata ayıklama için
            Log.d("QRScanner", "Sayısal QR kod tespit edildi: $trimmedContent - $baseUrl$trimmedContent adresine yönlendiriliyor")
            
            // Sayısal QR kodları Equipment URL'sine dönüştür
            return baseUrl + trimmedContent
        }
        
        // "app.szutest.com.tr" formatındaki içerikleri kontrol et
        if (trimmedContent.contains("szutest.com.tr", ignoreCase = true) && 
            !trimmedContent.contains(" ") && 
            !trimmedContent.startsWith("http")) {
            
            return "https://" + trimmedContent
        }
        
        // Genel URL formatına benziyor mu kontrol et (örn: www.example.com)
        if (trimmedContent.contains(".") && !trimmedContent.contains(" ")) {
            return "https://" + trimmedContent
        }
        
        // Diğer durumlarda orijinal içeriği döndür
        return trimmedContent
    }

    /**
     * Flash'ı aç/kapat
     */
    private fun toggleFlash() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                isFlashOn = !isFlashOn
                cam.cameraControl.enableTorch(isFlashOn)
                
                // Buton metnini güncelle
                binding.btnFlash.text = if (isFlashOn) "Flash ✓" else "Flash"
            } else {
                Toast.makeText(context, "Bu cihazda flaş bulunmuyor", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Düşük ışık modunu değiştir
     */
    private fun toggleLowLightMode() {
        if (isLowLightModeAuto) {
            // Otomatik moddan manuel moda geç
            isLowLightModeAuto = false
            
            // Döngüsel olarak modu değiştir
            lowLightMode = when (lowLightMode) {
                LowLightEnhancer.ScanMode.NORMAL -> LowLightEnhancer.ScanMode.LOW_LIGHT
                LowLightEnhancer.ScanMode.LOW_LIGHT -> LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT
                LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> LowLightEnhancer.ScanMode.BRIGHT
                LowLightEnhancer.ScanMode.BRIGHT -> LowLightEnhancer.ScanMode.NORMAL
                LowLightEnhancer.ScanMode.AUTO -> LowLightEnhancer.ScanMode.NORMAL
            }
        } else {
            // Manuel moddan otomatik moda geç
            if (lowLightMode == LowLightEnhancer.ScanMode.NORMAL) {
                isLowLightModeAuto = true
            } else {
                // Döngüsel olarak modu değiştir
                lowLightMode = when (lowLightMode) {
                    LowLightEnhancer.ScanMode.LOW_LIGHT -> LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT
                    LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> LowLightEnhancer.ScanMode.BRIGHT
                    LowLightEnhancer.ScanMode.BRIGHT -> LowLightEnhancer.ScanMode.NORMAL
                    LowLightEnhancer.ScanMode.NORMAL -> LowLightEnhancer.ScanMode.AUTO
                    LowLightEnhancer.ScanMode.AUTO -> LowLightEnhancer.ScanMode.AUTO
                }
                
                if (lowLightMode == LowLightEnhancer.ScanMode.AUTO) {
                    isLowLightModeAuto = true
                    lowLightMode = advancedBarcodeAnalyzer.getCurrentMode()
                }
            }
        }
        
        // UI ve tarayıcı modunu güncelle
        updateLowLightMode()
        updateLowLightModeButton()
    }
    
    /**
     * Düşük ışık modunu uygula
     */
    private fun updateLowLightMode() {
        // Overlay renklerini ve görselleri güncelle
        binding.scannerOverlay.updateLightMode(lowLightMode)
        
        // Durum metni güncelle
        val statusText = when (lowLightMode) {
            LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> "Çok Düşük Işık Modu"
            LowLightEnhancer.ScanMode.LOW_LIGHT -> "Düşük Işık Modu"
            LowLightEnhancer.ScanMode.NORMAL -> "Standart Tarama Modu"
            LowLightEnhancer.ScanMode.BRIGHT -> "Parlak Ortam Modu"
            LowLightEnhancer.ScanMode.AUTO -> "Otomatik Mod"
        }
        binding.qrStatusText.text = statusText
    }
    
    /**
     * Düşük ışık modu butonunu güncelle
     */
    private fun updateLowLightModeButton() {
        val buttonText = if (isLowLightModeAuto) {
            "Auto"
        } else {
            when (lowLightMode) {
                LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> "Gece"
                LowLightEnhancer.ScanMode.LOW_LIGHT -> "Loş"
                LowLightEnhancer.ScanMode.NORMAL -> "Normal"
                LowLightEnhancer.ScanMode.BRIGHT -> "Parlak"
                LowLightEnhancer.ScanMode.AUTO -> "Auto"
            }
        }
        binding.btnLowLightMode.text = buttonText
    }

    /**
     * Zoom'u ayarla
     */
    private fun toggleZoom() {
        camera?.let { cam ->
            // Zoom değerlerini 1x, 2x, 3x arasında döngüle
            val newZoomRatio = when {
                currentZoomRatio < 1.5f -> 2.0f
                currentZoomRatio < 2.5f -> 3.0f
                else -> 1.0f
            }
            
            // Zoom'u ayarla
            cam.cameraControl.setZoomRatio(newZoomRatio)
            
            // Buton metnini güncelle
            binding.btnZoom.text = "Zoom ${String.format("%.1f", newZoomRatio)}x"
        }
    }



    /**
     * Dialog kapatıldığında cleanup yap
     */
    override fun dismiss() {
        // Overlay animasyonunu durdur
        binding.scannerOverlay.stopScanAnimation()
        
        // Kamerayı kapat
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        
        // Dialog'u kapat
        super.dismiss()
        
        // Callback'i çağır
        onDismiss()
    }

    companion object {
        // Kamera izin kodu MainActivity'de tanımlı
        
        /**
         * QR Tarama Dialog'unu göster
         * 
         * @param context Aktivite context'i
         * @param onQRCodeScanned QR kod tarandığında çağrılacak callback
         * @param onDismiss Dialog kapatıldığında çağrılacak callback
         * @return QRScannerDialog instance'ı
         */
        fun show(
            context: Context,
            onQRCodeScanned: (String) -> Unit,
            onDismiss: () -> Unit = {}
        ): QRScannerDialog {
            val dialog = QRScannerDialog(context, onQRCodeScanned, onDismiss)
            dialog.show()
            return dialog
        }
    }
}

/**
 * Extension functions
 */
fun android.graphics.Rect.area(): Int = width() * height()
