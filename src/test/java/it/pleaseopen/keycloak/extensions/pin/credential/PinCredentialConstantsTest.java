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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PinCredentialConstants.
 */
@DisplayName("PinCredentialConstants Tests")
class PinCredentialConstantsTest {
    
    @Test
    @DisplayName("Should have correct credential type")
    void shouldHaveCorrectCredentialType() {
        assertThat(PinCredentialConstants.CREDENTIAL_TYPE).isEqualTo("pin-code");
    }
    
    @Test
    @DisplayName("Should have correct display name")
    void shouldHaveCorrectDisplayName() {
        assertThat(PinCredentialConstants.DISPLAY_NAME).isEqualTo("PIN Code");
    }
    
    @Test
    @DisplayName("Should have correct format constants")
    void shouldHaveCorrectFormatConstants() {
        assertThat(PinCredentialConstants.FORMAT_4_DIGITS).isEqualTo("4-digits");
        assertThat(PinCredentialConstants.FORMAT_6_DIGITS).isEqualTo("6-digits");
        assertThat(PinCredentialConstants.FORMAT_8_DIGITS).isEqualTo("8-digits");
        assertThat(PinCredentialConstants.FORMAT_CUSTOM).isEqualTo("custom");
    }
    
    @Test
    @DisplayName("Should have correct regex patterns")
    void shouldHaveCorrectRegexPatterns() {
        assertThat(PinCredentialConstants.REGEX_4_DIGITS).isEqualTo("^\\d{4}$");
        assertThat(PinCredentialConstants.REGEX_6_DIGITS).isEqualTo("^\\d{6}$");
        assertThat(PinCredentialConstants.REGEX_8_DIGITS).isEqualTo("^\\d{8}$");
    }
    
    @Test
    @DisplayName("Should have correct default format")
    void shouldHaveCorrectDefaultFormat() {
        assertThat(PinCredentialConstants.DEFAULT_FORMAT).isEqualTo("4-digits");
    }
    
    @Test
    @DisplayName("Should have correct config keys")
    void shouldHaveCorrectConfigKeys() {
        assertThat(PinCredentialConstants.CONFIG_PIN_FORMAT).isEqualTo("pin-format");
        assertThat(PinCredentialConstants.CONFIG_CUSTOM_REGEX).isEqualTo("pin-custom-regex");
    }
    
    @Test
    @DisplayName("Regex patterns should be valid")
    void regexPatternsShouldBeValid() {
        // Test 4-digit pattern
        assertThat("1234".matches(PinCredentialConstants.REGEX_4_DIGITS)).isTrue();
        assertThat("123".matches(PinCredentialConstants.REGEX_4_DIGITS)).isFalse();
        
        // Test 6-digit pattern
        assertThat("123456".matches(PinCredentialConstants.REGEX_6_DIGITS)).isTrue();
        assertThat("12345".matches(PinCredentialConstants.REGEX_6_DIGITS)).isFalse();
        
        // Test 8-digit pattern
        assertThat("12345678".matches(PinCredentialConstants.REGEX_8_DIGITS)).isTrue();
        assertThat("1234567".matches(PinCredentialConstants.REGEX_8_DIGITS)).isFalse();
    }
}
