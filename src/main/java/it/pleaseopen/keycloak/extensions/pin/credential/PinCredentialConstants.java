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
package it.pleaseopen.keycloak.extensions.pin.credential;

/**
 * Constants for PIN credential management.
 */
public class PinCredentialConstants {
    
    /**
     * The credential type identifier for PIN credentials.
     */
    public static final String CREDENTIAL_TYPE = "pin-code";
    
    /**
     * Display name for the PIN credential type.
     */
    public static final String DISPLAY_NAME = "PIN Code";
    
    /**
     * Help text for PIN credential configuration.
     */
    public static final String HELP_TEXT = "PIN code for additional authentication";
    
    /**
     * Configuration key for PIN format in credential metadata.
     */
    public static final String CONFIG_PIN_FORMAT = "pin-format";
    
    /**
     * Configuration key for custom regex pattern.
     */
    public static final String CONFIG_CUSTOM_REGEX = "pin-custom-regex";
    
    /**
     * Pre-defined PIN format: 4 digits.
     */
    public static final String FORMAT_4_DIGITS = "4-digits";
    
    /**
     * Pre-defined PIN format: 6 digits.
     */
    public static final String FORMAT_6_DIGITS = "6-digits";
    
    /**
     * Pre-defined PIN format: 8 digits.
     */
    public static final String FORMAT_8_DIGITS = "8-digits";
    
    /**
     * Pre-defined PIN format: custom regex.
     */
    public static final String FORMAT_CUSTOM = "custom";
    
    /**
     * Default PIN format if not specified.
     */
    public static final String DEFAULT_FORMAT = FORMAT_4_DIGITS;
    
    /**
     * Regex pattern for 4 digit PIN.
     */
    public static final String REGEX_4_DIGITS = "^\\d{4}$";
    
    /**
     * Regex pattern for 6 digit PIN.
     */
    public static final String REGEX_6_DIGITS = "^\\d{6}$";
    
    /**
     * Regex pattern for 8 digit PIN.
     */
    public static final String REGEX_8_DIGITS = "^\\d{8}$";
    
    /**
     * Hash iteration count for PIN hashing.
     */
    public static final int HASH_ITERATIONS = 5;

    public static final Boolean DEFAULT_FORCE_PIN_REQUIRED_ACTION = false;
    
    /**
     * Gets the regex pattern for a given PIN format.
     */
    public static String getRegexForFormat(String format) {
        switch (format) {
            case FORMAT_4_DIGITS:
                return REGEX_4_DIGITS;
            case FORMAT_6_DIGITS:
                return REGEX_6_DIGITS;
            case FORMAT_8_DIGITS:
                return REGEX_8_DIGITS;
            default:
                return null;
        }
    }
    
    private PinCredentialConstants() {
        // Utility class
    }
}
