package com.asforce.asforcebrowser.presentation.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.asforce.asforcebrowser.databinding.FragmentWebViewBinding
import com.asforce.asforcebrowser.util.configure
import com.asforce.asforcebrowser.util.normalizeUrl
import com.asforce.asforcebrowser.util.setupWithSwipeRefresh
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * WebViewFragment - Her sekme için bir WebView içeren fragment
 * 
 * Bu fragment, sekme sayfasını ve web içeriğini görüntülemekten sorumludur.
 * Referans: Android Fragment ve WebView kullanımı
 */
@AndroidEntryPoint
class WebViewFragment : Fragment() {
    
    private var _binding: FragmentWebViewBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: WebViewViewModel by viewModels()
    
    private var tabId: Long = -1
    private var initialUrl: String = ""
    
    // WebViewClient - Sayfa yükleme ve URL değişimleri için
    private val webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let { 
                // URL değişimini viewModel'e bildir
                viewModel.updateCurrentUrl(it)
                
                // Sekme verilerini güncelle
                lifecycleScope.launch {
                    viewModel.updateTab(tabId, it, view?.title ?: "Yükleniyor...", favicon)
                }
            }
            
            // BrowserActivity'ye sayfa yüklenmeye başladığını bildir
            (activity as? BrowserCallback)?.onPageLoadStarted()
        }
        
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            
            // Sekme verilerini güncelle
            url?.let {
                lifecycleScope.launch {
                    viewModel.updateTab(tabId, it, view?.title ?: "Yükleniyor...", null)
                }
            }
            
            // BrowserActivity'ye sayfa yüklemesinin bittiğini bildir
            (activity as? BrowserCallback)?.onPageLoadFinished()
        }
        
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return false // Web sayfası yüklemeyi WebView içinde yap
        }
    }
    
    // WebChromeClient - Sayfa başlığı değişimleri için
    private val webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            title?.let {
                lifecycleScope.launch {
                    val url = view?.url ?: initialUrl
                    viewModel.updateTab(tabId, url, it, null)
                }
            }
        }
        
        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            super.onReceivedIcon(view, icon)
            val url = view?.url ?: initialUrl
            val title = view?.title ?: "Yükleniyor..."
            
            lifecycleScope.launch {
                viewModel.updateTab(tabId, url, title, icon)
            }
        }
        
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            (activity as? BrowserCallback)?.onProgressChanged(newProgress)
        }
    }
    
    companion object {
        private const val ARG_TAB_ID = "arg_tab_id"
        private const val ARG_URL = "arg_url"
        
        fun newInstance(tabId: Long, url: String): WebViewFragment {
            return WebViewFragment().apply {
                arguments = bundleOf(
                    ARG_TAB_ID to tabId,
                    ARG_URL to url
                )
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabId = it.getLong(ARG_TAB_ID, -1)
            initialUrl = it.getString(ARG_URL, "").normalizeUrl()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebViewBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWebView()
        
        // WebView'ı son bilinen konuma yükle, eğer yeni sekme ise initialUrl'e git
        if (savedInstanceState == null) {
            loadUrl(initialUrl)
        }
    }
    
    private fun setupWebView() {
        binding.webView.apply {
            // WebView ayarlarını yapılandır
            configure()
            
            // Client'ları ayarla
            webViewClient = this@WebViewFragment.webViewClient
            webChromeClient = this@WebViewFragment.webChromeClient
            
            // SwipeRefreshLayout ile entegre et
            setupWithSwipeRefresh(binding.swipeRefresh)
        }
    }
    
    fun loadUrl(url: String) {
        val normalizedUrl = url.normalizeUrl()
        binding.webView.loadUrl(normalizedUrl)
    }
    
    fun canGoBack(): Boolean {
        return binding.webView.canGoBack()
    }
    
    fun canGoForward(): Boolean {
        return binding.webView.canGoForward()
    }
    
    fun goBack() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        }
    }
    
    fun goForward() {
        if (binding.webView.canGoForward()) {
            binding.webView.goForward()
        }
    }
    
    fun refresh() {
        binding.webView.reload()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        binding.webView.stopLoading()
        _binding = null
    }
    
    interface BrowserCallback {
        fun onPageLoadStarted()
        fun onPageLoadFinished()
        fun onProgressChanged(progress: Int)
    }
}