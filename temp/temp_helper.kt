// İyileştirilmiş vibrator çağrısı:

// Titreşim feedback'i - Güvenli çağırım
try {
    vibrator?.vibrate(
        VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
    )
} catch (e: SecurityException) {
    // Titreşim izni yoksa sessizce devam et
} catch (e: Exception) {
    // Diğer hatalar da sessizce devam et
}
