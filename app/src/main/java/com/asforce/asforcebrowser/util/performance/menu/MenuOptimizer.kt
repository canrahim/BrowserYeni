package com.asforce.asforcebrowser.util.performance.menu

import android.content.Context
import android.webkit.WebView
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MenuOptimizer - Web sitelerindeki menü açılma ve kapanma performansını optimize eder
 *
 * Bu sınıf, WebView içerisinde yüklenen web sitelerindeki açılır menülerin
 * performansını iyileştirmek için özel JavaScript optimizasyonları enjekte eder.
 *
 * Referanslar:
 * - Web Performance API (MDN)
 * - DOM Event Optimization best practices
 * - CSS Animation Performance Guide
 */
class MenuOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "MenuOptimizer"
    }

    /**
     * Menü optimizasyonlarını WebView'a enjekte eder
     * @param webView Optimize edilecek WebView instance'ı
     */
    fun optimizeMenuPerformance(webView: WebView) {
        // Önce CSS optimizasyonlarını enjekte et
        injectMenuOptimizationCSS(webView)
        
        // Sonra JavaScript optimizasyonlarını uygula
        val optimizationScript = buildMenuOptimizationScript()
        
        // Script'i WebView'a enjekte et
        webView.evaluateJavascript(optimizationScript, null)
    }
    
    /**
     * Menü optimizasyon CSS'ini WebView'a enjekte eder
     * @param webView WebView instance'ı
     */
    private fun injectMenuOptimizationCSS(webView: WebView) {
        try {
            // Assets klasöründen CSS dosyasını oku
            val css = context.assets.open("menu_optimizations.css").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
            
            // CSS'i JavaScript ile enjekte et
            val script = """
                (function() {
                    var style = document.createElement('style');
                    style.innerHTML = `$css`;
                    style.id = 'asforce-menu-optimizations';
                    
                    // Önceki CSS'i kaldır (var ise)
                    var existingStyle = document.getElementById('asforce-menu-optimizations');
                    if (existingStyle) {
                        existingStyle.remove();
                    }
                    
                    // Yeni CSS'i ekle
                    document.head.appendChild(style);
                    console.log('AsforceBrowser: Menü CSS optimizasyonları yüklendi');
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Menü optimizasyon script'ini oluşturur
     * @return String - Enjekte edilecek JavaScript kodu
     */
    private fun buildMenuOptimizationScript(): String {
        return """
            (function() {
                console.log('AsforceBrowser: JavaScript menü optimizasyonları uygulanıyor...');
                
                // 1. Mevcut menü animasyonlarını optimize et
                optimizeMenuAnimations();
                
                // 2. Event listener'ları optimize et
                optimizeMenuEventListeners();
                
                // 3. DOM manipülasyonlarını optimize et
                optimizeDOMManipulation();
                
                // 4. CSS transition'ları optimize et
                optimizeMenuTransitions();
                
                // 5. Menü içeriğinin lazy loading'ini ayarla
                setupMenuLazyLoading();
                
                // 6. Mobil touch event'leri optimize et
                optimizeTouchEvents();
                
                // 7. Responsive menü davranışını optimize et
            optimizeResponsiveMenu();
            
            // 8. Son kontrol: Menü öğelerinin tümünü tekrar optimize et
            finalizeMenuOptimizations();
            
            console.log('AsforceBrowser: JavaScript menü optimizasyonları başarıyla uygulandı');
            })();
            
            // Menü animasyonlarını optimize etme fonksiyonu
            function optimizeMenuAnimations() {
                // CSS animasyonlarını hardware acceleration ile optimize et
                const menuElements = document.querySelectorAll('.side-menu, .sidebar-inner, .mdi-menu, [class*="menu"]');
                
                menuElements.forEach(el => {
                    // Hardware acceleration'ı etkinleştir
                    el.style.transform = 'translateZ(0)';
                    el.style.willChange = 'transform, opacity';
                    
                    // Smooth scrolling için
                    el.style.scrollBehavior = 'smooth';
                    
                    // Tek seferlik transition'ları kaldır
                    el.style.transition = 'all 0.2s ease-out';
                });
                
                // İcon'lar için özel optimizasyon
                const icons = document.querySelectorAll('.mdi, [class*="mdi-"]');
                icons.forEach(icon => {
                    icon.style.backfaceVisibility = 'hidden';
                    icon.style.transform = 'translateZ(0)';
                });
            }
            
            // Event listener'ları optimize etme fonksiyonu
            function optimizeMenuEventListeners() {
                // Eski event listener'ları temizle
                let menuButtons = document.querySelectorAll('.mdi-menu, [class*="menu-toggle"], .has_sub > a');
                
                menuButtons.forEach(button => {
                    // Throttle/debounce mekanizması ekle
                    let timeout;
                    const originalOnClick = button.onclick;
                    
                    button.onclick = function(e) {
                        clearTimeout(timeout);
                        timeout = setTimeout(() => {
                            if (originalOnClick) {
                                originalOnClick.call(this, e);
                            }
                        }, 10); // 10ms debounce
                    };
                    
                    // Passive event listener kullan
                    if (button.addEventListener) {
                        button.addEventListener('touchstart', function(e) {
                            e.preventDefault();
                        }, { passive: false });
                    }
                });
                
                // Global menü event handler'ı ekle
                document.addEventListener('click', function(e) {
                    const menuTrigger = e.target.closest('.has_sub > a, .mdi-menu');
                    if (menuTrigger) {
                        // Menü açılma işlemini optimize et
                        requestAnimationFrame(() => {
                            handleMenuToggle(menuTrigger);
                        });
                    }
                }, { passive: false });
            }
            
            // DOM manipülasyonlarını optimize etme fonksiyonu
            function optimizeDOMManipulation() {
                // DocumentFragment kullanarak batch DOM updates
                let originalAppendChild = Node.prototype.appendChild;
                let batchQueue = [];
                let batchScheduled = false;
                
                Node.prototype.appendChild = function(...args) {
                    if (this.classList && this.classList.contains('list-unstyled')) {
                        batchQueue.push({ node: this, args: args });
                        
                        if (!batchScheduled) {
                            batchScheduled = true;
                            requestAnimationFrame(() => {
                                let fragment = document.createDocumentFragment();
                                batchQueue.forEach(item => {
                                    originalAppendChild.apply(item.node, item.args);
                                });
                                batchQueue = [];
                                batchScheduled = false;
                            });
                        }
                        return args[0];
                    }
                    return originalAppendChild.apply(this, args);
                };
            }
            
            // CSS transition'ları optimize etme fonksiyonu
            function optimizeMenuTransitions() {
                // GPU-friendly CSS properties kullan
                const style = document.createElement('style');
                style.textContent = `
                    .has_sub .list-unstyled {
                        transform: translateY(-100%);
                        opacity: 0;
                        transition: transform 0.2s ease-out, opacity 0.2s ease-out;
                        will-change: transform, opacity;
                    }
                    
                    .has_sub.active .list-unstyled {
                        transform: translateY(0);
                        opacity: 1;
                    }
                    
                    .side-menu {
                        transform: translate3d(0, 0, 0);
                        transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
                    }
                    
                    .mdi-menu {
                        transition: transform 0.2s ease;
                    }
                    
                    .mdi-menu:active {
                        transform: scale(0.95);
                    }
                    
                    @media (max-width: 768px) {
                        .has_sub .list-unstyled {
                            transition: all 0.15s ease-out !important;
                        }
                    }
                `;
                document.head.appendChild(style);
            }
            
            // Menü içeriğinin lazy loading'ini ayarlama fonksiyonu
            function setupMenuLazyLoading() {
                // Alt menüleri sadece ana menü açıldığında yükle
                const subMenus = document.querySelectorAll('.has_sub .list-unstyled');
                const observer = new IntersectionObserver((entries) => {
                    entries.forEach(entry => {
                        if (entry.isIntersecting) {
                            // İçeriği yükle
                            const links = entry.target.querySelectorAll('a[data-href]');
                            links.forEach(link => {
                                if (link.dataset.href) {
                                    link.href = link.dataset.href;
                                    link.removeAttribute('data-href');
                                }
                            });
                            observer.unobserve(entry.target);
                        }
                    });
                }, { rootMargin: '50px' });
                
                subMenus.forEach(menu => {
                    // Icon'ları lazy load et
                    const icons = menu.querySelectorAll('.mdi');
                    icons.forEach(icon => {
                        icon.style.fontFamily = 'Material Design Icons';
                    });
                    
                    observer.observe(menu);
                });
            }
            
            // Touch event'leri optimize etme fonksiyonu
            function optimizeTouchEvents() {
                // Touch delay'ini azalt
                document.addEventListener('touchstart', function() {}, { passive: true });
                
                // Menü touch handler'ını optimize et
                const menuElements = document.querySelectorAll('.side-menu, .mdi-menu');
                menuElements.forEach(el => {
                    el.addEventListener('touchstart', function(e) {
                        this.style.transition = 'none';
                        requestAnimationFrame(() => {
                            this.style.transition = '';
                        });
                    }, { passive: true });
                });
                
                // FastClick benzeri davranış için
                if ('ontouchstart' in window) {
                    let lastTap = 0;
                    document.addEventListener('touchend', function(e) {
                        let currentTime = new Date().getTime();
                        let tapLength = currentTime - lastTap;
                        
                        if (tapLength < 500 && tapLength > 0) {
                            e.preventDefault();
                            e.target.click();
                        }
                        
                        lastTap = currentTime;
                    });
                }
            }
            
            // Menü toggle işlemini optimize etme fonksiyonu
            function handleMenuToggle(trigger) {
                const subMenu = trigger.nextElementSibling;
                const parentLi = trigger.parentElement;
                
                if (subMenu && subMenu.classList.contains('list-unstyled')) {
                    // Batch updates
                    requestAnimationFrame(() => {
                        if (parentLi.classList.contains('active')) {
                            parentLi.classList.remove('active');
                            subMenu.style.height = '0px';
                            subMenu.style.overflow = 'hidden';
                        } else {
                            parentLi.classList.add('active');
                            subMenu.style.height = 'auto';
                            const height = subMenu.offsetHeight;
                            subMenu.style.height = '0px';
                            
                            requestAnimationFrame(() => {
                                subMenu.style.height = height + 'px';
                                subMenu.style.overflow = 'visible';
                            });
                        }
                    });
                }
                
                // Diğer açık menüleri kapat
                const otherOpenMenus = document.querySelectorAll('.has_sub.active');
                otherOpenMenus.forEach(menu => {
                    if (menu !== parentLi) {
                        menu.classList.remove('active');
                        const submenu = menu.querySelector('.list-unstyled');
                        if (submenu) {
                            submenu.style.height = '0px';
                        }
                    }
                });
            }
            
            // Scroll performansını optimize et
            function optimizeScrollPerformance() {
                const sidebar = document.querySelector('.sidebar-inner');
                if (sidebar) {
                    // Momentum scrolling
                    sidebar.style.webkitOverflowScrolling = 'touch';
                    
                    // Virtual scrolling için container oluştur
                    let isScrolling = false;
                    let scrollTimeout;
                    
                    sidebar.addEventListener('scroll', function() {
                        window.clearTimeout(scrollTimeout);
                        
                        if (!isScrolling) {
                            // Scroll başladı
                            isScrolling = true;
                            this.style.pointerEvents = 'none';
                        }
                        
                        scrollTimeout = setTimeout(function() {
                            // Scroll bitti
                            isScrolling = false;
                            sidebar.style.pointerEvents = 'auto';
                        }, 66); // ~15fps update rate
                    }, { passive: true });
                }
            }
            
            // Başlangıçta scroll optimizasyonunu da çalıştır
            optimizeScrollPerformance();
                
                // Responsive menü optimizasyon fonksiyonu
                function optimizeResponsiveMenu() {
                    // Mobil cihazlar için hamburger menü optimizasyonu
                    const hamburgerMenu = document.querySelector('.mdi-menu');
                    const sideMenu = document.querySelector('.side-menu');
                    
                    if (hamburgerMenu && sideMenu) {
                        // Mobil menü açma/kapama için optimize edilmiş handler
                        let isAnimating = false;
                        
                        const toggleMenu = function(e) {
                            if (isAnimating) return;
                            
                            isAnimating = true;
                            e.preventDefault();
                            e.stopPropagation();
                            
                            // Menü durumunu kontrol et
                            const isOpen = sideMenu.classList.contains('show') || 
                                         sideMenu.style.left === '0px' ||
                                         sideMenu.style.transform === 'translateX(0px)';
                            
                            requestAnimationFrame(() => {
                                if (isOpen) {
                                    // Menüyü kapat
                                    sideMenu.style.transform = 'translateX(-100%)';
                                    sideMenu.classList.remove('show');
                                } else {
                                    // Menüyü aç
                                    sideMenu.style.transform = 'translateX(0)';
                                    sideMenu.classList.add('show');
                                }
                                
                                setTimeout(() => {
                                    isAnimating = false;
                                }, 250);
                            });
                        };
                        
                        // Event listener'ları ekle
                        hamburgerMenu.removeEventListener('click', toggleMenu);
                        hamburgerMenu.addEventListener('click', toggleMenu, { passive: false });
                        
                        // Touch olayları için de
                        hamburgerMenu.removeEventListener('touchend', toggleMenu);
                        hamburgerMenu.addEventListener('touchend', toggleMenu, { passive: false });
                    }
                    
                    // Viewport değişikliklerini izle
                    const mediaQuery = window.matchMedia('(max-width: 768px)');
                    const handleMediaChange = (mq) => {
                        if (mq.matches) {
                            // Mobil mod
                            document.body.classList.add('mobile-menu');
                        } else {
                            // Desktop mod
                            document.body.classList.remove('mobile-menu');
                            if (sideMenu) {
                                sideMenu.style.transform = '';
                                sideMenu.classList.remove('show');
                            }
                        }
                    };
                    
                    mediaQuery.addListener(handleMediaChange);
                    handleMediaChange(mediaQuery);
                }
                
                // Final optimizasyon fonksiyonu
                function finalizeMenuOptimizations() {
                    // Tüm menü öğelerini topla
                    const menuItems = document.querySelectorAll('.sidebar-menu li, .side-menu li');
                    
                    menuItems.forEach(item => {
                        // Hover performansı için
                        item.addEventListener('mouseenter', function() {
                            this.style.willChange = 'background-color';
                        }, { passive: true });
                        
                        item.addEventListener('mouseleave', function() {
                            this.style.willChange = 'auto';
                        }, { passive: true });
                        
                        // Click delay'ini azalt
                        const link = item.querySelector('a');
                        if (link) {
                            link.style.cursor = 'pointer';
                            link.style.touchAction = 'manipulation';
                        }
                    });
                    
                    // Scroll container optimizasyonu
                    const scrollContainers = document.querySelectorAll('.sidebar-inner, .side-menu');
                    scrollContainers.forEach(container => {
                        if (container.scrollHeight > container.clientHeight) {
                            container.style.scrollBehavior = 'smooth';
                            container.style.overflowY = 'auto';
                            container.style.webkitOverflowScrolling = 'touch';
                        }
                    });
                    
                    // Animation frame'ler için optimizasyon
                    if (window.requestIdleCallback) {
                        requestIdleCallback(() => {
                            // Idle zamanlarında ek optimizasyonlar
                            const allMenuElements = document.querySelectorAll('.side-menu *, .sidebar-menu *');
                            allMenuElements.forEach(el => {
                                if (el.style) {
                                    el.style.backfaceVisibility = 'hidden';
                                }
                            });
                        });
                    }
                }
        """.trimIndent()
    }

    /**
     * Menü yavaşlığını tespit eden ve düzelten script
     * Özellikle event listener'ları ve click handler'ları optimize eder
     * @param webView WebView instance'ı
     */
    fun fixSlowMenuResponse(webView: WebView) {
        // Önce CSS optimizasyonlarını tekrar enjekte et
        injectMenuOptimizationCSS(webView)
        val fixScript = """
            (function() {
                console.log('AsforceBrowser: Yavaş menü tepkisi düzeltiliyor...');
                
                // Mevcut click event'lerini hijack et ve optimize et
                const originalAddEventListener = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if (type === 'click' && this.querySelector && this.querySelector('.mdi-menu')) {
                        // Menü butonu için özel handler
                        const optimizedListener = function(e) {
                            if (e.cancelable) {
                                e.preventDefault();
                            }
                            // Hemen yanıt ver
                            requestAnimationFrame(() => {
                                listener.call(this, e);
                            });
                        };
                        return originalAddEventListener.call(this, type, optimizedListener, { passive: false });
                    }
                    return originalAddEventListener.call(this, type, listener, options);
                };
                
                // Menü animasyonlarını hızlandır
                const menuIcon = document.querySelector('.mdi-menu');
                if (menuIcon) {
                    // Doğrudan click handler ekle
                    menuIcon.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        
                        // Menüyü hemen aç/kapat
                        const sidebar = document.querySelector('.side-menu');
                        if (sidebar) {
                            if (sidebar.style.transform === 'translateX(0px)') {
                                sidebar.style.transform = 'translateX(-100%)';
                            } else {
                                sidebar.style.transform = 'translateX(0px)';
                            }
                        }
                    }, { passive: false, capture: true });
                }
                
                // Tüm menü element'lerini bul ve optimize et
                const menuElements = document.querySelectorAll('.has_sub > a');
                menuElements.forEach(element => {
                    let lastClick = 0;
                    element.addEventListener('click', function(e) {
                        const now = Date.now();
                        if (now - lastClick < 300) return; // Double click prevention
                        lastClick = now;
                        
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        
                        // Hemen tepki ver
                        const parentLi = this.parentElement;
                        const submenu = this.nextElementSibling;
                        
                        if (submenu) {
                            if (parentLi.classList.contains('active')) {
                                parentLi.classList.remove('active');
                                submenu.style.display = 'none';
                            } else {
                                parentLi.classList.add('active');
                                submenu.style.display = 'block';
                            }
                        }
                    }, { passive: false, capture: true });
                });
                
                console.log('AsforceBrowser: Menü tepki süresi optimizasyonu tamamlandı');
                
                // Son olarak tüm optimizasyonları bir kez daha uygula
                setTimeout(() => {
                    finalizeMenuOptimizations();
                }, 100);
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(fixScript, null)
    }

    /**
     * WebView yüklendikten sonra menü optimizasyonlarını uygula
     * @param webView WebView instance'ı
     */
    fun applyMenuOptimizations(webView: WebView) {
        // CSS'i hemen enjekte et
        injectMenuOptimizationCSS(webView)
        
        // Sayfa tamamen yüklendikten sonra JavaScript optimizasyonlarını uygula
        val script = """
            // Hemen uygula (DOM'un zaten hazır olduğunu varsayarak)
            (function() {
                if (typeof optimizeMenuPerformance === 'function') {
                    optimizeMenuPerformance();
                } else {
                    ${buildMenuOptimizationScript()}
                }
            })();
            
            // Window load event'inde de uygula
            window.addEventListener('load', function() {
                setTimeout(function() {
                    ${buildMenuOptimizationScript()}
                }, 100);
            });
            
            // DOM ready olduğunda da uygula
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', function() {
                    setTimeout(function() {
                        ${buildMenuOptimizationScript()}
                    }, 100);
                });
            } else {
                // DOM zaten hazır
                setTimeout(function() {
                    ${buildMenuOptimizationScript()}
                }, 50);
            }
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }
}
