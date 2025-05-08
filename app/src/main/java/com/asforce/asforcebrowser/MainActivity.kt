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
    }
    
    private fun setupAdapters() {
        // Sekme adaptörünü ayarla
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
        binding.viewPager.adapter = pagerAdapter
        
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
            val position = pagerAdapter.getPositionForTabId(currentTab.id)
            val fragment = pagerAdapter.getFragmentByTabId(currentTab.id)
            
            if (fragment?.canGoBack() == true) {
                fragment.goBack()
            }
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
                tabAdapter.updateTabs(tabs)
                pagerAdapter.updateTabs(tabs)
            }
        }
        
        // Aktif sekmeyi gözlemle
        lifecycleScope.launch {
            viewModel.activeTab.collectLatest { activeTab ->
                activeTab?.let {
                    val position = pagerAdapter.getPositionForTabId(it.id)
                    if (position != -1 && binding.viewPager.currentItem != position) {
                        binding.viewPager.setCurrentItem(position, true)
                    }
                    
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
}