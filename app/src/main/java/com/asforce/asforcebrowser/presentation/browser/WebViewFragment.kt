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
import com.asforce.asforcebrowser.util.performance.MediaOptimizer
import com.asforce.asforcebrowser.util.performance.PageLoadOptimizer
import com.asforce.asforcebrowser.util.performance.PerformanceOptimizer
import com.asforce.asforcebrowser.util.performance.ScrollOptimizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * WebViewFragment - Her sekme için bir WebView içeren fragment
 *
 * Bu fragment, sekme sayfasını ve web içeriğini görüntülemekten sorumludur.
 */
@AndroidEntryPoint
class WebViewFragment : Fragment() {

    private var _binding: FragmentWebViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WebViewViewModel by viewModels()

    private var tabId: Long = -1
    private var initialUrl: String = ""

    // Performans optimizasyon sınıfları
    private lateinit var performanceOptimizer: PerformanceOptimizer
    private lateinit var scrollOptimizer: ScrollOptimizer
    private lateinit var mediaOptimizer: MediaOptimizer
    private lateinit var pageLoadOptimizer: PageLoadOptimizer

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
            if (icon != null) {
                val url = view?.url ?: initialUrl
                val title = view?.title ?: "Yükleniyor..."

                // İkon yüklemede sorun var, lifecycleScope içinde güncelleyelim
                lifecycleScope.launch {
                    viewModel.updateTab(tabId, url, title, icon)
                }
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

        setHasOptionsMenu(true) // Fragment'in opsiyon menüsü olduğunu belirt

        // Performans optimizasyon sınıflarını başlat
        context?.let { ctx ->
            performanceOptimizer = PerformanceOptimizer.getInstance(ctx)
            scrollOptimizer = ScrollOptimizer(ctx)
            mediaOptimizer = MediaOptimizer(ctx)
            pageLoadOptimizer = PageLoadOptimizer(ctx)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Eğer mevcut binding varsa yeniden kullan
        if (_binding != null) {
            return binding.root
        }

        // Yeni bir binding oluştur - her zaman temiz bir WebView kullan
        _binding = FragmentWebViewBinding.inflate(inflater, container, false)

        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // WebView'in zaten yapılandırılıp yapılandırılmadığını kontrol et
        if (!binding.webView.settings.javaScriptEnabled) {
            setupWebView()
        }

        // Ekran yönünü kontrol et ve webview'ı ona göre ayarla
        configureWebViewForScreenOrientation()

        // Eğer URL boş değilse ve WebView henüz sayfa yüklemediyse URL'yi yükle
        if (initialUrl.isNotEmpty() && binding.webView.url == null) {
            loadUrl(initialUrl)
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            // WebView ayarlarını yapılandır
            configure()

            // Render modunu hardware olarak ayarla
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Performans optimizasyonlarını uygula
            performanceOptimizer.optimizeWebView(this)

            // Client'ları ayarla
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let {
                        // URL değişimini viewModel'e bildir
                        viewModel.updateCurrentUrl(it)

                        // Sekme verilerini güncelle
                        lifecycleScope.launch {
                            viewModel.updateTab(tabId, it, view?.title ?: "Yükleniyor...", favicon)
                        }

                        // Sayfa yükleme optimizasyonlarını uygula
                        if (view != null) {
                            pageLoadOptimizer.optimizePageLoadSettings(view)
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

                        // Sayfa yüklendiğinde optimizasyonları uygula
                        if (view != null) {
                            scrollOptimizer.injectOptimizedScrollingScript(view)
                            mediaOptimizer.optimizeVideoPlayback(view)
                            pageLoadOptimizer.injectLoadOptimizationScript(view)

                            // Kodeck desteğini kontrol et ve optimize et
                            mediaOptimizer.enableAdvancedCodecSupport(view)
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

                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)

                    // Video veya medya dosyası yüklenirken
                    if (url != null && view != null) {
                        if (url.contains(".mp4") || url.contains(".m3u8") ||
                            url.contains(".ts") || url.contains("video")) {
                            mediaOptimizer.optimizeVideoPlayback(view)
                        }
                    }
                }
            }

            // WebChromeClient ata
            webChromeClient = this@WebViewFragment.webChromeClient

            // SwipeRefreshLayout ile entegre et
            setupWithSwipeRefresh(binding.swipeRefresh)
        }
    }

    fun loadUrl(url: String) {
        val normalizedUrl = url.normalizeUrl()

        // WebView'in mevcut URL'ini kontrol et
        val currentUrl = binding.webView.url
        if (currentUrl == normalizedUrl) {
            return
        }

        try {
            // WebView henüz başlamadıysa veya kullanılamaz durumdaysa
            if (!binding.webView.isActivated || binding.webView.settings == null) {
                setupWebView() // WebView'i yeniden yapılandır
            }

            // Boş URL'yi engellemek için kontrol
            if (normalizedUrl.isEmpty() || normalizedUrl == "about:blank") {
                binding.webView.loadUrl("https://www.google.com")
            } else {
                // URL'yi yükle
                binding.webView.loadUrl(normalizedUrl)
            }

            // Bir ek güvenlik olarak URL yüklendiğini kontrol et (1 saniye sonra)
            binding.root.postDelayed({
                if (binding.webView.url == null || binding.webView.url.isNullOrEmpty()) {
                    binding.webView.loadUrl(normalizedUrl.ifEmpty { "https://www.google.com" })
                }
            }, 1000)

            // Sayfa yükleme performansını izle
            performanceOptimizer.collectPerformanceMetrics(binding.webView) { _ ->
                // Metrics processing code would go here if needed
            }
        } catch (e: Exception) {
            // Hata durumunda Google'a yönlendir
            try {
                binding.webView.loadUrl("https://www.google.com")
            } catch (e2: Exception) {
                // Silent catch for production
            }
        }
    }

    fun canGoBack(): Boolean = binding.webView.canGoBack()

    fun canGoForward(): Boolean = binding.webView.canGoForward()

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

        // WebView'in özelliklerini sakla fakat tamamen temizleme
        // Bu, fragment yeniden oluşturulduğunda bile durumu korumamızı sağlar
        binding.webView.stopLoading()
    }

    override fun onResume() {
        super.onResume()

        // Ekran yönünü yeniden kontrol et
        configureWebViewForScreenOrientation()

        // WebView içeriğinin yüklü olup olmadığını kontrol et
        if (binding.webView.url == null && initialUrl.isNotEmpty()) {
            loadUrl(initialUrl)
        }
    }

    /**
     * Ekran yönüne göre WebView'i yapılandırır
     */
    private fun configureWebViewForScreenOrientation() {
        // Ekran yönünü al
        val orientation = resources.configuration.orientation
        val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        // WebView'i ekran yönüne göre yapılandır
        binding.webView.apply {
            // Yatay mod için WebView yükseklik/genişlik parametrelerini ayarla
            layoutParams = layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }

            // WebView durumunu en iyi şekilde korumak için
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Ekran yönüne göre kaydırma davranışı optimize et
            if (isLandscape) {
                // Yatay mod için özel ayarlar
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    // Yatay moddaki performans iyileştirmeleri
                    setNeedInitialFocus(false)
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                }
            } else {
                // Dikey mod için özel ayarlar
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    // Dikey mod için standart ayarlar
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                }
            }
        }
    }

    /**
     * WebView getter - WebView için dışarıdan erişim sağlar
     */
    fun getWebView(): WebView? {
        return if (_binding != null) binding.webView else null
    }

    override fun onDestroy() {
        super.onDestroy()

        // FragmentCache kullanıldığından, sadece aktivite sonlandığında kaynakları temizle
        if (activity?.isFinishing == true) {
            try {
                if (_binding != null) {
                    binding.webView.stopLoading()
                    binding.webView.destroy()
                    _binding = null
                }
            } catch (e: Exception) {
                // Silent catch for production
            }
        }
    }

    interface BrowserCallback {
        fun onPageLoadStarted()
        fun onPageLoadFinished()
        fun onProgressChanged(progress: Int)
    }
}