package com.asforce.asforcebrowser.suggestion.util

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager

/**
 * Klavye yüksekliğini ve durumunu kontrol etmek için yardımcı sınıf
 */
object KeyboardUtils {
    // Son ölçülen klavye yüksekliği
    private var lastKeyboardHeight = 0
    
    // Klavye dinleyicileri
    private val keyboardListeners = mutableListOf<KeyboardVisibilityListener>()
    
    /**
     * Klavye yüksekliğini hesapla
     * 
     * @param rootView Ana düzen
     * @return Klavye yüksekliği (piksel)
     */
    fun getKeyboardHeight(rootView: View): Int {
        val screenHeight = rootView.height
        
        val r = Rect()
        rootView.getWindowVisibleDisplayFrame(r)
        
        val visibleHeight = r.bottom - r.top
        val keyboardHeight = screenHeight - visibleHeight
        
        // Minimum yükseklik eşiği - klavye açık kabul etmek için
        return if (keyboardHeight > 200) keyboardHeight else 0
    }
    
    /**
     * Klavyenin açık olup olmadığını kontrol et
     * 
     * @param rootView Ana düzen
     * @return Klavye açıksa true
     */
    fun isKeyboardVisible(rootView: View): Boolean {
        return getKeyboardHeight(rootView) > 0
    }
    
    /**
     * Klavye durumunu izlemeye başla
     * 
     * @param rootView Ana düzen
     * @param listener Klavye durum değişikliği dinleyicisi
     */
    fun startKeyboardListener(rootView: View, listener: KeyboardVisibilityListener) {
        if (!keyboardListeners.contains(listener)) {
            keyboardListeners.add(listener)
        }
        
        // İlk kez bir dinleyici ekleniyorsa, global layout dinleyicisini başlat
        if (keyboardListeners.size == 1) {
            setupKeyboardVisibilityListener(rootView)
        }
    }
    
    /**
     * Klavye dinleyicisini kaldır
     * 
     * @param listener Kaldırılacak dinleyici
     */
    fun removeKeyboardListener(listener: KeyboardVisibilityListener) {
        keyboardListeners.remove(listener)
    }
    
    /**
     * Klavye durumunu izlemek için global layout dinleyicisi ayarla
     * 
     * @param rootView Ana düzen
     */
    private fun setupKeyboardVisibilityListener(rootView: View) {
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val currentKeyboardHeight = getKeyboardHeight(rootView)
            val wasVisible = lastKeyboardHeight > 0
            val isVisible = currentKeyboardHeight > 0
            
            // Klavye durumu değişti mi?
            if (wasVisible != isVisible) {
                // Tüm kayıtlı dinleyicilere bildir
                keyboardListeners.forEach { listener ->
                    listener.onKeyboardVisibilityChanged(isVisible, currentKeyboardHeight)
                }
            } else if (isVisible && currentKeyboardHeight != lastKeyboardHeight) {
                // Klavye boyutu değişti, dinleyicilere bildir
                keyboardListeners.forEach { listener ->
                    listener.onKeyboardHeightChanged(currentKeyboardHeight)
                }
            }
            
            lastKeyboardHeight = currentKeyboardHeight
        }
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }
    
    /**
     * Mevcut odaklı görünümde klavyeyi göster
     * 
     * @param context Context
     */
    fun showKeyboard(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }
    
    /**
     * Klavyeyi gizle
     * 
     * @param view Odaklı görünüm
     * @param context Context
     */
    fun hideKeyboard(view: View, context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    /**
     * Klavye görünürlük değişimi dinleyici arayüzü
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
