package com.asforce.asforcebrowser.qr.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import com.asforce.asforcebrowser.qr.enhancement.LowLightEnhancer

/**
 * QR Tarama ekranı için özel görsel öğe
 * 
 * QR tarayıcı için çerçeve, tarama animasyonu ve odak göstergesi sağlar
 * Farklı ışık koşullarına göre görsel ayarlamaları yapar
 * 
 * Referanslar:
 * - Material Design Guidelines: https://material.io/design/motion/
 * - Android Custom View Documentation: https://developer.android.com/develop/ui/views/layout/custom-views
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Boya nesneleri
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val scanLinePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        alpha = 180  // Yarı şeffaf
    }
    
    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    private val focusPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 180
    }
    
    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 150  // Yarı şeffaf
    }
    
    private val statusTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    
    // Tarama alanı değişkenleri
    private var frameRect = RectF()
    private var scanLineY = 0f
    private var frameSize = 240f  // Varsayılan boyut
    private var cornerSize = 40f   // Köşe boyutu
    
    // Animasyon değişkenleri
    private var scanAnimator: ValueAnimator? = null
    private var isScanningActive = false
    
    // Odak noktası değişkenleri
    private var focusX = 0f
    private var focusY = 0f
    private var isFocusVisible = false
    private var focusSize = 64f
    
    // Durum mesajı
    private var statusMessage = ""
    private var statusMessageVisible = false
    
    // Düşük ışık modu renkleri
    private var currentMode = LowLightEnhancer.ScanMode.NORMAL
    
    init {
        // Arka planı şeffaf yap
        setBackgroundColor(Color.TRANSPARENT)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Tarama çerçevesini ekran ortasına yerleştir
        val centerX = w / 2f
        val centerY = h / 2f
        
        // Çerçeve boyutunu ekranın %80'i veya 240dp (hangisi küçükse) olarak ayarla
        frameSize = minOf(w * 0.8f, h * 0.8f, 700f)
        
        // Tarama çerçevesini oluştur
        frameRect.set(
            centerX - frameSize / 2,
            centerY - frameSize / 2,
            centerX + frameSize / 2,
            centerY + frameSize / 2
        )
        
        // Başlangıç tarama çizgisi pozisyonu
        scanLineY = frameRect.top
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Yarı-saydam overlay çiz (tarama alanı dışında)
        // Üst bölge
        canvas.drawRect(0f, 0f, width.toFloat(), frameRect.top, overlayPaint)
        // Sol bölge
        canvas.drawRect(0f, frameRect.top, frameRect.left, frameRect.bottom, overlayPaint)
        // Sağ bölge
        canvas.drawRect(frameRect.right, frameRect.top, width.toFloat(), frameRect.bottom, overlayPaint)
        // Alt bölge
        canvas.drawRect(0f, frameRect.bottom, width.toFloat(), height.toFloat(), overlayPaint)
        
        // Tarama çerçevesini çiz (sadece kenarlar)
        canvas.drawRect(frameRect, framePaint)
        
        // Köşeleri çiz (L şeklinde köşe efektleri)
        val cornerRadius = cornerSize / 2
        
        // Sol üst köşe
        canvas.drawLine(frameRect.left, frameRect.top + cornerRadius, frameRect.left, frameRect.top, cornerPaint)
        canvas.drawLine(frameRect.left, frameRect.top, frameRect.left + cornerRadius, frameRect.top, cornerPaint)
        
        // Sağ üst köşe
        canvas.drawLine(frameRect.right - cornerRadius, frameRect.top, frameRect.right, frameRect.top, cornerPaint)
        canvas.drawLine(frameRect.right, frameRect.top, frameRect.right, frameRect.top + cornerRadius, cornerPaint)
        
        // Sol alt köşe
        canvas.drawLine(frameRect.left, frameRect.bottom - cornerRadius, frameRect.left, frameRect.bottom, cornerPaint)
        canvas.drawLine(frameRect.left, frameRect.bottom, frameRect.left + cornerRadius, frameRect.bottom, cornerPaint)
        
        // Sağ alt köşe
        canvas.drawLine(frameRect.right - cornerRadius, frameRect.bottom, frameRect.right, frameRect.bottom, cornerPaint)
        canvas.drawLine(frameRect.right, frameRect.bottom, frameRect.right, frameRect.bottom - cornerRadius, cornerPaint)
        
        // Tarama çizgisini çiz (animasyon aktifse)
        if (isScanningActive) {
            canvas.drawRect(
                frameRect.left + 10,
                scanLineY,
                frameRect.right - 10,
                scanLineY + 4,
                scanLinePaint
            )
        }
        
        // Odak göstergesini çiz (dokunma noktasında)
        if (isFocusVisible) {
            canvas.drawCircle(focusX, focusY, focusSize / 2, focusPaint)
            canvas.drawLine(focusX - focusSize / 2, focusY, focusX + focusSize / 2, focusY, focusPaint)
            canvas.drawLine(focusX, focusY - focusSize / 2, focusX, focusY + focusSize / 2, focusPaint)
        }
        
        // Durum mesajını çiz
        if (statusMessageVisible && statusMessage.isNotEmpty()) {
            canvas.drawText(
                statusMessage,
                width / 2f,
                frameRect.bottom + 80,
                statusTextPaint
            )
        }
    }
    
    /**
     * Tarama animasyonunu başlat
     */
    fun startScanAnimation() {
        // Önceki animasyonu iptal et
        scanAnimator?.cancel()
        
        // Tarama durumunu aktif yap
        isScanningActive = true
        
        // Yeni animasyon oluştur
        scanAnimator = ValueAnimator.ofFloat(frameRect.top, frameRect.bottom).apply {
            duration = 2000 // 2 saniye
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                scanLineY = animator.animatedValue as Float
                invalidate() // Görünümü yeniden çiz
            }
            
            start()
        }
    }
    
    /**
     * Tarama animasyonunu durdur
     */
    fun stopScanAnimation() {
        scanAnimator?.cancel()
        isScanningActive = false
        invalidate()
    }
    
    /**
     * Odak göstergesini belirtilen konumda göster
     */
    fun showFocusIndicator(x: Float, y: Float) {
        focusX = x
        focusY = y
        isFocusVisible = true
        invalidate()
        
        // 1 saniye sonra gizle
        postDelayed({
            isFocusVisible = false
            invalidate()
        }, 1000)
    }
    
    /**
     * Durum mesajını güncelle ve göster
     */
    fun updateStatusMessage(message: String) {
        statusMessage = message
        statusMessageVisible = message.isNotEmpty()
        invalidate()
    }
    
    /**
     * Düşük ışık moduna göre renkleri güncelle
     */
    fun updateLightMode(mode: LowLightEnhancer.ScanMode) {
        currentMode = mode
        
        when (mode) {
            LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> {
                // Aşırı düşük ışık - parlak kırmızı
                scanLinePaint.color = Color.parseColor("#FF5252")
                cornerPaint.color = Color.parseColor("#FF5252")
                framePaint.color = Color.parseColor("#FF5252")
                cornerPaint.alpha = 255
                framePaint.alpha = 200
            }
            LowLightEnhancer.ScanMode.LOW_LIGHT -> {
                // Düşük ışık - sarı
                scanLinePaint.color = Color.parseColor("#FFEB3B")
                cornerPaint.color = Color.parseColor("#FFEB3B")
                framePaint.color = Color.parseColor("#FFEB3B")
                cornerPaint.alpha = 255
                framePaint.alpha = 200
            }
            LowLightEnhancer.ScanMode.NORMAL -> {
                // Normal - yeşil
                scanLinePaint.color = Color.parseColor("#4CAF50")
                cornerPaint.color = Color.WHITE
                framePaint.color = Color.WHITE
                cornerPaint.alpha = 255
                framePaint.alpha = 200
            }
            LowLightEnhancer.ScanMode.BRIGHT -> {
                // Aşırı parlak - mavi
                scanLinePaint.color = Color.parseColor("#2196F3")
                cornerPaint.color = Color.parseColor("#2196F3")
                framePaint.color = Color.WHITE
                cornerPaint.alpha = 255
                framePaint.alpha = 180
            }
            else -> {
                // Varsayılan - yeşil
                scanLinePaint.color = Color.parseColor("#4CAF50")
                cornerPaint.color = Color.WHITE
                framePaint.color = Color.WHITE
                cornerPaint.alpha = 255
                framePaint.alpha = 200
            }
        }
        
        invalidate()
    }
}
