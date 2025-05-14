package com.asforce.asforcebrowser.presentation.browser

import android.webkit.WebView
import com.asforce.asforcebrowser.suggestion.SuggestionManager
import timber.log.Timber

/**
 * WebViewFragment eklentisi - Öneri sistemi entegrasyonu
 * 
 * Bu eklenti WebViewFragment sınıfına öneri sistemi entegrasyonu sağlar.
 * Aşağıdaki kod WebViewFragment sınıfına eklenmelidir.
 */

/**
 * Öneri yöneticisi
 */
private var suggestionManager: SuggestionManager? = null

/**
 * Öneri yöneticisini başlat
 * WebViewFragment'in onViewCreated metodu içinde çağrılmalıdır
 */
private fun WebViewFragment.initSuggestionManager() {
    if (suggestionManager == null) {
        val activity = requireActivity()
        val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        
        if (rootView != null) {
            suggestionManager = SuggestionManager(activity, rootView)
            
            Timber.d("SuggestionManager initialized")
        } else {
            Timber.e("Root view not found for SuggestionManager")
        }
    }
}

/**
 * WebView için öneri sistemini ayarla
 * WebView oluşturulduğunda çağrılmalıdır
 * 
 * @param webView WebView instance
 * @param tabId Sekme kimliği
 */
private fun WebViewFragment.setupSuggestionSystem(webView: WebView, tabId: Long) {
    if (suggestionManager == null) {
        initSuggestionManager()
    }
    
    suggestionManager?.setupWebView(webView, tabId.toString())
    Timber.d("Suggestion system setup for WebView, tabId: $tabId")
}

/**
 * Fragment yok edildiğinde öneri yöneticisini temizle
 * WebViewFragment'in onDestroy metodu içinde çağrılmalıdır
 * 
 * @param tabId Sekme kimliği
 */
private fun WebViewFragment.cleanupSuggestionManager(tabId: Long) {
    suggestionManager?.onTabClosed(tabId.toString())
    Timber.d("Suggestion manager cleanup for tabId: $tabId")
}

/**
 * Sayfa yüklendiğinde JavaScript enjekte et
 * WebViewFragment'in onPageFinished metodu içinde çağrılmalıdır
 * 
 * @param webView WebView instance
 */
private fun injectSuggestionScripts(webView: WebView) {
    // JavaScript kodu enjekte et - input alanlarını izle
    val script = """
        (function() {
            if (window.asforceInputObserver) {
                window.asforceInputObserver.observeAllInputs();
                return true;
            } else {
                // Input observer yüklenmemiş, tekrar yüklemeyi dene
                ${com.asforce.asforcebrowser.suggestion.js.JsInjectionScript.INPUT_OBSERVER_SCRIPT}
                return true;
            }
        })();
    """.trimIndent()
    
    webView.evaluateJavascript(script) { result ->
        Timber.d("Suggestion scripts injected, result: $result")
    }
}

/**
 * WebViewFragment sınıfında aşağıdaki değişiklikleri yapın:
 * 
 * 1. onViewCreated metoduna ekle:
 *    initSuggestionManager()
 * 
 * 2. setupWebView metoduna ekle (veya WebView oluşturulduğunda):
 *    setupSuggestionSystem(binding.webView, tabId)
 * 
 * 3. webViewClient'in onPageFinished metoduna ekle:
 *    injectSuggestionScripts(view)
 * 
 * 4. onDestroy metoduna ekle:
 *    cleanupSuggestionManager(tabId)
 */