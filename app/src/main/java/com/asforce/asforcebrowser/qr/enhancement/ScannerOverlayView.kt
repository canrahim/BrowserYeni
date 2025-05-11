package com.asforce.asforcebrowser.qr.enhancement

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.asforce.asforcebrowser.R

/**
 * Gelişmiş QR tarayıcı ön izleme görünümü
 * 
 * Düşük ışık koşullarında daha belirgin bir tarama alanı ve göstergeleri
 * ile kullanıcı deneyimini iyileştirir.
 * 
 * Referanslar:
 * - "Mobile UI Design Patterns for QR Code Scanning" - Nielsen Norman Group
 * - "Designing User Interfaces for Low-Light Environments" - (2022)
 * - Android Custom View API: https://developer.android.com/reference/android/view/View
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Boya özellikleri
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val cornerPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    private val scanLinePaint = Paint().apply {
        color = Color.GREEN
        alpha = 180
        style = Paint.Style.FILL
    }
    
    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 150
    }
    
    private val transparentPaint = Paint().apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    
    private val focusIndicatorPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val statusTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    
    // Çerçeve boyutları
    private var frameWidth = 280f
    private var frameHeight = 280f
    
    // Köşe çizgi uzunluğu
    private val cornerLength = 32f
    
    // Tarama çizgisi ve animasyon
    private var scanLineTop = 0f
    private var scanLineTranslation = 0f
    private var scanLineAnimation: Animation? = null
    private var isScanAnimationActive = false
    
    // Durum değişkenleri
    private var lightMode = LowLightEnhancer.ScanMode.NORMAL
    private var focusPoint: Pair<Float, Float>? = null
    private var focusAnimationProgress = 0f
    private var lastStatusMessage = ""
    
    init {
        // Tarayıcı üst katmanı için alfa kanal etkinleştirme
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setupScanAnimation()
    }
    
    /**
     * Tarama çizgisi animasyonunu ayarla
     */
    private fun setupScanAnimation() {
        scanLineAnimation = TranslateAnimation(0f, 0f, -frameHeight / 2, frameHeight / 2).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    isScanAnimationActive = true
                }
                
                override fun onAnimationEnd(animation: Animation?) {
                    isScanAnimationActive = false
                }
                
                override fun onAnimationRepeat(animation: Animation?) {
                    // Tekrar animasyonu
                }
            })
        }
    }
    
    /**
     * Tarama animasyonunu başlat
     */
    fun startScanAnimation() {
        if (!isScanAnimationActive) {
            scanLineAnimation?.let { startAnimation(it) }
        }
    }
    
    /**
     * Tarama animasyonunu durdur
     */
    fun stopScanAnimation() {
        if (isScanAnimationActive) {
            clearAnimation()
            isScanAnimationActive = false
        }
    }
    
    /**
     * Tarama modunu güncelle
     */
    fun updateLightMode(mode: LowLightEnhancer.ScanMode) {
        this.lightMode = mode
        
        // Mod değiştiyse görsel stilini güncelle
        when (mode) {
            LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> {
                cornerPaint.color = Color.rgb(0, 255, 255) // Mavi-yeşil
                scanLinePaint.color = Color.rgb(0, 255, 255)
                framePaint.color = Color.WHITE
                framePaint.strokeWidth = 2f
                cornerPaint.strokeWidth = 10f
                overlayPaint.alpha = 180
            }
            LowLightEnhancer.ScanMode.LOW_LIGHT -> {
                cornerPaint.color = Color.rgb(0, 255, 0) // Parlak yeşil
                scanLinePaint.color = Color.rgb(0, 255, 0)
                framePaint.color = Color.WHITE
                framePaint.strokeWidth = 2f
                cornerPaint.strokeWidth = 8f
                overlayPaint.alpha = 170
            }
            LowLightEnhancer.ScanMode.NORMAL -> {
                cornerPaint.color = Color.rgb(0, 200, 0) // Normal yeşil
                scanLinePaint.color = Color.rgb(0, 200, 0)
                framePaint.color = Color.WHITE
                framePaint.strokeWidth = 2f
                cornerPaint.strokeWidth = 6f
                overlayPaint.alpha = 150
            }
            LowLightEnhancer.ScanMode.BRIGHT -> {
                cornerPaint.color = Color.rgb(0, 150, 0) // Koyu yeşil
                scanLinePaint.color = Color.rgb(0, 150, 0)
                framePaint.color = Color.WHITE
                framePaint.strokeWidth = 1f
                cornerPaint.strokeWidth = 4f
                overlayPaint.alpha = 120
            }
            LowLightEnhancer.ScanMode.AUTO -> {
                // Otomatik mod için varsayılan değerler (Normal mode benzer)
                cornerPaint.color = Color.rgb(0, 180, 180) // Turkuaz
                scanLinePaint.color = Color.rgb(0, 180, 180)
                framePaint.color = Color.WHITE
                framePaint.strokeWidth = 2f
                cornerPaint.strokeWidth = 6f
                overlayPaint.alpha = 150
            }
            else -> {
                // Varsayılan stil
                cornerPaint.color = Color.rgb(0, 200, 0) // Normal yeşil
                scanLinePaint.color = Color.rgb(0, 200, 0)
                framePaint.color = Color.WHITE
                framePaint.strokeWidth = 2f
                cornerPaint.strokeWidth = 6f
                overlayPaint.alpha = 150
            }
        }
        
        invalidate()
    }
    
    /**
     * Odak noktasını güncelle - kamera odaklandığında görsel geri bildirim
     */
    fun showFocusIndicator(x: Float, y: Float) {
        focusPoint = Pair(x, y)
        focusAnimationProgress = 0f
        
        // Odak animasyonu için yenileme döngüsünü başlat
        postDelayed(object : Runnable {
            override fun run() {
                focusAnimationProgress += 0.05f
                invalidate()
                
                if (focusAnimationProgress < 1f) {
                    postDelayed(this, 16) // 60fps için yaklaşık 16ms
                } else {
                    focusPoint = null
                }
            }
        }, 16)
    }
    
    /**
     * Durum mesajını güncelle
     */
    fun updateStatusMessage(message: String) {
        lastStatusMessage = message
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Yarı saydam siyah arkaplan
        canvas.drawRect(0f, 0f, width, height, overlayPaint)
        
        // Tarama çerçevesi
        val left = (width - frameWidth) / 2
        val top = (height - frameHeight) / 2
        val right = left + frameWidth
        val bottom = top + frameHeight
        
        // Şeffaf dikdörtgen (QR gösterge alanı)
        canvas.drawRect(left, top, right, bottom, transparentPaint)
        
        // Çerçeve kenarları
        canvas.drawRect(left, top, right, bottom, framePaint)
        
        // Köşe işaretleri
        drawCorners(canvas, left, top, right, bottom)
        
        // Tarama çizgisi
        if (isScanAnimationActive) {
            val lineY = top + (scanLineTranslation * frameHeight)
            canvas.drawRect(left + 10, lineY - 2, right - 10, lineY + 2, scanLinePaint)
        }
        
        // Odak göstergesi (eğer varsa)
        focusPoint?.let { (x, y) ->
            val focusSize = 100f * (1f - focusAnimationProgress)
            canvas.drawCircle(x, y, focusSize, focusIndicatorPaint)
        }
        
        // Durum metni (düşük ışık için) 
        if (lightMode == LowLightEnhancer.ScanMode.LOW_LIGHT || 
            lightMode == LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT) {
            
            val modeText = when (lightMode) {
                LowLightEnhancer.ScanMode.EXTREME_LOW_LIGHT -> "Düşük Işık Modu Aktif"
                LowLightEnhancer.ScanMode.LOW_LIGHT -> "Geliştirilmiş Tarama Modu Aktif"
                else -> ""
            }
            
            if (modeText.isNotEmpty()) {
                canvas.drawText(modeText, width / 2, bottom + 60, statusTextPaint)
            }
        }
        
        // Ek durum mesajı
        if (lastStatusMessage.isNotEmpty()) {
            canvas.drawText(lastStatusMessage, width / 2, top - 30, statusTextPaint)
        }
    }
    
    /**
     * Çerçeve köşelerini çiz
     */
    private fun drawCorners(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        // Sol Üst Köşe
        canvas.drawLine(left, top + cornerLength, left, top, cornerPaint) // Dikey
        canvas.drawLine(left, top, left + cornerLength, top, cornerPaint) // Yatay
        
        // Sağ Üst Köşe
        canvas.drawLine(right - cornerLength, top, right, top, cornerPaint) // Yatay
        canvas.drawLine(right, top, right, top + cornerLength, cornerPaint) // Dikey
        
        // Sol Alt Köşe
        canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint) // Dikey
        canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint) // Yatay
        
        // Sağ Alt Köşe
        canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint) // Yatay
        canvas.drawLine(right, bottom - cornerLength, right, bottom, cornerPaint) // Dikey
    }
}
