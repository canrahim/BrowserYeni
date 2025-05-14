# AsforceBrowser Geliştirici Kılavuzu

## Proje Hakkında

AsforceBrowser, Android platformunda geliştirilmiş özel bir tarayıcı uygulamasıdır. Temel web tarayıcı işlevlerinin yanı sıra, endüstriyel kontrol ve ölçüm araçları entegrasyonu sunar. Bu kılavuz, proje geliştirme sürecinde dikkat edilmesi gereken standartları ve iş akışını açıklar.

## Kodlama Standartları

### 1. Dil ve İletişim Standardı

* **Türkçe Dokümantasyon**: Tüm yorum satırları, açıklamalar ve dokümantasyon Türkçe yazılmalıdır.
* **Kod İçi Yorumlar**: Karmaşık algoritmaların ve iş mantığının açıklamaları için ayrıntılı yorum satırları eklenmelidir.
* **Commit Mesajları**: Git commit mesajları Türkçe olmalı ve yapılan değişiklikleri açıkça belirtmelidir.

### 2. Mimari Prensipler

* **MVVM Mimarisi**: Uygulama MVVM (Model-View-ViewModel) mimarisini takip etmelidir.
* **Tek Sorumluluk Prensibi**: Her sınıf ve metot yalnızca bir işlevden sorumlu olmalıdır.
* **Bağımlılık Enjeksiyonu**: Hilt kullanarak bağımlılık enjeksiyonu uygulanmalıdır.
* **Repository Pattern**: Veri erişimi için Repository desenini kullanın.

### 3. Kod Düzeni ve Yapısı

* **Paket Yapısı**: Kodlar, işlevselliklerine göre aşağıdaki paketlerde organize edilmelidir:
  * `data`: Veri modelleri, yerel veritabanı, uzak veri kaynakları ve repository uygulamaları
  * `domain`: İş mantığı, repository arayüzleri ve use case'ler
  * `presentation`: Aktiviteler, fragmentler, view modeller ve UI bileşenleri
  * `di`: Bağımlılık enjeksiyonu modülleri
  * `util`: Yardımcı sınıflar ve uzantı fonksiyonları

* **Dosya İsimlendirme**:
  * Aktiviteler: `[Feature]Activity.kt` (örn. `MainActivity.kt`)
  * Fragmentler: `[Feature]Fragment.kt` (örn. `WebViewFragment.kt`)
  * View Modeller: `[Feature]ViewModel.kt` (örn. `MainViewModel.kt`)
  * Veri Modelleri: `[Entity].kt` (örn. `Tab.kt`) 
  * Repository'ler: `[Feature]Repository.kt` veya `[Feature]RepositoryImpl.kt`

### 4. Kodlama Stili

* **Kotlin Stili**: Resmi [Kotlin Kod Konvansiyonları](https://kotlinlang.org/docs/coding-conventions.html) takip edilmelidir.
* **İsimlendirme**:
  * Sınıflar: PascalCase (örn. `WebViewFragment`)
  * Fonksiyonlar ve değişkenler: camelCase (örn. `loadUrl()`)
  * Sabitler: SCREAMING_SNAKE_CASE (örn. `DEFAULT_URL`)
* **Maksimum Satır Uzunluğu**: 100 karakteri geçmemelidir.
* **İndentasyon**: 4 boşluk kullanılmalıdır.

### 5. Asenkron İşlemler

* **Coroutines**: Asenkron işlemler için RxJava yerine Kotlin Coroutines ve Flow kullanılmalıdır.
* **ViewModel Kapsamı**: Uzun süreli işlemler viewModelScope içinde yönetilmelidir.
* **Hata Yönetimi**: try-catch blokları veya Flow/LiveData için hata durumları (error state) eklenmelidir.

### 6. Performans Optimizasyonu

* **Bellek Yönetimi**: WebView ve büyük nesnelerin yaşam döngüsü dikkatli bir şekilde yönetilmelidir.
* **Lazy İçerik Yükleme**: Büyük içerikler ve görseller lazily (ihtiyaç anında) yüklenmelidir.
* **WorkManager**: Arka plan görevleri için WorkManager kullanılmalıdır.

### 7. Referans Kontrolü

* Yeni eklenen kodlar için ilgili bir referans (doküman, kaynak, kitap vb.) belirtilmelidir.
* Referanslar aşağıdaki formatta eklenmelidir:

```kotlin
/**
 * [Sınıf/Metot Açıklaması]
 * 
 * Referans: [Kaynak İsmi]
 * URL: [Kaynak Bağlantısı]
 */
```

### 8. Bağımlılık Denetimi

* Kullanılan her kütüphane için `implementation` veya `api` ifadeleri kontrol edilmelidir.
* Eskimiş bağımlılıklar güncellenmelidir.
* Test için kullanılan bağımlılıklar `testImplementation` veya `androidTestImplementation` olarak tanımlanmalıdır.

## İş Akışı Kuralları

### 1. Yerel Düzenleme

* Kod veya konfigürasyon dosyaları asla doğrudan sohbet penceresinde düzenlenmemelidir.
* Proje dizini yerel ortamda uygun bir editörde açılarak değişiklikler yapılmalıdır.
* Asistanın bilgisayara erişim izni mevcuttur.
* Düzenlemeler tamamlandığında, yapılan güncellemeler sohbete yansıtılmalıdır.

### 2. Versiyonlama

* Semantic Versioning (Anlamsal Sürümleme) kullanılmalıdır: MAJOR.MINOR.PATCH
  * MAJOR: Geriye dönük uyumsuz API değişiklikleri
  * MINOR: Geriye dönük uyumlu yeni özellikler
  * PATCH: Geriye dönük uyumlu hata düzeltmeleri

### 3. Code Review Süreci

* Tüm değişiklikler bir branch üzerinde yapılmalı ve pull request ile ana branch'e eklenmelidir.
* Pull request'ler en az bir kişi tarafından incelenmelidir.
* Otomatik testler çalıştırılmalı ve başarılı olmalıdır.

### 4. İşlem Özeti

* Her kod bloğu veya özellik geliştirmesi tamamlandığında:
  * Eklenen fonksiyonellik
  * Yapılan güncellemeler
  * Değişiklikler
  * Karşılaşılan zorluklar ve çözümleri 
  
  kısa ve öz şekilde özetlenmelidir.

### 5. Dokümantasyon

* Yeni eklenen her özellik için dokümantasyon güncellenmelidir.
* Karmaşık işlevler için diyagramlar veya akış şemaları eklenmelidir.
* Eklenen API'ler için KDoc formatında dokümantasyon yazılmalıdır.

### 6. Test Standardı

* **Unit Test**: Tüm iş mantığı ve ViewModel sınıfları için unit test yazılmalıdır.
* **UI Test**: Kritik kullanıcı arayüzü işlevleri için UI testleri eklenmelidir.
* **Test Kapsamı**: %70 minimum test kapsamı hedeflenmelidir.

### 7. Sohbetler Arası Devamlılık

* Farklı bir sohbete geçildiğinde; bugüne kadar yapılan tüm adımlar ve elde edilen sonuçlar başlıklar halinde toparlanır.
* Böylece yeni sohbete kaldığımız yerden hızlıca devam edilebilir.

## Bildirim ve Raporlama

### 1. Günlük Rapor

* Günlük yapılan değişikliklerin ve ilerlemelerin özeti.
* Karşılaşılan sorunlar ve çözümleri.
* Bir sonraki gün için planlanan görevler.

### 2. Haftalık Değerlendirme

* Haftalık ilerlemenin genel özeti.
* Kod kalitesi ve performans değerlendirmesi.
* İyileştirme önerileri.

## Özel Modüller

### 1. Kaçak Akım Modülü

* `LeakageControlActivity` sınıfı MVVM yapısına uygun olarak geliştirilmelidir.
* Kaçak akım ölçüm sonuçları veritabanında saklanmalıdır.
* Material Design 3 UI bileşenleri kullanılmalıdır.

### 2. Pano Fonksiyon Kontrolü

* `PanelControlActivity` sınıfı MVVM yapısına uygun olarak geliştirilmelidir.
* Panel tipleri için filtreleme seçenekleri eklenmelidir.
* Kontrol sonuçları için görsel geri bildirim sağlanmalıdır.

### 3. Topraklama Modülü

* `TopraklamaControlActivity` sınıfı MVVM yapısına uygun olarak geliştirilmelidir.
* Ölçüm sonuçları için grafik gösterimler eklenmelidir.
* QR kod entegrasyonu için `TopraklamaViewModel` sınıfı geliştirilmelidir.

### 4. Termal Kamera Modülü

* `Menu4Activity` sınıfı `ThermalCameraActivity` olarak yeniden adlandırılmalıdır.
* MVVM yapısına uygun olarak geliştirilmelidir.
* Görüntü işleme performansı optimize edilmelidir.

## Sonuç

Bu kurallara uyulması, proje kalitesini ve sürdürülebilirliğini artıracaktır. Tüm ekip üyeleri bu standartlara bağlı kalarak geliştirme yapmalıdır.
