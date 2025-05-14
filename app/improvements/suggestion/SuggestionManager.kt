package com.asforce.asforcebrowser.suggestion

import android.content.Context
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
        // WebView instance'ını kaydet
        webViewInstances[webView] = tabId
        
        // JavaScript köprüsünü ekle
        setupJsBridge(webView)
        
        // JavaScript kodu enjekte et
        injectJavaScript(webView)
    }
    
    /**
     * JavaScript köprüsünü ayarla
     * 
     * @param webView WebView instance
     */
    private fun setupJsBridge(webView: WebView) {
        // WebView için JavaScript köprüsü oluştur
        val jsInterface = WebViewJsInterface(
            viewModel = viewModel,
            onInputFocused = { fieldIdentifier, fieldType ->
                // Input odaklandığında
                onInputFieldFocused(fieldIdentifier, fieldType, webView)
            },
            onInputBlurred = { fieldIdentifier ->
                // Input odak kaybettiğinde
                onInputFieldBlurred(fieldIdentifier, webView)
            },
            onInputValueChanged = { fieldIdentifier, value ->
                // Input değeri değiştiğinde
                onInputValueChanged(fieldIdentifier, value)
            }
        )
        
        // Köprüyü WebView'e ekle
        webView.addJavascriptInterface(jsInterface, "AsforceSuggestionBridge")
        
        // JavaScript enable
        webView.settings.javaScriptEnabled = true
    }
    
    /**
     * JavaScript kodunu enjekte et
     * 
     * @param webView WebView instance
     */
    private fun injectJavaScript(webView: WebView) {
        // Sayfa yükleme işleminden sonra JavaScript kodunu enjekte et
        webView.evaluateJavascript(JsInjectionScript.INPUT_OBSERVER_SCRIPT, null)
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
        Timber.d("Input blurred: $fieldIdentifier")
        
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
        
        // Değer boş değilse ve yeterince uzunsa potansiyel öneri olarak kaydet
        if (value.isNotBlank() && value.length > 2) {
            viewModel.addSuggestion(fieldIdentifier, value)
        }
    }
    
    /**
     * Belirli bir alan için öneri panelini göster
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     */
    private fun showSuggestionPanelForField(fieldIdentifier: String) {
        // İlk önce mevcut job'ı iptal et
        currentSuggestionsJob?.cancel()
        
        // Önerileri getir ve paneli göster
        currentSuggestionsJob = activity.lifecycleScope.launch {
            val suggestions = viewModel.getSuggestionsForField(fieldIdentifier)
            
            // Öneri panelini göster
            suggestionPanel.showPanel(fieldIdentifier, suggestions)
            
            // Panel durumunu güncelle
            viewModel.setPanelShowing(true)
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
     * Klavye görünürlüğü değiştiğinde
     * 
     * @param isVisible Klavye görünür mü
     * @param keyboardHeight Klavye yüksekliği
     */
    override fun onKeyboardVisibilityChanged(isVisible: Boolean, keyboardHeight: Int) {
        keyboardVisible = isVisible
        
        if (isVisible) {
            // Klavye açıldığında, odaklı alan varsa öneri panelini göster
            viewModel.currentField.value?.let { fieldId ->
                showSuggestionPanelForField(fieldId)
            }
        } else {
            // Klavye kapandığında paneli gizle
            if (suggestionPanel.isVisible()) {
                suggestionPanel.hidePanel()
                viewModel.setPanelShowing(false)
            }
        }
        
        // Panele klavye durumunu bildir
        suggestionPanel.onKeyboardVisibilityChanged(isVisible)
    }
    
    /**
     * Klavye yüksekliği değiştiğinde
     * 
     * @param keyboardHeight Yeni klavye yüksekliği
     */
    override fun onKeyboardHeightChanged(keyboardHeight: Int) {
        // Panel görünürse pozisyonunu güncelle
        if (suggestionPanel.isVisible()) {
            suggestionPanel.onKeyboardVisibilityChanged(true)
        }
    }
}
