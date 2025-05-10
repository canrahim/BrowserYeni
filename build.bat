@echo off
echo ==========================================
echo AsforceBrowser Build Script
echo ==========================================
cd /d C:\AsforceBrowser
echo.
echo 1. Proje temizleniyor...
gradlew clean
echo.
echo 2. Debug APK derleniyor...
gradlew assembleDebug
echo.
echo 3. Build tamamlandi!
echo.
pause
