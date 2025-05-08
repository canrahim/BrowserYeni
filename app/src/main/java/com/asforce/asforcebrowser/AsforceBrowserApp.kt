package com.asforce.asforcebrowser

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * AsforceBrowserApp - Uygulama sınıfı
 * 
 * Hilt Dependency Injection'ı başlatmak için kullanılır.
 * Referans: Hilt Application Sınıfı
 */
@HiltAndroidApp
class AsforceBrowserApp : Application()