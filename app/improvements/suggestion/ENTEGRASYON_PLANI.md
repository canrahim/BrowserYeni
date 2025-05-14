# WebView Öneri Sistemi Entegrasyon Planı

## Özellikler ve Yapı

AsforceBrowser projenize HTML input alanları için güçlü bir öneri sistemi eklemek için tüm gerekli dosyaları ve entegrasyon adımlarını hazırladım. Eklenen bu öneri sistemi şu özellikleri sağlıyor:

1. **Akıllı Öneri Paneli**: Klavye açıldığında, input alanları için özelleştirilmiş öneri paneli gösterilir
2. **Veri Kalıcılığı**: Room Database ile önerilerin saklanması ve yönetilmesi
3. **Kullanım İstatistikleri**: Sık kullanılan ve son kullanılan önerilerin önceliklendirilmesi
4. **Silme ve İptal İşlemleri**: Kullanıcı dostu öneri yönetimi
5. **Performans Odaklı**: Asenkron işlemler ve kaynak optimizasyonu

## Entegrasyon Planı

Geliştirilen modülü AsforceBrowser projenize entegre etmek için aşağıdaki adımları izleyin:

### 1. Dosyaları Kopyalama

`improvements/suggestion` klasöründeki tüm dosyaları ana proje kaynak kodlarına taşıyın:

```bash
# Dosyaları kopyalamak için komut örneği
cp -r C:\AsforceBrowser\app\improvements\suggestion\*.kt C:\AsforceBrowser\app\src\main\java\com\asforce\asforcebrowser\suggestion\

# XML ve resource dosyalarını uygun klasörlere kopyalayın
cp C:\AsforceBrowser\app\improvements\suggestion\*.xml C:\AsforceBrowser\app\src\main\res\[uygun_klasör]\
```

### 2. Paket Yapısını Düzenleme

Aşağıdaki paket yapısını oluşturun:

```
com.asforce.asforcebrowser.suggestion/
├── data/
│   ├── local/
│   ├── model/
│   └── repository/
├── di/
├── js/
├── ui/
├── util/
└── viewmodel/
```

### 3. Dependencies Ekleme

`build.gradle.kts` dosyasında gerekli bağımlılıkların olduğundan emin olun:

```kotlin
dependencies {
    // Room için
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
}
```

### 4. WebViewFragment Entegrasyonu

`WebViewFragment.kt` dosyasına `WebViewFragmentExtension.kt` dosyasındaki kodları entegre edin:

1. Öneri yöneticisi değişkenini ekleyin
2. `initSuggestionManager()` metodunu `onViewCreated` içinde çağırın
3. `setupSuggestionSystem()` metodunu WebView kurulumunda çağırın
4. `injectSuggestionScripts()` metodunu `onPageFinished` içinde çağırın
5. `cleanupSuggestionManager()` metodunu `onDestroy` içinde çağırın

### 5. Resource Dosyalarını Ekleme

Aşağıdaki resource dosyalarını uygun klasörlere ekleyin:

- Layout: `suggestion_panel_layout.xml` ve `suggestion_item_layout.xml`
- Drawable: `suggestion_panel_background.xml`
- Values: `suggestion_colors.xml`
- Anim: `slide_up.xml` ve `slide_down.xml`

Ayrıca, `ic_close` ve `ic_delete_small` drawable kaynakları için vektör ikon ekleyin.

### 6. Projeyi Build Etme ve Test Etme

Entegrasyon tamamlandıktan sonra projeyi derleyin ve öneri sisteminin düzgün çalıştığını test edin. Temel test senaryoları:

1. Bir web formuna giderek input alanına tıklayın
2. Klavye açıldığında öneri panelinin gösterildiğini kontrol edin
3. Bir değer girin ve formu gönderin
4. Aynı formu tekrar açın ve girilen değerin önerilerde gösterildiğini kontrol edin

## Entegrasyon Notları

1. **JavaScript Enjeksiyonu**: WebView içinde HTML input alanlarını tespit etmek için JS kodu enjekte edilir.
2. **Klavye Yüksekliği**: Klavye yüksekliği tespiti farklı cihazlarda değişkenlik gösterebilir, gerekirse KeyboardUtils sınıfını güncelleyin.
3. **Room Migrations**: Veritabanı şeması güncellenirse migration stratejisi ekleyin.
4. **Gizlilik**: Şifre alanları için öneri sistemi devre dışı bırakılmıştır.
5. **Performans**: Çok sayıda öneri birikirse, eski önerileri temizlemek için bir mekanizma ekleyebilirsiniz.

## Ekstra İyileştirmeler

1. **Tema Desteği**: Karanlık/aydınlık tema desteği eklenebilir
2. **Dil Desteği**: Kullanıcı arayüzü metinleri için çoklu dil desteği eklenebilir
3. **Synchronization**: Farklı cihazlar arasında öneri verilerini senkronize etme özelliği eklenebilir
4. **Akıllı Tahmin**: Kullanıcının yazma alışkanlıklarına göre önerileri sıralama sistemi geliştirilebilir

Modül, AsforceBrowser'ın mevcut MVVM mimarisine ve Hilt DI sistemine uygun şekilde tasarlanmıştır.
