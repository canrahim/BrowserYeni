# AsforceBrowser WebView Öneri (Suggestion) Sistemi

## Genel Bakış

Bu modül, AsforceBrowser'da WebView içindeki HTML input alanları için özelleştirilmiş bir öneri sistemi sağlar. Klavye açıldığında, Android'in yerleşik öneri sisteminin üzerinde yatay, kaydırılabilir ve tamamen özelleştirilebilir bir öneri paneli sunar.

## Özellikler

- **WebView-Native Entegrasyonu**: HTML input alanlarını izler ve klavye açıldığında öneri panelini gösterir
- **Input Alan Takibi**: İsimlendirme (id/name) bazlı olarak her input alanı için ayrı öneri listesi
- **Veri Yönetimi**: Room Database ile öneri verileri saklanır ve yönetilir
- **Kullanım İstatistikleri**: En sık/son kullanılan öneriler önceliklendirilir
- **Silme ve İptal**: Her öneri kartında silme butonu ve panelde iptal butonu
- **Performans**: Asenkron data yükleme, RecyclerView recycling ve hafif veri yapıları

## Mimari Yapı

Modül, MVVM mimarisine ve modüler tasarıma uygun olarak geliştirilmiştir:

1. **Data Katmanı**
   - `SuggestionEntity`: Öneri verilerini temsil eden entity sınıfı
   - `SuggestionDao`: Room veritabanı erişimi için Data Access Object
   - `SuggestionDatabase`: Room veritabanı sınıfı
   - `SuggestionRepository`: Veri erişim katmanı

2. **UI Katmanı**
   - `SuggestionPanel`: Klavye üstünde gösterilen panel
   - `SuggestionAdapter`: RecyclerView için adapter

3. **ViewModel**
   - `SuggestionViewModel`: Öneri verilerini yöneten ViewModel

4. **WebView Entegrasyonu**
   - `WebViewJsInterface`: JavaScript-Kotlin iletişimi için köprü
   - `JsInjectionScript`: WebView'e enjekte edilen JavaScript kodları
   - `SuggestionManager`: WebView entegrasyonunu yöneten sınıf

5. **Yardımcı Sınıflar**
   - `KeyboardUtils`: Klavye kontrolü için yardımcı sınıf

## Kurulum ve Kullanım

### 1. Modül Dosyalarını Ekleme

`improvements/suggestion` klasöründeki tüm dosyaları uygun paket yapısıyla birlikte projeye dahil edin:

```
com.asforce.asforcebrowser.suggestion/
├── data/
│   ├── local/
│   │   ├── SuggestionDao.kt
│   │   └── SuggestionDatabase.kt
│   ├── model/
│   │   └── SuggestionEntity.kt
│   └── repository/
│       └── SuggestionRepository.kt
├── di/
│   └── SuggestionModule.kt
├── js/
│   ├── JsInjectionScript.kt
│   └── WebViewJsInterface.kt
├── ui/
│   ├── SuggestionAdapter.kt
│   └── SuggestionPanel.kt
├── util/
│   └── KeyboardUtils.kt
└── viewmodel/
    └── SuggestionViewModel.kt
```

### 2. XML Dosyalarını Ekleme

- `res/layout/suggestion_panel_layout.xml`
- `res/layout/suggestion_item_layout.xml`
- `res/drawable/suggestion_panel_background.xml`
- `res/values/suggestion_colors.xml`
- `res/anim/slide_up.xml`
- `res/anim/slide_down.xml`

### 3. WebViewFragment Entegrasyonu

`WebViewFragment.kt` dosyasında aşağıdaki değişiklikleri yapın:

```kotlin
// WebViewFragment sınıfı içinde tanımla
private var suggestionManager: SuggestionManager? = null

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    // Diğer kodlar...
    
    // Öneri yöneticisini başlat
    initSuggestionManager()
}

private fun setupWebView() {
    binding.webView.apply {
        // Diğer WebView ayarları...
        
        // Öneri sistemini ayarla
        setupSuggestionSystem(this)
    }
}

// WebViewClient'ın onPageFinished metoduna ekle
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    
    // Diğer kodlar...
    
    // JavaScript enjekte et
    if (view != null) {
        injectSuggestionScripts(view)
    }
}

override fun onDestroy() {
    super.onDestroy()
    
    // Diğer temizleme kodları...
    
    // Öneri yöneticisini temizle
    cleanupSuggestionManager()
}

// WebViewFragmentExtension.kt dosyasından diğer metotları ekleyin
```

### 4. Hilt Modülünü Kaydetme

`AppModule.kt` dosyasında SuggestionModule'ü dahil edin:

```kotlin
@InstallIn(SingletonComponent::class)
@Module(
    includes = [
        SuggestionModule::class
        // Diğer modüller...
    ]
)
object AppModule {
    // Diğer provider'lar...
}
```

## Kullanım

Sistem kurulduktan sonra otomatik olarak çalışır:

1. Kullanıcı bir WebView içindeki input alanına tıklar
2. Klavye açılır ve aynı anda öneri paneli gösterilir
3. Kullanıcı daha önce girdiği değerlerden birini seçebilir
4. Önerilerden birini silmek için X butonuna tıklayabilir
5. Paneli kapatmak için İptal butonuna tıklayabilir
6. Form gönderildiğinde yeni değerler otomatik olarak kaydedilir

## Performans İpuçları

1. **Önbellek Kullanımı**:
   - Öneri verileri Room veritabanında saklanır
   - ViewModel ve Repository seviyesinde önbellek mekanizmaları vardır

2. **Asenkron İşlemler**:
   - Tüm veritabanı işlemleri arka planda Kotlin Coroutines ile yapılır
   - UI güncellemeleri Main thread'de Flow kullanarak yapılır

3. **Kaynak Kullanımı**:
   - RecyclerView view recycling mekanizmasını kullanır
   - Panel yalnızca klavye açıldığında oluşturulur
   - Klavye kapandığında panel ve ilgili kaynaklar temizlenir

## İyileştirme Potansiyeli

- **Çevrimdışı Depolama Boyutu**: Kullanıcı başına depolanan öneri sayısı sınırlandırılabilir
- **Akıllı Filtreleme**: Kullanıcının yazıkça önerileri dinamik olarak filtreleyen bir sistem eklenebilir
- **Özelleştirme**: Kullanıcıların öneri sistemini açıp kapatabilmesi için ayarlar eklenebilir
- **Birden Çok Tab Desteği**: Öneri sistemi birden çok WebView/Tab için daha iyi optimize edilebilir

## Sorun Giderme

1. **Öneriler Gösterilmiyor**:
   - JavaScript entegrasyonunun düzgün çalıştığından emin olun
   - Klavye durumunun doğru tespit edildiğini kontrol edin

2. **Performans Sorunları**:
   - Fazla öneri kaydı varsa temizleme işlemi yapılabilir
   - Kompleks sayfalar için JavaScript enjeksiyonu optimize edilebilir

3. **Klavye Çakışmaları**:
   - Farklı klavye uygulamaları için test edin
   - Klavye yüksekliği tespitini iyileştirin

## Notlar

- Bu modül Android 8.0 (API 26) ve üzeri sürümleri destekler.
- Şifre alanları için öneri özelliği kapatılmıştır.
- Form gönderim işlemlerini izlemek için JavaScript köprüsü kullanılır.
