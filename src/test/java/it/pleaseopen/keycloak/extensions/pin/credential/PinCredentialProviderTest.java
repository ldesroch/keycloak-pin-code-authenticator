package it.pleaseopen.keycloak.extensions.pin.credential;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PinCredentialProvider}.
 * These tests focus on the provider's logic without mocking Keycloak internals.
 */
@DisplayName("PIN Credential Provider Tests")
class PinCredentialProviderTest {
    
    private PinCredentialProvider provider;
    
    @BeforeEach
    void setUp() {
        // Create provider with null session (we won't use session-dependent methods in these tests)
        provider = new PinCredentialProvider(null);
    }
    
    @Test
    @DisplayName("Provider type should be 'pin-code'")
    void testGetType() {
        assertThat(provider.getType()).isEqualTo("pin-code");
    }
    
    @Test
    @DisplayName("Should support PIN credential type")
    void testSupportsCredentialType() {
        assertThat(provider.supportsCredentialType("pin-code")).isTrue();
        assertThat(provider.supportsCredentialType("password")).isFalse();
    }
    
    @ParameterizedTest
    @CsvSource({
        "1234, 4-digits, true",
        "123456, 6-digits, true",
        "12345678, 8-digits, true",
        "abc, 4-digits, false",
        "12345, 4-digits, false",
        "123, 6-digits, false"
    })
    @DisplayName("Should validate PIN format correctly")
    void testPinFormatValidation(String pin, String format, boolean expectedValid) {
        boolean isValid = provider.isValidPinFormat(pin, format, null);
        assertThat(isValid).isEqualTo(expectedValid);
    }
    
    @Test
    @DisplayName("Should validate custom regex pattern")
    void testCustomRegexValidation() {
        String customRegex = "^[A-Z]{4}$";  // 4 uppercase letters
        
        assertThat(provider.isValidPinFormat("ABCD", "custom", customRegex)).isTrue();
        assertThat(provider.isValidPinFormat("1234", "custom", customRegex)).isFalse();
        assertThat(provider.isValidPinFormat("abcd", "custom", customRegex)).isFalse();
    }
    
    @Test
    @DisplayName("Should reject null PIN")
    void testNullPinValidation() {
        assertThat(provider.isValidPinFormat(null, "4-digits", null)).isFalse();
    }
    
    @Test
    @DisplayName("Should reject custom format without regex")
    void testCustomFormatWithoutRegex() {
        assertThat(provider.isValidPinFormat("1234", "custom", null)).isFalse();
        assertThat(provider.isValidPinFormat("1234", "custom", "")).isFalse();
    }
    
    @Test
    @DisplayName("Should reject unknown format")
    void testUnknownFormat() {
        assertThat(provider.isValidPinFormat("1234", "unknown-format", null)).isFalse();
    }
}
