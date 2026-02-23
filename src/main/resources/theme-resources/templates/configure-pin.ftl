<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('newPin','confirmPin'); section>
    <#if section = "header">
        ${msg("pinRequiredActionTitle")}
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
            
            /* Format hint styling */
            .format-hint {
                text-align: center;
                color: #666;
                font-size: 0.95em;
                margin: 15px 0;
                padding: 10px;
                background-color: #f5f5f5;
                border-radius: 4px;
                border-left: 4px solid #4a90e2;
            }
            
            /* PIN field container */
            .pin-field-container {
                position: relative;
            }
            
            /* Hide password input visually but keep functional */
            .pin-field-container input[type="password"] {
                position: absolute;
                left: -9999px;
                opacity: 0;
                height: 0;
            }
            
            /* Server-rendered keyboard image */
            .keyboard-container {
                text-align: center;
                margin: 15px 0;
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
                margin-top: 8px;
            }
            .keyboard-actions button {
                padding: 8px 20px;
                font-size: 14px;
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
            
            /* Active field indicator */
            .pin-field-active {
                outline: 2px solid #4a90e2;
                outline-offset: 4px;
                border-radius: 8px;
                padding: 4px;
            }
            
            .pin-hint {
                text-align: center;
                color: #666;
                font-size: 0.85em;
                margin-bottom: 8px;
            }
        </style>
        
        <div class="${properties.kcFormGroupClass!}">
            <div class="${properties.kcLabelWrapperClass!}">
                <label class="${properties.kcLabelClass!}">${msg("pinRequiredActionText")}</label>
            </div>
        </div>

        <!-- Display configured PIN format hint -->
        <div class="format-hint">
            <strong>${msg("pinRequiredActionFormatHint")}:</strong> ${pinFormatHint!'4 digits'}
        </div>

        <form id="kc-configure-pin-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <!-- New PIN -->
            <div class="${properties.kcFormGroupClass!}">
                <label for="newPin" class="${properties.kcLabelClass!}" style="text-align: center; display: block;">
                    ${msg("pinRequiredActionNewPinLabel")}
                </label>
                
                <!-- Visual Digit Dots -->
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
                
                <div class="pin-field-container" id="new-pin-field">
                    <#if pinLen gt 0>
                        <div class="pin-dots-container" id="new-pin-dots">
                            <#list 1..pinLen as i>
                                <div class="pin-dot" data-index="${i}"></div>
                            </#list>
                        </div>
                    <#else>
                        <div class="pin-dots-container pin-dots-dynamic" id="new-pin-dots"></div>
                    </#if>
                    
                    <input type="password" 
                           id="newPin" 
                           name="newPin" 
                           autocomplete="off"
                           <#if pinLen gt 0>
                               maxlength="${pinLen}"
                               pattern="\d{${pinLen}}"
                           </#if>
                           aria-invalid="<#if messagesPerField.existsError('newPin')>true</#if>"
                    />
                </div>
                
                <#if messagesPerField.existsError('newPin')>
                    <span id="input-error-newPin" class="${properties.kcInputErrorMessageClass!}" aria-live="polite" style="display: block; text-align: center; margin-top: 10px;">
                        ${kcSanitize(messagesPerField.get('newPin'))?no_esc}
                    </span>
                </#if>
            </div>

            <!-- Confirm PIN -->
            <div class="${properties.kcFormGroupClass!}">
                <label for="confirmPin" class="${properties.kcLabelClass!}" style="text-align: center; display: block;">
                    ${msg("pinRequiredActionConfirmPinLabel")}
                </label>
                
                <div class="pin-field-container" id="confirm-pin-field">
                    <#if pinLen gt 0>
                        <div class="pin-dots-container" id="confirm-pin-dots">
                            <#list 1..pinLen as i>
                                <div class="pin-dot" data-index="${i}"></div>
                            </#list>
                        </div>
                    <#else>
                        <div class="pin-dots-container pin-dots-dynamic" id="confirm-pin-dots"></div>
                    </#if>
                    
                    <input type="password" 
                           id="confirmPin" 
                           name="confirmPin" 
                           autocomplete="off"
                           <#if pinLen gt 0>
                               maxlength="${pinLen}"
                               pattern="\d{${pinLen}}"
                           </#if>
                           aria-invalid="<#if messagesPerField.existsError('confirmPin')>true</#if>"
                    />
                </div>
                
                <#if messagesPerField.existsError('confirmPin')>
                    <span id="input-error-confirmPin" class="${properties.kcInputErrorMessageClass!}" aria-live="polite" style="display: block; text-align: center; margin-top: 10px;">
                        ${kcSanitize(messagesPerField.get('confirmPin'))?no_esc}
                    </span>
                </#if>
            </div>

            <#if showVisualKeyboard?? && showVisualKeyboard == true && keyboardImage??>
                <!-- Hidden fields for coordinate data -->
                <input type="hidden" id="newPinCoords" name="newPinCoords" value="" />
                <input type="hidden" id="confirmPinCoords" name="confirmPinCoords" value="" />
                
                <!-- Shared keyboard image -->
                <div class="pin-hint">Click on the keyboard to enter digits. Select the target field first.</div>
                <div class="keyboard-container">
                    <img id="configure-keyboard-image" 
                         class="keyboard-image" 
                         src="data:image/png;base64,${keyboardImage}" 
                         width="${keyboardWidth?c}" 
                         height="${keyboardHeight?c}"
                         alt="Virtual PIN Keyboard"
                         draggable="false" />
                    
                    <!-- Backspace & Clear: regular HTML buttons -->
                    <div class="keyboard-actions">
                        <button type="button" id="kb-backspace" title="Delete last digit">&#9003; Backspace</button>
                        <button type="button" id="kb-clear" title="Clear all digits">C Clear</button>
                    </div>
                </div>
            </#if>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" 
                           type="submit" 
                           value="${msg("pinRequiredActionSubmit")}"/>
                </div>
            </div>
        </form>
        
        <script type="text/javascript">
            (function() {
                var newPinInput = document.getElementById('newPin');
                var confirmPinInput = document.getElementById('confirmPin');
                var newPinDotsContainer = document.getElementById('new-pin-dots');
                var confirmPinDotsContainer = document.getElementById('confirm-pin-dots');
                var maxLength = <#if pinLen gt 0>${pinLen}<#else>0</#if>;
                var isDynamic = (maxLength === 0);
                var showKeyboard = <#if showVisualKeyboard?? && showVisualKeyboard == true && keyboardImage??>true<#else>false</#if>;
                
                // Visual keyboard state
                var activeField = 'newPin'; // 'newPin' or 'confirmPin'
                var newPinCoords = [];
                var confirmPinCoords = [];
                var newPinLength = 0;
                var confirmPinLength = 0;
                
                // Update visual dots for fixed-length formats
                function updateDotsFixed(input, container, length) {
                    var dots = container.querySelectorAll('.pin-dot');
                    var len = (length !== undefined) ? length : input.value.length;
                    dots.forEach(function(dot, index) {
                        if (index < len) {
                            dot.classList.add('filled');
                        } else {
                            dot.classList.remove('filled');
                        }
                    });
                }

                // Update visual dots for dynamic (custom) format — add/remove dots
                function updateDotsDynamic(input, container, length) {
                    var len = (length !== undefined) ? length : input.value.length;
                    var dots = container.querySelectorAll('.pin-dot');
                    // Remove extra dots
                    while (dots.length > len) {
                        container.removeChild(dots[dots.length - 1]);
                        dots = container.querySelectorAll('.pin-dot');
                    }
                    // Add missing dots (all filled)
                    while (dots.length < len) {
                        var dot = document.createElement('div');
                        dot.className = 'pin-dot filled';
                        container.appendChild(dot);
                        dots = container.querySelectorAll('.pin-dot');
                    }
                }

                function updateDots(input, container, length) {
                    if (isDynamic) {
                        updateDotsDynamic(input, container, length);
                    } else {
                        updateDotsFixed(input, container, length);
                    }
                }
                
                if (!showKeyboard) {
                    // Non-visual-keyboard mode: use hidden inputs
                    newPinInput.style.position = 'relative';
                    newPinInput.style.left = '0';
                    newPinInput.style.opacity = '0';
                    newPinInput.style.height = '0';
                    newPinInput.style.width = '100%';
                    
                    confirmPinInput.style.position = 'relative';
                    confirmPinInput.style.left = '0';
                    confirmPinInput.style.opacity = '0';
                    confirmPinInput.style.height = '0';
                    confirmPinInput.style.width = '100%';
                    
                    if (maxLength > 0) {
                        newPinInput.inputMode = 'numeric';
                        confirmPinInput.inputMode = 'numeric';
                    }
                    
                    newPinInput.addEventListener('input', function() {
                        updateDots(newPinInput, newPinDotsContainer);
                    });
                    
                    confirmPinInput.addEventListener('input', function() {
                        updateDots(confirmPinInput, confirmPinDotsContainer);
                    });
                    
                    if (newPinDotsContainer) {
                        newPinDotsContainer.addEventListener('click', function() {
                            newPinInput.focus();
                        });
                    }
                    
                    if (confirmPinDotsContainer) {
                        confirmPinDotsContainer.addEventListener('click', function() {
                            confirmPinInput.focus();
                        });
                    }
                    
                    newPinInput.focus();
                }
                
                if (showKeyboard) {
                    var kbImage = document.getElementById('configure-keyboard-image');
                    var newPinCoordsInput = document.getElementById('newPinCoords');
                    var confirmPinCoordsInput = document.getElementById('confirmPinCoords');
                    var newPinFieldContainer = document.getElementById('new-pin-field');
                    var confirmPinFieldContainer = document.getElementById('confirm-pin-field');
                    
                    // Highlight active field
                    function setActiveField(field) {
                        activeField = field;
                        if (field === 'newPin') {
                            newPinFieldContainer.classList.add('pin-field-active');
                            confirmPinFieldContainer.classList.remove('pin-field-active');
                        } else {
                            confirmPinFieldContainer.classList.add('pin-field-active');
                            newPinFieldContainer.classList.remove('pin-field-active');
                        }
                    }
                    
                    // Click on dots containers to switch active field
                    newPinDotsContainer.addEventListener('click', function() {
                        setActiveField('newPin');
                    });
                    confirmPinDotsContainer.addEventListener('click', function() {
                        setActiveField('confirmPin');
                    });
                    
                    // Start with newPin active
                    setActiveField('newPin');
                    
                    // Handle click on keyboard image
                    kbImage.addEventListener('click', function(e) {
                        var rect = kbImage.getBoundingClientRect();
                        var scaleX = kbImage.naturalWidth / rect.width;
                        var scaleY = kbImage.naturalHeight / rect.height;
                        var cx = Math.round((e.clientX - rect.left) * scaleX);
                        var cy = Math.round((e.clientY - rect.top) * scaleY);
                        
                        if (activeField === 'newPin') {
                            if (maxLength > 0 && newPinLength >= maxLength) return;
                            newPinCoords.push(cx + ',' + cy);
                            newPinLength++;
                            newPinCoordsInput.value = newPinCoords.join('|');
                            updateDots(newPinInput, newPinDotsContainer, newPinLength);
                            // Auto-switch to confirmPin when newPin is full
                            if (maxLength > 0 && newPinLength === maxLength) {
                                setActiveField('confirmPin');
                            }
                        } else {
                            if (maxLength > 0 && confirmPinLength >= maxLength) return;
                            confirmPinCoords.push(cx + ',' + cy);
                            confirmPinLength++;
                            confirmPinCoordsInput.value = confirmPinCoords.join('|');
                            updateDots(confirmPinInput, confirmPinDotsContainer, confirmPinLength);
                        }
                    });
                    
                    // Backspace button
                    var backspaceBtn = document.getElementById('kb-backspace');
                    if (backspaceBtn) {
                        backspaceBtn.addEventListener('click', function(e) {
                            e.preventDefault();
                            if (activeField === 'newPin' && newPinCoords.length > 0) {
                                newPinCoords.pop();
                                newPinLength--;
                                newPinCoordsInput.value = newPinCoords.join('|');
                                updateDots(newPinInput, newPinDotsContainer, newPinLength);
                            } else if (activeField === 'confirmPin' && confirmPinCoords.length > 0) {
                                confirmPinCoords.pop();
                                confirmPinLength--;
                                confirmPinCoordsInput.value = confirmPinCoords.join('|');
                                updateDots(confirmPinInput, confirmPinDotsContainer, confirmPinLength);
                            }
                        });
                    }
                    
                    // Clear button
                    var clearBtn = document.getElementById('kb-clear');
                    if (clearBtn) {
                        clearBtn.addEventListener('click', function(e) {
                            e.preventDefault();
                            if (activeField === 'newPin') {
                                newPinCoords = [];
                                newPinLength = 0;
                                newPinCoordsInput.value = '';
                                updateDots(newPinInput, newPinDotsContainer, 0);
                            } else {
                                confirmPinCoords = [];
                                confirmPinLength = 0;
                                confirmPinCoordsInput.value = '';
                                updateDots(confirmPinInput, confirmPinDotsContainer, 0);
                            }
                        });
                    }
                }
            })();
        </script>
    </#if>
</@layout.registrationLayout>
