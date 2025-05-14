package com.asforce.asforcebrowser.suggestion.ui

import android.content.Context
import android.graphics.Color
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
    
    // Arka plan overlay (dışarı tıklamayı algılamak için)
    private var overlayView: View? = null
    
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
        
        // Tüm öneriler silindiğinde çağrılacak callback
        private var onDeleteAllSuggestions: ((String) -> Unit)? = null
        
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
        
        /**
         * Tüm öneriler silindiğinde çağrılacak callback'i ayarlar
         * 
         * @param callback Callback fonksiyonu
         */
        fun setDeleteAllSuggestionsCallback(callback: (String) -> Unit) {
            onDeleteAllSuggestions = callback
        }
    }
    
    /**
     * Panel view'ını oluşturur ve hazırlar
     */
    private fun initializePanelView() {
        // LayoutInflater ile panel view'ını oluştur
        val inflater = LayoutInflater.from(context)
        panelView = inflater.inflate(R.layout.suggestion_panel_layout, rootView, false)
        
        // Overlay view (dışarı tıklamayı yakalamak için)
        overlayView = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Overlay'ı tamamen şeffaf yap
            setBackgroundColor(Color.TRANSPARENT)
            
            // Tıklanmayı yakalamak için
            setOnClickListener {
                Timber.d("Öneri paneli dışına tıklandı, panel ve klavye kapatılıyor")
                
                // Hem paneli hem de klavyeyi kapat
                hidePanel(true)
                
                // Aktif inputu bul ve odağını kaldır
                val currentFocus = (activity as? FragmentActivity)?.currentFocus
                if (currentFocus != null) {
                    // Aktif alan varsa klavyeyi gizle
                    KeyboardUtils.hideKeyboard(context, currentFocus)
                } else {
                    // Aktif alan bulunamadıysa, rootView üzerinden klavyeyi gizlemeyi dene
                    try {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(rootView.windowToken, 0)
                    } catch (e: Exception) {
                        Timber.e("Klavye gizleme hatası: ${e.message}")
                    }
                }
            }
        }
        
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
            onSuggestionDelete = { suggestion -> onSuggestionDeleted(suggestion) },
            onDeleteAllForField = { fieldIdentifier -> 
                // Tüm öneriler siliniyor
                Timber.d("'$fieldIdentifier' alanı için tüm öneriler siliniyor")
                
                // Tüm önerileri silme işlemi için onDeleteAllSuggestions'ı çağır
                onDeleteAllSuggestions?.invoke(fieldIdentifier)
                
                // UI'ı güncelle, adaptördeki tüm öğeleri temizle
                suggestionAdapter?.removeAllSuggestions()
                
                // Boş durum mesajını göster
                recyclerView?.visibility = View.GONE
                emptyStateView?.visibility = View.VISIBLE
                emptyStateView?.text = "Tüm öneriler silindi"
            }
        )
        
        // Adapter'ı RecyclerView'a ata
        recyclerView?.adapter = suggestionAdapter
        
        // Kapatma butonunu gizle
        panelView?.findViewById<ImageButton>(R.id.btnCancelPanel)?.visibility = View.GONE
    }
    
    /**
     * Paneli gösterir ve verileri yükler
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param suggestions Öneriler listesi
     */
    fun showPanel(fieldIdentifier: String, suggestions: List<SuggestionEntity>) {
        currentFieldIdentifier = fieldIdentifier
        
        // Alan başlığını gizle
        panelTitle?.visibility = View.GONE
        
        // Panel zaten görünür mü kontrol et
        if (isVisible) {
            // Sadece verileri güncelle
            updateSuggestions(suggestions)
            return
        }
        
        // Önce mevcut liste öğelerini güncelle
        updateSuggestions(suggestions)
        
        // Önce overlay'ı ekle (tam ekrana dışarı tıklamalar için)
        if (overlayView?.parent == null) {
            rootView.addView(overlayView)
        }
        
        // Sonra paneli rootView'a ekle (henüz eklenmemişse)
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
     * Panel görünür mü?
     * 
     * @return Panel görünür mü
     */
    fun isVisible(): Boolean {
        return isVisible
    }
    
    /**
     * Paneli gizler
     * 
     * @param forceHide Animasyon olmadan zorla gizle
     */
    fun hidePanel(forceHide: Boolean = false) {
        // İşlem öncesi erken çıkış kontrolü
        if (!isVisible) {
            Timber.d("Panel zaten görünmüyor, gizleme işlemi atlanıyor")
            return
        }
        
        if (panelView == null) {
            Timber.d("Panel view null, gizleme işlemi atlanıyor")
            isVisible = false
            return
        }
        
        try {
            // Her durumda overlay'ı hemen kaldır
            if (overlayView?.parent != null) {
                rootView.removeView(overlayView)
            }
            
            if (forceHide) {
                // Animasyon olmadan hemen kaldır
                if (panelView?.parent != null) {
                    rootView.removeView(panelView)
                }
                isVisible = false
                Timber.d("Panel zorla gizlendi")
                
                // Gecikmeli olarak bir kez daha kontrol et (bazı cihazlarda parent'tan ayrılmayabilir)
                uiHandler.postDelayed({
                    if (panelView?.parent != null) {
                        try {
                            (panelView?.parent as? ViewGroup)?.removeView(panelView)
                            Timber.d("Panel ikincil kontrol ile kaldırıldı")
                        } catch (e: Exception) {
                            Timber.e("Panel ikincil kaldırma hatası: ${e.message}")
                        }
                    }
                }, 100)
                
                return
            }
            
            // Normal animasyonlu kapatma
            val slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_down)
            
            slideDown.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Animasyon tamamlandığında paneli kaldır
                    try {
                        // Panel View'ı kaldır
                        if (panelView?.parent != null) {
                            rootView.removeView(panelView)
                        }
                        
                        // Overlay'ı kaldır (eğer hala duruyorsa)
                        if (overlayView?.parent != null) {
                            rootView.removeView(overlayView)
                        }
                        
                        isVisible = false
                    } catch (e: Exception) {
                        Timber.e("Panel animasyon sonrası kaldırma hatası: ${e.message}")
                        // Hata durumunda yine de gizli olarak işaretle
                        isVisible = false
                    }
                }
                
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            
            panelView?.startAnimation(slideDown)
            
            Timber.d("Panel animasyon ile gizlendi")
        } catch (e: Exception) {
            Timber.e("Panel gizleme genel hatası: ${e.message}")
            // Hata durumunda yine de gizli olarak işaretle ve view'ı kaldırmaya çalış
            isVisible = false
            try {
                // Paneli kaldır
                if (panelView?.parent != null) {
                    rootView.removeView(panelView)
                }
                
                // Overlay'ı kaldır
                if (overlayView?.parent != null) {
                    rootView.removeView(overlayView)
                }
            } catch (e2: Exception) {
                Timber.e("Panel hata sonrası kaldırma girişimi başarısız: ${e2.message}")
            }
        }
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