package com.asforce.asforcebrowser.suggestion.ui

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.databinding.SuggestionPanelLayoutBinding
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import com.asforce.asforcebrowser.suggestion.util.KeyboardUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Klavye üzerinde görüntülenen öneri paneli
 * 
 * WebView input alanları için önerileri gösteren ve yöneten panel sınıfı.
 */
class SuggestionPanel(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val rootView: ViewGroup
) {
    private var binding: SuggestionPanelLayoutBinding
    private var panelView: View
    private var adapter: SuggestionAdapter
    
    // Panel gösterim durumu
    private var isShowing = false
    
    // Mevcut odaklanılan alan bilgisi
    private var currentField: String? = null
    
    init {
        // Panel view'ı oluştur
        val inflater = LayoutInflater.from(context)
        binding = SuggestionPanelLayoutBinding.inflate(inflater)
        panelView = binding.root
        
        // RecyclerView adaptörünü ayarla
        adapter = SuggestionAdapter(
            onSuggestionClicked = { suggestion ->
                // Öneri seçildiğinde
                applySelectedSuggestion(suggestion)
                hidePanel()
            },
            onDeleteClicked = { suggestion ->
                // Silme butonuna tıklandığında
                onDeleteSuggestion(suggestion)
            }
        )
        
        binding.suggestionRecyclerView.adapter = adapter
        
        // Panel kapatma butonu
        binding.btnCancelPanel.setOnClickListener {
            hidePanel()
        }
        
        // Başlangıçta gizli
        panelView.visibility = View.GONE
    }
    
    /**
     * Paneli göster ve önerileri yükle
     * 
     * @param fieldIdentifier Odaklanılan HTML input alanının id/name'i
     * @param suggestions Gösterilecek öneriler
     */
    fun showPanel(fieldIdentifier: String, suggestions: Flow<List<SuggestionEntity>>) {
        currentField = fieldIdentifier
        
        // Önerileri yükle
        lifecycleOwner.lifecycleScope.launch {
            suggestions.collectLatest { suggestionList ->
                adapter.submitList(suggestionList)
                
                // Boş durum gösterimi
                if (suggestionList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.suggestionRecyclerView.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.suggestionRecyclerView.visibility = View.VISIBLE
                }
                
                // Panel zaten gösteriliyorsa güncelleme yap
                if (isShowing) {
                    updatePanelPosition()
                } else {
                    // İlk kez gösteriliyor
                    showPanelWithAnimation()
                }
            }
        }
    }
    
    /**
     * Paneli gizle
     */
    fun hidePanel() {
        if (!isShowing) return
        
        // Animasyon ile gizle
        val slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_down)
        panelView.startAnimation(slideDown)
        
        slideDown.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                panelView.visibility = View.GONE
                
                // Panel view'ı ebeveynden kaldır
                if (panelView.parent != null) {
                    (panelView.parent as ViewGroup).removeView(panelView)
                }
                
                isShowing = false
                currentField = null
            }
            
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
    }
    
    /**
     * Seçilen öneriyi uygula
     */
    private fun applySelectedSuggestion(suggestion: SuggestionEntity) {
        // JavaScript ile WebView'deki input alanını doldur
        val script = """
            (function() {
                var input = document.querySelector('[id="${suggestion.fieldIdentifier}"], [name="${suggestion.fieldIdentifier}"]');
                if (input) {
                    input.value = "${suggestion.value}";
                    
                    // Input ve change olaylarını tetikle
                    var event = new Event('input', { bubbles: true });
                    input.dispatchEvent(event);
                    
                    var changeEvent = new Event('change', { bubbles: true });
                    input.dispatchEvent(changeEvent);
                    
                    return true;
                }
                return false;
            })();
        """.trimIndent()
        
        // WebView'e scripti gönder
        // Bu metot WebViewFragment'ten çağrılmalı
        SuggestionJsInterface.evaluateJavascript(script) { result ->
            if (result == "true") {
                // Başarılı bir şekilde uygulandı
                // Kullanım sayacını artır
                onSuggestionUsed(suggestion)
            }
        }
    }
    
    /**
     * Öneri kullanıldığında çağrılır - kullanım sayacını artırmak için
     */
    private fun onSuggestionUsed(suggestion: SuggestionEntity) {
        // Bu işlem SuggestionViewModel tarafından yapılmalıdır
        SuggestionJsInterface.onSuggestionUsed(suggestion)
    }
    
    /**
     * Öneri silindiğinde çağrılır
     */
    private fun onDeleteSuggestion(suggestion: SuggestionEntity) {
        // Bu işlem SuggestionViewModel tarafından yapılmalıdır
        SuggestionJsInterface.onSuggestionDeleted(suggestion)
    }
    
    /**
     * Animasyon ile paneli göster
     */
    private fun showPanelWithAnimation() {
        // Eğer zaten gösteriliyorsa tekrar gösterme
        if (isShowing) return
        
        // Panel pozisyonunu ayarla
        updatePanelPosition()
        
        // Ebeveyn view'a ekle
        if (panelView.parent == null) {
            rootView.addView(panelView)
        }
        
        // Animasyon ile göster
        panelView.visibility = View.VISIBLE
        val slideUp = AnimationUtils.loadAnimation(context, R.anim.slide_up)
        panelView.startAnimation(slideUp)
        
        isShowing = true
    }
    
    /**
     * Klavye pozisyonuna göre panel pozisyonunu güncelle
     */
    private fun updatePanelPosition() {
        // Klavye yüksekliğini ve ekran pozisyonunu al
        val keyboardHeight = KeyboardUtils.getKeyboardHeight(rootView)
        
        if (keyboardHeight > 0) {
            // Klavye açık, panelin LayoutParams'ini güncelle
            val params = if (panelView.parent == null) {
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                panelView.layoutParams as ViewGroup.MarginLayoutParams
            }
            
            // Panelin klavyenin üstünde olmasını sağla
            params.bottomMargin = keyboardHeight
            panelView.layoutParams = params
        }
    }
    
    /**
     * Klavye kapandığında paneli de kapat
     */
    fun onKeyboardClosed() {
        if (isShowing) {
            hidePanel()
        }
    }
    
    /**
     * Klavye durumu değiştiğinde çağrılır
     */
    fun onKeyboardVisibilityChanged(isVisible: Boolean) {
        if (!isVisible && isShowing) {
            hidePanel()
        } else if (isVisible && isShowing) {
            updatePanelPosition()
        }
    }
    
    /**
     * Panelin görünür olup olmadığını kontrol et
     */
    fun isVisible(): Boolean = isShowing
    
    /**
     * Statik JavaScript arabirimi
     * WebViewFragment tarafından enjekte edilecek
     */
    companion object SuggestionJsInterface {
        private var evaluateJsFunction: ((String, (String) -> Unit) -> Unit)? = null
        private var suggestionUsedCallback: ((SuggestionEntity) -> Unit)? = null
        private var suggestionDeletedCallback: ((SuggestionEntity) -> Unit)? = null
        
        /**
         * JavaScript evaluate fonksiyonunu ayarla
         */
        fun setEvaluateJsFunction(function: (String, (String) -> Unit) -> Unit) {
            evaluateJsFunction = function
        }
        
        /**
         * Öneri kullanıldığında çağrılacak callback'i ayarla
         */
        fun setSuggestionUsedCallback(callback: (SuggestionEntity) -> Unit) {
            suggestionUsedCallback = callback
        }
        
        /**
         * Öneri silindiğinde çağrılacak callback'i ayarla
         */
        fun setSuggestionDeletedCallback(callback: (SuggestionEntity) -> Unit) {
            suggestionDeletedCallback = callback
        }
        
        /**
         * JavaScript kodu çalıştır
         */
        fun evaluateJavascript(script: String, resultCallback: (String) -> Unit) {
            evaluateJsFunction?.invoke(script, resultCallback)
        }
        
        /**
         * Öneri kullanıldığında çağrılır
         */
        fun onSuggestionUsed(suggestion: SuggestionEntity) {
            suggestionUsedCallback?.invoke(suggestion)
        }
        
        /**
         * Öneri silindiğinde çağrılır
         */
        fun onSuggestionDeleted(suggestion: SuggestionEntity) {
            suggestionDeletedCallback?.invoke(suggestion)
        }
    }
}
