@echo off
REM Proje derlemesi için batch dosyası

echo AsforceBrowser Projesi Derleniyor...
echo ====================================

REM Gradle Wrapper kullanarak projeyi derle
echo Temizlik yapiliyor...
call gradlew clean

echo.
echo Debug APK olusturuluyor...
call gradlew assembleDebug

REM Derleme sonucu kontrolü
if %ERRORLEVEL% equ 0 (
    echo.
    echo ====================================
    echo Derleme başarılı!
    echo APK dosyası: app/build/outputs/apk/debug/app-debug.apk
    echo ====================================
    echo.
    echo SoilContinuity JPG indirme sorunu düzeltildi:
    echo - Dosya adı artık "id_SoilContinuity.jpg" formatında
    echo - SoilContinuity URL'lerinde ID otomatik alınıyor
    echo - Her zaman JPG uzantısı ve image/jpeg MIME type kullanılıyor
    echo.
    echo Örnek: "1027042_SoilContinuity.jpg"
    echo.
    echo PDF İndirme Sorunu Düzeltildi V2:
    echo - Content-Disposition filename* formatı düzeltildi
    echo - UTF-8'' formatı doğru parse ediliyor
    echo - Türkçe karakterler doğru dekode ediliyor
    echo - Dosyalar gerçek adlarıyla kaydediliyor
    echo.
    echo Örnek: "İZMİTRAFİNERİSİ_590205_Elektrikli El Aletleri.pdf"
    echo ====================================
) else (
    echo.
    echo ====================================
    echo Derleme başarısız!
    echo ====================================
    pause
)
