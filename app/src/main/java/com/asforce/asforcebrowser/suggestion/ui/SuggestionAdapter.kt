package com.asforce.asforcebrowser.suggestion.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity
import com.google.android.material.card.MaterialCardView

/**
 * Öneri listesi için RecyclerView adapter
 *
 * @param context Context
 * @param onSuggestionClick Öneri tıklandığında çağrılacak lambda
 * @param onSuggestionDelete Öneri silindiğinde çağrılacak lambda
 * @param onDeleteAllForField Bir alan için tüm önerileri silme lambda
 */
class SuggestionAdapter(
    private val context: Context,
    private val onSuggestionClick: (SuggestionEntity) -> Unit,
    private val onSuggestionDelete: (SuggestionEntity) -> Unit,
    private val onDeleteAllForField: ((String) -> Unit)? = null
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    // Öneri listesi
    private var suggestions = listOf<SuggestionEntity>()

    /**
     * ViewHolder sınıfı
     */
    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.suggestionItemCard)
        private val textView: TextView = itemView.findViewById(R.id.tvSuggestionText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteSuggestion)

        /**
         * Öneri verisiyle view'ı bağlar
         */
        fun bind(suggestion: SuggestionEntity) {
            // Metin değerini ayarla - değer boş değilse görünür yap
            val textValue = suggestion.value.trim()
            if (textValue.isNotEmpty()) {
                textView.text = textValue
                textView.visibility = View.VISIBLE
            } else {
                textView.text = "Değer"
                textView.visibility = View.VISIBLE
            }
            
            // Metin stilini sağlamlaştır
            textView.setTextColor(Color.BLACK)
            textView.textSize = 15f
            
            // Arka plan ve çerçeve ayarlarını güncelle
            cardView.strokeWidth = 1
            cardView.strokeColor = context.getColor(R.color.colorPrimary)

            // Tıklama olaylarını ayarla
            cardView.setOnClickListener {
                onSuggestionClick(suggestion)
            }

            // Silme butonunu ayarla
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                // Silme işlemi başlatıldı
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Silme onayı sor
                    showDeleteConfirmationDialog(suggestion)
                }
            }
            
            // Uzun basma olayını ayarla - tüm önerileri silme seçeneği
            cardView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showDeleteOptionsDialog(suggestion)
                }
                true
            }
        }
    }
    
    /**
     * Silme onayı diyaloğunu göster
     */
    private fun showDeleteConfirmationDialog(suggestion: SuggestionEntity) {
        AlertDialog.Builder(context)
            .setTitle("Öneri Silme")
            .setMessage("\"${suggestion.value}\" önerisini silmek istediğinizden emin misiniz?")
            .setPositiveButton("Sil") { _, _ ->
                // Silme işlemini gerçekleştir
                onSuggestionDelete(suggestion)
                Toast.makeText(context, "Öneri silindi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .create()
            .show()
    }
    
    /**
     * Silme seçenekleri diyaloğunu göster (tek öneri veya tüm öneriler)
     */
    private fun showDeleteOptionsDialog(suggestion: SuggestionEntity) {
        val options = arrayOf("Bu öneriyi sil", "Bu alan için tüm önerileri sil")
        
        AlertDialog.Builder(context)
            .setTitle("Öneri Silme Seçenekleri")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Tek öneriyi sil
                        showDeleteConfirmationDialog(suggestion)
                    }
                    1 -> {
                        // Alan için tüm önerileri silme onayı
                        AlertDialog.Builder(context)
                            .setTitle("Tüm Önerileri Sil")
                            .setMessage("\"${suggestion.fieldIdentifier}\" alanı için TÜM önerileri silmek istediğinizden emin misiniz?")
                            .setPositiveButton("Tümünü Sil") { _, _ ->
                                onDeleteAllForField?.invoke(suggestion.fieldIdentifier)
                                Toast.makeText(context, "Tüm öneriler silindi", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("İptal", null)
                            .create()
                            .show()
                    }
                }
            }
            .create()
            .show()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.suggestion_item_layout, parent, false
        )
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    /**
     * Önerileri günceller
     */
    fun updateSuggestions(newSuggestions: List<SuggestionEntity>) {
        // Boş olmayan önerileri filtrele
        val validSuggestions = newSuggestions.filter { 
            it.value.trim().isNotEmpty() && it.value.trim() != "." 
        }
        
        suggestions = validSuggestions
        notifyDataSetChanged()
    }
    
    /**
     * Öneriyi listeden kaldır
     */
    fun removeSuggestion(suggestion: SuggestionEntity) {
        val position = suggestions.indexOf(suggestion)
        if (position >= 0) {
            val newList = suggestions.toMutableList()
            newList.removeAt(position)
            suggestions = newList
            notifyItemRemoved(position)
        }
    }
    
    /**
     * Tüm önerileri listeden kaldır
     */
    fun removeAllSuggestions() {
        val itemCount = suggestions.size
        suggestions = emptyList()
        notifyItemRangeRemoved(0, itemCount)
    }
}