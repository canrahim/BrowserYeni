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
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.asforce.asforcebrowser.databinding.FragmentWebViewBinding
import com.asforce.asforcebrowser.util.configure
import com.asforce.asforcebrowser.util.normalizeUrl
import com.asforce.asforcebrowser.util.setupWithSwipeRefresh
import com.asforce.asforcebrowser.util.performance.MediaOptimizer
import com.asforce.asforcebrowser.util.performance.PageLoadOptimizer
import com.asforce.asforcebrowser.util.performance.PerformanceOptimizer
import com.asforce.asforcebrowser.util.performance.ScrollOptimizer
import com.asforce.asforcebrowser.util.performance.menu.MenuOptimizer
import com.asforce.asforcebrowser.download.WebViewDownloadHelper
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
    private lateinit var menuOptimizer: MenuOptimizer
    private lateinit var webViewDownloadHelper: WebViewDownloadHelper

    // Dosya seçimi için değişkenler
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoPath: String? = null
    
    // Kamera ile fotoğraf çekme ve galeriden dosya seçme sonuçları için
    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            filePathCallback?.onReceiveValue(arrayOf(uri))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }
    
    // Kamera ile fotoğraf çekme sonuçları için
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        try {
            if (success && cameraPhotoPath != null) {
                // FileProvider kullanarak güvenli URI oluştur
                val photoFile = File(cameraPhotoPath!!)
                if (photoFile.exists() && photoFile.length() > 0) {
                    val photoUri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        photoFile
                    )
                    filePathCallback?.onReceiveValue(arrayOf(photoUri))
                } else {
                    // Dosya yoksa veya boşsa null dön
                    filePathCallback?.onReceiveValue(null) 
                }
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } catch (e: Exception) {
            println("Fotoğraf sonucu işlenirken hata: ${e.message}")
            filePathCallback?.onReceiveValue(null)
        } finally {
            filePathCallback = null
        }
    }

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

                        // MainActivity'ye URL değişikliğini bildir
                (activity as? BrowserCallback)?.onUrlChanged(it)
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
        
        /**
         * Dosya seçim dialog'unu gösterir (input[type=file] için)
         * Camera, galeri ve dosya seçim işlemleri burada yönetilir
         * 
         * Referans: Android WebView
         * URL: https://developer.android.com/reference/android/webkit/WebChromeClient#onShowFileChooser
         */
        override fun onShowFileChooser(
            webView: WebView?, 
            filePathCallback: ValueCallback<Array<Uri>>?, 
            fileChooserParams: FileChooserParams?
        ): Boolean {
            // Önceki callback'i iptal et
            this@WebViewFragment.filePathCallback?.onReceiveValue(null)
            this@WebViewFragment.filePathCallback = filePathCallback
            
            val context = requireContext()
            
            try {
                // Seçim dialog'unu göster
                showFileChooserDialog(context, fileChooserParams)
                return true
            } catch (e: Exception) {
                println("Dosya seçici açılamadı: ${e.message}")
                filePathCallback?.onReceiveValue(null)
                this@WebViewFragment.filePathCallback = null
                return false
            }
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            super.onReceivedIcon(view, icon)
            
            // Favicon alındığında hem tab verilerini hem de FaviconManager'a bildir
            if (view != null) {
                val url = view.url ?: initialUrl
                val title = view.title ?: "Yükleniyor..."

                // Önce sekme verilerini güncelle
                lifecycleScope.launch {
                    viewModel.updateTab(tabId, url, title, icon)
                    
                    // Favicon'u kaızak için ayrıca FaviconManager'a kaydet
                    if (icon != null && url.isNotEmpty()) {
                        requireContext().let { context ->
                            // FaviconManager'a favicon'u kaydet
                            com.asforce.asforcebrowser.util.FaviconManager.downloadAndSaveFavicon(
                                context,
                                url,
                                tabId
                            )
                        }
                    }
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
            menuOptimizer = MenuOptimizer(ctx)
            webViewDownloadHelper = WebViewDownloadHelper(ctx)
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

            // İndirme modülünü kur
            webViewDownloadHelper.setupWebViewDownloads(this)

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
                            
                            // Erken menü optimizasyonu (sayfa yüklenirken)
                            view.postDelayed({
                                menuOptimizer.optimizeMenuPerformance(view)
                            }, 200)
                        }

                        // MainActivity'ye URL değişikliğini bildir
                        (activity as? BrowserCallback)?.onUrlChanged(it)
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
                            
                            // Menü optimizasyonlarını uygula
                            menuOptimizer.applyMenuOptimizations(view)
                            
                            // Yavaş menü tepkisini düzelt
                            view.postDelayed({
                                menuOptimizer.fixSlowMenuResponse(view)
                            }, 500)
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
    
    /**
     * Geçici kamera fotoğraf dosyası oluşturur
     * @return Oluşturulan geçici dosyanın URI'si
     */
    /**
     * Geçici kamera fotoğraf dosyası oluşturur
     * @return Oluşturulan geçici dosyanın URI'si
     */
    private fun createTempImageFileUri(): Uri? {
        try {
            val context = requireContext()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            
            // Storage klasörü var mı kontrol et
            if (storageDir == null || !storageDir.exists()) {
                println("Depolama dizini bulunamadı veya erişilemez")
                return null
            }
            
            // Geçici dosya oluştur
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            cameraPhotoPath = imageFile.absolutePath
            
            // FileProvider URI oluştur
            val authority = "${context.packageName}.fileprovider"
            return FileProvider.getUriForFile(context, authority, imageFile)
        } catch (e: Exception) {
            println("Geçici fotoğraf dosyası oluşturulamadı: ${e.message}")
            return null
        }
    }
    
    /**
     * Dosya seçim dialog'unu gösterir
     * Kamera, galeri ve dosya seçeneklerini sunar
     */
    private fun showFileChooserDialog(context: Context, fileChooserParams: WebChromeClient.FileChooserParams?) {
        val options = arrayOf("Kamera", "Galeri/Dosyalar")

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Görüntü veya Dosya Seç")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Kamera
                        takePictureWithCamera()
                    }
                    1 -> { // Galeri/Dosyalar
                        // Kabul edilen mimetype'ları al
                        val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                        var mimeType = "*/*"
                        
                        // Eğer özel mime type belirtilmişse kullan
                        if (acceptTypes.isNotEmpty() && acceptTypes[0].isNotEmpty() && acceptTypes[0] != "*/*") {
                            mimeType = acceptTypes[0]
                        }
                        
                        // Dosya/resim seçiciyi aç
                        getContentLauncher.launch(mimeType)
                    }
                }
            }
            .setOnCancelListener {
                // Kullanıcı iptal ederse callback'i null ile çağır
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }
    
    /**
     * Kamera ile fotoğraf çekme işlemini başlatır
     */
    /**
     * Kamera ile fotoğraf çekme işlemini başlatır
     * Güvenli null kontrolleri ile fotoğraf URI'si oluşturur
     */
    private fun takePictureWithCamera() {
        try {
            val photoUri = createTempImageFileUri()
            
            if (photoUri != null) {
                takePictureLauncher.launch(photoUri)
            } else {
                // Fotoğraf URI'si oluşturulamazsa callback'i iptal et
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
        } catch (e: Exception) {
            // Herhangi bir hata durumunda güvenli şekilde iptal et
            println("Kamera başlatılırken hata: ${e.message}")
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // İndirme helper'ını temizle
        if (this::webViewDownloadHelper.isInitialized) {
            webViewDownloadHelper.cleanup()
        }

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
        fun onUrlChanged(url: String)
    }
}