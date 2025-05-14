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
                        
                        // Mevcut odaklı input'u temizle
                        window.asforceInputObserver.currentFocusedInput = null;
                        
                        // Native tarafa bildir
                        try {
                            window.AsforceSuggestionBridge.onInputBlurred(fieldIdentifier);
                        } catch (error) {
                            console.error('Error notifying blur event:', error);
                            window.AsforceSuggestionBridge.logError('Blur event error: ' + error.message);
                        }
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
                        
                        mutations.forEach(function(mutation) {
                            if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                                mutation.addedNodes.forEach(function(node) {
                                    // Eklenen düğüm bir HTML elementi ise
                                    if (node.nodeType === 1) {
                                        // Eğer bu bir form veya input ise yeniden tara
                                        if (node.tagName === 'FORM' || node.tagName === 'INPUT') {
                                            shouldRescan = true;
                                        }
                                        // Alt düğümleri kontrol et
                                        else if (node.querySelector) {
                                            if (node.querySelector('form, input')) {
                                                shouldRescan = true;
                                            }
                                        }
                                    }
                                });
                            }
                        });
                        
                        // Yeniden tarama gerekiyorsa
                        if (shouldRescan) {
                            setTimeout(function() {
                                self.observeAllInputs();
                            }, 100);
                        }
                    });
                    
                    // Tüm DOM ağacını izlemeye başla
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                },
                
                // Input değerini güncelle - native taraftan çağrılır
                setInputValue: function(fieldIdentifier, value) {
                    var input = this.trackedInputs[fieldIdentifier];
                    if (input) {
                        input.value = value;
                        
                        // Input ve change olaylarını tetikle
                        var event = new Event('input', { bubbles: true });
                        input.dispatchEvent(event);
                        
                        var changeEvent = new Event('change', { bubbles: true });
                        input.dispatchEvent(changeEvent);
                        
                        return true;
                    }
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
                if (window.asforceInputObserver) {
                    console.log('[Asforce] Calling setInputValue on observer');
                    var result = window.asforceInputObserver.setInputValue("$fieldIdentifier", "$value");
                    console.log('[Asforce] setInputValue result:', result);
                    return result;
                }
                
                // Observer yoksa doğrudan input'u bul ve değerini ayarla
                console.log('[Asforce] Observer not found, trying direct DOM manipulation');
                var input = document.getElementById("$fieldIdentifier") || document.getElementsByName("$fieldIdentifier")[0];
                if (input) {
                    console.log('[Asforce] Input element found directly:', input);
                    input.value = "$value";
                    
                    // Input ve change olaylarını tetikle
                    var event = new Event('input', { bubbles: true });
                    input.dispatchEvent(event);
                    console.log('[Asforce] Input event dispatched');
                    
                    var changeEvent = new Event('change', { bubbles: true });
                    input.dispatchEvent(changeEvent);
                    console.log('[Asforce] Change event dispatched');
                    
                    return true;
                }
                console.log('[Asforce] Input element not found:', '$fieldIdentifier');
                return false;
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