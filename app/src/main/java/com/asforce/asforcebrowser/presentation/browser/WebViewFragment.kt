package com.asforce.asforcebrowser.presentation.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.view.ActionMode
import android.widget.PopupMenu
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
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
import com.asforce.asforcebrowser.R
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.ImageView
import android.widget.TextView
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
import com.asforce.asforcebrowser.suggestion.SuggestionManager
import timber.log.Timber
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

    // Öneri yöneticisi
    private var suggestionManager: SuggestionManager? = null

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
                    
                    // Favicon'u kayzak için ayrıca FaviconManager'a kaydet
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

        // Öneri sistemini başlat
        initSuggestionManager()

        // WebView'in zaten yapılandırılıp yapılandırılmadığını kontrol et
        if (!binding.webView.settings.javaScriptEnabled) {
            setupWebView()
        }

        // Uzun basma işlevi için bağlam menüsünü ayarla
        setupLongPressContextMenu()

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
            
            // HitTestResult için uzun basma desteğini etkinleştir
            isLongClickable = true

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
                            
                            // Öneri sistemi için JavaScript enjekte et
                            injectSuggestionScripts(view)
                            Timber.d("onPageFinished: Suggestion scripts injected for URL: $url")
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
            
            // Öneri sistemi için ayarla
            if (suggestionManager == null) {
                initSuggestionManager()
            }
            suggestionManager?.setupWebView(this, tabId.toString())

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
     * Uzun basma işlevi için bağlam menüsü
     * Kullanıcı bir linke uzun bastığında seçenekler sunar
     * "Yeni sekmede aç", "Bağlantıyı kopyala" vb.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupLongPressContextMenu() {
        // Popup konumunu ve davranışını düzenleyen field
        val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
        popupField.isAccessible = true

        // Touch konumunu izleme
        var lastTouchX = 0f
        var lastTouchY = 0f
        
        // Touch olaylarını daha hassas kontrol etmek için OnTouchListener kullan
        binding.webView.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Dokunma başlangıcında konumu kaydet
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            
            false // Normal touch işlemlerinin devam etmesine izin ver
        }
        
        // WebView'e özel uzun basılma olayı için
        binding.webView.setOnLongClickListener { view ->
            val webView = view as WebView
            val hitTestResult = webView.hitTestResult
            
            // Link türünü kontrol et
            when (hitTestResult.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                WebView.HitTestResult.IMAGE_TYPE -> {
                    // HitTestResult'tan link URL'sini al
                    val url = hitTestResult.extra
                    if (url != null) {
                        try {
                            // Özel konum hesaplamak için gerekli değerleri al
                            val webViewLocation = IntArray(2)
                            webView.getLocationOnScreen(webViewLocation)
                            
                            // Mutlak dokunma konumunu hesapla 
                            val absoluteTouchX = webViewLocation[0] + lastTouchX.toInt()
                            val absoluteTouchY = webViewLocation[1] + lastTouchY.toInt()
                            
                            // Geçici görünmez view oluştur tam dokunulan koordinatlarda
                            val anchorView = View(requireContext())
                            val containerLayout = binding.root as ViewGroup
                            
                            // View'i doğru pozisyonda yerleştir
                            val layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            anchorView.layoutParams = layoutParams
                            containerLayout.addView(anchorView)
                            
                            // View'i dokunulan konuma taşı
                            anchorView.x = lastTouchX
                            anchorView.y = lastTouchY
                            
                            // Popup menüyü dokunulan noktada göster
                            val popupMenu = PopupMenu(requireContext(), anchorView)
                            
                            // Menünün tam dokunulan yerin yanında açılmasını sağla
                            val popup = popupField.get(popupMenu)
                            popup.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                                .invoke(popup, true)
                            
                            // Menü öğelerini ekle
                            if (hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                popupMenu.menu.add(0, 1, 0, "Yeni sekmede aç")
                                popupMenu.menu.add(0, 2, 0, "Bağlantıyı kopyala")
                                popupMenu.menu.add(0, 3, 0, "Arka planda aç")
                            }
                            
                            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                popupMenu.menu.add(0, 4, 0, "Görseli kaydet")
                                popupMenu.menu.add(0, 5, 0, "Görsel URL'sini kopyala")
                            }

                            // Menü tıklama dinleyicisi
                            popupMenu.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> { // Yeni sekmede aç
                                        (activity as? BrowserCallback)?.onOpenInNewTab(url)
                                        true
                                    }
                                    2 -> { // Bağlantıyı kopyala
                                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Bağlantı", url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(requireContext(), "Bağlantı kopyalandı", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                    3 -> { // Arka planda aç
                                        // Önce yeni sekme aç
                                        (activity as? BrowserCallback)?.onOpenInNewTab(url)
                                        // Mevcut sekmede kalmaya devam et
                                        true
                                    }
                                    4 -> { // Görseli kaydet
                                        webView?.let { safeWebView ->
                                            webViewDownloadHelper.handleImageDownload(url, safeWebView)
                                        }
                                        true
                                    }
                                    5 -> { // Görsel URL'sini kopyala
                                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Görsel URL'si", url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(requireContext(), "Görsel URL'si kopyalandı", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                    else -> false
                                }
                            }

                            // PopupMenu kapatıldığında geçici view'i temizle
                            popupMenu.setOnDismissListener {
                                containerLayout.removeView(anchorView)
                            }

                            // Menüyü göster
                            popupMenu.show()
                            return@setOnLongClickListener true
                        } catch (e: Exception) {
                            // Herhangi bir hata durumunda varsayılan davranışı kullan
                            e.printStackTrace()
                            
                            // Basit bir popup menü göster
                            val popupMenu = PopupMenu(requireContext(), webView)
                            // Menü öğelerini ekle...
                            if (hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                popupMenu.menu.add(0, 1, 0, "Yeni sekmede aç")
                                popupMenu.menu.add(0, 2, 0, "Bağlantıyı kopyala")
                                popupMenu.menu.add(0, 3, 0, "Arka planda aç")
                            }
                            
                            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                popupMenu.menu.add(0, 4, 0, "Görseli kaydet")
                                popupMenu.menu.add(0, 5, 0, "Görsel URL'sini kopyala")
                            }

                            // Menü tıklama dinleyicisi
                            popupMenu.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> { // Yeni sekmede aç
                                        (activity as? BrowserCallback)?.onOpenInNewTab(url)
                                        true
                                    }
                                    2 -> { // Bağlantıyı kopyala
                                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Bağlantı", url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(requireContext(), "Bağlantı kopyalandı", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                    3 -> { // Arka planda aç
                                        // Önce yeni sekme aç
                                        (activity as? BrowserCallback)?.onOpenInNewTab(url)
                                        // Mevcut sekmede kalmaya devam et
                                        true
                                    }
                                    4 -> { // Görseli kaydet
                                        webView?.let { safeWebView ->
                                            webViewDownloadHelper.handleImageDownload(url, safeWebView)
                                        }
                                        true
                                    }
                                    5 -> { // Görsel URL'sini kopyala
                                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Görsel URL'si", url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(requireContext(), "Görsel URL'si kopyalandı", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                    else -> false
                                }
                            }

                            // Menüyü göster
                            popupMenu.show()
                            return@setOnLongClickListener true
                        }
                    }
                }
            }
            false
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
     * @return WebView nesnesi veya null (binding boş ise)
     */
    fun getWebView(): WebView? {
        return if (_binding != null) binding.webView else null
    }
    
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
     * Modern Bottom Sheet style dialog ile çeşitli dosya seçenekleri sunar
     */
    private fun showFileChooserDialog(context: Context, fileChooserParams: WebChromeClient.FileChooserParams?) {
        // Bottom sheet dialog oluştur
        val bottomSheetDialog = BottomSheetDialog(context)
        val bottomSheetView = layoutInflater.inflate(R.layout.file_chooser_bottom_sheet, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        // RecyclerView'i ayarla
        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.recyclerViewOptions)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Seçenekleri oluştur
        val options = createFilePickerOptions(fileChooserParams)

        // Adapter'i ayarla
        val adapter = FilePickerOptionAdapter(options) { option ->
            // Seçenek tıklandığında
            when (option.id) {
                1 -> { // Kamera
                    takePictureWithCamera()
                    bottomSheetDialog.dismiss()
                }
                2 -> { // Galeri - Resimler
                    getContentLauncher.launch("image/*")
                    bottomSheetDialog.dismiss()
                }
                3 -> { // Video
                    getContentLauncher.launch("video/*")
                    bottomSheetDialog.dismiss()
                }
                4 -> { // Ses
                    getContentLauncher.launch("audio/*")
                    bottomSheetDialog.dismiss()
                }
                5 -> { // Belgeler
                    getContentLauncher.launch("application/pdf")
                    bottomSheetDialog.dismiss()
                }
                6 -> { // Tüm dosyalar
                    getContentLauncher.launch("*/*")
                    bottomSheetDialog.dismiss()
                }
            }
        }

        recyclerView.adapter = adapter

        // Dialog kapandığında iptal et
        bottomSheetDialog.setOnCancelListener { 
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }

        // Dialog'u göster
        bottomSheetDialog.show()
    }

    /**
     * Dosya seçici için tüm seçenekleri oluşturur
     */
    private fun createFilePickerOptions(fileChooserParams: WebChromeClient.FileChooserParams?): List<FilePickerOption> {
        val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
        val isOnlyImage = acceptTypes.any { it.contains("image") && !it.contains("*/*") }
        val isOnlyVideo = acceptTypes.any { it.contains("video") && !it.contains("*/*") }
        val isOnlyAudio = acceptTypes.any { it.contains("audio") && !it.contains("*/*") }
        val isOnlyDocument = acceptTypes.any { (it.contains("application/pdf") || it.contains("text/")) && !it.contains("*/*") }

        val options = mutableListOf<FilePickerOption>()

        // Her durumda kamera seçeneğini ekle
        options.add(
            FilePickerOption(
                id = 1,
                title = "Kamera",
                description = "Fotoğraf çek",
                iconResource = android.R.drawable.ic_menu_camera
            )
        )

        // Eğer sadece belirli bir tür istendiyse sadece o türü ekle
        // Aksi halde tüm türleri ekle
        if (!isOnlyVideo && !isOnlyAudio && !isOnlyDocument) {
            options.add(
                FilePickerOption(
                    id = 2,
                    title = "Galeri",
                    description = "Galerinizden bir resim seçin",
                    iconResource = android.R.drawable.ic_menu_gallery
                )
            )
        }

        if (!isOnlyImage && !isOnlyAudio && !isOnlyDocument) {
            options.add(
                FilePickerOption(
                    id = 3,
                    title = "Video",
                    description = "Video seçin veya yükleyin",
                    iconResource = android.R.drawable.ic_media_play
                )
            )
        }

        if (!isOnlyImage && !isOnlyVideo && !isOnlyDocument) {
            options.add(
                FilePickerOption(
                    id = 4,
                    title = "Ses Dosyası",
                    description = "Ses dosyası seçin",
                    iconResource = android.R.drawable.ic_lock_silent_mode_off
                )
            )
        }

        if (!isOnlyImage && !isOnlyVideo && !isOnlyAudio) {
            options.add(
                FilePickerOption(
                    id = 5,
                    title = "Belgeler",
                    description = "PDF ve diğer belgeler",
                    iconResource = android.R.drawable.ic_menu_edit
                )
            )
        }

        // Tüm dosyalar seçeneğini her zaman ekle
        options.add(
            FilePickerOption(
                id = 6,
                title = "Tüm Dosyalar",
                description = "Herhangi bir dosya seçin",
                iconResource = android.R.drawable.ic_menu_save
            )
        )

        return options
    }

    /**
     * Dosya seçim seçeneği sınıfı
     */
    data class FilePickerOption(
        val id: Int,
        val title: String,
        val description: String,
        val iconResource: Int
    )

    /**
     * Dosya seçim seçenekleri için adapter
     */
    inner class FilePickerOptionAdapter(
        private val options: List<FilePickerOption>,
        private val onOptionClick: (FilePickerOption) -> Unit
    ) : RecyclerView.Adapter<FilePickerOptionAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.ivIcon)
            val title: TextView = itemView.findViewById(R.id.tvOptionTitle)
            val description: TextView = itemView.findViewById(R.id.tvOptionDescription)

            init {
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onOptionClick(options[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_picker_option, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = options[position]
            holder.icon.setImageResource(option.iconResource)
            holder.title.text = option.title
            holder.description.text = option.description
        }

        override fun getItemCount() = options.size
    }
    
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
        
        // Öneri sistemini temizle
        suggestionManager?.onTabClosed(tabId.toString())

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

    /**
     * Öneri yöneticisini başlat
     */
    private fun initSuggestionManager() {
        if (suggestionManager == null) {
            val activity = requireActivity()
            val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            
            if (rootView != null) {
                try {
                    suggestionManager = SuggestionManager(activity, rootView)
                    Timber.d("SuggestionManager initialized successfully")
                } catch (e: Exception) {
                    Timber.e("SuggestionManager initialization failed: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Timber.e("Root view not found for SuggestionManager")
            }
        }
    }
    
    /**
     * WebView için öneri sistemini ayarla
     * 
     * @param webView WebView instance
     */
    private fun setupSuggestionSystem(webView: WebView?) {
        try {
            if (suggestionManager == null) {
                Timber.d("setupSuggestionSystem: SuggestionManager null, initializing")
                initSuggestionManager()
            }
            
            if (webView != null) {
                if (suggestionManager != null) {
                    Timber.d("Setting up suggestion system for WebView, tabId: $tabId")
                    suggestionManager?.setupWebView(webView, tabId.toString())
                    
                    // WebView'in JavaScript etkin olduğundan emin ol
                    if (!webView.settings.javaScriptEnabled) {
                        Timber.w("JavaScript was not enabled on WebView! Enabling it now.")
                        webView.settings.javaScriptEnabled = true
                    }
                } else {
                    Timber.e("setupSuggestionSystem: SuggestionManager is still null after initialization")
                }
            } else {
                Timber.e("setupSuggestionSystem: WebView is null")
            }
        } catch (e: Exception) {
            Timber.e("Error in setupSuggestionSystem: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Sayfa yüklendiğinde JavaScript enjekte et
     * 
     * @param webView WebView instance
     */
    private fun injectSuggestionScripts(webView: WebView?) {
        if (webView == null) {
            Timber.e("injectSuggestionScripts: WebView is null")
            return
        }
        
        try {
            // Önce WebView'i SuggestionManager ile eşleştir
            setupSuggestionSystem(webView)
            
            // JavaScript kodu enjekte et - input alanlarını izle
            val script = """
                (function() {
                    if (window.asforceInputObserver) {
                        window.asforceInputObserver.observeAllInputs();
                        console.log("AsforceInputObserver mevcut, gözlem başlatıldı");
                        return "Observer mevcut ve yeniden başlatıldı";
                    } else {
                        // Input observer yüklenmemiş, ilk kez yüklemeyi dene
                        ${com.asforce.asforcebrowser.suggestion.js.JsInjectionScript.INPUT_OBSERVER_SCRIPT}
                        console.log("AsforceInputObserver oluşturuldu ve gözlem başlatıldı");
                        return "Observer yeni oluşturuldu";
                    }
                })();
            """.trimIndent()
            
            // JavaScript'i çalıştır ve hata ayıklama bilgisini logla
            webView.evaluateJavascript(script) { result ->
                Timber.d("Suggestion scripts injected, result: $result")
            }
            
            // Kısa bir bekleme sonrası JS Interface'in doğru çalıştığını kontrol et
            webView.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        if (window.AsforceSuggestionBridge) {
                            console.log("AsforceSuggestionBridge mevcut");
                            return "Köprü hazır";
                        } else {
                            console.log("AsforceSuggestionBridge eksik!");
                            return "Köprü eksik";
                        }
                    })();
                """.trimIndent()) { result ->
                    Timber.d("SuggestionBridge check: $result")
                    
                    // Köprü eksikse SuggestionManager'ı yeniden başlat
                    if (result.contains("eksik")) {
                        Timber.w("Köprü eksik, SuggestionManager yeniden ayarlanıyor")
                        
                        // SuggestionManager'ı sıfırla ve yeniden kur
                        if (suggestionManager != null) {
                            suggestionManager?.onTabClosed(tabId.toString()) // Temizlik yap
                            suggestionManager = null
                            initSuggestionManager()
                            setupSuggestionSystem(webView)
                            
                            // Tekrar JavaScript enjekte et
                            webView.evaluateJavascript(script) { innerResult ->
                                Timber.d("Suggestion scripts re-injected, result: $innerResult")
                            }
                        }
                    }
                }
            }, 300) // Köprü kontrolünü kısa bir gecikme ile yap
        } catch (e: Exception) {
            Timber.e("Error injecting suggestion scripts: ${e.message}")
            e.printStackTrace()
        }
    }

    interface BrowserCallback {
        fun onPageLoadStarted()
        fun onPageLoadFinished()
        fun onProgressChanged(progress: Int)
        fun onUrlChanged(url: String)
        fun onOpenInNewTab(url: String)
    }
}