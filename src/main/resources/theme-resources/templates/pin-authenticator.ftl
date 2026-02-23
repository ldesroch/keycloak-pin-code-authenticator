<!--
  ~ Copyright 2026 Pin Code Authenticator Contributors
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('pin'); section>
    <#if section = "header">
        ${msg("pinAuthenticatorTitle")}
    <#elseif section = "form">
        <style>
            /* PIN Digit Dots Display */
            .pin-dots-container {
                display: flex;
                justify-content: center;
                gap: 12px;
                margin: 20px 0;
                padding: 10px;
            }
            .pin-dot {
                width: 16px;
                height: 16px;
                border-radius: 50%;
                background-color: #e0e0e0;
                transition: background-color 0.2s ease;
                border: 2px solid #ccc;
            }
            .pin-dot.filled {
                background-color: #333;
                border-color: #333;
            }
            
            /* Hide actual password input but keep it functional */
            #pin {
                position: absolute;
                left: -9999px;
                opacity: 0;
            }
            
            /* Server-rendered keyboard image */
            .keyboard-container {
                text-align: center;
                margin: 20px 0;
            }
            .keyboard-image {
                cursor: pointer;
                border: 1px solid #ddd;
                border-radius: 8px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                display: block;
                margin: 0 auto;
                user-select: none;
                -webkit-user-drag: none;
            }
            
            /* Keyboard action buttons (HTML, outside image) */
            .keyboard-actions {
                display: flex;
                justify-content: center;
                gap: 12px;
                margin-top: 12px;
            }
            .keyboard-actions button {
                padding: 10px 24px;
                font-size: 16px;
                border: 1px solid #ccc;
                border-radius: 6px;
                cursor: pointer;
                background: #f5f5f5;
                color: #333;
                transition: background 0.15s;
            }
            .keyboard-actions button:hover {
                background: #e0e0e0;
            }
            
            /* Hint text */
            .pin-hint {
                text-align: center;
                color: #666;
                font-size: 0.9em;
                margin-bottom: 10px;
            }
        </style>
        
        <form id="kc-pin-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="pin" class="${properties.kcLabelClass!}" style="text-align: center; display: block;">
                    <#if pinFormatHint??>
                        ${pinFormatHint}
                    <#elseif pinFormat??>
                        ${msg("pinAuthenticatorLabel")}
                    <#else>
                        ${msg("pinAuthenticatorLabel")}
                    </#if>
                </label>
                
                <!-- Visual Digit Dots (for numeric PINs) -->
                <#assign pinLen = 4>
                <#if pinFormat??>
                    <#if pinFormat == "4-digits">
                        <#assign pinLen = 4>
                    <#elseif pinFormat == "6-digits">
                        <#assign pinLen = 6>
                    <#elseif pinFormat == "8-digits">
                        <#assign pinLen = 8>
                    <#else>
                        <#assign pinLen = 0>
                    </#if>
                </#if>
                
                <#if pinLen gt 0>
                    <div class="pin-dots-container" id="pin-dots">
                        <#list 1..pinLen as i>
                            <div class="pin-dot" data-index="${i}"></div>
                        </#list>
                    </div>
                <#else>
                    <div class="pin-dots-container pin-dots-dynamic" id="pin-dots"></div>
                </#if>
                
                <!-- Hidden password input (receives actual PIN in non-keyboard mode) -->
                <input type="password" 
                       id="pin" 
                       name="pin" 
                       autocomplete="off"
                       <#if pinLen gt 0>
                           maxlength="${pinLen}"
                           pattern="\d{${pinLen}}"
                       </#if>
                       aria-invalid="<#if messagesPerField.existsError('pin')>true</#if>"
                />
                
                <!-- Server-generated visual keyboard (if enabled) -->
                <#if showVisualKeyboard?? && showVisualKeyboard == true && keyboardImage??>
                    <!-- Hidden field for coordinate data -->
                    <input type="hidden" id="pinCoords" name="pinCoords" value="" />
                    
                    <div class="pin-hint">Use the virtual keyboard below</div>
                    <div class="keyboard-container">
                        <img id="pin-keyboard-image" 
                             class="keyboard-image" 
                             src="data:image/png;base64,${keyboardImage}" 
                             width="${keyboardWidth?c}" 
                             height="${keyboardHeight?c}"
                             alt="Virtual PIN Keyboard"
                             draggable="false" />
                        
                        <!-- Backspace & Clear: regular HTML buttons, OUTSIDE the image -->
                        <div class="keyboard-actions">
                            <button type="button" id="kb-backspace" title="Delete last digit">&#9003; Backspace</button>
                            <button type="button" id="kb-clear" title="Clear all digits">C Clear</button>
                        </div>
                    </div>
                </#if>
                
                <#if messagesPerField.existsError('pin')>
                    <span id="input-error-pin" class="${properties.kcInputErrorMessageClass!}" aria-live="polite" style="display: block; text-align: center;">
                        ${kcSanitize(messagesPerField.get('pin'))?no_esc}
                    </span>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" 
                           type="submit" 
                           value="${msg("doSubmit")}"
                           id="submit-btn"/>
                </div>
            </div>
            
            <#if resetEnabled?? && resetEnabled>
                <div style="text-align: center; margin-top: 15px;">
                    <a href="#" id="reset-pin-link" style="color: #4a90d9; font-size: 0.9em;">
                        ${msg("pinResetCredentialLink")}
                    </a>
                </div>
            </#if>
        </form>
        
        <script type="text/javascript">
            (function() {
                var pinInput = document.getElementById('pin');
                var pinDotsContainer = document.getElementById('pin-dots');
                var maxLength = <#if pinLen gt 0>${pinLen}<#else>0</#if>;
                var isDynamic = (maxLength === 0);
                var showKeyboard = <#if showVisualKeyboard?? && showVisualKeyboard == true && keyboardImage??>true<#else>false</#if>;
                
                // Coordinate clicks for visual keyboard mode
                var clickCoords = [];
                var pinCoordsInput = document.getElementById('pinCoords');
                
                // Track PIN length in visual keyboard mode
                var pinLength = 0;
                
                // Update visual dots for fixed-length formats
                function updateDotsFixed() {
                    var dots = pinDotsContainer.querySelectorAll('.pin-dot');
                    var length = showKeyboard ? pinLength : pinInput.value.length;
                    dots.forEach(function(dot, index) {
                        if (index < length) {
                            dot.classList.add('filled');
                        } else {
                            dot.classList.remove('filled');
                        }
                    });
                    // Auto-submit when PIN length reached (only in visual keyboard mode)
                    if (showKeyboard && maxLength > 0 && length === maxLength) {
                        setTimeout(function() {
                            document.getElementById('kc-pin-form').submit();
                        }, 300);
                    }
                }

                // Update visual dots for dynamic (custom) format
                function updateDotsDynamic() {
                    var length = showKeyboard ? pinLength : pinInput.value.length;
                    var dots = pinDotsContainer.querySelectorAll('.pin-dot');
                    while (dots.length > length) {
                        pinDotsContainer.removeChild(dots[dots.length - 1]);
                        dots = pinDotsContainer.querySelectorAll('.pin-dot');
                    }
                    while (dots.length < length) {
                        var dot = document.createElement('div');
                        dot.className = 'pin-dot filled';
                        pinDotsContainer.appendChild(dot);
                        dots = pinDotsContainer.querySelectorAll('.pin-dot');
                    }
                }

                function updateDots() {
                    if (isDynamic) {
                        updateDotsDynamic();
                    } else {
                        updateDotsFixed();
                    }
                }
                
                // Handle keyboard input (for non-visual-keyboard mode)
                if (!showKeyboard) {
                    pinInput.addEventListener('input', function() {
                        updateDots();
                        // Auto-submit when PIN length reached
                        if (maxLength > 0 && pinInput.value.length === maxLength) {
                            setTimeout(function() {
                                document.getElementById('kc-pin-form').submit();
                            }, 300);
                        }
                    });
                    pinInput.style.position = 'relative';
                    pinInput.style.left = '0';
                    pinInput.style.opacity = '0';
                    pinInput.style.height = '0';
                    pinInput.style.width = '100%';
                    pinInput.focus();

                    // Click on dots to focus input
                    if (pinDotsContainer) {
                        pinDotsContainer.addEventListener('click', function() {
                            pinInput.focus();
                        });
                    }
                }
                
                // Server-rendered visual keyboard
                if (showKeyboard) {
                    var kbImage = document.getElementById('pin-keyboard-image');
                    
                    // Handle click on the keyboard image — record coordinates
                    kbImage.addEventListener('click', function(e) {
                        // Compute coordinates relative to the image's natural size
                        var rect = kbImage.getBoundingClientRect();
                        var scaleX = kbImage.naturalWidth / rect.width;
                        var scaleY = kbImage.naturalHeight / rect.height;
                        var cx = Math.round((e.clientX - rect.left) * scaleX);
                        var cy = Math.round((e.clientY - rect.top) * scaleY);
                        
                        // Respect max length
                        if (maxLength > 0 && pinLength >= maxLength) {
                            return;
                        }
                        
                        clickCoords.push(cx + ',' + cy);
                        pinLength++;
                        pinCoordsInput.value = clickCoords.join('|');
                        
                        updateDots();
                    });
                    
                    // Backspace button
                    var backspaceBtn = document.getElementById('kb-backspace');
                    if (backspaceBtn) {
                        backspaceBtn.addEventListener('click', function(e) {
                            e.preventDefault();
                            if (clickCoords.length > 0) {
                                clickCoords.pop();
                                pinLength--;
                                pinCoordsInput.value = clickCoords.join('|');
                                updateDots();
                            }
                        });
                    }
                    
                    // Clear button
                    var clearBtn = document.getElementById('kb-clear');
                    if (clearBtn) {
                        clearBtn.addEventListener('click', function(e) {
                            e.preventDefault();
                            clickCoords = [];
                            pinLength = 0;
                            pinCoordsInput.value = '';
                            updateDots();
                        });
                    }
                }
                
                // Reset PIN link handler
                var resetLink = document.getElementById('reset-pin-link');
                if (resetLink) {
                    resetLink.addEventListener('click', function(e) {
                        e.preventDefault();
                        var form = document.getElementById('kc-pin-form');
                        var hiddenInput = document.createElement('input');
                        hiddenInput.type = 'hidden';
                        hiddenInput.name = 'resetPin';
                        hiddenInput.value = 'true';
                        form.appendChild(hiddenInput);
                        form.submit();
                    });
                }
            })();
        </script>
    </#if>
</@layout.registrationLayout>
