package com.asforce.asforcebrowser.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.asforce.asforcebrowser.suggestion.js.JsInjectionScript
import com.asforce.asforcebrowser.suggestion.js.WebViewJsInterface
import com.asforce.asforcebrowser.suggestion.ui.SuggestionPanel
import com.asforce.asforcebrowser.suggestion.util.KeyboardUtils
import com.asforce.asforcebrowser.suggestion.viewmodel.SuggestionViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * WebView öneri entegrasyon yöneticisi
 * 
 * WebView için öneri sistemi entegrasyonunu yönetir.
 * WebView-JS-Native arasındaki iletişimi koordine eder.
 * 
 * @param activity Aktivite
 * @param rootView Panel ekleneceği kök view (genellikle CoordinatorLayout veya FrameLayout)
 */
class SuggestionManager(
    private val activity: FragmentActivity,
    private val rootView: ViewGroup
) : LifecycleObserver, KeyboardUtils.KeyboardVisibilityListener {
    
    // ViewModel
    private val viewModel: SuggestionViewModel by lazy {
        ViewModelProvider(activity)[SuggestionViewModel::class.java]
    }
    
    // Öneri paneli
    private lateinit var suggestionPanel: SuggestionPanel
    
    // WebView bağlamları
    private val webViewInstances = mutableMapOf<WebView, String>() // WebView -> tabId
    
    // Öneri akışı job'ları
    private var currentSuggestionsJob: Job? = null
    
    // Klavye görünürlüğü kontrolü
    private var keyboardVisible = false
    
    init {
        // Aktivite yaşam döngüsünü izle
        activity.lifecycle.addObserver(this)
        
        // Panel'i oluştur
        suggestionPanel = SuggestionPanel(activity, activity, rootView)
        
        // Klavye durumunu izlemeye başla
        KeyboardUtils.startKeyboardListener(rootView, this)
        
        // SuggestionPanel'e callback'leri ayarla
        setupPanelCallbacks()
    }
    
    /**
     * WebView'i öneri sistemi için hazırla
     * 
     * @param webView WebView instance
     * @param tabId Sekme ID'si
     */
    fun setupWebView(webView: WebView, tabId: String) {
        try {
            Timber.d("Setting up suggestion system for WebView with tabId: $tabId")
            
            // WebView instance'ını kaydet
            webViewInstances[webView] = tabId
            
            // JavaScript köprüsünü ekle
            setupJsBridge(webView)
            
            // JavaScript kodu enjekte et
            injectJavaScript(webView)
            
            // WebView hazır log mesajı
            Timber.d("Suggestion system setup complete for tab: $tabId")
            
        } catch (e: Exception) {
            Timber.e("Error setting up WebView for suggestions: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * JavaScript köprüsünü ayarla
     * 
     * @param webView WebView instance
     */
    private fun setupJsBridge(webView: WebView) {
        try {
            // WebView için JavaScript köprüsü oluştur
            val jsInterface = WebViewJsInterface(
                viewModel = viewModel,
                onInputFocused = { fieldIdentifier, fieldType ->
                    // Input odaklandığında
                    Timber.d("Input focused callback: $fieldIdentifier, type: $fieldType")
                    onInputFieldFocused(fieldIdentifier, fieldType, webView)
                },
                onInputBlurred = { fieldIdentifier ->
                    // Input odak kaybettiğinde
                    Timber.d("Input blurred callback: $fieldIdentifier")
                    onInputFieldBlurred(fieldIdentifier, webView)
                },
                onInputValueChanged = { fieldIdentifier, value ->
                    // Input değeri değiştiğinde
                    Timber.d("Input value changed callback: $fieldIdentifier, value: $value")
                    onInputValueChanged(fieldIdentifier, value)
                }
            )
            
            // Köprüyü WebView'e ekle
            webView.addJavascriptInterface(jsInterface, "AsforceSuggestionBridge")
            
            // JavaScript'in etkin olduğundan emin ol
            if (!webView.settings.javaScriptEnabled) {
                webView.settings.javaScriptEnabled = true
                Timber.d("JavaScript enabled for WebView")
            }
            
            Timber.d("JavaScript bridge setup complete for WebView")
            
        } catch (e: Exception) {
            Timber.e("Error setting up JS bridge: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * JavaScript kodunu enjekte et
     * 
     * @param webView WebView instance
     */
    private fun injectJavaScript(webView: WebView) {
        try {
            // Sayfa yükleme işleminden sonra JavaScript kodunu enjekte et
            activity.runOnUiThread {
                webView.evaluateJavascript(JsInjectionScript.INPUT_OBSERVER_SCRIPT) { result ->
                    Timber.d("JavaScript injection result: $result")
                    
                    // Köprü kontrollerini yap
                    verifyInjectionSuccess(webView)
                }
            }
            
            Timber.d("JavaScript injection attempted for WebView")
        } catch (e: Exception) {
            Timber.e("Error injecting JavaScript: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * JavaScript enjeksiyonunun başarılı olup olmadığını doğrula
     * 
     * @param webView WebView instance
     */
    private fun verifyInjectionSuccess(webView: WebView) {
        // JavaScript köprüsünün doğru çalıştığını kontrol et
        activity.runOnUiThread {
            webView.evaluateJavascript("""
                (function() {
                    var result = {
                        observer: window.asforceInputObserver ? true : false,
                        bridge: window.AsforceSuggestionBridge ? true : false
                    };
                    return JSON.stringify(result);
                })();
            """.trimIndent()) { result ->
                try {
                    // JSON sonuç temizle ve parse et
                    val cleanResult = result.replace("\"", "").replace("\\\\", "\\")
                        .replace("\\{", "{").replace("\\}", "}")
                    val isObserverLoaded = cleanResult.contains("observer:true")
                    val isBridgeLoaded = cleanResult.contains("bridge:true")
                    
                    Timber.d("Injection verification - Observer: $isObserverLoaded, Bridge: $isBridgeLoaded")
                    
                    if (!isObserverLoaded || !isBridgeLoaded) {
                        Timber.w("JavaScript injection partially failed: $cleanResult")
                        
                        // Enjeksiyon başarısız oldu, tekrar dene
                        webView.postDelayed({
                            Timber.d("Retrying JavaScript injection...")
                            activity.runOnUiThread {
                                webView.evaluateJavascript(JsInjectionScript.INPUT_OBSERVER_SCRIPT, null)
                            }
                        }, 500)
                    }
                } catch (e: Exception) {
                    Timber.e("Error parsing injection verification result: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Panel callback'lerini ayarla
     */
    private fun setupPanelCallbacks() {
        // JavaScript kodu çalıştırma fonksiyonunu ayarla
        SuggestionPanel.setEvaluateJsFunction { script, resultCallback ->
            // Aktif WebView'i bul ve JavaScript'i çalıştır
            getActiveWebView()?.evaluateJavascript(script, resultCallback)
        }
        
        // Öneri kullanıldığında callback
        SuggestionPanel.setSuggestionUsedCallback { suggestion ->
            viewModel.incrementSuggestionUsage(suggestion)
        }
        
        // Öneri silindiğinde callback
        SuggestionPanel.setSuggestionDeletedCallback { suggestion ->
            viewModel.deleteSuggestion(suggestion)
        }
        
        // Tüm öneriler silindiğinde callback
        SuggestionPanel.setDeleteAllSuggestionsCallback { fieldIdentifier ->
            Timber.d("'$fieldIdentifier' alanı için tüm öneriler siliniyor")
            viewModel.deleteAllSuggestionsForField(fieldIdentifier)
        }
    }
    
    /**
     * Input alanı odaklandığında
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param fieldType Alan tipi
     * @param webView Odaklanılan alan içeren WebView
     */
    private fun onInputFieldFocused(fieldIdentifier: String, fieldType: String, webView: WebView) {
        Timber.d("Input focused: $fieldIdentifier, type: $fieldType")
        
        // Mevcut alanı güncelle
        viewModel.setCurrentField(fieldIdentifier)
        
        // Klavye açıksa öneri panelini göster
        if (keyboardVisible) {
            showSuggestionPanelForField(fieldIdentifier)
        }
    }
    
    /**
     * Input alanı odak kaybettiğinde
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param webView Odak kaybedilen alan içeren WebView
     */
    private fun onInputFieldBlurred(fieldIdentifier: String, webView: WebView) {
        Timber.d("Input blurred: $fieldIdentifier - değer almaya çalışıyorum")
        
        try {
            // WebView metodları UI thread'inde çağrılmalı
            activity.runOnUiThread {
                // Daha basit bir JavaScript kodu kullanarak değeri alalım
                webView.evaluateJavascript("""
                    (function() {
                        try {
                            // Önce document.getElementById ile deneyelim
                            var input = document.getElementById('${fieldIdentifier}');
                            
                            // Yoksa document.getElementsByName ile deneyelim
                            if (!input) {
                                var inputs = document.getElementsByName('${fieldIdentifier}');
                                if (inputs && inputs.length > 0) {
                                    input = inputs[0];
                                }
                            }
                            
                            // Yoksa querySelector ile deneyelim
                            if (!input) {
                                input = document.querySelector('[id="${fieldIdentifier}"], [name="${fieldIdentifier}"]');
                            }
                            
                            // Eğer input bulunduysa değerini döndür
                            if (input) {
                                return input.value || '';
                            }
                            
                            return '';
                        } catch(e) {
                            console.error('Error getting field value:', e);
                            return '';
                        }
                    })();
                """.trimIndent()) { valueWithQuotes ->
                    try {
                        // JavaScript'ten dönen değeri temizle (tırnak işaretlerini kaldır)
                        val value = valueWithQuotes.replace("\"", "")
                        Timber.d("Alan değeri alındı: '$value'")
                        
                        // Değer anlamlıysa (boş değilse ve yeterince uzunsa) öneri olarak kaydet
                        if (value.isNotBlank() && value.length > 1) {
                            Timber.d("Alan odağı kaybedildi, son değer kaydediliyor: $value")
                            viewModel.addSuggestion(fieldIdentifier, value)
                            Timber.d("Değer kaydedildi: $fieldIdentifier = $value")
                        } else {
                            Timber.d("Boş veya çok kısa değer, kaydedilmedi: '$value'")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Değeri işlerken hata oluştu: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Alan değerini alırken hata oluştu: ${e.message}")
        }
        
        // Odaklı alanı temizle
        if (viewModel.currentField.value == fieldIdentifier) {
            viewModel.setCurrentField(null)
        }
    }
    
    /**
     * Input değeri değiştiğinde
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Yeni değer
     */
    private fun onInputValueChanged(fieldIdentifier: String, value: String) {
        Timber.d("Input value changed: $fieldIdentifier, value: $value")
        
        // NOT: Yazmaya devam edilirken öneri kaydetmiyoruz
        // Öneriler sadece alan terk edildiğinde (blur) veya form gönderildiğinde kaydedilecek
    }
    
    /**
     * SuggestionPanel için bir alan için önerileri göster
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     */
    private fun showSuggestionPanelForField(fieldIdentifier: String) {
        try {
            // ViewModel'den öneriler için akışı al
            currentSuggestionsJob?.cancel() // Mevcut flow varsa iptal et
            
            // Yeni flow oluştur
            currentSuggestionsJob = activity.lifecycleScope.launch {
                val suggestions = viewModel.getSuggestionsForField(fieldIdentifier)
                // UI thread'inde panel güncelleme
                activity.runOnUiThread {
                    suggestionPanel.showPanel(fieldIdentifier, suggestions)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Öneri panel gösterme hatası")
        }
    }
    
    /**
     * Aktif WebView'i getir
     * 
     * @return Aktif WebView veya null
     */
    private fun getActiveWebView(): WebView? {
        // TabId'ye göre WebView getir
        return webViewInstances.keys.firstOrNull()
    }
    
    /**
     * Tab kapatıldığında
     * 
     * @param tabId Kapatılan tab ID'si
     */
    fun onTabClosed(tabId: String) {
        // İlgili WebView'i listeden kaldır
        webViewInstances.entries.removeIf { it.value == tabId }
    }
    
    /**
     * Aktivite oluşturulduğunda
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onActivityCreated() {
        // İhtiyaç duyulan başlangıç işlemleri
    }
    
    /**
     * Aktivite sonlandırıldığında
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onActivityDestroyed() {
        // Kaynakları temizle
        KeyboardUtils.removeKeyboardListener(this)
        
        // Job'ları iptal et
        currentSuggestionsJob?.cancel()
        
        // WebView listesini temizle
        webViewInstances.clear()
    }
    
    /**
     * Klavye görünürlüğü değiştiğinde çağrılır
     * 
     * @param isVisible Klavye görünür mü
     * @param keyboardHeight Klavye yüksekliği (piksel)
     */
    override fun onKeyboardVisibilityChanged(isVisible: Boolean, keyboardHeight: Int) {
        try {
            Timber.d("Klavye görünürlüğü değişti: $isVisible, yükseklik: $keyboardHeight")
            keyboardVisible = isVisible
            
            // UI işlemlerini ana thread'de yap
            activity.runOnUiThread {
                if (isVisible) {
                    // Klavye göründüğünde ve aktif bir alan varsa önerileri göster
                    val currentField = viewModel.currentField.value
                    if (currentField != null && currentField.isNotEmpty()) {
                        showSuggestionPanelForField(currentField)
                    }
                } else {
                    // Klavye gizlendiğinde her zaman paneli gizle
                    if (suggestionPanel.isVisible()) {
                        Timber.d("Klavye kapandı, panel kapatılıyor")
                        suggestionPanel.hidePanel(true) // Zorla kapat parametresi
                        
                        // Panel kapatma durumunu ViewModel'e bildir
                        viewModel.setPanelShowing(false)
                    }
                    
                    // Klavye kapandığında, mevcut odaklanmış alan değerini temizle
                    // Bu sayede bir alandan çıkıp panel açık kalırsa kapatılacak
                    if (viewModel.currentField.value != null) {
                        viewModel.setCurrentField(null)
                    }
                    
                    // Emin olmak için gecikmeli bir kapatma daha ekleyelim
                    activity.findViewById<View>(android.R.id.content).postDelayed({
                        if (!keyboardVisible && suggestionPanel.isVisible()) {
                            Timber.d("Klavye kapanma kontrolü gecikmeli tetiklendi, panel kapatılıyor")
                            suggestionPanel.hidePanel(true)
                            viewModel.setPanelShowing(false)
                        }
                    }, 300)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Klavye görünürlük değişikliği işleme hatası")
        }
    }
    
    /**
     * Klavye yüksekliği değiştiğinde çağrılır
     * 
     * @param keyboardHeight Yeni klavye yüksekliği
     */
    override fun onKeyboardHeightChanged(keyboardHeight: Int) {
        try {
            // UI işlemlerini ana thread'de yap
            activity.runOnUiThread {
                // Paneli yeni klavye yüksekliğine göre güncelle
                if (keyboardVisible) {
                    suggestionPanel.updateKeyboardHeight(keyboardHeight)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Klavye yükseklik değişikliği işleme hatası")
        }
    }
}