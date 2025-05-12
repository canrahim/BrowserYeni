plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.asforce.asforcebrowser"

    compileSdk = 34

    defaultConfig {
        applicationId = "com.asforce.asforcebrowser"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Core ve UI kütüphaneleri
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")
    
    // WebView/Tarayıcı özellikleri
    implementation("androidx.webkit:webkit:1.8.0")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    
    // Room DB - Sekme ve gezinme geçmişi için
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ViewPager2 - Sekmeler arası kaydırma
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    
    // SwipeRefreshLayout - Aşağı çekerek yenileme
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Glide - Resim yükleme kütüphanesi (favicon için)
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    
    // JSoup - HTML parsing için (favicon URL'lerini bulmak için)
    implementation("org.jsoup:jsoup:1.16.1")
    
    // Volley - HTTP istekleri için
    implementation("com.android.volley:volley:1.2.1")
    
    // Google ML Kit - QR Code scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0") // Temel barkod tarama
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.0") // Gelişmiş özellikler
    
    // CameraX dependencies - Güncel sürümler
    implementation("androidx.camera:camera-core:1.3.2")
    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")
    implementation("androidx.camera:camera-view:1.3.2")
    implementation("androidx.camera:camera-extensions:1.3.2") // Düşük ışık desteği için
    
    // Google MLKit için barcode modelleri
    implementation("com.google.mlkit:image-labeling:17.0.8")
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7") // Görüntü meta verisi
    
    // Material Components for Modal Dialog
    implementation("com.google.android.material:material:1.11.0")
    
    // Test kütüphaneleri
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}