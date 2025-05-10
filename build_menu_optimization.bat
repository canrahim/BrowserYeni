@echo off
echo Menü Optimizasyonu İçin Proje Derleniyor...
cd /d C:\AsforceBrowser
call gradlew assembleDebug
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Derleme başarılı! Menü optimizasyonları etkin.
    echo.
    echo Önemli değişiklikler:
    echo - MenuOptimizer.kt eklendi
    echo - menu_optimizations.css eklendi  
    echo - WebViewFragment.kt güncelleştirildi
    echo.
    echo Test için şu sitelerde deneyin:
    echo - Web sitelerindeki açılır menüler
    echo - Sidebar menüler (.side-menu)
    echo - Hamburger menüler (.mdi-menu)
    echo - Dropdown menüler (.has_sub)
    echo.
) else (
    echo.
    echo Derleme başarısız! Hataları kontrol edin.
)
pause
