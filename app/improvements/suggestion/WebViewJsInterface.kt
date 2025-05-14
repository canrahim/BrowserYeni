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
        Timber.d("Input focused: $fieldIdentifier, type: $fieldType")
        onInputFocused.invoke(fieldIdentifier, fieldType)
    }
    
    /**
     * Input alanı odağını kaybettiğinde JavaScript'ten çağrılır
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     */
    @JavascriptInterface
    fun onInputBlurred(fieldIdentifier: String) {
        Timber.d("Input blurred: $fieldIdentifier")
        onInputBlurred.invoke(fieldIdentifier)
    }
    
    /**
     * Input alanının değeri değiştiğinde JavaScript'ten çağrılır
     * 
     * @param fieldIdentifier Alan tanımlayıcısı (id veya name)
     * @param value Yeni değer
     */
    @JavascriptInterface
    fun onInputValueChanged(fieldIdentifier: String, value: String) {
        Timber.d("Input value changed: $fieldIdentifier, value: $value")
        if (value.isNotEmpty()) {
            onInputValueChanged.invoke(fieldIdentifier, value)
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
        Timber.d("Form submitted, saving value: $fieldIdentifier, value: $value")
        if (value.isNotEmpty()) {
            viewModel.addSuggestion(fieldIdentifier, value, fieldType)
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
        Timber.d("Input field count: $count")
    }
    
    /**
     * Hata durumlarını JavaScript'ten raporlar
     * 
     * @param errorMessage Hata mesajı
     */
    @JavascriptInterface
    fun logError(errorMessage: String) {
        Timber.e("JS Error: $errorMessage")
    }
}
