package com.asforce.asforcebrowser.suggestion.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import com.asforce.asforcebrowser.suggestion.util.KeyboardUtils
import timber.log.Timber

/**
 * Öneri paneli sınıfı
 * 
 * Klavye üzerinde gösterilen öneri panelini yönetir.
 */
class SuggestionPanel(
    private val context: Context,
    private val activity: FragmentActivity,
    private val rootView: ViewGroup
) {
    // Panel view'ı
    private var panelView: View? = null
    
    // Adapter ve RecyclerView
    private var suggestionAdapter: SuggestionAdapter? = null
    private var recyclerView: RecyclerView? = null
    
    // Mevcut alanı gösteren metin
    private var panelTitle: TextView? = null
    
    // Boş durum gösterimi
    private var emptyStateView: TextView? = null
    
    // Panel görünür mü?
    private var isVisible = false
    
    // Mevcut alan tanımlayıcısı
    private var currentFieldIdentifier: String? = null
    
    // UI güncellemeleri için handler
    private val uiHandler = Handler(Looper.getMainLooper())
    
    init {
        // Panel view'ını oluştur
        initializePanelView()
    }
    
    companion object {
        // JavaScript kodunu çalıştırmak için kullanılacak fonksiyon
        private var evaluateJsFunction: ((script: String, resultCallback: ((String) -> Unit)?) -> Unit)? = null
        
        // Öneri kullanıldığında çağrılacak callback
        private var onSuggestionUsed: ((SuggestionEntity) -> Unit)? = null
        
        // Öneri silindiğinde çağrılacak callback
        private var onSuggestionDeleted: ((SuggestionEntity) -> Unit)? = null
        
        /**
         * JavaScript çalıştırma fonksiyonunu ayarlar
         * 
         * @param function JavaScript kodunu çalıştıracak fonksiyon
         */
        fun setEvaluateJsFunction(function: (script: String, resultCallback: ((String) -> Unit)?) -> Unit) {
            evaluateJsFunction = function
        }
        
        /**
         * Öneri kullanıldığında çağrılacak callback'i ayarlar
         * 
         * @param callback Callback fonksiyonu
         */
        fun setSuggestionUsedCallback(callback: (SuggestionEntity) -> Unit) {
            onSuggestionUsed = callback
        }
        
        /**
         * Öneri silindiğinde çağrılacak callback'i ayarlar
         * 
         * @param callback Callback fonksiyonu
         */
        fun setSuggestionDeletedCallback(callback: (SuggestionEntity) -> Unit) {
            onSuggestionDeleted = callback
        }
    }
    
    /**
     * Panel view'ını oluşturur ve hazırlar
     */
    private fun initializePanelView() {
        // LayoutInflater ile panel view'ını oluştur
        val inflater = LayoutInflater.from(context)
        panelView = inflater.inflate(R.layout.suggestion_panel_layout, rootView, false)
        
        // View elemanlarını bul
        recyclerView = panelView?.findViewById(R.id.suggestionRecyclerView)
        panelTitle = panelView?.findViewById(R.id.tvPanelTitle)
        emptyStateView = panelView?.findViewById(R.id.tvEmptyState)
        
        // RecyclerView'i ayarla
        recyclerView?.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        
        // Adapter'ı oluştur
        suggestionAdapter = SuggestionAdapter(
            context = context,
            onSuggestionClick = { suggestion -> onSuggestionClicked(suggestion) },
            onSuggestionDelete = { suggestion -> onSuggestionDeleted(suggestion) }
        )
        
        // Adapter'ı RecyclerView'a ata
        recyclerView?.adapter = suggestionAdapter
        
        // Kapat butonunu ayarla
        panelView?.findViewById<ImageButton>(R.id.btnCancelPanel)?.setOnClickListener {
            hidePanel()
        }
    }
    
    /**
     * Paneli gösterir ve verileri yükler
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param suggestions Öneriler listesi
     */
    fun showPanel(fieldIdentifier: String, suggestions: List<SuggestionEntity>) {
        currentFieldIdentifier = fieldIdentifier
        
        // Alan başlığını güncelle
        val displayName = if (fieldIdentifier.length > 20) {
            fieldIdentifier.substring(0, 17) + "..."
        } else {
            fieldIdentifier
        }
        panelTitle?.text = "Öneriler: $displayName"
        
        // Panel zaten görünür mü kontrol et
        if (isVisible) {
            // Sadece verileri güncelle
            updateSuggestions(suggestions)
            return
        }
        
        // Önce mevcut liste öğelerini güncelle
        updateSuggestions(suggestions)
        
        // Paneli rootView'a ekle (henüz eklenmemişse)
        if (panelView?.parent == null) {
            updatePanelPosition()
            
            rootView.addView(panelView)
            
            // Yukarı kayma animasyonu
            panelView?.startAnimation(
                AnimationUtils.loadAnimation(context, R.anim.slide_up)
            )
        }
        
        isVisible = true
        
        Timber.d("Panel gösterildi, öneri sayısı: ${suggestions.size}")
    }
    
    /**
     * Panel pozisyonunu klavyeye göre günceller
     */
    private fun updatePanelPosition() {
        panelView?.let { view ->
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            params?.let {
                // Doğru klavye marjinini al
                val keyboardMargin = KeyboardUtils.getSuggestionPanelMargin()
                if (it.bottomMargin != keyboardMargin) {
                    it.bottomMargin = keyboardMargin
                    uiHandler.post {
                        view.layoutParams = it
                    }
                    Timber.d("Panel pozisyonu güncellendi, marjin: $keyboardMargin")
                }
            }
        }
    }
    
    /**
     * Klavye yüksekliği değiştiğinde panelin pozisyonunu günceller
     * 
     * @param keyboardHeight Yeni klavye yüksekliği
     */
    fun updateKeyboardHeight(keyboardHeight: Int) {
        if (isVisible && panelView?.parent != null) {
            panelView?.let { view ->
                val params = view.layoutParams as? ViewGroup.MarginLayoutParams
                params?.let {
                    if (it.bottomMargin != keyboardHeight && keyboardHeight > 0) {
                        it.bottomMargin = keyboardHeight
                        uiHandler.post {
                            view.layoutParams = it
                        }
                        Timber.d("Klavye yüksekliği değişikliği sonrası panel pozisyonu güncellendi: $keyboardHeight")
                    }
                }
            }
        }
    }
    
    /**
     * Paneli gizler
     */
    fun hidePanel() {
        if (!isVisible || panelView == null) {
            return
        }
        
        // Aşağı kayma animasyonu
        val slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_down)
        
        slideDown.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                // Animasyon tamamlandığında paneli kaldır
                rootView.removeView(panelView)
                isVisible = false
            }
            
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        
        panelView?.startAnimation(slideDown)
        
        Timber.d("Panel gizlendi")
    }
    
    /**
     * Öneri listesini günceller
     * 
     * @param suggestions Yeni öneriler listesi
     */
    private fun updateSuggestions(suggestions: List<SuggestionEntity>) {
        // Boş durum görünümünü kontrol et
        if (suggestions.isEmpty()) {
            recyclerView?.visibility = View.GONE
            emptyStateView?.visibility = View.VISIBLE
            emptyStateView?.text = "Bu alan için öneri bulunamadı"
        } else {
            recyclerView?.visibility = View.VISIBLE
            emptyStateView?.visibility = View.GONE
            
            // Günlüğe öneri bilgilerini yaz
            suggestions.forEach { suggestion ->
                Timber.d("Öneri gösteriliyor: '${suggestion.value}', ID: ${suggestion.id}")
            }
            
            // Adapter'ı güncelle
            suggestionAdapter?.updateSuggestions(suggestions)
            
            // RecyclerView'ı başa sar
            recyclerView?.scrollToPosition(0)
        }
    }
    
    /**
     * Öneri tıklaması işleyicisi
     * 
     * @param suggestion Tıklanan öneri
     */
    private fun onSuggestionClicked(suggestion: SuggestionEntity) {
        // JavaScript ile input değerini ayarla
        val fieldIdentifier = currentFieldIdentifier
        
        if (fieldIdentifier != null) {
            val script = com.asforce.asforcebrowser.suggestion.js.JsInjectionScript.getSetInputValueScript(
                fieldIdentifier = fieldIdentifier,
                value = suggestion.value
            )
            
            // JavaScript'i çalıştır
            evaluateJsFunction?.invoke(script) { result ->
                Timber.d("Öneri değeri ayarlandı, sonuç: $result")
                
                // İşlem başarılıysa kullanım sayacını artır
                if (result == "true") {
                    onSuggestionUsed?.invoke(suggestion)
                }
            }
        }
    }
    
    /**
     * Öneri silme işleyicisi
     * 
     * @param suggestion Silinecek öneri
     */
    private fun onSuggestionDeleted(suggestion: SuggestionEntity) {
        // Önerileri görünümden kaldır
        suggestionAdapter?.removeSuggestion(suggestion)
        
        // Silme callback'ini çağır
        onSuggestionDeleted?.invoke(suggestion)
        
        // Eğer tüm öneriler silindiyse boş durum görünümünü göster
        if (suggestionAdapter?.itemCount == 0) {
            recyclerView?.visibility = View.GONE
            emptyStateView?.visibility = View.VISIBLE
        }
    }
}