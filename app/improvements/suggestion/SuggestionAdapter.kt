package com.asforce.asforcebrowser.suggestion.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcebrowser.R
import com.asforce.asforcebrowser.databinding.SuggestionItemLayoutBinding
import com.asforce.asforcebrowser.suggestion.data.model.SuggestionEntity

/**
 * Öneri kartları için RecyclerView adapter'ı
 * 
 * ListAdapter tabanlı olarak DiffUtil kullanarak verimli güncellemeler yapılır.
 */
class SuggestionAdapter(
    private val onSuggestionClicked: (SuggestionEntity) -> Unit,
    private val onDeleteClicked: (SuggestionEntity) -> Unit
) : ListAdapter<SuggestionEntity, SuggestionAdapter.SuggestionViewHolder>(SUGGESTION_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = SuggestionItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val currentItem = getItem(position)
        if (currentItem != null) {
            holder.bind(currentItem)
        }
    }

    inner class SuggestionViewHolder(
        private val binding: SuggestionItemLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Kart tıklama işlemi - öneriyi seç
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val suggestion = getItem(position)
                    onSuggestionClicked(suggestion)
                }
            }
            
            // Silme butonu tıklama işlemi
            binding.btnDeleteSuggestion.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val suggestion = getItem(position)
                    onDeleteClicked(suggestion)
                }
            }
        }

        fun bind(suggestion: SuggestionEntity) {
            binding.apply {
                tvSuggestionText.text = suggestion.value
                
                // Önerinin kaynak tipine göre görsel düzenlemeler yapılabilir
                when (suggestion.source) {
                    "USER_INPUT" -> {
                        // Standart görünüm
                    }
                    "PREFILLED" -> {
                        // Örneğin otomatik doldurulan öneriler için farklı bir görünüm
                    }
                }
            }
        }
    }

    companion object {
        /**
         * DiffUtil için Comparator - verimli liste güncellemeleri sağlar
         */
        private val SUGGESTION_COMPARATOR = object : DiffUtil.ItemCallback<SuggestionEntity>() {
            override fun areItemsTheSame(oldItem: SuggestionEntity, newItem: SuggestionEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SuggestionEntity, newItem: SuggestionEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
