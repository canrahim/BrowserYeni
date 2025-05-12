package com.asforce.asforcebrowser.qrscan

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
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.databinding.DialogQrScannerBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * QR Tarama Dialog'u - Modernize Edilmiş Sürüm (2025)
 * 
 * Google ML Kit ve CameraX API'leri ile geliştirilmiş, basitleştirilmiş QR kod tarama
 * dialog bileşeni.
 * 
 * Özellikler:
 * - Hızlı QR kod algılama
 * - Otomatik odaklama
 * - Flaş/el feneri desteği
 * - Zoom desteği
 * 
 * Referanslar:
 * - Google ML Kit Barcode Scanning: https://developers.google.com/ml-kit/vision/barcode-scanning/android
 * - CameraX API: https://developer.android.com/training/camerax
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
    
    // ML Kit barkod tarayıcısı
    private lateinit var barcodeScanner: BarcodeScanner
    
    // Kontrol değişkenleri
    private var isFlashEnabled = false
    private var currentZoomRatio = 1.0f
    private var isScanning = true // Tarama durumu kontrolü
    private var lastScanTime = 0L // Çift tarama önleme
    
    // Titreşim için
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
        
        // Barkod tarayıcıyı yapılandır ve başlat
        setupBarcodeScanner()
        
        // UI'yi başlat
        setupUI()
        
        // Kamera iznini kontrol et ve başlat
        checkCameraPermission()
    }

    /**
     * Barkod tarayıcıyı yapılandır
     */
    private fun setupBarcodeScanner() {
        // QR tarama için optimize edilmiş barkod seçenekleri
        val barcodeOptions = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
            
        // Barkod tarayıcıyı başlat
        barcodeScanner = BarcodeScanning.getClient(barcodeOptions)
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
        
        // Durum mesajını ayarla
        binding.qrStatusText.text = "QR kodu taranıyor..."
        
        // Kamera önizlemesine dokunarak odaklanma
        binding.cameraPreview.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                try {
                    val factory = binding.cameraPreview.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    
                    val action = FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        
                    camera?.cameraControl?.startFocusAndMetering(action)
                    
                    // Dokunulan noktada odaklanma göster
                    showFocusPoint(event.x, event.y)
                    
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Odaklanma hatası: ${e.message}")
                    false
                }
            } else {
                false
            }
        }
    }
    
    /**
     * Odak noktası göster
     */
    private fun showFocusPoint(x: Float, y: Float) {
        // Odak noktası göstergesi animasyonu
        binding.focusIndicator.apply {
            translationX = x - width / 2
            translationY = y - height / 2
            alpha = 0f
            scaleX = 1.5f
            scaleY = 1.5f
            visibility = View.VISIBLE
            
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .withEndAction {
                    postDelayed({
                        animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction {
                                visibility = View.INVISIBLE
                            }
                            .start()
                    }, 1000)
                }
                .start()
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
            // İzin yoksa dialog'u kapat
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
                Log.e(TAG, "Kamera başlatma hatası: ${e.message}")
                Toast.makeText(context, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Kamera özelliklerini bağla (Preview, ImageAnalysis)
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        try {
            // Önceki tüm kullanım durumlarını kaldır
            cameraProvider.unbindAll()
            
            // Preview oluştur
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
            
            // Image Analysis için analiz ayarları
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
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
                // Flash'i kapat
                cam.cameraControl.enableTorch(false)
                
                // Sürekli odaklama
                setupAutoFocus(cam)
                
                // Zoom state'i gözlemle
                cam.cameraInfo.zoomState.observe(context as LifecycleOwner) { zoomState ->
                    currentZoomRatio = zoomState.zoomRatio
                    updateZoomUI()
                }
            }
            
            // QR tarama hazır
            binding.qrStatusText.text = "QR kodu taranıyor..."
            
        } catch (exc: Exception) {
            Log.e(TAG, "Kamera özellikleri bağlanamadı: ${exc.message}")
            Toast.makeText(context, "Kamera başlatılamadı. Lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Otomatik odaklamayı ayarla
     */
    private fun setupAutoFocus(camera: Camera) {
        try {
            // Otomatik odaklama için çoklu nokta stratejisi
            val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
            
            // Merkez ve etrafında odak noktaları
            val centerPoint = factory.createPoint(0.5f, 0.5f)  // Merkez
            val topPoint = factory.createPoint(0.5f, 0.3f)     // Üst merkez
            val bottomPoint = factory.createPoint(0.5f, 0.7f)  // Alt merkez
            val leftPoint = factory.createPoint(0.3f, 0.5f)    // Sol merkez
            val rightPoint = factory.createPoint(0.7f, 0.5f)   // Sağ merkez
            
            // Odaklama için tüm noktaları kullan
            val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
                .addPoint(topPoint, FocusMeteringAction.FLAG_AF)
                .addPoint(bottomPoint, FocusMeteringAction.FLAG_AF)
                .addPoint(leftPoint, FocusMeteringAction.FLAG_AF)
                .addPoint(rightPoint, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            // Odaklamayı başlat
            camera.cameraControl.startFocusAndMetering(action)
            
        } catch (e: Exception) {
            // Hata durumunda basit merkez odaklama kullan
            Log.e(TAG, "Gelişmiş odaklama hatası: ${e.message}")
            
            try {
                val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
                val centerPoint = factory.createPoint(0.5f, 0.5f)
                
                val action = FocusMeteringAction.Builder(centerPoint)
                    .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                camera.cameraControl.startFocusAndMetering(action)
            } catch (e2: Exception) {
                Log.e(TAG, "Basit odaklama hatası: ${e2.message}")
            }
        }
    }

    /**
     * ImageProxy içindeki QR kodunu analiz et
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }
        
        // Görüntü alma
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        
        // InputImage oluştur
        val image = InputImage.fromMediaImage(
            mediaImage, 
            imageProxy.imageInfo.rotationDegrees
        )
        
        // QR kodu tarama işlemi
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    processBarcodes(barcodes.first())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Tarama hatası: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Taranan QR kodunu işle
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
            // Taramayı durdur
            isScanning = false
            lastScanTime = currentTime
            
            // Başarılı tarama göster
            binding.qrStatusText.text = "QR Kod Algılandı!"
            
            // Titreşim feedback'i
            try {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } catch (e: Exception) {
                // Titreşim hatası sessizce geç
            }
            
            // Sonucu önizlemede göster
            binding.qrResultPreview.apply {
                visibility = View.VISIBLE
                text = "Bulunan: ${if (rawValue.length > 20) rawValue.take(20) + "..." else rawValue}"
            }
            
            // 800ms sonra işle (kullanıcı feedback'i için)
            binding.root.postDelayed({
                processScannedQR(rawValue)
            }, 800)
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
        
        // Sayısal QR kodları kontrol et (szutest.com.tr'ye özel)
        if (trimmedContent.all { it.isDigit() }) {
            val baseUrl = "https://app.szutest.com.tr/EXT/PKControl/Equipment/"
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
                isFlashEnabled = !isFlashEnabled
                cam.cameraControl.enableTorch(isFlashEnabled)
                
                // Buton metnini güncelle
                binding.btnFlash.text = if (isFlashEnabled) "Flash ✓" else "Flash"
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
        }
    }
    
    /**
     * Zoom UI'sini güncelle
     */
    private fun updateZoomUI() {
        binding.btnZoom.text = "Zoom ${String.format("%.1f", currentZoomRatio)}x"
    }

    /**
     * Dialog kapatıldığında cleanup yap
     */
    override fun dismiss() {
        // Kamerayı kapat
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        
        // Dialog'u kapat
        super.dismiss()
        
        // Callback'i çağır
        onDismiss()
    }

    companion object {
        private const val TAG = "QRScannerDialog"
        
        /**
         * QR Tarama Dialog'unu göster
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