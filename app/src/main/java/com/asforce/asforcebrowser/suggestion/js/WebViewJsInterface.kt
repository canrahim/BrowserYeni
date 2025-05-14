package com.asforce.asforcebrowser.suggestion.js

import android.webkit.JavascriptInterface
import com.asforce.asforcebrowser.suggestion.viewmodel.SuggestionViewModel
import timber.log.Timber

/**
 * WebView JavaScript entegrasyonu için köprü
 * 
 * WebView'deki HTML input alanlarını izlemek ve 
 * odaklandığında native tarafa bildirim göndermek için kullanılır.
 * 
 * @param viewModel Öneri verileri için ViewModel
 */
class WebViewJsInterface(
    private val viewModel: SuggestionViewModel,
    private val onInputFocused: (fieldIdentifier: String, fieldType: String) -> Unit,
    private val onInputBlurred: (fieldIdentifier: String) -> Unit,
    private val onInputValueChanged: (fieldIdentifier: String, value: String) -> Unit
) {
    /**
     * Input alanına odaklandığında JavaScript'ten çağrılır
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     * @param fieldType Alan tipi (text, email, number, vb.)
     */
    @JavascriptInterface
    fun onInputFocused(fieldIdentifier: String, fieldType: String) {
        Timber.d(">> JavaScript to Native: Input focused: $fieldIdentifier, type: $fieldType")
        try {
            onInputFocused.invoke(fieldIdentifier, fieldType)
        } catch (e: Exception) {
            Timber.e("Error in onInputFocused: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Input alanı odağını kaybettiğinde JavaScript'ten çağrılır
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     */
    @JavascriptInterface
    fun onInputBlurred(fieldIdentifier: String) {
        Timber.d(">> JavaScript to Native: Input blurred: $fieldIdentifier")
        try {
            onInputBlurred.invoke(fieldIdentifier)
        } catch (e: Exception) {
            Timber.e("Error in onInputBlurred: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Input alanının değeri değiştiğinde JavaScript'ten çağrılır
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     * @param value Yeni değer
     */
    @JavascriptInterface
    fun onInputValueChanged(fieldIdentifier: String, value: String) {
        Timber.d(">> JavaScript to Native: Input value changed: $fieldIdentifier, value: $value")
        if (value.isNotEmpty()) {
            try {
                onInputValueChanged.invoke(fieldIdentifier, value)
            } catch (e: Exception) {
                Timber.e("Error in onInputValueChanged: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Input değeri form gönderildiğinde kaydedilir
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     * @param value Gönderilen değer
     * @param fieldType Alan tipi
     */
    @JavascriptInterface
    fun saveSubmittedValue(fieldIdentifier: String, value: String, fieldType: String) {
        Timber.d(">> JavaScript to Native: Form submitted, saving value: $fieldIdentifier, value: $value, type: $fieldType")
        if (value.isNotEmpty()) {
            try {
                viewModel.addSuggestion(fieldIdentifier, value, fieldType)
                Timber.d(">> Suggestion added successfully for field: $fieldIdentifier")
            } catch (e: Exception) {
                Timber.e("Error in saveSubmittedValue: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Sayfa URL'si değiştiğinde JavaScript'ten çağrılır
     * 
     * @param url Yeni URL
     */
    @JavascriptInterface
    fun onPageUrlChanged(url: String) {
        Timber.d("Page URL changed: $url")
        // URL'yi sakla - öneriler için URL paterni olarak kullanılabilir
        viewModel.setCurrentUrl(url)
    }
    
    /**
     * Input alanlarını JavaScript'ten sayar ve bildirir
     * 
     * @param count Input alanı sayısı
     */
    @JavascriptInterface
    fun reportInputFieldCount(count: Int) {
        Timber.d(">> JavaScript to Native: Input field count: $count")
    }
    
    /**
     * Hata durumlarını JavaScript'ten raporlar
     * 
     * @param errorMessage Hata mesajı
     */
    @JavascriptInterface
    fun logError(errorMessage: String) {
        Timber.e(">> JavaScript to Native: JS Error: $errorMessage")
        // Gelişmiş hata ayıklama için özel hata raporlama ekle
        try {
            // Özel hata raporlama işlemleri
            val e = Exception("JavaScript Error: $errorMessage")
            e.printStackTrace()
        } catch (e: Exception) {
            Timber.e("Error in error logger: ${e.message}")
        }
    }
}