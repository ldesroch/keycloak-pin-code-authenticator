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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Argon2 PIN Hash Provider Tests")
class Argon2PinHashProviderTest {

    private Argon2PinHashProvider provider;

    @BeforeEach
    void setUp() {
        provider = new Argon2PinHashProvider();
    }

    @Test
    @DisplayName("Algorithm should be 'argon2'")
    void algorithmShouldBeArgon2() {
        assertThat(provider.getAlgorithm()).isEqualTo("argon2");
    }

    @Test
    @DisplayName("Encode should produce hash, salt, and parameters")
    void encodeShouldProduceHashSaltAndParams() {
        PinHashProvider.EncodedPin result = provider.encode("123456", 5);

        assertThat(result.hash).isNotNull().isNotEmpty();
        assertThat(result.salt).isNotNull().isNotEmpty();
        assertThat(result.additionalParameters).containsEntry("type", "id");
        assertThat(result.additionalParameters).containsEntry("version", "1.3");
        assertThat(result.additionalParameters).containsEntry("hashLength", "32");
        assertThat(result.additionalParameters).containsEntry("memory", "7168");
        assertThat(result.additionalParameters).containsEntry("parallelism", "1");

        // Hash should be valid Base64, 32 bytes decoded
        byte[] hashBytes = Base64.getDecoder().decode(result.hash);
        assertThat(hashBytes).hasSize(32);

        // Salt should be valid Base64, 16 bytes decoded
        byte[] saltBytes = Base64.getDecoder().decode(result.salt);
        assertThat(saltBytes).hasSize(16);
    }

    @Test
    @DisplayName("Verify should accept correct PIN")
    void verifyShouldAcceptCorrectPin() {
        PinHashProvider.EncodedPin encoded = provider.encode("1234", 5);

        boolean result = provider.verify("1234", encoded.hash, encoded.salt, 5, encoded.additionalParameters);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Verify should reject wrong PIN")
    void verifyShouldRejectWrongPin() {
        PinHashProvider.EncodedPin encoded = provider.encode("1234", 5);

        boolean result = provider.verify("5678", encoded.hash, encoded.salt, 5, encoded.additionalParameters);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Different PINs should produce different hashes")
    void differentPinsShouldProduceDifferentHashes() {
        PinHashProvider.EncodedPin a = provider.encode("1234", 5);
        PinHashProvider.EncodedPin b = provider.encode("5678", 5);

        assertThat(a.hash).isNotEqualTo(b.hash);
    }

    @Test
    @DisplayName("Same PIN encoded twice should produce different hashes (random salt)")
    void samePinShouldProduceDifferentHashes() {
        PinHashProvider.EncodedPin a = provider.encode("1234", 5);
        PinHashProvider.EncodedPin b = provider.encode("1234", 5);

        assertThat(a.hash).isNotEqualTo(b.hash);
        assertThat(a.salt).isNotEqualTo(b.salt);
    }

    @Test
    @DisplayName("Verify should return false for null hash")
    void verifyShouldReturnFalseForNullHash() {
        assertThat(provider.verify("1234", null, "c2FsdA==", 5, null)).isFalse();
    }

    @Test
    @DisplayName("Verify should return false for null salt")
    void verifyShouldReturnFalseForNullSalt() {
        assertThat(provider.verify("1234", "aGFzaA==", null, 5, null)).isFalse();
    }

    @Test
    @DisplayName("PolicyMatch should match default parameters")
    void policyMatchShouldMatchDefaults() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "id");
        params.put("version", "1.3");
        params.put("hashLength", "32");
        params.put("memory", "7168");
        params.put("parallelism", "1");

        assertThat(provider.policyMatch("argon2", 5, params)).isTrue();
    }

    @Test
    @DisplayName("PolicyMatch should reject wrong algorithm")
    void policyMatchShouldRejectWrongAlgorithm() {
        assertThat(provider.policyMatch("pbkdf2", 5, null)).isFalse();
    }

    @Test
    @DisplayName("PolicyMatch should reject wrong iterations")
    void policyMatchShouldRejectWrongIterations() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "id");
        params.put("version", "1.3");
        params.put("hashLength", "32");
        params.put("memory", "7168");
        params.put("parallelism", "1");

        assertThat(provider.policyMatch("argon2", 3, params)).isFalse();
    }

    @Test
    @DisplayName("PolicyMatch should reject wrong memory")
    void policyMatchShouldRejectWrongMemory() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "id");
        params.put("version", "1.3");
        params.put("hashLength", "32");
        params.put("memory", "65536");
        params.put("parallelism", "1");

        assertThat(provider.policyMatch("argon2", 5, params)).isFalse();
    }

    @Test
    @DisplayName("Encode should clamp invalid iterations to defaults")
    void encodeShouldClampInvalidIterations() {
        PinHashProvider.EncodedPin zeroIter = provider.encode("1234", 0);
        PinHashProvider.EncodedPin negIter = provider.encode("1234", -1);

        // Both should still produce valid output (defaulting to 5)
        assertThat(zeroIter.hash).isNotNull().isNotEmpty();
        assertThat(negIter.hash).isNotNull().isNotEmpty();

        // Verify still works
        assertThat(provider.verify("1234", zeroIter.hash, zeroIter.salt, 5, zeroIter.additionalParameters))
                .isTrue();
    }
}
