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
import com.asforce.asforcebrowser.util.viewpager.ViewPager2Optimizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity - Ana ekran
 * 
 * Tarayıcı uygulamasının ana aktivitesi, sekme yönetimi ve kullanıcı arayüzünü kontrol eder.
 * Referans: Android Activity yaşam döngüsü ve ViewBinding
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), WebViewFragment.BrowserCallback {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private lateinit var tabAdapter: TabAdapter
    private lateinit var pagerAdapter: BrowserPagerAdapter
    
    // ViewPager2 optimizasyonu için
    private lateinit var viewPagerOptimizer: ViewPager2Optimizer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // ViewBinding ile layout'u bağla
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Status bar ve navigation bar için inset'leri ayarla
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupAdapters()
        setupListeners()
        observeViewModel()
        
        // Ekran yönü değişimini dinle
        handleOrientationChanges()
    }
    
    private fun setupAdapters() {
        // ViewPager2 optimizer'i başlat
        viewPagerOptimizer = ViewPager2Optimizer(this)
        // Sekmeler için RecyclerView
        tabAdapter = TabAdapter(
            onTabSelected = { tab ->
                android.util.Log.d("MainActivity", "Sekme seçildi: ID=${tab.id}, URL=${tab.url}")
                viewModel.setActiveTab(tab.id)
            },
            onTabClosed = { tab ->
                android.util.Log.d("MainActivity", "Sekme kapatıldı: ID=${tab.id}")
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
        binding.viewPager.adapter = pagerAdapter
        
        // ViewPager2 optimize edici ile yapılandırma
        viewPagerOptimizer.optimizeViewPager(binding.viewPager, pagerAdapter)
        
        // ViewPager2'nin fragment yönetimini güncelleme
        android.util.Log.d("MainActivity", "ViewPager2 yapılandırması tamamlandı")
        
        // ViewPager sayfa değişim dinleyicisi
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                android.util.Log.d("MainActivity", "ViewPager sayfa seçildi: $position")
                
                pagerAdapter.getTabAt(position)?.let { tab ->
                    android.util.Log.d("MainActivity", "Seçilen tab: ID=${tab.id}, Active=${tab.isActive}, URL=${tab.url}")
                    
                    if (!tab.isActive) {
                        android.util.Log.d("MainActivity", "Tab aktif değil, aktif yapılıyor: ${tab.id}")
                        viewModel.setActiveTab(tab.id)
                    }
                    
                    // WebView fragmentini kontrol et
                    val fragment = pagerAdapter.getFragmentByTabId(tab.id)
                    android.util.Log.d("MainActivity", "Seçilen fragment: ${fragment != null}")
                    
                    // Adres çubuğunu güncelle
                    viewModel.updateAddressBar(tab.url)
                }
            }
            
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                // Kaydırma durumu - 0: boşta, 1: sürüklüyor, 2: ayarlı
                val stateText = when(state) {
                    ViewPager2.SCROLL_STATE_IDLE -> "Boşta"
                    ViewPager2.SCROLL_STATE_DRAGGING -> "Sürüklüyor"
                    ViewPager2.SCROLL_STATE_SETTLING -> "Ayarlanıyor"
                    else -> "Bilinmeyen"
                }
                android.util.Log.d("MainActivity", "ViewPager kaydırma durumu değişti: $stateText")
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
                android.util.Log.d("MainActivity", "Sekme listesi güncellendi: ${tabs.size} sekme")
                tabAdapter.updateTabs(tabs)
                pagerAdapter.updateTabs(tabs)
            }
        }
        
        // Aktif sekmeyi gözlemle
        lifecycleScope.launch {
            viewModel.activeTab.collectLatest { activeTab ->
                activeTab?.let {
                    android.util.Log.d("MainActivity", "Aktif sekme değişti: ID=${it.id}, URL=${it.url}")
                    val position = pagerAdapter.getPositionForTabId(it.id)
                    
                    if (position != -1) {
                        // Mevcut pozisyondan farklı ise, görünümü güncelle
                        if (binding.viewPager.currentItem != position) {
                            android.util.Log.d("MainActivity", "ViewPager pozisyonu değiştiriliyor: $position")
                            
                            // Optimize edilmiş tab geçişi - zorla yeniden yükleme
                            viewPagerOptimizer.setCurrentTabForceRefresh(binding.viewPager, position)
                        }
                        
                        // Fragment geçişlerini izle
                        viewPagerOptimizer.monitorFragmentSwitching(
                            binding.viewPager,
                            pagerAdapter.fragments,
                            it.id
                        ) { fragment ->
                            // Doğru fragment seçildiğinde yapacaklar
                            android.util.Log.d("MainActivity", "Fragmente geçildi: TabID=${it.id}")
                        }
                        
                        // WebView'u kontrol et ve güncelle
                        val fragment = pagerAdapter.getFragmentByTabId(it.id)
                        android.util.Log.d("MainActivity", "Aktif sekme fragment: ${fragment != null}")
                    } else {
                        android.util.Log.e("MainActivity", "Hata: Aktif sekme için geçersiz pozisyon: ${it.id}")
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
     * 
     * AndroidManifest.xml'deki configChanges sayesinde Activity yeniden oluşturulmaz
     * ancak layout'un yeniden düzenlenmesi gerekebilir.
     */
    private fun handleOrientationChanges() {
        // Ekran yönü değişikliği için dinleyici
        val currentOrientation = resources.configuration.orientation
        android.util.Log.d("MainActivity", "Ekran yönü: " + 
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "Yatay" else "Dikey")
        
        // configChanges kullanıldığı için onConfigurationChanged callback metodumuzu ekleyelim
    }
    
    /**
     * Ekran yönü değiştiğinde çağrılır
     * 
     * Bu metot configChanges değeri belirtildiği için çağrılır
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Ekran yönünü logla
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        android.util.Log.d("MainActivity", "Ekran yönü değişti: " + if (isLandscape) "Yatay" else "Dikey")
        
        // ViewPager'in fragment durumunu korumasını sağla
        val currentItem = binding.viewPager.currentItem
        
        // ViewPager2 optimizasyonunu yeniden uygula
        viewPagerOptimizer.optimizeViewPager(binding.viewPager, pagerAdapter)
        
        // Seçili sekmenin WebView içeriğini yeniden düzenle
        val currentTab = viewModel.activeTab.value
        if (currentTab != null) {
            val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
            
            // Fragment yeniden yüklenmek zorunda kalmadan, içerik yeniden düzenlenecek
            binding.viewPager.post {
                // Geçerli sekme pozisyonuna geri dön
                if (binding.viewPager.currentItem != currentItem) {
                    binding.viewPager.setCurrentItem(currentItem, false)
                }
                
                // Uygun fragment bulunduysa, fragmenti yeniden oluşturmadan güncelle
                fragment?.let {
                    android.util.Log.d("MainActivity", "Aktif fragment düzenleniyor: TabID=${currentTab.id}")
                }
            }
        }
    }
}