package com.asforce.asforcebrowser.suggestion.js

/**
 * WebView'e enjekte edilecek JavaScript kodları
 * 
 * Input alanlarını izlemek ve native tarafa bildirim göndermek için kullanılır.
 */
object JsInjectionScript {
    
    /**
     * Sayfa yüklendiğinde input alanlarını dinlemeye başlamak için enjekte edilecek JavaScript
     */
    val INPUT_OBSERVER_SCRIPT = """
        (function() {
            // Önceki observer'ı temizle (sayfa yeniden yüklendiğinde)
            if (window.asforceInputObserver) {
                delete window.asforceInputObserver;
            }
            
            // Observer nesnesini oluştur
            window.asforceInputObserver = {
                // İzlenen input alanları
                trackedInputs: {},
                
                // İzlenen form elemanları
                trackedForms: {},
                
                // Odaklanılan mevcut input
                currentFocusedInput: null,
                
                // URL değişikliğini izle
                lastUrl: window.location.href,
                
                // Input elemanını izlemeye başla
                trackInput: function(input) {
                    var identifier = this.getInputIdentifier(input);
                    if (!identifier) {
                        return false;
                    }
                    
                    // İzleme kaydı oluştur
                    this.trackedInputs[identifier] = input;
                    
                    // Focus event listener
                    input.addEventListener('focus', function(e) {
                        var fieldIdentifier = window.asforceInputObserver.getInputIdentifier(this);
                        var fieldType = this.type || 'text';
                        
                        // Şifre alanları için izleme yapma
                        if (fieldType === 'password') {
                            return;
                        }
                        
                        // Mevcut odaklı input'u güncelle
                        window.asforceInputObserver.currentFocusedInput = this;
                        
                        // Native tarafa bildir
                        try {
                            window.AsforceSuggestionBridge.onInputFocused(fieldIdentifier, fieldType);
                        } catch (error) {
                            console.error('Error notifying focus event:', error);
                            window.AsforceSuggestionBridge.logError('Focus event error: ' + error.message);
                        }
                    });
                    
                    // Blur event listener
                    input.addEventListener('blur', function(e) {
                        var fieldIdentifier = window.asforceInputObserver.getInputIdentifier(this);
                        var fieldValue = this.value || '';
                        
                        console.log('[Asforce] Input blur event for: ' + fieldIdentifier + ', value: ' + fieldValue);
                        
                        // Değer kontrolü
                        if (fieldValue && fieldValue.trim() !== '') {
                            console.log('[Asforce] Sending blur event with value: ' + fieldValue);
                            
                            // Eğer değer anlamlıysa native tarafa bildir
                            try {
                                // Alan tanımlayıcısı ile değeri de doğrudan kaydedebiliriz
                                window.AsforceSuggestionBridge.saveSubmittedValue(fieldIdentifier, fieldValue, this.type || 'text');
                                
                                // Ayrıca normal blur olayını da bildirelim
                                window.AsforceSuggestionBridge.onInputBlurred(fieldIdentifier);
                            } catch (error) {
                                console.error('Error notifying blur event:', error);
                                window.AsforceSuggestionBridge.logError('Blur event error: ' + error.message);
                            }
                        } else {
                            console.log('[Asforce] Empty value, only sending blur event');
                            
                            // Boş değer için sadece blur bildir
                            try {
                                window.AsforceSuggestionBridge.onInputBlurred(fieldIdentifier);
                            } catch (error) {
                                console.error('Error notifying blur event:', error);
                                window.AsforceSuggestionBridge.logError('Blur event error: ' + error.message);
                            }
                        }
                        
                        // Mevcut odaklı input'u temizle
                        window.asforceInputObserver.currentFocusedInput = null;
                    });
                    
                    // Input event listener - değer değiştiğinde
                    input.addEventListener('input', function(e) {
                        var fieldIdentifier = window.asforceInputObserver.getInputIdentifier(this);
                        var value = this.value;
                        
                        // Şifre alanları için izleme yapma
                        if (this.type === 'password') {
                            return;
                        }
                        
                        // Native tarafa bildir
                        try {
                            window.AsforceSuggestionBridge.onInputValueChanged(fieldIdentifier, value);
                        } catch (error) {
                            console.error('Error notifying input event:', error);
                            window.AsforceSuggestionBridge.logError('Input event error: ' + error.message);
                        }
                    });
                    
                    return true;
                },
                
                // Form elemanını izlemeye başla
                trackForm: function(form) {
                    var formId = form.id || form.name || ('form_' + Object.keys(this.trackedForms).length);
                    
                    // İzleme kaydı oluştur
                    this.trackedForms[formId] = form;
                    
                    // Form gönderildiğinde input değerlerini kaydet
                    form.addEventListener('submit', function(e) {
                        var formElements = this.elements;
                        
                        // Form içindeki tüm input alanlarını kontrol et
                        for (var i = 0; i < formElements.length; i++) {
                            var input = formElements[i];
                            
                            // Sadece belirli tiplerdeki input alanlarını kaydet
                            if (input.tagName === 'INPUT' && 
                                ['text', 'email', 'tel', 'number', 'search', 'url'].indexOf(input.type) >= 0) {
                                
                                var fieldIdentifier = window.asforceInputObserver.getInputIdentifier(input);
                                var value = input.value;
                                
                                if (fieldIdentifier && value && value.trim() !== '') {
                                    // Native tarafa bildir - form değeri kaydedilecek
                                    try {
                                        window.AsforceSuggestionBridge.saveSubmittedValue(
                                            fieldIdentifier, value, input.type || 'text'
                                        );
                                    } catch (error) {
                                        console.error('Error saving form value:', error);
                                        window.AsforceSuggestionBridge.logError('Form submit error: ' + error.message);
                                    }
                                }
                            }
                        }
                    });
                    
                    return true;
                },
                
                // Input tanımlayıcısını al (id veya name)
                getInputIdentifier: function(input) {
                    // Şifre alanları için izleme yapma
                    if (input.type === 'password') {
                        return null;
                    }
                    
                    return input.id || input.name || null;
                },
                
                // Sayfadaki tüm input alanlarını izlemeye başla
                observeAllInputs: function() {
                    // Mevcut input alanlarını bul ve izlemeye başla
                    var inputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input[type="number"], input[type="search"], input[type="url"]');
                    var count = 0;
                    
                    console.log('[Asforce] DOM Input Scan: Found ' + inputs.length + ' potential input fields to track');
                    
                    // Input alanlarının tümlerine detaylı tanımlama
                    var inputDetails = [];
                    for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    inputDetails.push({
                            index: i,
                    id: input.id || 'no-id',
                    name: input.name || 'no-name',
                    type: input.type || 'unknown-type',
                    visible: (input.offsetParent !== null) ? 'visible' : 'hidden'
                });
            }
            console.log('[Asforce] Input Field Details:', JSON.stringify(inputDetails));
            
            for (var i = 0; i < inputs.length; i++) {
                var input = inputs[i];
                
                // Input alanını izlemeye başla
                if (this.trackInput(input)) {
                    count++;
                    console.log('[Asforce] Successfully tracking input: ' + 
                         (input.id || input.name || ('input_' + i)) + 
                         ' (Type: ' + input.type + ')');
                }
            }
                    
                    // Form elemanlarını izlemeye başla
                    var forms = document.querySelectorAll('form');
                    for (var j = 0; j < forms.length; j++) {
                        this.trackForm(forms[j]);
                    }
                    
                    // Native tarafa bildir
                    try {
                        window.AsforceSuggestionBridge.reportInputFieldCount(count);
                    } catch (error) {
                        console.error('Error reporting input count:', error);
                    }
                    
                    // Sayfa URL'sini izle
                    this.startUrlObserver();
                    
                    // DOM değişikliklerini gözlemle
                    this.startMutationObserver();
                },
                
                // URL değişikliklerini izlemek için
                startUrlObserver: function() {
                    var self = this;
                    
                    // 500ms'de bir URL değişikliğini kontrol et - SPA uygulamaları için
                    setInterval(function() {
                        var currentUrl = window.location.href;
                        if (currentUrl !== self.lastUrl) {
                            self.lastUrl = currentUrl;
                            
                            // Native tarafa bildir
                            try {
                                window.AsforceSuggestionBridge.onPageUrlChanged(currentUrl);
                                
                                // Yeni URL'de yeni alanlar olabilir
                                setTimeout(function() {
                                    self.observeAllInputs();
                                }, 500);
                            } catch (error) {
                                console.error('Error notifying URL change:', error);
                            }
                        }
                    }, 500);
                },
                
                // DOM değişikliklerini izlemek için MutationObserver
                startMutationObserver: function() {
                    var self = this;
                    
                    // MutationObserver oluştur
                    var observer = new MutationObserver(function(mutations) {
                        // DOM ağacında değişiklik varsa yeni input alanları aranır
                        var shouldRescan = false;
                        var newInputsFound = 0;
                        
                        mutations.forEach(function(mutation) {
                            if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                                // Eklenen yeni düğümleri kontrol et
                                mutation.addedNodes.forEach(function(node) {
                                    // Eklenen düğüm bir HTML elementi ise
                                    if (node.nodeType === 1) {
                                        // Eğer bu bir form veya input ise yeniden tara
                                        if (node.tagName === 'FORM' || node.tagName === 'INPUT') {
                                            shouldRescan = true;
                                            
                                            // Doğrudan input ise hemen izle 
                                            if (node.tagName === 'INPUT' && 
                                                ['text', 'email', 'tel', 'number', 'search', 'url'].indexOf(node.type) >= 0) {
                                                newInputsFound++;
                                                self.trackInput(node);
                                            } else if (node.tagName === 'FORM') {
                                                self.trackForm(node);
                                            }
                                        }
                                        // Alt düğümleri kontrol et
                                        else if (node.querySelector) {
                                            // Form elemanlarını hemen izle
                                            var forms = node.querySelectorAll('form');
                                            if (forms.length > 0) {
                                                shouldRescan = true;
                                                forms.forEach(function(form) {
                                                    self.trackForm(form);
                                                });
                                            }
                                            
                                            // Input elemanlarını hemen izle
                                            var inputs = node.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input[type="number"], input[type="search"], input[type="url"]');
                                            if (inputs.length > 0) {
                                                shouldRescan = true;
                                                inputs.forEach(function(input) {
                                                    newInputsFound++;
                                                    self.trackInput(input);
                                                });
                                            }
                                        }
                                    }
                                });
                            } else if (mutation.type === 'attributes') {
                                // Nitelik değişikliği, input tipi değişmiş olabilir
                                var node = mutation.target;
                                if (node.tagName === 'INPUT' && 
                                    ['text', 'email', 'tel', 'number', 'search', 'url'].indexOf(node.type) >= 0) {
                                    if (!self.trackedInputs[self.getInputIdentifier(node)]) {
                                        newInputsFound++;
                                        self.trackInput(node);
                                    }
                                }
                            }
                        });
                        
                        // Yeni inputlar bulundu mu log
                        if (newInputsFound > 0) {
                            console.log('[Asforce] Mutation observer found and tracked ' + newInputsFound + ' new input fields');
                        }
                        
                        // Yeniden tarama gerekiyorsa
                        if (shouldRescan) {
                            // Daha kapsamlı bir taramayı gecikmeli olarak yap
                            setTimeout(function() {
                                self.observeAllInputs();
                            }, 200);
                        }
                    });
                    
                    // Tüm DOM ağacını, tüm değişiklikleri izlemeye başla
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['type', 'id', 'name']
                    });
                    
                    // Hata durumunda yeniden bağlama
                    window.addEventListener('error', function(e) {
                        if (e.message && e.message.indexOf('mutation') !== -1) {
                            // Mutation observer ile ilgili hata olabilir, yeniden başlat
                            setTimeout(function() {
                                self.startMutationObserver();
                            }, 2000);
                        }
                    });
                },
                
                // Input değerini güncelle - native taraftan çağrılır
                setInputValue: function(fieldIdentifier, value) {
                    var input = this.trackedInputs[fieldIdentifier];
                    if (input) {
                        // Değeri ayarla
                        input.value = value;
                        
                        // Input ve change olaylarını tetikle
                        var event = new Event('input', { bubbles: true, cancelable: true });
                        input.dispatchEvent(event);
                        
                        var changeEvent = new Event('change', { bubbles: true, cancelable: true });
                        input.dispatchEvent(changeEvent);
                        
                        // KeyDown, KeyPress ve KeyUp olayları (bazı formlar buna ihtiyaç duyabilir)
                        var keyEventTypes = ['keydown', 'keypress', 'keyup'];
                        keyEventTypes.forEach(function(eventType) {
                            var keyEvent = new KeyboardEvent(eventType, { 
                                bubbles: true, 
                                cancelable: true,
                                key: value.charAt(value.length - 1)
                            });
                            input.dispatchEvent(keyEvent);
                        });
                        
                        // Odaklanma (bir kez daha) olayı
                        input.focus();
                        
                        // Javascript değişiklik özelliğini doğrudan çağır
                        if (typeof input.oninput === 'function') {
                            input.oninput();
                        }
                        
                        if (typeof input.onchange === 'function') {
                            input.onchange();
                        }
                        
                        console.log('[Asforce] Input value set through observer with all events');
                        return true;
                    }
                    
                    console.log('[Asforce] Input not found in tracked inputs:', fieldIdentifier);
                    return false;
                }
            };
            
            // Sayfa yüklendiğinde tüm input alanlarını izlemeye başla
            if (document.readyState === "complete" || document.readyState === "interactive") {
                window.asforceInputObserver.observeAllInputs();
            } else {
                document.addEventListener("DOMContentLoaded", function() {
                    window.asforceInputObserver.observeAllInputs();
                });
            }
            
            return true;
        })();
    """.trimIndent()
    
    /**
     * Input değerini ayarlamak için JavaScript fonksiyonu
     * 
     * @param fieldIdentifier Alan tanımlayıcısı
     * @param value Ayarlanacak değer
     * @return JavaScript fonksiyonu
     */
    fun getSetInputValueScript(fieldIdentifier: String, value: String): String {
        return """
            (function() {
                try {
                    console.log('[Asforce] Setting input value for: "$fieldIdentifier" to: "$value"');
                    
                    // 1. Observer kullanarak değeri ayarlamayı dene
                    if (window.asforceInputObserver) {
                        console.log('[Asforce] Using observer to set input value');
                        var result = window.asforceInputObserver.setInputValue("$fieldIdentifier", "$value");
                        console.log('[Asforce] Observer setInputValue result:', result);
                        if (result === true) {
                            return true;
                        }
                    }
                    
                    // 2. Doğrudan DOM elemanını bulmayı dene - farklı seçiciler kullan
                    var input = null;
                    
                    // ID ile ara
                    input = document.getElementById("$fieldIdentifier");
                    
                    // Name ile ara
                    if (!input) {
                        var inputs = document.getElementsByName("$fieldIdentifier");
                        if (inputs && inputs.length > 0) {
                            input = inputs[0];
                        }
                    }
                    
                    // CSS seçici ile ara
                    if (!input) {
                        input = document.querySelector('[id="$fieldIdentifier"], [name="$fieldIdentifier"]');
                    }
                    
                    // Genişletilmiş seçici ile ara
                    if (!input) {
                        input = document.querySelector('input[id*="$fieldIdentifier"], input[name*="$fieldIdentifier"]');
                    }
                    
                    if (input) {
                        console.log('[Asforce] Input element found directly:', input);
                        
                        // Değeri doğrudan ve input özelliği üzerinden ayarla
                        input.value = "$value";
                        
                        // Tüm olası olayları tetikle
                        // Input olayı
                        var inputEvent = new Event('input', { bubbles: true, cancelable: true });
                        input.dispatchEvent(inputEvent);
                        
                        // Change olayı
                        var changeEvent = new Event('change', { bubbles: true, cancelable: true });
                        input.dispatchEvent(changeEvent);
                        
                        // KeyDown, KeyPress ve KeyUp olayları (bazı formlar buna ihtiyaç duyabilir)
                        var keyEventTypes = ['keydown', 'keypress', 'keyup'];
                        keyEventTypes.forEach(function(eventType) {
                            var keyEvent = new KeyboardEvent(eventType, { 
                                bubbles: true, 
                                cancelable: true,
                                key: "$value".charAt("$value".length - 1)
                            });
                            input.dispatchEvent(keyEvent);
                        });
                        
                        // Odaklanma ve odak kaybı (Focus/Blur) olayları
                        input.focus();
                        
                        // Angular, React gibi modern çerçeveleri destekle
                        if (input.__proto__ && typeof input.__proto__.dispatchEvent === 'function') {
                            // Özel bir olay oluştur (React kullanabilir)
                            var customEvent = new Event('reactInput', { bubbles: true });
                            input.__proto__.dispatchEvent(customEvent);
                        }
                        
                        // Javascript değişiklik özelliğini doğrudan çağır
                        if (typeof input.oninput === 'function') {
                            input.oninput();
                        }
                        
                        if (typeof input.onchange === 'function') {
                            input.onchange();
                        }
                        
                        console.log('[Asforce] Input value set and events dispatched');
                        return true;
                    }
                    
                    console.log('[Asforce] Input element not found for: "$fieldIdentifier"');
                    return false;
                } catch (error) {
                    console.error('[Asforce] Error setting input value:', error);
                    return false;
                }
            })();
        """.trimIndent()
    }
    
    /**
     * Input odağını kontrol etmek için JavaScript fonksiyonu
     * 
     * @return JavaScript fonksiyonu
     */
    val CHECK_FOCUSED_INPUT_SCRIPT = """
        (function() {
            if (window.asforceInputObserver && window.asforceInputObserver.currentFocusedInput) {
                var input = window.asforceInputObserver.currentFocusedInput;
                var identifier = window.asforceInputObserver.getInputIdentifier(input);
                var type = input.type || 'text';
                
                return JSON.stringify({
                    focused: true,
                    identifier: identifier,
                    type: type,
                    value: input.value
                });
            }
            return JSON.stringify({ focused: false });
        })();
    """.trimIndent()
}