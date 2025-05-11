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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupDownloadManager()
        setupSearchDialog()
        setupAdapters()
        setupListeners()
        observeViewModel()
        handleOrientationChanges()
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
        
        // Search dialog'u oluştur
        searchDialog = SearchDialog(this)
        
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

    private fun loadUrl(url: String) {
        val currentTab = viewModel.activeTab.value
        if (currentTab != null) {
            pagerAdapter.getFragmentByTabId(currentTab.id)?.loadUrl(url)
        } else {
            // Eğer aktif sekme yoksa yeni bir sekme oluşturuyoruz
            viewModel.createNewTab(url)
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
     */
    private fun checkContentForTopraklama(url: String, lastDigits: String) {
        // Log ekleyelim
        println("checkContentForTopraklama çağrıldı - URL: $url, LastDigits: $lastDigits")
        
        // Aktif sekmedeki WebView'i al
        val currentTab = viewModel.activeTab.value ?: run {
            println("currentTab is null")
            return
        }
        
        val fragment = pagerAdapter.getFragmentByTabId(currentTab.id) ?: run {
            println("fragment is null for tabId: ${currentTab.id}")
            return
        }
        
        val webView = fragment.getWebView() ?: run {
            println("webView is null")
            return
        }
        
        // JavaScript ile sayfa içeriğinde "Topraklama Tesisatı" ara
        val checkContentScript = """
            (function() {
                console.log('Sayfa içeriği kontrol ediliyor...');
                var content = document.documentElement.innerHTML.toLowerCase();
                var found = content.indexOf('topraklama tesisat') !== -1 || content.indexOf('topraklama tesisatı') !== -1;
                
                console.log('Böylelikle: ' + found);
                
                if (found) {
                    // Daha spesifik kontrol: <p class="form-control-static">Topraklama Tesisatı</p>
                    var elements = document.querySelectorAll('p.form-control-static');
                    console.log('p.form-control-static elementleri: ' + elements.length);
                    
                    for (var i = 0; i < elements.length; i++) {
                        var text = elements[i].textContent.trim().toLowerCase();
                        console.log('Element ' + i + ' text: "' + text + '"');
                        
                        if (text === 'topraklama tesisatı') {
                            console.log('BULUNDU: Topraklama Tesisatı spesifik olarak bulundu!');
                            return {found: true, specific: true};
                        }
                    }
                    
                    // Alternatif kontrol
                    console.log('Özel format bulunamadı, genel kontrol yapılıyor...');
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
                            console.log('Normalized search text: "' + normalizedSearchText + '"');
                            
                            // Standart select elementleri ara
                            var selects = document.querySelectorAll('select');
                            for (var i = 0; i < selects.length; i++) {
                                var select = selects[i];
                                if (select.disabled || !select.offsetParent) continue;
                                
                                for (var j = 0; j < select.options.length; j++) {
                                    var option = select.options[j];
                                    var optionText = option.text;
                                    var normalizedOptionText = normalizeText(optionText);
                                    
                                    console.log('Comparing: "' + optionText + '" (normalized: "' + normalizedOptionText + '") with "' + searchText + '" (normalized: "' + normalizedSearchText + '")');
                                    
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
                        Toast.makeText(this@MainActivity, "Arama hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                        
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
                        Toast.makeText(this@MainActivity, "Tamamlandı: $totalMatches eşleşme bulundu", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Hiç eşleşme bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                }, 500)
            }
        }
        
        // İlk aramayı başlat
        searchNext()
    }
    
    override fun onDestroy() {
        super.onDestroy()

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
