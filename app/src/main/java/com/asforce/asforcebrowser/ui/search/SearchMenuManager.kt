package com.asforce.asforcebrowser.ui.search

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import com.asforce.asforcebrowser.R

/**
 * Arama menüsünü yöneten sınıf
 * Sekmelerin altında görünen arama menüsünü kontrol eder
 * 
 * Referanslar:
 * - Android View System
 * - Android LayoutInflater
 */
class SearchMenuManager(
    private val context: Context,
    private val container: ViewGroup
) {
    private lateinit var menuLayout: View
    private lateinit var searchFieldsLayout: LinearLayout
    private lateinit var searchResultStatus: TextView
    private lateinit var toggleButton: ImageButton
    
    private var searchFieldCount = 1
    private var isMenuVisible = false
    
    // Arama işlemini gerçekleştirecek callback
    var onSearchClick: ((List<String>) -> Unit)? = null
    
    init {
        setupMenu()
    }
    
    /**
     * Menüyü başlangıçta oluştur ve bağla
     */
    private fun setupMenu() {
        // Layout'u inflate et
        val inflater = LayoutInflater.from(context)
        menuLayout = inflater.inflate(R.layout.search_menu_layout, container, false)
        
        // Container'a ekle
        container.addView(menuLayout, 0) // En üste ekle
        
        // View referanslarını al
        searchFieldsLayout = menuLayout.findViewById(R.id.searchFieldsLayout)
        searchResultStatus = menuLayout.findViewById(R.id.searchResultStatus)
        toggleButton = menuLayout.findViewById(R.id.toggleMenuButton)
        
        // Butonları ayarla
        setupButtons()
        
        // İlk arama alanının silme butonunu ayarla
        val removeButton = menuLayout.findViewById<ImageButton>(R.id.removeField1)
        removeButton.visibility = View.GONE // İlk alan silinemez
    }
    
    /**
     * Menü butonlarını ayarla
     */
    private fun setupButtons() {
        // Arama alanı ekleme butonu
        menuLayout.findViewById<ImageButton>(R.id.addSearchFieldButton).setOnClickListener {
            addNewSearchField()
        }
        
        // Arama butonu
        menuLayout.findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            performSearch()
        }
        
        // Menü görünürlük butonu
        toggleButton.setOnClickListener {
            toggleMenuVisibility()
        }
    }
    
    /**
     * Yeni arama alanı ekle
     */
    private fun addNewSearchField() {
        searchFieldCount++
        
        // Yeni alan container'ı oluştur
        val fieldContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            ).apply {
                bottomMargin = 4
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        // EditText oluştur
        val editText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            hint = "Aranacak metin $searchFieldCount"
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 16, 16, 16)
            textSize = 14f
            tag = "searchField$searchFieldCount"
        }
        
        // Silme butonu oluştur - Basit background kullan
        val removeButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size),
                context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            )
            setImageResource(android.R.drawable.ic_delete)
            
            // Basit seffaf arkplan kullan
            setBackgroundColor(Color.TRANSPARENT)
            
            imageTintList = context.getColorStateList(android.R.color.holo_red_dark)
            contentDescription = "Bu alanı kaldır"
            
            setOnClickListener {
                removeSearchField(fieldContainer)
            }
        }
        
        // View'leri container'a ekle
        fieldContainer.addView(editText)
        fieldContainer.addView(removeButton)
        
        // Ana layout'a ekle
        searchFieldsLayout.addView(fieldContainer)
        
        // İlk alanın silme butonunu göster (2. alan eklendikten sonra)
        if (searchFieldCount == 2) {
            menuLayout.findViewById<ImageButton>(R.id.removeField1).visibility = View.VISIBLE
        }
    }
    
    /**
     * Arama alanını kaldır
     */
    private fun removeSearchField(fieldContainer: View) {
        searchFieldsLayout.removeView(fieldContainer)
        searchFieldCount--
        
        // Eğer sadece bir alan kaldıysa silme butonunu gizle
        if (searchFieldCount == 1) {
            menuLayout.findViewById<ImageButton>(R.id.removeField1).visibility = View.GONE
        }
        
        // Alan numaralarını yeniden düzenle
        updateFieldHints()
    }
    
    /**
     * Kalan alanların hint'lerini güncelle
     */
    private fun updateFieldHints() {
        var count = 1
        for (i in 0 until searchFieldsLayout.childCount) {
            val container = searchFieldsLayout.getChildAt(i) as LinearLayout
            val editText = container.getChildAt(0) as EditText
            editText.hint = "Aranacak metin $count"
            editText.tag = "searchField$count"
            count++
        }
    }
    
    /**
     * Arama işlemini gerçekleştir
     */
    private fun performSearch() {
        val searchTexts = mutableListOf<String>()
        
        // Tüm arama alanlarından metinleri topla
        for (i in 0 until searchFieldsLayout.childCount) {
            val container = searchFieldsLayout.getChildAt(i) as LinearLayout
            val editText = container.getChildAt(0) as EditText
            val text = editText.text.toString().trim()
            
            if (text.isNotEmpty()) {
                searchTexts.add(text)
            }
        }
        
        // Boş değilse arama yap
        if (searchTexts.isNotEmpty()) {
            showStatus("Aranıyor...")
            onSearchClick?.invoke(searchTexts)
        } else {
            Toast.makeText(context, "Lütfen aranacak metinleri girin", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Menüyü göster/gizle
     */
    private fun toggleMenuVisibility() {
        isMenuVisible = !isMenuVisible
        
        if (isMenuVisible) {
            showMenu()
        } else {
            hideMenu()
        }
    }
    
    /**
     * Menüyü göster
     */
    fun showMenu() {
        isMenuVisible = true
        menuLayout.visibility = View.VISIBLE
        toggleButton.setImageResource(android.R.drawable.arrow_up_float)
        
        // Sekmelerin altında yer açması için padding ekle
        container.setPadding(
            container.paddingLeft,
            container.paddingTop,
            container.paddingRight,
            container.paddingBottom
        )
    }
    
    /**
     * Menüyü gizle
     */
    fun hideMenu() {
        isMenuVisible = false
        menuLayout.visibility = View.GONE
        toggleButton.setImageResource(android.R.drawable.arrow_down_float)
    }
    
    /**
     * Durum mesajını göster
     */
    fun showStatus(message: String) {
        searchResultStatus.text = message
        searchResultStatus.visibility = View.VISIBLE
    }
    
    /**
     * Durum mesajını temizle
     */
    fun clearStatus() {
        searchResultStatus.visibility = View.GONE
    }
    
    /**
     * Menünün görünürlük durumunu döndür
     */
    fun isVisible(): Boolean = isMenuVisible
}