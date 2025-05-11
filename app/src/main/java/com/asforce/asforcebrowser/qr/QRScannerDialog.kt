package com.asforce.asforcebrowser.qr

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.asforce.asforcebrowser.MainActivity
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.databinding.DialogQrScannerBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * QR Tarama Dialog'u - Profesyonel Edition
 * 
 * Düşük ışık performansına optimize edilmiş, yüksek hızlı QR kod taraması
 * Flash, tap-to-focus ve gelişmiş algılama özellikleri
 * 
 * Referanslar:
 * - Google ML Kit Barcode Scanning: https://developers.google.com/ml-kit/vision/barcode-scanning/android
 * - CameraX Documentation: https://developer.android.com/training/camerax
 * - Material Dialog Design: https://material.io/components/dialogs
 */
class QRScannerDialog(
    private val context: Context,
    private val onQRCodeScanned: (String) -> Unit,
    private val onDismiss: () -> Unit = {}
) : Dialog(context, R.style.Theme_Dialog_QRScanner) {

    private lateinit var binding: DialogQrScannerBinding
    
    // CameraX bileşenleri
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // ML Kit bileşeni - Optimize edilmiş scanner
    private var barcodeScanner: BarcodeScanner? = null
    
    // Kontrol değişkenleri
    private var isFlashOn = false
    private var currentZoomRatio = 1.0f
    private var isScanning = true  // Tarama durumu kontrolü
    private var lastScanTime = 0L  // Çift tarama önleme
    
    // UI animasyonları
    private var scanLineAnimator: ObjectAnimator? = null
    
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
        
        // Optimize edilmiş barcode scanner'i oluştur
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .build()
            
        barcodeScanner = BarcodeScanning.getClient(options)
        
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
                showFocusIndicator(event.x, event.y)
                true
            } else {
                false
            }
        }
        
        // Tarama çizgisi animasyonu
        setupScanningLineAnimation()
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
            // Kamera sağlayıcısını al
            cameraProvider = cameraProviderFuture.get()
            
            // Kamerayı başlat
            bindCameraUseCases()
            
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Kamera özelliklerini bağla (Preview, ImageAnalysis)
     * Düşük ışık performansına optimize edildi
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        // Preview oluştur
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
        
        // Image Analysis oluştur - Optimize edilmiş çözünürlük ve performans
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                    processBarcodes(barcode)
                })
            }
        
        // Kamera seçimi (arka kamera) - Düşük ışık için optimize edilmiş
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            // Mevcut kamerayı kaldır
            cameraProvider.unbindAll()
            
            // Kamera uygulamalarını bağla
            camera = cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            // Zoom değerlerini ayarla
            camera?.cameraInfo?.zoomState?.observe(context) { zoomState ->
                currentZoomRatio = zoomState.zoomRatio
            }
            
            // Düşük ışık için optimize edilmiş ayarlar
            camera?.cameraControl?.apply {
                // Otomatik pozlama kompanzasyonu
                setExposureCompensationIndex(1)
                
                // Flash başlangıçta kapalı
                enableTorch(false)
            }
            
        } catch (exc: Exception) {
            Toast.makeText(context, "Kamera başlatılamadı: ${exc.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Barcode analiz sınıfı - Gelişmiş algılama
     */
    private inner class BarcodeAnalyzer(
        private val barcodeListener: (Barcode) -> Unit
    ) : ImageAnalysis.Analyzer {

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            if (!isScanning) {
                imageProxy.close()
                return
            }
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Düşük ışık performansı için optimize edilmiş görüntü işleme
                val image = InputImage.fromMediaImage(
                    mediaImage, 
                    imageProxy.imageInfo.rotationDegrees
                )
                
                // Brightness kontrolı
                val cameraControl = camera?.cameraControl
                cameraControl?.setExposureCompensationIndex(1) // Slightly increase exposure
                
                // ML Kit ile barcode tanıma
                barcodeScanner?.process(image)
                    ?.addOnSuccessListener { barcodes ->
                        // En güçlü barcode'u bul
                        val bestBarcode = barcodes
                            .filter { it.format == Barcode.FORMAT_QR_CODE }
                            .maxByOrNull { it.boundingBox?.area() ?: 0 }
                            
                        bestBarcode?.let { barcodeListener(it) }
                    }
                    ?.addOnFailureListener { 
                        // Barcode tanıma hatası - sessizce devam et
                    }
                    ?.addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
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
        if (rawValue != null) {
            // Taramayı durdur
            isScanning = false
            lastScanTime = currentTime
            
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
                
                // 1 saniye sonra işle
                binding.root.postDelayed({
                    processScannedQR(rawValue)
                }, 1000)
            }
        }
    }

    /**
     * Taranan QR kodu işle
     */
    private fun processScannedQR(qrContent: String) {
        // Callback fonksiyonunu çağır
        onQRCodeScanned(qrContent)
        
        // Dialog'u kapat
        dismiss()
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
                binding.btnFlash.text = if (isFlashOn) "Flash (Açık)" else "Flash"
            } else {
                Toast.makeText(context, "Bu cihazda flaş bulunmuyor", Toast.LENGTH_SHORT).show()
            }
        }
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
     * Tarama çizgisi animasyonunu ayarla
     */
    private fun setupScanningLineAnimation() {
        // Tarama çizgisini yukarıdan aşağıya hareket ettir
        scanLineAnimator = ObjectAnimator.ofFloat(
            binding.scanningLine,
            "translationY",
            -140f,  // Yukarı pozisyon
            140f    // Aşağı pozisyon
        ).apply {
            duration = 2000  // 2 saniyede bir tur
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    /**
     * Dialog kapatıldığında cleanup yap
     */
    override fun dismiss() {
        // Animasyonu durdur
        scanLineAnimator?.cancel()
        
        // Kamerayı kapat
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        
        // Barcode scanner'ı kapat
        barcodeScanner?.close()
        
        // Dialog'u kapat
        super.dismiss()
        
        // Callback'i çağır
        onDismiss()
    }
    
    /**
     * Focus göstergesi göster
     */
    private fun showFocusIndicator(x: Float, y: Float) {
        // TODO: Focus halka animasyonu ekle
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
