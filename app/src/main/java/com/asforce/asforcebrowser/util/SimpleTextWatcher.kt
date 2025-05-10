package com.asforce.asforcebrowser.util

import android.text.Editable
import android.text.TextWatcher

/**
 * TextWatcher arayüzünün basitleştirilmiş sürümü
 * Sadece afterTextChanged metodunu override etmeyi gerektirir
 */
abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Varsayılan implementasyon - boş
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Varsayılan implementasyon - boş
    }

    override abstract fun afterTextChanged(s: Editable)
}