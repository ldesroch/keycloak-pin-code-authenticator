/*
 * Copyright 2026 Pin Code Authenticator Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.pleaseopen.keycloak.extensions.pin.config;

import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialConstants;
import org.jboss.logging.Logger;
import org.keycloak.Config;

/**
 * Shared configuration for PIN authenticator and required action.
 * Initialized from Keycloak's native Config.Scope in PinAuthenticatorFactory.init().
 *
 * Configuration is read from the authenticator SPI scope:
 *   Config.scope("authenticator", "pin-authenticator")
 *
 * This maps to environment variables (double-underscore = scope separator):
 *   KC_SPI_AUTHENTICATOR__PIN_AUTHENTICATOR__PIN_FORMAT=6-digits
 *   KC_SPI_AUTHENTICATOR__PIN_AUTHENTICATOR__VISUAL_KEYBOARD_ENABLED=true
 *   KC_SPI_AUTHENTICATOR__PIN_AUTHENTICATOR__VISUAL_KEYBOARD_DEBUG=false
 *   KC_SPI_AUTHENTICATOR__PIN_AUTHENTICATOR__PIN_CUSTOM_REGEX=
 *
 * Or in keycloak.conf:
 *   spi-authenticator-pin-authenticator-pin-format=6-digits
 *   spi-authenticator-pin-authenticator-visual-keyboard-enabled=true
 */
public class PinConfiguration {

    private static final Logger logger = Logger.getLogger(PinConfiguration.class);

    private static volatile PinConfiguration INSTANCE;

    private final String pinFormat;
    private final String customRegex;
    private final boolean visualKeyboardEnabled;
    private final boolean visualKeyboardDebug;
    private final boolean visualKeyboardObfuscation;

    private PinConfiguration(String pinFormat, boolean visualKeyboardEnabled,
                             boolean visualKeyboardDebug, boolean visualKeyboardObfuscation,
                             String customRegex) {
        this.pinFormat = pinFormat;
        this.visualKeyboardEnabled = visualKeyboardEnabled;
        this.visualKeyboardDebug = visualKeyboardDebug;
        this.visualKeyboardObfuscation = visualKeyboardObfuscation;
        this.customRegex = customRegex;
    }

    /**
     * Initializes configuration from a Keycloak Config.Scope.
     * Called by PinAuthenticatorFactory.init(Config.Scope).
     */
    public static void init(Config.Scope scope) {
        String format = scope.get("pin-format", PinCredentialConstants.DEFAULT_FORMAT);
        boolean keyboard = scope.getBoolean("visual-keyboard-enabled", false);
        boolean debug = scope.getBoolean("visual-keyboard-debug", false);
        boolean obfuscation = scope.getBoolean("visual-keyboard-obfuscation", false);
        String regex = scope.get("pin-custom-regex", "");

        INSTANCE = new PinConfiguration(format, keyboard, debug, obfuscation, regex);

        logger.infof("PIN Configuration initialized from Config.Scope: " +
                      "pinFormat=%s, visualKeyboard=%s, visualKeyboardDebug=%s, " +
                      "visualKeyboardObfuscation=%s, customRegex='%s'",
                      format, keyboard, debug, obfuscation, regex);
    }

    /**
     * Returns the singleton instance. Falls back to defaults if not yet initialized
     * (should not happen in normal Keycloak lifecycle).
     */
    public static PinConfiguration getInstance() {
        if (INSTANCE == null) {
            logger.warn("PinConfiguration accessed before init() — using defaults");
            INSTANCE = new PinConfiguration(
                PinCredentialConstants.DEFAULT_FORMAT, false, false, false, "");
        }
        return INSTANCE;
    }

    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    public String getPinFormat() {
        return pinFormat;
    }

    public String getCustomRegex() {
        return customRegex;
    }

    public boolean isVisualKeyboardEnabled() {
        return visualKeyboardEnabled;
    }

    public boolean isVisualKeyboardDebugEnabled() {
        return visualKeyboardDebug;
    }

    public boolean isVisualKeyboardObfuscationEnabled() {
        return visualKeyboardObfuscation;
    }
}
