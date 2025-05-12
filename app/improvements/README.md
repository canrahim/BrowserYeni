# Uzaktan QR Tarama Özelliği İyileştirmesi

Bu iyileştirme, AsforceBrowser uygulamasının QR tarama özelliğini geliştirerek uzaktan (mesafeli) QR kodlarını daha iyi algılayabilmesini sağlar.

## Geliştirilen Özellikler

1. **Uzak mesafelerden QR algılama**
   - Daha yüksek çözünürlüklü görüntü işleme
   - Gelişmiş odaklama algoritmaları
   - Akıllı zoom kontrolü

2. **Görüntü optimizasyonu**
   - Düşük ışık koşullarında iyileştirilmiş performans
   - Kontrast ve parlaklık ayarları
   - GPU hızlandırmalı görüntü işleme

3. **Akıllı tarama algoritmaları**
   - Çoklu algılama stratejileri
   - Farklı QR kod formatları desteği
   - Algılama hassasiyeti iyileştirmeleri

## Entegrasyon Adımları

MainActivity sınıfına aşağıdaki şekilde entegre edilebilir:

```kotlin
// MainActivity.kt dosyasında yapılacak değişiklikler

// 1. İçe aktarımları ekleyin
import com.asforce.asforcebrowser.qr.ImprovedQRScannerDialog
import com.asforce.asforcebrowser.qr.QRIntegration

// 2. QR tarama metodunu değiştirin
private fun showQRScanner() {
    // Eski metodu değiştirin:
    // QRScannerDialog.show(this) { qrContent -> handleQRResult(qrContent) }
    
    // Yeni geliştirilmiş metodu kullanın:
    QRIntegration.startImprovedQRScanner(this) { qrContent -> 
        handleQRResult(qrContent) 
    }
}

// 3. İzin sonucu kontrolünü ekleyin
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    
    // QR tarama izni sonucunu kontrol et
    QRIntegration.checkPermissionResult(
        requestCode, 
        permissions, 
        grantResults,
        this,
        { showQRScanner() } // İzin verilirse QR taramayı başlat
    )
}
```

## Kullanım Şekli

Mevcut QR tarama fonksiyonu yerine geliştirilmiş uzaktan QR tarama özelliğini kullanmak için yukarıdaki entegrasyonu yapmanız yeterlidir. Kullanıcı arayüzünde herhangi bir değişiklik gerekmemektedir, mevcut QR tarama butonu bu yeni özelliği çağıracaktır.

## Test Önerileri

- Uzak mesafelerdeki (1-3 metre) QR kodları test edin
- Farklı ışık koşullarında performansı değerlendirin
- Çeşitli boyutlardaki QR kodlarıyla deneyler yapın
- Farklı açılardan tarama yaparak algılama kapasitesini değerlendirin

## Ek Notlar

- Bu iyileştirme, mevcut QR tarama fonksiyonu yerine geçerek daha kapsamlı bir çözüm sunar
- Daha karmaşık algoritmaları kullandığı için, eski cihazlarda daha fazla işlemci gücü gerektirebilir
- Kamera donanımına bağlı olarak, bazı cihazlarda performans farklılıkları görülebilir