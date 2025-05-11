package com.asforce.asforcebrowser.ui.search

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// Button artık MaterialButton ile değiştirildi
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import android.widget.LinearLayout
import android.widget.Toast
import com.asforce.asforcebrowser.R
import org.json.JSONArray

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
    
    // SharedPreferences için
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "SearchDialogPrefs", 
        Context.MODE_PRIVATE
    )
    
    // SharedPreferences key'leri
    private companion object {
        const val KEY_SEARCH_TEXTS = "search_texts"
        const val KEY_SEARCH_COUNT = "search_count"
    }
    
    // Sinif örneği oluşturulduğunda SharedPreferences'dan veri yükle
    init {
        loadSearchTextsFromSharedPreferences()
    }
    
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
        val addFieldButton = dialog.findViewById<MaterialButton>(R.id.addField)
        val clearAllButton = dialog.findViewById<MaterialButton>(R.id.clearAllFields)
        val cancelButton = dialog.findViewById<MaterialButton>(R.id.cancelDialog)
        val saveButton = dialog.findViewById<MaterialButton>(R.id.saveAndClose)
        
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
        val removeButton = dialog.findViewById<MaterialButton>(R.id.removeField1)
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
                // İlk alan TextInputLayout içinde olduğundan, ona göre erişelim
                val firstLayout = searchFieldsLayout.getChildAt(0) as? LinearLayout
                if (firstLayout != null) {
                    val firstTextInputLayout = firstLayout.getChildAt(0) as? TextInputLayout
                    // TextInputLayout'un getEditText() metodunu kullan
                    val firstField = firstTextInputLayout?.editText
                    firstField?.setText(searchTexts[i])
                }
            } else {
                // Yeni alan ekle
                addNewSearchField()
                val layouts = searchFieldsLayout.childCount
                if (layouts > i) {
                    val layout = searchFieldsLayout.getChildAt(i) as LinearLayout
                    val textInputLayout = layout.getChildAt(0) as TextInputLayout
                    // TextInputLayout'un getEditText() metodunu kullan
                    val editText = textInputLayout.editText
                    editText?.setText(searchTexts[i])
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
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        // TextInputLayout oluştur
        val textInputLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            hint = "Aranacak metin $searchFieldCount"
            
            // XML'deki stilı uygula
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hintTextColor = context.getColorStateList(R.color.colorPrimary)
            boxStrokeColor = context.resources.getColor(R.color.colorPrimary)
        }
        
        // TextInputEditText oluştur
        val editText = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 16f
            tag = "searchField$searchFieldCount"
            setTextColor(context.resources.getColor(R.color.text_primary))
            setPadding(16, 16, 16, 16)
        }
        
        // EditText'i TextInputLayout'a ekle
        textInputLayout.addView(editText)
        
        // Silme butonu oluştur - MaterialButton kullanarak
        val removeButton = MaterialButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                marginStart = 8
            }
            setIcon(context.getDrawable(R.drawable.ic_delete))
            setIconTint(context.getColorStateList(android.R.color.holo_red_dark))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Bu alanı kaldır"
            
            // MaterialButton'un default padding'ini kısaltmak için
            setPadding(0, 0, 0, 0)
            
            setOnClickListener {
                removeSearchField(fieldContainer)
            }
        }
        
        // View'leri container'a ekle
        fieldContainer.addView(textInputLayout)
        fieldContainer.addView(removeButton)
        
        // Ana layout'a ekle
        searchFieldsLayout.addView(fieldContainer)
        
        // İlk alanın silme butonunu göster (2. alan eklendikten sonra)
        if (searchFieldCount == 2) {
            dialog?.findViewById<MaterialButton>(R.id.removeField1)?.visibility = View.VISIBLE
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
            dialog?.findViewById<MaterialButton>(R.id.removeField1)?.visibility = View.GONE
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
            val textInputLayout = container.getChildAt(0) as TextInputLayout
            
            // Hint'i TextInputLayout üzerinden güncelleyelim
            textInputLayout.hint = "Aranacak metin $count"
            // TextInputLayout'un getEditText() metodunu kullanarak EditText'e erişelim
            val editText = textInputLayout.editText
            editText?.tag = "searchField$count"
            count++
        }
    }
    
    /**
     * Tüm alanları temizle
     */
    private fun clearAllFields() {
        // İlk alanı boşalt
        val firstLayout = searchFieldsLayout.getChildAt(0) as? LinearLayout
        if (firstLayout != null) {
            val firstTextInputLayout = firstLayout.getChildAt(0) as? TextInputLayout
            // TextInputLayout'un getEditText() metodunu kullan
            val firstField = firstTextInputLayout?.editText
            firstField?.setText("")
        }
        
        // Diğer tüm alanları kaldır
        val childCount = searchFieldsLayout.childCount
        for (i in childCount - 1 downTo 1) {
            searchFieldsLayout.removeViewAt(i)
        }
        
        searchFieldCount = 1
        dialog?.findViewById<MaterialButton>(R.id.removeField1)?.visibility = View.GONE
    }
    
    /**
     * Alanları kaydet ve callback'i çağır
     */
    private fun saveFields() {
        val texts = mutableListOf<String>()
        
        // Tüm alanlardan metinleri topla
        for (i in 0 until searchFieldsLayout.childCount) {
            val container = searchFieldsLayout.getChildAt(i) as LinearLayout
            val textInputLayout = container.getChildAt(0) as TextInputLayout
            // TextInputLayout'un getEditText() metodunu kullan
            val editText = textInputLayout.editText
            val text = editText?.text?.toString()?.trim() ?: ""
            
            if (text.isNotEmpty()) {
                texts.add(text)
            }
        }
        
        // Metinleri kaydet
        searchTexts.clear()
        searchTexts.addAll(texts)
        
        // SharedPreferences'a kaydet
        saveSearchTextsToSharedPreferences(texts)
        
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
    
    /**
     * SharedPreferences'dan arama metinlerini yükle
     */
    private fun loadSearchTextsFromSharedPreferences() {
        try {
            val savedTexts = sharedPreferences.getString(KEY_SEARCH_TEXTS, "[]")
            val jsonArray = JSONArray(savedTexts)
            
            searchTexts.clear()
            for (i in 0 until jsonArray.length()) {
                val text = jsonArray.getString(i)
                if (text.isNotEmpty()) {
                    searchTexts.add(text)
                }
            }
            
            searchFieldCount = sharedPreferences.getInt(KEY_SEARCH_COUNT, 1)
        } catch (e: Exception) {
            // Hata durumunda varsayılan değerler
            searchTexts.clear()
            searchFieldCount = 1
        }
    }
    
    /**
     * Arama metinlerini SharedPreferences'a kaydet
     */
    private fun saveSearchTextsToSharedPreferences(texts: List<String>) {
        try {
            // Sadece boş olmayan metinleri kaydet
            val validTexts = texts.filter { it.isNotEmpty() }
            
            // Boş liste ise kaydetme
            if (validTexts.isEmpty()) {
                // Mevcut kayıtları sil
                sharedPreferences.edit()
                    .remove(KEY_SEARCH_TEXTS)
                    .remove(KEY_SEARCH_COUNT)
                    .apply()
                return
            }
            
            // JSON array'e dönüştür
            val jsonArray = JSONArray()
            validTexts.forEach { text ->
                jsonArray.put(text)
            }
            
            // SharedPreferences'a kaydet
            sharedPreferences.edit()
                .putString(KEY_SEARCH_TEXTS, jsonArray.toString())
                .putInt(KEY_SEARCH_COUNT, validTexts.size + 1)
                .apply()
        } catch (e: Exception) {
            // Hata durumunu logla
            e.printStackTrace()
        }
    }
}
