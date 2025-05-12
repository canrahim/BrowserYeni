package com.asforce.asforcebrowser.qr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.asforce.asforcebrowser.MainActivity

/**
 * QR tarama entegrasyonu için yardımcı sınıf
 * 
 * Uzaktan QR tarama özelliğini uygulamaya entegre etmek için kullanılır.
 * Kamera izinleri yönetimi ve QR tarama dialog başlatma işlevselliğini sağlar.
 */
object QRIntegration {
    private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    
    /**
     * Uzaktan QR tarama özelliğini başlat
     * 
     * @param activity Ana aktivite
     * @param onQRScanned QR kod tarandığında çağrılacak callback
     */
    fun startImprovedQRScanner(activity: MainActivity, onQRScanned: (String) -> Unit) {
        // Kamera izni kontrolü
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            
            // İzin yoksa, izin iste
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            
            Toast.makeText(
                activity, 
                "QR tarama için kamera izni gerekiyor. Lütfen izin verin.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // İzin varsa, QR tarama dialog'unu göster
            ImprovedQRScannerDialog.show(activity, onQRScanned)
        }
    }
    
    /**
     * İzin sonucunu kontrol et
     * 
     * MainActivity'nin onRequestPermissionsResult metodunda çağrılmalıdır
     */
    fun checkPermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        activity: Activity,
        onSuccess: () -> Unit
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // İzin verildi, QR tarayıcıyı başlat
                onSuccess()
            } else {
                // İzin reddedildi
                Toast.makeText(
                    activity,
                    "QR tarama özelliği için kamera izni gereklidir.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}