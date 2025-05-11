package com.asforce.asforcebrowser.ui.search

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import com.asforce.asforcebrowser.R

/**
 * Arama alanlarını yöneten dialog sınıfı
 * Dialog içinde dinamik EditText alanları oluşturur ve yönetir
 */
class SearchDialog(private val context: Context) {
    
    private var dialog: Dialog? = null
    private lateinit var searchFieldsLayout: LinearLayout
    private var searchFieldCount = 1
    private var searchTexts = mutableListOf<String>()
    
    // Kaydet ve kapat butonuna basıldığında çağrılacak callback
    var onSaveAndClose: ((List<String>) -> Unit)? = null
    
    /**
     * Dialog'u oluştur ve göster
     */
    fun show() {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_search_fields)
        
        // Dialog'un arka plan stilini ayarla
        dialog.window?.setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame)
        
        // View referanslarını al
        searchFieldsLayout = dialog.findViewById(R.id.searchFieldsLayout)
        val addFieldButton = dialog.findViewById<Button>(R.id.addField)
        val clearAllButton = dialog.findViewById<Button>(R.id.clearAllFields)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelDialog)
        val saveButton = dialog.findViewById<Button>(R.id.saveAndClose)
        
        // Buton dinleyicilerini ayarla
        addFieldButton.setOnClickListener {
            addNewSearchField()
        }
        
        clearAllButton.setOnClickListener {
            clearAllFields()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            saveFields()
            dialog.dismiss()
        }
        
        // İlk alanın silme butonunu ayarla
        val removeButton = dialog.findViewById<ImageButton>(R.id.removeField1)
        removeButton.visibility = View.GONE
        
        // Mevcut arama metinlerini yükle
        loadExistingFields()
        
        this.dialog = dialog
        dialog.show()
    }
    
    /**
     * Mevcut arama metinlerini dialog'a yükle
     */
    private fun loadExistingFields() {
        // Mevcut arama metinleri varsa onları yükle
        for (i in 0 until searchTexts.size) {
            if (i == 0) {
                // İlk alan zaten var, sadece metni ayarla
                val firstField = dialog?.findViewById<EditText>(R.id.searchField1)
                firstField?.setText(searchTexts[i])
            } else {
                // Yeni alan ekle
                addNewSearchField()
                val layouts = searchFieldsLayout.childCount
                if (layouts > i) {
                    val layout = searchFieldsLayout.getChildAt(i) as LinearLayout
                    val editText = layout.getChildAt(0) as EditText
                    editText.setText(searchTexts[i])
                }
            }
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
                48
            ).apply {
                bottomMargin = 8
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
            setPadding(12, 12, 12, 12)
            textSize = 14f
            tag = "searchField$searchFieldCount"
        }
        
        // Silme butonu oluştur
        val removeButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                marginStart = 8
            }
            setImageResource(android.R.drawable.ic_delete)
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
            dialog?.findViewById<ImageButton>(R.id.removeField1)?.visibility = View.VISIBLE
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
            dialog?.findViewById<ImageButton>(R.id.removeField1)?.visibility = View.GONE
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
     * Tüm alanları temizle
     */
    private fun clearAllFields() {
        // İlk alanı boşalt
        dialog?.findViewById<EditText>(R.id.searchField1)?.setText("")
        
        // Diğer tüm alanları kaldır
        val childCount = searchFieldsLayout.childCount
        for (i in childCount - 1 downTo 1) {
            searchFieldsLayout.removeViewAt(i)
        }
        
        searchFieldCount = 1
        dialog?.findViewById<ImageButton>(R.id.removeField1)?.visibility = View.GONE
    }
    
    /**
     * Alanları kaydet ve callback'i çağır
     */
    private fun saveFields() {
        val texts = mutableListOf<String>()
        
        // Tüm alanlardan metinleri topla
        for (i in 0 until searchFieldsLayout.childCount) {
            val container = searchFieldsLayout.getChildAt(i) as LinearLayout
            val editText = container.getChildAt(0) as EditText
            val text = editText.text.toString().trim()
            
            if (text.isNotEmpty()) {
                texts.add(text)
            }
        }
        
        // Metinleri kaydet
        searchTexts.clear()
        searchTexts.addAll(texts)
        
        // Callback'i çağır
        onSaveAndClose?.invoke(texts)
        
        if (texts.isEmpty()) {
            Toast.makeText(context, "Hiç metin girilmedi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "${texts.size} arama metni kaydedildi", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Dialog'u kapat
     */
    fun dismiss() {
        dialog?.dismiss()
    }
    
    /**
     * Mevcut arama metinlerini ayarla
     */
    fun setSearchTexts(texts: List<String>) {
        searchTexts.clear()
        searchTexts.addAll(texts)
    }
    
    /**
     * Mevcut arama metinlerini al
     */
    fun getSearchTexts(): List<String> {
        return searchTexts.toList()
    }
}
