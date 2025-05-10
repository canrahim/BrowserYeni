package com.asforce.asforcebrowser

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity - Ana ekran
 * Tarayıcı uygulamasının ana aktivitesi, sekme yönetimi ve kullanıcı arayüzünü kontrol eder.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupDownloadManager()
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

        // Menü açma butonu dinleyicisi
        binding.menuOpenButton.setOnClickListener { view ->
            showBrowserMenu(view)
        }

        // Yeni sekme butonu dinleyicisi
        binding.newTabButton.setOnClickListener {
            viewModel.createNewTab("https://www.google.com")
        }

        // Menü butonu dinleyicisi
        binding.menuButton.setOnClickListener { view ->
            showBrowserMenu(view)
        }
    }

    private fun showBrowserMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.browser_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    val currentTab = viewModel.activeTab.value ?: return@setOnMenuItemClickListener false
                    pagerAdapter.getFragmentByTabId(currentTab.id)?.refresh()
                    true
                }
                R.id.action_forward -> {
                    val currentTab = viewModel.activeTab.value ?: return@setOnMenuItemClickListener false
                    val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
                    if (fragment?.canGoForward() == true) {
                        fragment.goForward()
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