package com.asforce.asforcebrowser

import android.os.Bundle
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.asforce.asforcebrowser.databinding.ActivityMainBinding
import com.asforce.asforcebrowser.presentation.browser.BrowserPagerAdapter
import com.asforce.asforcebrowser.presentation.browser.TabAdapter
import com.asforce.asforcebrowser.presentation.browser.WebViewFragment
import com.asforce.asforcebrowser.presentation.main.MainViewModel
import com.asforce.asforcebrowser.util.normalizeUrl
import com.asforce.asforcebrowser.util.viewpager.FragmentCache
import com.asforce.asforcebrowser.util.viewpager.ViewPager2Optimizer
import com.asforce.asforcebrowser.download.DownloadManager
import com.asforce.asforcebrowser.download.WebViewDownloadHelper
import com.asforce.asforcebrowser.ui.leakage.LeakageControlActivity
import com.asforce.asforcebrowser.ui.panel.kotlin.PanelControlActivity
import com.asforce.asforcebrowser.ui.topraklama.kotlin.TopraklamaControlActivity
import com.asforce.asforcebrowser.ui.termal.kotlin.Menu4Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.asforce.asforcebrowser.util.DataHolder
import android.widget.Toast
import com.asforce.asforcebrowser.ui.search.SearchDialog
import com.asforce.asforcebrowser.webview.ComboboxSearchHelper
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.view.View.OnClickListener
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import org.json.JSONArray
import android.view.inputmethod.InputMethodManager

// QR Scanner bileşenleri
import com.asforce.asforcebrowser.qr.QRScannerDialog
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * MainActivity - Ana ekran
 * Tarayıcı uygulamasının ana aktivitesi, sekme yönetimi ve kullanıcı arayüzünü kontrol eder.
 * 
 * Menü Değişikliği: Sol ve sağ menü itemleri güncellendi
 * Sol: Kaçak Akım, Pano Fonksiyon Kontrolü, Topraklama, Termal Kamera
 * Sağ: Yenile, İleri, İndirilenler
 * 
 * Referans: Android Development Documentation - PopupMenu
 * URL: https://developer.android.com/reference/android/widget/PopupMenu
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), WebViewFragment.BrowserCallback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var tabAdapter: TabAdapter
    private lateinit var pagerAdapter: BrowserPagerAdapter
    private lateinit var viewPagerOptimizer: ViewPager2Optimizer
    private lateinit var downloadManager: DownloadManager
    private lateinit var webViewDownloadHelper: WebViewDownloadHelper
    private lateinit var searchDialog: SearchDialog
    private lateinit var searchButton: Button
    private var savedSearchTexts = mutableListOf<String>()
    
    // SharedPreferences için
    private lateinit var sharedPreferences: SharedPreferences
    
    // QR ve Seri No arama alanları için
    private lateinit var qrSearchInput: TextInputEditText
    private lateinit var serialSearchInput: TextInputEditText
    
    // Arama bayrakları
    private var isManualSearchActive = false
    private var isSerialSearchActive = false
    
    // QR kontrol için zamanlayıcı
    private var qrCheckHandler: Handler? = null
    private var qrCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Durum çubuğu ve navigasyon çubuğu renkleri için modern API kullan
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Durum çubuğu ve navigasyon çubuğu renklerini ayarla
        window.statusBarColor = getColor(R.color.colorSurface)
        window.navigationBarColor = getColor(R.color.colorSurface)
        
        // Modern WindowInsetsController kullanarak metin/ikon renklerini ayarla
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        // Karanlık mod kontrolü
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLightMode = uiMode != Configuration.UI_MODE_NIGHT_YES
        
        // Durum çubuğu metin rengini ayarla (true: koyu metin, false: beyaz metin)
        insetsController.isAppearanceLightStatusBars = isLightMode
        // Navigasyon çubuğu metin rengini ayarla
        insetsController.isAppearanceLightNavigationBars = isLightMode
        
        // SharedPreferences'i başlat
        sharedPreferences = getSharedPreferences("SearchDialogPrefs", Context.MODE_PRIVATE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupDownloadManager()
        setupSearchDialog()
        setupSearchInputs()  // QR ve Seri No arama alanlarını ayarla
        setupAdapters()
        setupListeners()
        setupFloatingMenuButtons()  // Yeni eklenen - açılır kapanır menü butonlarını ayarla
        observeViewModel()
        handleOrientationChanges()
        
        // QR handler'ı başlat
        qrCheckHandler = Handler(Looper.getMainLooper())
    }

    private fun setupDownloadManager() {
        // DownloadManager instance'ını al
        downloadManager = DownloadManager.getInstance(this)
        // Context'i güncelle
        downloadManager.updateContext(this)
        
        // WebViewDownloadHelper'ı başlat
        webViewDownloadHelper = WebViewDownloadHelper(this)
    }
    
    /**
     * Arama dialog'unu başlat ve konfigüre et
     */
    private fun setupSearchDialog() {
        // Search button'u bul
        searchButton = findViewById(R.id.searchButton)
        
        // Search dialog'u oluştur - SharedPreferences'dan veri otomatik yüklenecek
        searchDialog = SearchDialog(this)
        
        // SearchDialog'daki arama metinlerini al
        savedSearchTexts.clear()
        savedSearchTexts.addAll(searchDialog.getSearchTexts())
        
        // Eğer arama metinleri varsa, arama butonunu güncelle
        if (savedSearchTexts.isNotEmpty()) {
            searchButton.text = "Ara (${savedSearchTexts.size} metin)"
        } else {
            searchButton.text = "ComboBox Arama"
        }
        
        // Dialog'da kaydet ve kapat butonuna basıldığında
        searchDialog.onSaveAndClose = { searchTexts ->
            savedSearchTexts.clear()
            savedSearchTexts.addAll(searchTexts)
            
            // Eğer arama metinleri varsa, arama butonunu güncelle
            if (searchTexts.isNotEmpty()) {
                searchButton.text = "Ara (${searchTexts.size} metin)"
            } else {
                searchButton.text = "ComboBox Arama"
            }
        }
        
        // Search button'a tıklandığında
        searchButton.setOnClickListener {
            if (savedSearchTexts.isNotEmpty()) {
                // Eğer arama metinleri varsa, direkt arama yap
                performComboBoxSearch(savedSearchTexts)
            } else {
                // Arama metinleri yoksa, önce dialog'u göster
                searchDialog.setSearchTexts(savedSearchTexts)
                searchDialog.show()
            }
        }
    }

    /**
     * QR Kod ve Seri Numarası arama işlevlerini ayarla
     * Eski projede qrNo ve srNo EditTextlerinin mantığını uygula
     * 
     * Referans: Eski projeden qrNo ve srNo arama mantığı
     */
    private fun setupSearchInputs() {
        // QR arama alanını başlat
        qrSearchInput = findViewById(R.id.searchEditText1)
        
        // Seri No arama alanını başlat
        serialSearchInput = findViewById(R.id.searchEditText2)
        
        // QR arama alanı için IME_ACTION_SEARCH işleyicisi
        qrSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performQrCodeSearch()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        
        // QR arama alanı için end icon ayarla
        val qrTextInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout_qr)
        qrTextInputLayout?.setEndIconOnClickListener {
            hideKeyboard()
            performQrCodeSearch()
        }
        
        // Seri No arama alanı için IME_ACTION_SEARCH işleyicisi
        serialSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performSerialNumberSearch()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        
        // Seri No arama alanı için end icon ayarla
        val serialTextInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout_sr)
        serialTextInputLayout?.setEndIconOnClickListener {
            hideKeyboard()
            performSerialNumberSearch()
        }
    }
    
    private fun setupAdapters() {
        // ViewPager2 optimizer'i başlat
        viewPagerOptimizer = ViewPager2Optimizer(this)

        // Sekmeler için RecyclerView
        tabAdapter = TabAdapter(
            onTabSelected = { tab ->
                viewModel.setActiveTab(tab.id)
            },
            onTabClosed = { tab ->
                viewModel.closeTab(tab)
            }
        )
        binding.tabsRecyclerView.adapter = tabAdapter

        // Sürükle-bırak işlemleri için ItemTouchHelper
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition

                // Geçersiz pozisyon kontrolü
                if (fromPos < 0 || toPos < 0 || fromPos >= viewModel.tabs.value.size || toPos >= viewModel.tabs.value.size) {
                    return false
                }

                // Sekme listesini güncelle
                val tabs = viewModel.tabs.value.toMutableList()

                // Taşınan öğeyi geçici olarak al
                val movedTab = tabs[fromPos]

                // Listeyi düzenle - öğeyi kaldırıp hedef konuma ekle
                tabs.removeAt(fromPos)
                tabs.add(toPos, movedTab)

                // Tüm sekmelerin position özelliklerini güncelle
                val updatedTabs = tabs.mapIndexed { index, tab ->
                    tab.copy(position = index)
                }

                // Sekme pozisyonlarını veritabanında güncelle
                viewModel.updateTabPositions(updatedTabs)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Swipe işlemi yok
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.tabsRecyclerView)

        // ViewPager adaptörünü ayarla
        pagerAdapter = BrowserPagerAdapter(this)

        // ViewPager2 yapılandırması
        binding.viewPager.apply {
            offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
            setPageTransformer(null)
            isUserInputEnabled = false

            try {
                val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
                recyclerViewField.isAccessible = true
                val recyclerView = recyclerViewField.get(this)

                val touchSlopField = recyclerView.javaClass.getDeclaredField("mTouchSlop")
                touchSlopField.isAccessible = true
                val touchSlop = touchSlopField.get(recyclerView) as Int
                touchSlopField.set(recyclerView, touchSlop * 5)

                val mFragmentMaxLifecycleEnforcerField = ViewPager2::class.java.getDeclaredField("mFragmentMaxLifecycleEnforcer")
                mFragmentMaxLifecycleEnforcerField.isAccessible = true
                val mFragmentMaxLifecycleEnforcer = mFragmentMaxLifecycleEnforcerField.get(this)

                val mPageTransformerAdapterField = mFragmentMaxLifecycleEnforcer.javaClass.getDeclaredField("mPageTransformerAdapter")
                mPageTransformerAdapterField.isAccessible = true
            } catch (e: Exception) {
                // Reflection hatası yakalandı, sessizce devam et
            }

            adapter = pagerAdapter
        }

        // ViewPager2 optimize edici ile yapılandırma
        viewPagerOptimizer.optimizeViewPager(binding.viewPager, pagerAdapter)

        // ViewPager sayfa değişim dinleyicisi
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                pagerAdapter.getTabAt(position)?.let { tab ->
                    if (!tab.isActive) {
                        viewModel.setActiveTab(tab.id)
                    }

                    // Adres çubuğunu güncelle
                    viewModel.updateAddressBar(tab.url)
                    
                    // URL'deki son rakamları DataHolder'a kaydet
                    // Sekme değişiminde de çağrılır
                    saveLastDigitsToDataHolder(tab.url)
                    
                    // **İyileştirildi: Sekme değişiminde QR kontrol fonksiyonunu çağır**
                    if (!isManualSearchActive && tab.url.contains("szutest.com.tr", ignoreCase = true)) {
                        // Önceki kontrolü iptal et
                        qrCheckRunnable?.let { 
                            qrCheckHandler?.removeCallbacks(it)
                        }
                        
                        // Yeni kontrol başlat
                        qrCheckRunnable = Runnable {
                            checkForQrCodeOnPage()
                        }
                        
                        // QR kontrolünü 1 saniye sonra başlat
                        qrCheckHandler?.postDelayed(qrCheckRunnable!!, 1000)
                    }
                }
            }
        })
    }

    private fun setupListeners() {
        // Adres çubuğu dinleyicisi
        binding.addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val url = binding.addressBar.text.toString().normalizeUrl()
                loadUrl(url)
                true
            } else {
                false
            }
        }

        // Geri butonu dinleyicisi
        binding.backButton.setOnClickListener {
            val currentTab = viewModel.activeTab.value ?: return@setOnClickListener
            val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)

            if (fragment?.canGoBack() == true) {
                fragment.goBack()
            }
        }

        // İleri butonu dinleyicisi
        binding.forwardButton.setOnClickListener {
            val currentTab = viewModel.activeTab.value ?: return@setOnClickListener
            val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)

            if (fragment?.canGoForward() == true) {
                fragment.goForward()
            }
        }

        /**
         * Sol menü açma butonu dinleyicisi 
         * Menü itemleri: Kaçak Akım, Pano Fonksiyon Kontrolü, Topraklama, Termal Kamera
         */
        binding.menuOpenButton.setOnClickListener { view ->
            showLeftBrowserMenu(view)
        }

        // Yeni sekme butonu dinleyicisi
        binding.newTabButton.setOnClickListener {
            viewModel.createNewTab("https://www.google.com")
        }

        /**
         * Sağ menü butonu dinleyicisi
         * Menü itemleri: Yenile, İleri, İndirilenler
         */
        binding.menuButton.setOnClickListener { view ->
            showRightBrowserMenu(view)
        }
    }

    /**
     * Sol menü işlevi - Uygulama özellikleri
     * Menü itemleri: ComboBox Ara, Kaçak Akım, Pano Fonksiyon Kontrolü, Topraklama, Termal Kamera
     */
    private fun showLeftBrowserMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        
        // Uygulama özelliklerini ekle
        val items = arrayOf(
            "ComboBox Arama Ayarları",
            "Kaçak Akım",
            "Pano Fonksiyon Kontrolü",
            "Topraklama",
            "Termal Kamera"
        )
        
        // Menü öğelerini ekle
        items.forEachIndexed { index, item ->
            popupMenu.menu.add(0, index, index, item)
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> { // ComboBox Arama Ayarları
                    searchDialog.setSearchTexts(savedSearchTexts)
                    searchDialog.show()
                    true
                }
                1 -> { // Kaçak Akım
                    handleKacakAkim()
                    true
                }
                2 -> { // Pano Fonksiyon Kontrolü
                    handlePanoFonksiyonKontrol()
                    true
                }
                3 -> { // Topraklama
                    handleTopraklama()
                    true
                }
                4 -> { // Termal Kamera
                    handleTermalKamera()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    /**
     * Açılır kapanır menü butonlarını ayarla
     * Floating action style butonlar kullanıcı dostu görünüm sağlar
     */
    private fun setupFloatingMenuButtons() {
        // Menü açma/kapama butonu
        binding.btnToggleButtons.setOnClickListener {
            toggleFloatingMenu()
        }

        // Ekipman Listesi butonu
        binding.btnEquipmentList.setOnClickListener {
            loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")
        }

        // Kontrol Listesi butonu (PE)
        binding.btnControlList.setOnClickListener {
            loadUrl("https://app.szutest.com.tr/EXT/PKControl/EKControlList")
        }

        // Kapsam Dışı butonu - pop-up menü gösterir
        binding.btnScopeOut.setOnClickListener {
            showScopeOutMenu()
        }

        // Cihaz Ekle butonu - mevcut aktif WebView'de cihaz listesini alır
        binding.btnAddDevice.setOnClickListener {
            showDeviceAddMenu()
        }

        // QR Tarama butonu - QR kamera aktivitesini başlatır
        binding.btnQrScan.setOnClickListener {
            // Kamera iznini kontrol et
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                // QR tarama dialog'unu göster
                showQrScannerDialog()
            } else {
                // Kamera izni iste
                requestCameraPermission()
            }
        }

        // Uygulama ilk açıldığında menü görünürlüğünü ayarla
        binding.buttonsScrollView.visibility = View.GONE
    }

    /**
     * Açılır kapanır menüyü aç/kapat
     * Animasyonlu geçişler ile kullanıcı deneyimini iyileştirir
     */
    private fun toggleFloatingMenu() {
        // Menü görünürlüğünü değiştir
        val isVisible = binding.buttonsScrollView.visibility == View.VISIBLE

        // Interpolator'ları tanımla (daha yumuşak animasyonlar için)
        val overshootInterpolator = android.view.animation.OvershootInterpolator(0.5f)
        val accelerateDecelerateInterpolator = android.view.animation.AccelerateDecelerateInterpolator()

        if (isVisible) {
            // Menü açıksa kapat
            binding.buttonsScrollView.animate()
                .alpha(0f)
                .translationX(-50f)
                .setDuration(350)
                .setInterpolator(accelerateDecelerateInterpolator)
                .withEndAction {
                    binding.buttonsScrollView.visibility = View.GONE
                    binding.buttonsScrollView.translationX = 0f
                }
                .start()

            // Açma/kapama butonunu zarif animasyonla döndür
            binding.btnToggleButtons.animate()
                .rotation(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(overshootInterpolator)
                .start()

            // Buton renk değişimi
            val colorAnimation = android.animation.ValueAnimator.ofArgb(
                binding.btnToggleButtons.backgroundTintList?.defaultColor ?: getColor(R.color.colorPrimary),
                getColor(R.color.colorPrimary)
            )
            colorAnimation.duration = 350
            colorAnimation.addUpdateListener { animator ->
                binding.btnToggleButtons.backgroundTintList = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
            }
            colorAnimation.start()
        } else {
            // Menü kapalıysa aç
            binding.buttonsScrollView.alpha = 0f
            binding.buttonsScrollView.translationX = -50f
            binding.buttonsScrollView.visibility = View.VISIBLE
            binding.buttonsScrollView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(350)
                .setInterpolator(overshootInterpolator)
                .start()

            // Açma/kapama butonunu zarif animasyonla döndür
            binding.btnToggleButtons.animate()
                .rotation(90f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(350)
                .setInterpolator(overshootInterpolator)
                .start()

            // Buton renk değişimi (açıkken daha koyu ton)
            val colorAnimation = android.animation.ValueAnimator.ofArgb(
                binding.btnToggleButtons.backgroundTintList?.defaultColor ?: getColor(R.color.colorPrimary),
                android.graphics.Color.parseColor("#005e9e") // Daha koyu mavi ton
            )
            colorAnimation.duration = 350
            colorAnimation.addUpdateListener { animator ->
                binding.btnToggleButtons.backgroundTintList = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
            }
            colorAnimation.start()
        }
    }

    /**
     * Sağ menü işlevi - Tarayıcı işlevleri
     * Menü itemleri: Yenile, İleri, İndirilenler
     */
    private fun showRightBrowserMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        
        // Tarayıcı işlevlerini ekle
        val items = arrayOf(
            "Yenile",
            "İleri",
            "İndirilenler"
        )
        
        // Menü öğelerini ekle
        items.forEachIndexed { index, item ->
            popupMenu.menu.add(0, index, index, item)
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> { // Yenile
                    val currentTab = viewModel.activeTab.value ?: return@setOnMenuItemClickListener false
                    pagerAdapter.getFragmentByTabId(currentTab.id)?.refresh()
                    true
                }
                1 -> { // İleri
                    val currentTab = viewModel.activeTab.value ?: return@setOnMenuItemClickListener false
                    val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
                    if (fragment?.canGoForward() == true) {
                        fragment.goForward()
                    }
                    true
                }
                2 -> { // İndirilenler
                    if (downloadManager != null) {
                        downloadManager.showDownloadsManager(this)
                    }
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun observeViewModel() {
        // Sekme listesini gözlemle  
        lifecycleScope.launch {
            viewModel.tabs.collectLatest { tabs ->
                // Önce aktif sekmeyi kontrol et
                val activeTab = tabs.find { it.isActive }
                activeTab?.let { tabAdapter.activeTabId = it.id }
                
                tabAdapter.updateTabs(tabs)
                pagerAdapter.updateTabs(tabs)
                
                // Başlangıçta sekme görünümlerini yenile
                if (tabs.isNotEmpty()) {
                    binding.tabsRecyclerView.post {
                        tabAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        // Aktif sekmeyi gözlemle
        lifecycleScope.launch {
            viewModel.activeTab.collectLatest { activeTab ->
                activeTab?.let {
                    val position = pagerAdapter.getPositionForTabId(it.id)

                    if (position != -1) {
                        // Aktif sekme ID'sini güncelle
                        tabAdapter.activeTabId = it.id
                        
                        // Mevcut pozisyondan farklı ise, görünümü güncelle
                        // Aktif sekme durumunu kaydet - durumun korunması için
                        saveCurrentFragmentState()

                        if (binding.viewPager.currentItem != position) {
                            // Optimize edilmiş tab geçişi - zorla yeniden yükleme
                            viewPagerOptimizer.setCurrentTabForceRefresh(binding.viewPager, position)

                            // Kısa bir gecikme ile fragment durumlarını yeniden kontrol et
                            binding.viewPager.postDelayed({
                                verifyFragmentStates()
                            }, 100)
                        }

                        // Fragment geçişlerini izle
                        viewPagerOptimizer.monitorFragmentSwitching(
                            binding.viewPager,
                            FragmentCache.getAllFragments(),
                            it.id
                        ) { _ ->
                            // Doğru fragment seçildiğinde yapacaklar
                        }
                        
                        // Sekme görünümlerini yenile
                        binding.tabsRecyclerView.post {
                            tabAdapter.notifyDataSetChanged()
                        }
                    } else {
                        // Adapter'i yenileme için zorla
                        viewPagerOptimizer.refreshViewPager(binding.viewPager)
                    }

                    // Adres çubuğunu güncelle
                    viewModel.updateAddressBar(it.url)
                    
                    // URL'deki son rakamları DataHolder'a kaydet
                    saveLastDigitsToDataHolder(it.url)
                }
            }
        }

        // Adres çubuğunu gözlemle
        lifecycleScope.launch {
            viewModel.addressBarText.collectLatest { url ->
                if (binding.addressBar.text.toString() != url) {
                    binding.addressBar.setText(url)
                }
            }
        }

        // Yükleme durumunu gözlemle
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Verilen URL'yi yükler
     * URL'yi normalize eder ve geçerli sekmede açar veya yeni sekme oluşturur
     * 
     * @param url Yüklenecek URL
     * @param forceLoad URL zaten yüklü olsa bile zorla yeniden yükle
     */
    private fun loadUrl(url: String, forceLoad: Boolean = false) {
        // URL'yi normalize et
        val normalizedUrl = url.normalizeUrl()
        
        val currentTab = viewModel.activeTab.value
        if (currentTab != null) {
            val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
            
            // Eğer forceLoad seçeneği aktifse veya URL mevcut URL'den farklıysa yükle
            if (forceLoad || fragment?.getWebView()?.url != normalizedUrl) {
                fragment?.loadUrl(normalizedUrl)
            }
        } else {
            // Eğer aktif sekme yoksa yeni bir sekme oluşturuyoruz
            viewModel.createNewTab(normalizedUrl)
        }
    }

    override fun onPageLoadStarted() {
        viewModel.setLoading(true)
    }

    override fun onPageLoadFinished() {
        viewModel.setLoading(false)
    }

    override fun onProgressChanged(progress: Int) {
        // İlerleme çubuğu özellikleri burada ayarlanabilir
    }

    /**
     * WebView'de URL değiştiğinde çağrılır
     * Bu metod WebViewFragment.BrowserCallback interface'inden gelir
     */
    override fun onUrlChanged(url: String) {
        // URL değiştiğinde DataHolder'a kaydet
        saveLastDigitsToDataHolder(url)
        
        // **İyileştirildi: Manuel arama kontrolü sadece performQrCodeSearch çağrıldığında aktif olur**
        // URL değişiminde her zaman QR kontrolü yap, ancak performans için kısa gecikme ekle
        if (url.contains("szutest.com.tr", ignoreCase = true)) {
            // Önceki kontrolü iptal et
            qrCheckRunnable?.let { 
                qrCheckHandler?.removeCallbacks(it)
            }
            
            // Yeni kontrol başlat
            qrCheckRunnable = Runnable {
                if (!isManualSearchActive) {
                    checkForQrCodeOnPage()
                }
            }
            
            // QR kontrolünü 500ms sonra başlat
            qrCheckHandler?.postDelayed(qrCheckRunnable!!, 500)
        }
    }

    /**
     * Ekran yönü değişikliğini yönetir
     */
    private fun handleOrientationChanges() {
        // Ekran yönü değişikliği için dinleyici
        // AndroidManifest.xml'deki configChanges ayarı için gerekli hazırlık
    }

    /**
     * Ekran yönü değiştiğinde çağrılır
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Tema değişikliğinde sistem çubuğu renklerini güncelle
        val uiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLightMode = uiMode != Configuration.UI_MODE_NIGHT_YES
        
        // Durum çubuğu ve navigasyon çubuğu renklerini güncelle
        window.statusBarColor = getColor(R.color.colorSurface)
        window.navigationBarColor = getColor(R.color.colorSurface)
        
        // Windows Insets Controller ile metin renklerini güncelle
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = isLightMode
        insetsController.isAppearanceLightNavigationBars = isLightMode

        // Şu anki fragmanların durumlarını kaydet
        saveCurrentFragmentState()

        // ViewPager'in fragment durumunu korumasını sağla
        val currentItem = binding.viewPager.currentItem

        // ViewPager2 optimizasyonunu yeniden uygula
        viewPagerOptimizer.optimizeViewPager(binding.viewPager, pagerAdapter)

        // Seçili sekmenin WebView içeriğini yeniden düzenle
        val currentTab = viewModel.activeTab.value
        if (currentTab != null) {
            binding.viewPager.post {
                // Geçerli sekme pozisyonuna geri dön
                if (binding.viewPager.currentItem != currentItem) {
                    binding.viewPager.setCurrentItem(currentItem, false)
                }

                // Fragment durumlarının doğruluğunu kontrol et
                verifyFragmentStates()
            }
        }
    }

    /**
     * Mevcut fragmanın durumunu kaydeder
     */
    private fun saveCurrentFragmentState() {
        val currentTab = viewModel.activeTab.value
        if (currentTab != null) {
            FragmentCache.saveFragmentState(currentTab.id, supportFragmentManager)
        }
    }
    
    // Handler fonksiyonları
    private fun handleKacakAkim() {
        // Kaçak Akım aktivitesini başlat
        val intent = Intent(this, LeakageControlActivity::class.java)
        startActivity(intent)
    }
    
    private fun handlePanoFonksiyonKontrol() {
        // Pano Fonksiyon Kontrol aktivitesini başlat
        val intent = Intent(this, PanelControlActivity::class.java)
        startActivity(intent)
    }
    
    private fun handleTopraklama() {
        // Topraklama Kontrol aktivitesini başlat
        val intent = Intent(this, TopraklamaControlActivity::class.java)
        startActivity(intent)
    }
    
    private fun handleTermalKamera() {
        // Termal Kamera aktivitesini başlat
        val intent = Intent(this, Menu4Activity::class.java)
        startActivity(intent)
    }

    /**
     * Kapsam Dışı ana menüsünü gösterir
     * Alt kategorileri de içeren pop-up menü
     */
    private fun showScopeOutMenu() {
        // Ana menü kategorileri
        val mainCategories = arrayOf(
            "Aydınlatma Cihazları",
            "Elektrikli El Aletleri",
            "Şarjlı El Aletleri",
            "Elektrikli Kaynak Makinası",
            "Diğer Elektirikli Cihazlar"
        )

        // PopupMenu ile ana kategorileri göster
        val menuBtn = binding.btnScopeOut
        val popup = PopupMenu(this, menuBtn)

        // Menü öğelerini ekle
        for (i in mainCategories.indices) {
            popup.menu.add(android.view.Menu.NONE, i, i, mainCategories[i])
        }

        // Tıklama olaylarını yönet
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> showAydinlatmaSubMenu()
                1 -> showElektrikElSubMenu()
                2 -> showSarjliElSubMenu()
                3 -> showKaynakSubMenu()
                4 -> showDigerElektrikSubMenu()
            }
            true
        }

        // Menüyü göster
        popup.show()
    }

    /**
     * Aydınlatma alt menüsünü gösterir
     */
    private fun showAydinlatmaSubMenu() {
        // Aydınlatma alt menü öğeleri
        val subItems = arrayOf(
            "24V Kablolu Aydınlatma",
            "Akülü Alan Aydınlatma"
        )

        showSubMenu("Aydınlatma Cihazları", subItems) { position ->
            val currentTab = viewModel.activeTab.value
            val webViewFragment = currentTab?.let { pagerAdapter.getFragmentByTabId(it.id) }
            val webView = webViewFragment?.getWebView()

            if (webView != null) {
                when (position) {
                    0 -> {/* OutOfScopeModule.set24VAydinlatmaOutOfScope(webView) */
                         Toast.makeText(this, "24V Kablolu Aydınlatma kapsam dışı ayarlandı", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {/* OutOfScopeModule.setAkuluAydinlatmaOutOfScope(webView) */
                         Toast.makeText(this, "Akülü Alan Aydınlatma kapsam dışı ayarlandı", Toast.LENGTH_SHORT).show()
                    }
                }
                showScopeOutSuccessMessage(subItems[position])
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Elektrik Malzemeleri alt menüsünü gösterir
     */
    private fun showElektrikElSubMenu() {
        // Elektrik alt menü öğeleri
        val subItems = arrayOf(
            "Avuç Taşlama",
            "Sigorta Grubu",
            "Şalter Grubu",
            "Pano Aksesuarları"
        )

        showSubMenu("Elektrik El Aletleri", subItems) { position ->
            showScopeOutSuccessMessage(subItems[position])
        }
    }

    /**
     * Şarjlı El Aletleri alt menüsünü gösterir
     */
    private fun showSarjliElSubMenu() {
        // Şarjlı alt menü öğeleri
        val subItems = arrayOf(
            "Şarjlı Avuç Taşlama",
            "Sigorta Grubu",
            "Şalter Grubu",
            "Pano Aksesuarları"
        )

        showSubMenu("Şarjlı El Aletleri", subItems) { position ->
            showScopeOutSuccessMessage(subItems[position])
        }
    }

    /**
     * Elektrikli Kaynak Makinası alt menüsünü gösterir
     */
    private fun showKaynakSubMenu() {
        // Kaynak alt menü öğeleri
        val subItems = arrayOf(
            "Kablo Grubu",
            "Buat Grubu",
            "Kablo Kanalları"
        )

        showSubMenu("Tesisat Malzemeleri", subItems) { position ->
            showScopeOutSuccessMessage(subItems[position])
        }
    }

    /**
     * Diğer Elektrikli Cihazlar alt menüsünü gösterir
     */
    private fun showDigerElektrikSubMenu() {
        // Ölçüm alt menü öğeleri
        val subItems = arrayOf(
            "Multimetre",
            "Topraklama Ölçüm",
            "İzolasyon Ölçüm",
            "Termal Kamera"
        )

        showSubMenu("Ölçüm Aletleri", subItems) { position ->
            showScopeOutSuccessMessage(subItems[position])
        }
    }

    /**
     * Alt kategori menüsünü gösterir ve seçilen öğeyi işler
     * @param title Menü başlığı
     * @param items Menü öğeleri
     * @param onItemSelected Öğe seçildiğinde çalışacak fonksiyon
     */
    private fun showSubMenu(title: String, items: Array<String>, onItemSelected: (Int) -> Unit) {
        // Alt menü diyaloğunu göster
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                onItemSelected(which)
            }
            .show()
    }

    /**
     * Kapsam dışı ayarlaması başarılı olduğunda bilgi mesajı gösterir
     * @param itemName Kapsam dışı yapılan öğenin adı
     */
    private fun showScopeOutSuccessMessage(itemName: String) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "$itemName kapsam dışı olarak ayarlandı",
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * Cihaz ekleme menüsünü gösterir
     * WebView'de mevcut cihaz listesini alır ve kullanıcıya sunar
     */
    private fun showDeviceAddMenu() {
        val currentTab = viewModel.activeTab.value
        val webViewFragment = currentTab?.let { pagerAdapter.getFragmentByTabId(it.id) }
        val webView = webViewFragment?.getWebView()

        if (webView != null) {
            // JavaScript ile cihaz listesini almak için DeviceManager'ı kullan
            // DeviceManager sınıfı henüz oluşturulmadığı için basit bir implementasyon kullanabiliriz
            
            // Geçici çözüm - kullanıcıya bilgi ver
            Toast.makeText(this, "Cihaz ekleme özelliği yakında...", Toast.LENGTH_SHORT).show()
            
            /* Gelecekteki implementasyon:
            val deviceManager = DeviceManager(this, webView)
            deviceManager.fetchDeviceList()
            */
        } else {
            Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Tüm fragment durumlarının doğruluğunu kontrol eder
     */
    private fun verifyFragmentStates() {
        val tabs = viewModel.tabs.value
        val cachedFragments = FragmentCache.getAllFragments()

        tabs.forEach { tab ->
            cachedFragments[tab.id]?.let { fragment ->
                // Eğer fragment aktif sekme ise, görüntü durumunu kontrol et
                if (tab.isActive) {
                    fragment.getWebView()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Context'i güncelle
        downloadManager.updateContext(this)
        
        // DataHolder değerlerini kontrol et ve logla
        println("onResume - DataHolder.urltoprak: '${DataHolder.urltoprak}'")
        println("onResume - DataHolder.topraklama: '${DataHolder.topraklama}'")
        println("onResume - DataHolder.url: '${DataHolder.url}'")
    }

    /**
     * URL'deki son rakamları DataHolder'a kaydeder
     * Aktif sekme değiştiğinde çağrılır
     */
    private fun saveLastDigitsToDataHolder(url: String) {
        if (url.isNotEmpty()) {
            // URL'den son rakamları ayıkla
            val lastDigits = extractLastDigits(url)
            
            // DataHolder'a kaydet
            DataHolder.url = lastDigits
            
            // Log ekle
            println("DataHolder.url güncellendi: $lastDigits")
            
            // WebView içeriğini kontrol et, "Topraklama Tesisatı" var mı?
            checkContentForTopraklama(url, lastDigits)
        }
    }
    
    /**
     * Verilen URL'den son rakamları çıkarır
     * 
     * @param url Analiz edilecek URL
     * @return URL'deki tüm rakamlar veya son rakamlar
     */
    private fun extractLastDigits(url: String): String {
        try {
            // URL'den tüm rakamları çıkar
            val digits = url.filter { it.isDigit() }
            
            // Eğer rakam yoksa boş string döndür
            if (digits.isEmpty()) {
                return ""
            }
            
            // Eğer URL bir IP adresi gibi görünüyorsa, tüm rakamları al
            if (url.contains(".") && url.split(".").size > 2) {
                return digits
            }
            
            // Diğer durumlarda son rakamları al
            val digitCount = 3  // Son kaç rakam alınacak
            
            return if (digits.length <= digitCount) {
                digits
            } else {
                digits.takeLast(digitCount)
            }
        } catch (e: Exception) {
            // Hata durumunda boş string döndür
            println("URL'den rakam çıkarılırken hata: ${e.message}")
            return ""
        }
    }
    
    /**
     * WebView içeriğinde "Topraklama Tesisatı" metni olup olmadığını kontrol eder
     * Eğer varsa, URL'nin son rakamlarını DataHolder'daki urltoprak'a kaydeder
     * 
     * **İyileştirildi: Fragment null kontrolü ve tekrar deneme mekanizması**
     */
    private fun checkContentForTopraklama(url: String, lastDigits: String) {
        // Log ekleyelim
        println("checkContentForTopraklama çağrıldı - URL: $url, LastDigits: $lastDigits")
        
        // Aktif sekmedeki WebView'i al
        val currentTab = viewModel.activeTab.value ?: run {
            println("currentTab is null")
            return
        }
        
        val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
        
        if (fragment == null) {
            println("fragment is null for tabId: ${currentTab.id} - tekrar denenecek")
            
            // Fragment henüz yüklenmemiş olabilir, 500ms sonra tekrar dene
            Handler(Looper.getMainLooper()).postDelayed({
                checkContentForTopraklama(url, lastDigits)
            }, 500)
            
            return
        }
        
        val webView = fragment.getWebView() ?: run {
            println("webView is null")
            return
        }
        
        // JavaScript ile sayfa içeriğinde "Topraklama Tesisatı" ara
        val checkContentScript = """
            (function() {
                var content = document.documentElement.innerHTML.toLowerCase();
                var found = content.indexOf('topraklama tesisat') !== -1 || content.indexOf('topraklama tesisatı') !== -1;
                
                if (found) {
                    // Daha spesifik kontrol: <p class="form-control-static">Topraklama Tesisatı</p>
                    var elements = document.querySelectorAll('p.form-control-static');
                    
                    for (var i = 0; i < elements.length; i++) {
                        var text = elements[i].textContent.trim().toLowerCase();
                        
                        if (text === 'topraklama tesisatı') {
                            return {found: true, specific: true};
                        }
                    }
                    
                    // Alternatif kontrol
                    return {found: true, specific: false};
                }
                
                return {found: false, specific: false};
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(checkContentScript) { result ->
            println("JavaScript sonucu: $result")
            
            try {
                // Result'u parse et
                val cleanResult = result.replace("^\"|\"$".toRegex(), "")
                val jsonResult = org.json.JSONObject(cleanResult)
                val found = jsonResult.optBoolean("found", false)
                val specific = jsonResult.optBoolean("specific", false)
                
                println("Found: $found, Specific: $specific")
                
                if (found && specific) {
                    // "Topraklama Tesisatı" bulundu, URL'nin rakamlarını kaydet
                    DataHolder.urltoprak = lastDigits
                    println("Topraklama Tesisatı bulundu! DataHolder.urltoprak güncellendi: $lastDigits")
                    
                    // UI'da bildirim gösterme (isteğe bağlı)
                    runOnUiThread {
                        Toast.makeText(this, "Topraklama Tesisatı sayfası tespit edildi", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    println("Bu sayfada Topraklama Tesisatı bulunamadı (found=$found, specific=$specific)")
                }
            } catch (e: Exception) {
                println("JavaScript sonucunu parse etme hatası: ${e.message}")
                println("Orijinal sonuc: $result")
                
                // Basit kontrol de yapalım
                if (result.contains("true") && result.contains("specific")) {
                    DataHolder.urltoprak = lastDigits
                    println("Basit kontrol ile Topraklama Tesisatı bulundu! DataHolder.urltoprak güncellendi: $lastDigits")
                    
                    runOnUiThread {
                        Toast.makeText(this, "Topraklama Tesisatı sayfası tespit edildi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    /**
     * ComboBox arama işlemini gerçekleştir
     */
    private fun performComboBoxSearch(searchTexts: List<String>) {
        // Mevcut aktif tab'ı al
        val currentTab = viewModel.activeTab.value ?: run {
            Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Tab fragment'ını al
        val fragment = pagerAdapter.getFragmentByTabId(currentTab.id) ?: run {
            Toast.makeText(this, "WebView bileşeni bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        
        // WebView bileşenini almak için fragment'ın doğrudansa WebView al
        val webView = fragment.getWebView() ?: run {
            Toast.makeText(this, "WebView yüklenemedi", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Arama başlatılıyor
        searchButton.text = "Aranıyor..."
        
        var searchIndex = 0
        var totalMatches = 0
        
        // Sırayla arama yap
        fun searchNext() {
            if (searchIndex < searchTexts.size) {
                val searchText = searchTexts[searchIndex]
                searchButton.text = "Aranan: $searchText"
                
                // JavaScript kodu ile WebView'de arama yap
                val searchScript = """
                    // ComboBox arama komut dizisi
                    (function() {
                        // Gelişmiş Türkçe karakter normalizasyonu
                        function normalizeText(text) {
                            if (!text) return '';
                            
                            // Önce küçük harfe çevir
                            text = text.toLowerCase();
                            
                            // Türkçe karakter haritası - HEM büyük HEM küçük harfleri içeriyor
                            var characterMap = {
                                'ç': 'c', 'Ç': 'c',
                                'ğ': 'g', 'Ğ': 'g',
                                'ı': 'i', 'I': 'i',
                                'İ': 'i', 'i': 'i',
                                'ş': 's', 'Ş': 's',
                                'ö': 'o', 'Ö': 'o',
                                'ü': 'u', 'Ü': 'u'
                            };
                            
                            // Tüm karakterleri değiştir
                            var result = '';
                            for (var i = 0; i < text.length; i++) {
                                var char = text[i];
                                result += characterMap[char] || char;
                            }
                            
                            // Ek olarak Unicode normalizasyonu yap
                            if (typeof result.normalize === 'function') {
                                result = result.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
                            }
                            
                            return result;
                        }
                        
                        // Arama fonksiyonunu tanımla
                        function findAndSelectCombobox(searchText) {
                            var found = false;
                            var normalizedSearchText = normalizeText(searchText);
                            
                            // Standart select elementleri ara
                            var selects = document.querySelectorAll('select');
                            for (var i = 0; i < selects.length; i++) {
                                var select = selects[i];
                                if (select.disabled || !select.offsetParent) continue;
                                
                                for (var j = 0; j < select.options.length; j++) {
                                    var option = select.options[j];
                                    var optionText = option.text;
                                    var normalizedOptionText = normalizeText(optionText);
                                    // Tam eşleşme önce kontrol edilir
                                    if (normalizedOptionText === normalizedSearchText) {
                                        select.selectedIndex = j;
                                        var event = new Event('change', { bubbles: true });
                                        select.dispatchEvent(event);
                                        select.scrollIntoView({behavior: 'smooth', block: 'center'});
                                        found = true;
                                        return {
                                            found: true,
                                            comboboxName: select.name || select.id || ('combobox_' + i),
                                            selectedItem: optionText,
                                            matchType: 'exact'
                                        };
                                    }
                                    // Sonra kısmi eşleşme kontrol edilir
                                    else if (normalizedOptionText.indexOf(normalizedSearchText) !== -1) {
                                        select.selectedIndex = j;
                                        var event = new Event('change', { bubbles: true });
                                        select.dispatchEvent(event);
                                        select.scrollIntoView({behavior: 'smooth', block: 'center'});
                                        found = true;
                                        return {
                                            found: true,
                                            comboboxName: select.name || select.id || ('combobox_' + i),
                                            selectedItem: optionText,
                                            matchType: 'partial'
                                        };
                                    }
                                }
                            }
                            
                            // Bootstrap select'ler ara
                            var bootstrapSelects = document.querySelectorAll('.bootstrap-select');
                            for (var i = 0; i < bootstrapSelects.length; i++) {
                                var select = bootstrapSelects[i].querySelector('select');
                                if (!select || select.disabled) continue;
                                
                                for (var j = 0; j < select.options.length; j++) {
                                    var option = select.options[j];
                                    var optionText = option.text;
                                    var normalizedOptionText = normalizeText(optionText);
                                    
                                    // Tam eşleşme önce
                                    if (normalizedOptionText === normalizedSearchText) {
                                        select.selectedIndex = j;
                                        var event = new Event('change', { bubbles: true });
                                        select.dispatchEvent(event);
                                        
                                        if (typeof jQuery !== 'undefined') {
                                            jQuery(select).selectpicker('val', option.value);
                                            jQuery(select).selectpicker('refresh');
                                        }
                                        
                                        bootstrapSelects[i].scrollIntoView({behavior: 'smooth', block: 'center'});
                                        found = true;
                                        return {
                                            found: true,
                                            comboboxName: select.name || select.id || ('bootstrap_' + i),
                                            selectedItem: optionText,
                                            matchType: 'exact'
                                        };
                                    }
                                    // Kısmi eşleşme sonra
                                    else if (normalizedOptionText.indexOf(normalizedSearchText) !== -1) {
                                        select.selectedIndex = j;
                                        var event = new Event('change', { bubbles: true });
                                        select.dispatchEvent(event);
                                        
                                        if (typeof jQuery !== 'undefined') {
                                            jQuery(select).selectpicker('val', option.value);
                                            jQuery(select).selectpicker('refresh');
                                        }
                                        
                                        bootstrapSelects[i].scrollIntoView({behavior: 'smooth', block: 'center'});
                                        found = true;
                                        return {
                                            found: true,
                                            comboboxName: select.name || select.id || ('bootstrap_' + i),
                                            selectedItem: optionText,
                                            matchType: 'partial'
                                        };
                                    }
                                }
                            }
                            
                            return {found: false};
                        }
                        
                        // Arama yap ve sonucu döndür
                        return findAndSelectCombobox('$searchText');
                    })();
                """.trimIndent()
                
                // JavaScript'i çalıştır
                webView.evaluateJavascript(searchScript) { result ->
                    try {
                        // JavaScript sonucunu işle
                        val cleanResult = result.replace("\"", "").replace("\\\\", "\\")
                        val jsonResult = org.json.JSONObject(cleanResult)
                        val found = jsonResult.optBoolean("found", false)
                        
                        if (found) {
                            totalMatches++
                            val comboboxName = jsonResult.optString("comboboxName", "")
                            val selectedItem = jsonResult.optString("selectedItem", "")
                            val matchType = jsonResult.optString("matchType", "unknown")
                            
                            // Buton durumunu güncelle
                            searchButton.text = "Ara (${savedSearchTexts.size} metin)"
                            println("Eşleşme bulundu: \"$searchText\" -> \"$selectedItem\" (Tip: $matchType)")
                        } else {
                            println("Eşleşme bulunamadı: \"$searchText\"")
                        }
                        
                        // Sonraki aramaya geç (gecikme ile)
                        Handler(Looper.getMainLooper()).postDelayed({
                            searchIndex++
                            searchNext()
                        }, 1000) // 1 saniye bekleme
                        
                    } catch (e: Exception) {
                        
                        // Hata olsa da sonraki aramaya geç
                        Handler(Looper.getMainLooper()).postDelayed({
                            searchIndex++
                            searchNext()
                        }, 1000)
                    }
                }
            } else {
                // Tüm aramalar tamamlandı
                Handler(Looper.getMainLooper()).postDelayed({
                    searchButton.text = "Ara (${savedSearchTexts.size} metin)"
                    if (totalMatches > 0) {
                       // Toast.makeText(this@MainActivity, "Tamamlandı: $totalMatches eşleşme bulundu", Toast.LENGTH_SHORT).show()
                    } else {
                        //Toast.makeText(this@MainActivity, "Hiç eşleşme bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                }, 500)
            }
        }
        
        // İlk aramayı başlat
        searchNext()
    }
    
    /**
     * Klavyeyi gizle
     */
    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        currentFocus?.let { view ->
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
    
    /**
     * Sayfa yüklenme tamamlanmayı bekler ve callback çalıştırır
     */
    private fun waitForPageLoad(fragment: WebViewFragment, urlKeyword: String, callback: () -> Unit) {
        // İlk 100ms'de hızlı kontroller
        val fastHandler = Handler(Looper.getMainLooper())
        var attempts = 0
        val maxAttempts = 20 // 2 saniye toplam
        
        val checkLoad = object : Runnable {
            override fun run() {
                val webView = fragment.getWebView()
                val currentUrl = webView?.url
                
                if (currentUrl?.contains(urlKeyword) == true && webView.progress == 100) {
                    // Sayfa yüklendi - daha fazla bekle form elemanları için
                    Handler(Looper.getMainLooper()).postDelayed({
                        callback.invoke()
                    }, 1000)
                } else if (attempts < maxAttempts) {
                    attempts++
                    fastHandler.postDelayed(this, 100) // 100ms aralıklarla kontrol
                } else {
                    // Timeout - yine de aramayı yap
                    callback.invoke()
                }
            }
        }
        
        fastHandler.post(checkLoad)
    }
    
    /**
     * QR kod arama işlemi
     * Eski projedeki performQrCodeSearch() mantığını uygula
     */
    private fun performQrCodeSearch() {
        val qrText = qrSearchInput.text.toString().trim()
        
        if (qrText.isEmpty()) {
            // Boş girdi durumunda sessizce çık
            return
        }
        
        // Mevcut aktif tab ve WebView bileşenini al
        val currentTab = viewModel.activeTab.value ?: return
        val fragment = pagerAdapter.getFragmentByTabId(currentTab.id) ?: return
        val webView = fragment.getWebView() ?: return
        
        // **Manuel arama modunu aktifleştir**
        isManualSearchActive = true
        
        // Eğer current page EquipmentList değilse önce oraya git
        val currentUrl = currentTab.url
        if (!currentUrl.contains("EquipmentList")) {
            // Navigate to EquipmentList page first
            webView.loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")
            
            // URL değişikliğine göre sayfa yüklenişini izle
            // Handler ile yükleme tamamlanmaya kadar bekle
            waitForPageLoad(fragment, "EquipmentList") {
                // Sayfa yüklendiğinde arama yap
                executeQrCodeSearch(fragment, qrText)
                // **Arama tamamlandıktan sonra manuel arama modunu kapat**
                Handler(Looper.getMainLooper()).postDelayed({
                    isManualSearchActive = false
                }, 2000)
            }
        } else {
            // Already on the correct page, execute search directly
            executeQrCodeSearch(fragment, qrText)
            // **Arama tamamlandıktan sonra manuel arama modunu kapat**
            Handler(Looper.getMainLooper()).postDelayed({
                isManualSearchActive = false
            }, 2000)
        }
    }
    
    /**
     * WebView'da QR kodunu ara ve sonucu kontrol et
     */
    private fun executeQrCodeSearch(fragment: WebViewFragment, qrText: String) {
        val webView = fragment.getWebView() ?: return
        
        // JavaScript ile QR kodunu ara
        val searchScript = """
            (function() {
                try {
                    // QR input alanını bul
                    var qrInput = document.querySelector('input#filter_qr');
                    if (!qrInput) {
                        return "QR input field not found";
                    }
                    
                    // QR değerini ayarla
                    qrInput.value = "$qrText";
                    
                    // Arama butonunu bul ve tıkla
                    var searchButton = document.querySelector('i.fa.fa-search[title="Filtrele"]');
                    if (!searchButton) {
                        return "Search button not found";
                    }
                    
                    // Arama butonunu tıkla
                    searchButton.click();
                    
                    return "Search executed";
                } catch(e) {
                    return "Error: " + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(searchScript) { result ->
            // Arama tamamlandıktan sonra sonuçları kontrol et
            Handler(Looper.getMainLooper()).postDelayed({
                checkQrCodeSearchResult(fragment, qrText)
            }, 1500)
        }
    }
    
    /**
     * QR kod arama sonuçlarını kontrol et
     * **İyileştirildi: QR arama alanını bulunan değerle güncellemeyi garanti eder**
     */
    private fun checkQrCodeSearchResult(fragment: WebViewFragment, qrText: String) {
        val webView = fragment.getWebView() ?: return
        
        val checkResultScript = """
            (function() {
                try {
                    // Sonuç değerini spesifik formatta bul
                    var resultElements = document.querySelectorAll('div.col-sm-8 p.form-control-static');
                    var results = [];
                    
                    for (var i = 0; i < resultElements.length; i++) {
                        var text = resultElements[i].textContent.trim();
                        if (text && /^\d+$/.test(text)) {  // Sadece sayı içeren değerleri kontrol et
                            results.push(text);
                        }
                    }
                    
                    if (results.length > 0) {
                        return JSON.stringify(results);
                    } else {
                        return "NO_RESULTS";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(checkResultScript) { result ->
            try {
                // Sonucu temizle
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                
                if (cleanResult == "NO_RESULTS") {
                    // Sessizce devam et - bilgi mesajı gösterme
                    return@evaluateJavascript
                }
                
                if (cleanResult.startsWith("ERROR")) {
                    // Sessizce devam et - hata mesajı gösterme
                    return@evaluateJavascript
                }
                
                // JSON sonucu parse et
                val jsonArray = org.json.JSONArray(cleanResult)
                
                if (jsonArray.length() > 0) {
                    val foundQrCode = jsonArray.getString(0)
                    
                    runOnUiThread {
                        // **QR edittext'i bulunan değerle güncelle**
                        qrSearchInput.setText(foundQrCode)
                        
                        // Güvenli setSelection
                        val textLength = qrSearchInput.text?.length ?: 0
                        val selectionEnd = minOf(foundQrCode.length, textLength)
                        if (selectionEnd >= 0 && selectionEnd <= textLength) {
                            qrSearchInput.setSelection(selectionEnd)
                        }
                        
                        // Ek garantili güncelleme - görevini tamamladığından emin ol
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (qrSearchInput.text.toString() != foundQrCode) {
                                qrSearchInput.setText(foundQrCode)
                                val newTextLength = qrSearchInput.text?.length ?: 0
                                val newSelectionEnd = minOf(foundQrCode.length, newTextLength)
                                if (newSelectionEnd >= 0 && newSelectionEnd <= newTextLength) {
                                    qrSearchInput.setSelection(newSelectionEnd)
                                }
                            }
                        }, 100)
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }
    
    /**
     * Seri numarası arama işlemi
     * Eski projedeki performSerialNumberSearch() mantığını uygula
     */
    private fun performSerialNumberSearch() {
        val serialText = serialSearchInput.text.toString().trim()
        
        if (serialText.isEmpty()) {
            // Boş girdi durumunda sessizce çık
            return
        }
        
        // Mevcut aktif tab ve WebView bileşenini al
        val currentTab = viewModel.activeTab.value ?: return
        val fragment = pagerAdapter.getFragmentByTabId(currentTab.id) ?: return
        val webView = fragment.getWebView() ?: return
        
        // Manuel seri numarası arama modunu aktifleştir
        isSerialSearchActive = true
        
        // Eğer current page EquipmentList değilse önce oraya git
        val currentUrl = currentTab.url
        if (!currentUrl.contains("EquipmentList")) {
            // Navigate to EquipmentList page first
            webView.loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")
            
            // URL değişikliğine göre sayfa yüklenişini izle
            // Handler ile yükleme tamamlanmaya kadar bekle
            waitForPageLoad(fragment, "EquipmentList") {
                // Sayfa yüklendiğinde arama yap
                executeSerialNumberSearch(fragment, serialText)
                isSerialSearchActive = false
            }
        } else {
            // Already on the correct page, execute search directly
            executeSerialNumberSearch(fragment, serialText)
            isSerialSearchActive = false
        }
    }
    
    /**
     * WebView'da seri numarasını ara
     */
    private fun executeSerialNumberSearch(fragment: WebViewFragment, serialText: String) {
        val webView = fragment.getWebView() ?: return
        
        // JavaScript ile seri numarasını ara
        val searchScript = """
            (function() {
                try {
                    // Seri numarası input alanını bul
                    var serialInput = document.querySelector('input#filter_serialnumber');
                    if (!serialInput) {
                        return "Serial number input field not found";
                    }
                    
                    // Seri numarası değerini ayarla
                    serialInput.value = "$serialText";
                    
                    // Arama butonunu bul ve tıkla
                    var searchButton = document.querySelector('i.fa.fa-search[title="Filtrele"]');
                    if (!searchButton) {
                        return "Search button not found";
                    }
                    
                    // Arama butonunu tıkla
                    searchButton.click();
                    
                    return "Search executed";
                } catch(e) {
                    return "Error: " + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(searchScript) { result ->
            // Arama tamamlandıktan sonra sonuçları kontrol et
            Handler(Looper.getMainLooper()).postDelayed({
                checkSerialNumberSearchResult(fragment, serialText)
            }, 1500)
        }
    }
    
    /**
     * Seri numarası arama sonuçlarını kontrol et
     */
    private fun checkSerialNumberSearchResult(fragment: WebViewFragment, serialText: String) {
        val webView = fragment.getWebView() ?: return
        
        val checkResultScript = """
            (function() {
                try {
                    // Sonuç değerini spesifik formatta bul
                    var resultElements = document.querySelectorAll('div.col-sm-8 p.form-control-static');
                    var results = [];
                    
                    for (var i = 0; i < resultElements.length; i++) {
                        var text = resultElements[i].textContent.trim();
                        if (text) {
                            results.push(text);
                        }
                    }
                    
                    if (results.length > 0) {
                        return JSON.stringify(results);
                    } else {
                        return "NO_RESULTS";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(checkResultScript) { result ->
            try {
                // Sonucu temizle
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                
                if (cleanResult == "NO_RESULTS") {
                    // Sessizce devam et - bilgi mesajı gösterme
                    return@evaluateJavascript
                }
                
                if (cleanResult.startsWith("ERROR")) {
                    // Sessizce devam et - hata mesajı gösterme
                    return@evaluateJavascript
                }
                
                // JSON sonucu parse et
                val jsonArray = org.json.JSONArray(cleanResult)
                
                if (jsonArray.length() > 0) {
                    val foundSerialNumber = jsonArray.getString(0)
                    
                    runOnUiThread {
                        // Seri No edittext'i bulunan değerle güncelle
                        serialSearchInput.setText(foundSerialNumber)
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }
    
    /**
     * Otomatik QR kod kontrolü - her sayfada QR kodu olup olmadığını kontrol eder
     * Eski projedeki checkForQrCodeOnPage() mantığını uygula
     * **İyileştirildi: QR arama alanını otomatik olarak güncellenir ve Fragment null kontrolü**
     */
    private fun checkForQrCodeOnPage() {
        // Aktif sekmedeki WebView'i al
        val currentTab = viewModel.activeTab.value ?: return
        val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
        
        if (fragment == null) {
            println("checkForQrCodeOnPage: fragment is null for tabId: ${currentTab.id}")
            return
        }
        
        val webView = fragment.getWebView() ?: return
        
        val checkResultScript = """
            (function() {
                try {
                    // Sonuç değerini spesifik formatta bul
                    var resultElements = document.querySelectorAll('div.col-sm-8 p.form-control-static');
                    var results = [];
                    
                    for (var i = 0; i < resultElements.length; i++) {
                        var text = resultElements[i].textContent.trim();
                        if (text && /^\d+$/.test(text)) {  // Sadece sayı içeren değerleri kontrol et
                            results.push(text);
                        }
                    }
                    
                    if (results.length > 0) {
                        return JSON.stringify(results);
                    } else {
                        return "NO_RESULTS";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(checkResultScript) { result ->
            try {
                // Sonucu temizle
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                
                if (cleanResult == "NO_RESULTS" || cleanResult.startsWith("ERROR")) {
                    // Herhangi bir hata veya sonuç bulunamazsa sessizce çık
                    return@evaluateJavascript
                }
                
                // JSON sonucu parse et
                val jsonArray = org.json.JSONArray(cleanResult)
                
                if (jsonArray.length() > 0) {
                    val foundQrCode = jsonArray.getString(0)
                    
                    // Bulunan değer şu anki değerden farklıysa güncelle
                    val currentQrText = qrSearchInput.text.toString().trim()
                    if (foundQrCode != currentQrText) {
                        runOnUiThread {
                            // **QR edittext'i bulunan değerle güncelle**
                            qrSearchInput.setText(foundQrCode)
                            
                            // Güvenli setSelection
                            val textLength = qrSearchInput.text?.length ?: 0
                            val selectionEnd = minOf(foundQrCode.length, textLength)
                            if (selectionEnd >= 0 && selectionEnd <= textLength) {
                                qrSearchInput.setSelection(selectionEnd)
                            }
                            
                            // Ek garantili güncelleme - görevini tamamladığından emin ol
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (qrSearchInput.text.toString() != foundQrCode) {
                                    qrSearchInput.setText(foundQrCode)
                                    val newTextLength = qrSearchInput.text?.length ?: 0
                                    val newSelectionEnd = minOf(foundQrCode.length, newTextLength)
                                    if (newSelectionEnd >= 0 && newSelectionEnd <= newTextLength) {
                                        qrSearchInput.setSelection(newSelectionEnd)
                                    }
                                }
                            }, 100)
                        }
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }
    
    /**
     * QR Tarama Dialog'unu göster
     */
    private fun showQrScannerDialog() {
        QRScannerDialog.show(
            context = this,
            onQRCodeScanned = { qrContent ->
                // QR kod içeriğini işle
                processQrCodeResult(qrContent)
            },
            onDismiss = {
                // Dialog kapatıldığında yapılacaklar (eğer gerekiyorsa)
            }
        )
    }
    
    /**
     * QR kod sonucunu işle
     * Özellikle szutest.com.tr URL'leri için optimize edilmiştir
     */
    private fun processQrCodeResult(qrContent: String) {
        // Boş içerik kontrolü
        if (qrContent.isBlank()) {
            Toast.makeText(this, "QR kod boş veya okunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        
        // QR kodunun szutest.com.tr ile ilgili olup olmadığını kontrol et
        val isSzutestUrl = qrContent.contains("szutest.com.tr", ignoreCase = true) ||
                          qrContent.all { it.isDigit() } // Sayısal içerik de szutest için olabilir
        
        // Adres çubuğundaki değeri URL olarak yükle - force load parametresi ile
        loadUrl(qrContent, true)
        
        // 500ms gecikme ile adres çubuğunu güncelle (sayfa yüklemesi başladıktan sonra)
        Handler(Looper.getMainLooper()).postDelayed({
            // QR kodunu adres çubuğuna yaz
            binding.addressBar.setText(qrContent)
            
            // Güvenli setSelection - adres çubuğu için
            val textLength = binding.addressBar.text?.length ?: 0
            val selectionEnd = minOf(qrContent.length, textLength)
            if (selectionEnd >= 0 && selectionEnd <= textLength) {
                binding.addressBar.setSelection(selectionEnd)
            }
        }, 500)
        
        // Bilgi mesajı göster
        if (isSzutestUrl) {
            Toast.makeText(this, "Cihaz sayfası açılıyor...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "QR Kod tarandı, sayfa açılıyor...", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Kamera izni iste
     */
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * İzin sonucunu yönet
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // İzin verildi, QR tarama dialog'unu göster
                    showQrScannerDialog()
                } else {
                    // İzin reddedildi
                    Toast.makeText(this, "QR kod taramak için kamera izni gereklidir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    companion object {
        // Kamera izin kodu
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onDestroy() {
        super.onDestroy()

        // Handler'ları temizle
        qrCheckRunnable?.let { qrCheckHandler?.removeCallbacks(it) }
        qrCheckHandler = null
        
        // İndirme modülünü temizle
        webViewDownloadHelper.cleanup()

        // Aktivite kapatılırken tüm fragment durumlarını kaydet ve Fragment Cache'i temizle
        if (isFinishing) {
            // Tüm fragment durumlarını kaydet
            viewModel.tabs.value.forEach { tab ->
                FragmentCache.saveFragmentState(tab.id, supportFragmentManager)
            }

            // Cache'i temizle
            FragmentCache.clearFragments()
        } else {
            // Sadece aktif fragment'i kaydet
            saveCurrentFragmentState()
        }
    }
}
