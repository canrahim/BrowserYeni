package com.asforce.asforcebrowser.suggestion.util

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import timber.log.Timber

/**
 * Klavye görünürlüğünü ve yüksekliğini izlemek için yardımcı sınıf
 */
object KeyboardUtils {
    
    // Varsayılan klavye yüksekliği - algılanmazsa bu değer kullanılır
    private const val DEFAULT_KEYBOARD_HEIGHT = 800
    
    // Son algılanan klavye yüksekliği
    private var lastKeyboardHeight = DEFAULT_KEYBOARD_HEIGHT
    
    // Klavye dinleyicileri
    private val listeners = mutableListOf<KeyboardVisibilityListener>()
    
    // Klavye görünür mü?
    private var isKeyboardVisible = false
    
    // Global düzen değişikliği dinleyicisi
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    
    // İki klavye algılama arasındaki geçen süre kontrolü
    private var lastKeyboardDetectionTime = 0L
    private const val KEYBOARD_DETECTION_THROTTLE_MS = 100
    
    /**
     * Klavye dinleyicisini ayarlar ve klavye durumundaki değişiklikleri bildirir
     * 
     * @param rootView Ana layout
     * @param listener Klavye olaylarını dinleyen nesne
     */
    fun startKeyboardListener(rootView: View, listener: KeyboardVisibilityListener) {
        // Dinleyiciyi listeye ekle
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        
        // GlobalLayoutListener zaten kurulu mu kontrol et
        if (globalLayoutListener != null) {
            return
        }
        
        // GlobalLayoutListener kur
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            try {
                val currentTime = System.currentTimeMillis()
                // Çok sık algılama yaparak performans sorunu yaratmayı önle
                if (currentTime - lastKeyboardDetectionTime < KEYBOARD_DETECTION_THROTTLE_MS) {
                    return@OnGlobalLayoutListener
                }
                lastKeyboardDetectionTime = currentTime
                
                // Görünür ekran alanını al
                val r = Rect()
                rootView.getWindowVisibleDisplayFrame(r)
                
                // Toplam ekran yüksekliği
                val screenHeight = rootView.rootView.height
                
                // Klavye yüksekliği (px olarak)
                val keyboardHeight = screenHeight - r.bottom
                
                // Klavye görünür mü kontrol et (yüksekliği ekranın %5'inden büyükse)
                // Daha düşük eşik değeriyle daha duyarlı hale getirdik
                val keyboardVisibilityThreshold = screenHeight * 0.05
                val isKeyboardNowVisible = keyboardHeight > keyboardVisibilityThreshold
                
                // Durum değiştiyse bildir
                if (isKeyboardVisible != isKeyboardNowVisible) {
                    isKeyboardVisible = isKeyboardNowVisible
                    
                    // Klavye kapandığında hemen bildir
                    if (!isKeyboardVisible) {
                        Timber.d("Klavye kapandı olarak tespit edildi")
                        notifyKeyboardVisibilityChanged(false, 0)
                        
                        // Klavye kapandığında gecikmeli olarak bir kez daha kontrol et
                        // Kimi cihazlarda kapanma olayı doğru algılanmayabilir
                        rootView.postDelayed({
                            val r2 = Rect()
                            rootView.getWindowVisibleDisplayFrame(r2)
                            val currentKeyboardHeight = screenHeight - r2.bottom
                            
                            // Eğer hala kapandığını düşünüyorsak tekrar bildir
                            if (currentKeyboardHeight < keyboardVisibilityThreshold && isKeyboardVisible) {
                                Timber.d("Klavye kapanma olayı tekrar kontrol edildi ve bildirildi")
                                isKeyboardVisible = false
                                notifyKeyboardVisibilityChanged(false, 0)
                            }
                        }, 200) // Kısa bir gecikme
                    } else {
                        // Durum değişikliğini bildir
                        notifyKeyboardVisibilityChanged(isKeyboardVisible, keyboardHeight)
                        
                        // Klavye görünür hale geldiyse yüksekliği kaydet
                        if (keyboardHeight > 100) {
                            lastKeyboardHeight = keyboardHeight
                        }
                    }
                    
                    Timber.d("Klavye durumu değişti: $isKeyboardVisible, yükseklik: $keyboardHeight")
                } 
                // Klavye hala görünür ama yüksekliği değiştiyse
                // Daha küçük değişimlere duyarlı olalım (50px)
                else if (isKeyboardVisible && Math.abs(keyboardHeight - lastKeyboardHeight) > 50) {
                    lastKeyboardHeight = keyboardHeight
                    notifyKeyboardHeightChanged(keyboardHeight)
                    
                    Timber.d("Klavye yüksekliği değişti: $keyboardHeight")
                    
                    // Eğer klavye çok küçük hale geldiyse, kapanmış olabilir
                    if (keyboardHeight < keyboardVisibilityThreshold && isKeyboardVisible) {
                        isKeyboardVisible = false
                        Timber.d("Klavye kapanma durumu (yükseklik değişiminden tespit edildi)")
                        notifyKeyboardVisibilityChanged(false, 0)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Klavye durumu izlenirken hata")
                // Herhangi bir hatada işleme devam et
            }
        }
        
        // Dinleyiciyi ViewTreeObserver'a ekle
        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }
    
    /**
     * Klavye dinleyicisini kaldırır
     * 
     * @param listener Klavye olaylarını dinleyen nesne
     */
    fun removeKeyboardListener(listener: KeyboardVisibilityListener) {
        listeners.remove(listener)
    }
    
    /**
     * Tüm kayıtlı dinleyicilere klavye görünürlüğü değişikliğini bildirir
     * 
     * @param isVisible Klavye görünür mü
     * @param keyboardHeight Klavye yüksekliği
     */
    private fun notifyKeyboardVisibilityChanged(isVisible: Boolean, keyboardHeight: Int) {
        listeners.forEach { it.onKeyboardVisibilityChanged(isVisible, keyboardHeight) }
    }
    
    /**
     * Tüm kayıtlı dinleyicilere klavye yüksekliği değişikliğini bildirir
     * 
     * @param keyboardHeight Yeni klavye yüksekliği
     */
    private fun notifyKeyboardHeightChanged(keyboardHeight: Int) {
        listeners.forEach { it.onKeyboardHeightChanged(keyboardHeight) }
    }
    
    /**
     * Klavye görünür durumda mı?
     * 
     * @return Klavye görünür mü
     */
    fun isKeyboardVisible(): Boolean {
        return isKeyboardVisible
    }
    
    /**
     * Kayıtlı son klavye yüksekliğini döndürür
     * 
     * @return Klavye yüksekliği (piksel)
     */
    fun getKeyboardHeight(): Int {
        return lastKeyboardHeight
    }
    
    /**
     * Öneri paneli için doğru marjin değerini hesapla
     * Özellikle klavye ile panel arasında boşluk olmamasını sağlar
     *
     * @return Öneri paneli için kullanılacak alt marjin değeri
     */
    fun getSuggestionPanelMargin(): Int {
        // Klavye yüksekliği 0 ise varsayılan değer döndür
        return if (lastKeyboardHeight <= 0) 0 else lastKeyboardHeight
    }
    
    /**
     * Klavyeyi açar
     * 
     * @param context Context
     * @param view Odaklanacak view
     */
    fun showKeyboard(context: Context, view: View) {
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    
    /**
     * Klavyeyi kapatır
     * 
     * @param context Context
     * @param view Odağı kaybedecek view
     */
    fun hideKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    /**
     * Klavye görünürlüğü değişikliğini dinlemek için arayüz
     */
    interface KeyboardVisibilityListener {
        /**
         * Klavye görünürlüğü değiştiğinde çağrılır
         * 
         * @param isVisible Klavye görünür mü
         * @param keyboardHeight Klavye yüksekliği
         */
        fun onKeyboardVisibilityChanged(isVisible: Boolean, keyboardHeight: Int)
        
        /**
         * Klavye yüksekliği değiştiğinde çağrılır
         * 
         * @param keyboardHeight Yeni klavye yüksekliği
         */
        fun onKeyboardHeightChanged(keyboardHeight: Int)
    }
}