package com.asforce.asforcebrowser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.os.Handler
import android.os.Looper

/**
 * Custom WebView for tabs
 * 
 * This is a wrapper class for WebView to support tab functionality
 * Extends WebView and provides additional tab-specific features
 */
@SuppressLint("SetJavaScriptEnabled")
class TabWebView(context: Context) : WebView(context) {
    
    init {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
    }
    
    /**
     * Executes JavaScript in the WebView safely
     * 
     * @param script The JavaScript code to execute
     * @param callback Optional callback for the result
     */
    fun executeJavascript(script: String, callback: ((String) -> Unit)?) {
        try {
            // Execute on main thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                evaluateJavascript(script, callback)
            } else {
                Handler(Looper.getMainLooper()).post {
                    evaluateJavascript(script, callback)
                }
            }
        } catch (e: Exception) {
            callback?.invoke("null")
        }
    }
}
