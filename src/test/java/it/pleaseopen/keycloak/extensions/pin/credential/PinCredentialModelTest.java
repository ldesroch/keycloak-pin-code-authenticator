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
import org.keycloak.credential.CredentialModel;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PinCredentialModel Tests")
class PinCredentialModelTest {

    @Test
    @DisplayName("Should create model with all fields")
    void shouldCreateModelWithAllFields() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "id");

        PinCredentialModel model = PinCredentialModel.create(
                "hashValue", "saltValue", "argon2", 5, "6-digits", null, params);

        assertThat(model.getHashedPin()).isEqualTo("hashValue");
        assertThat(model.getSalt()).isEqualTo("saltValue");
        assertThat(model.getAlgorithm()).isEqualTo("argon2");
        assertThat(model.getHashIterations()).isEqualTo(5);
        assertThat(model.getPinFormat()).isEqualTo("6-digits");
        assertThat(model.getCustomRegex()).isNull();
        assertThat(model.getAdditionalParam("type")).isEqualTo("id");
    }

    @Test
    @DisplayName("Should round-trip through CredentialModel")
    void shouldRoundTripThroughCredentialModel() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "id");
        params.put("memory", "7168");

        PinCredentialModel original = PinCredentialModel.create(
                "abc123", "salt123", "argon2", 5, "4-digits", null, params);

        CredentialModel stored = original.toCredentialModel();
        assertThat(stored.getType()).isEqualTo("pin-code");
        assertThat(stored.getSecretData()).contains("abc123");
        assertThat(stored.getCredentialData()).contains("argon2");

        PinCredentialModel restored = PinCredentialModel.createFromCredentialModel(stored);
        assertThat(restored).isNotNull();
        assertThat(restored.getHashedPin()).isEqualTo("abc123");
        assertThat(restored.getSalt()).isEqualTo("salt123");
        assertThat(restored.getAlgorithm()).isEqualTo("argon2");
        assertThat(restored.getHashIterations()).isEqualTo(5);
        assertThat(restored.getPinFormat()).isEqualTo("4-digits");
        assertThat(restored.getAdditionalParam("memory")).isEqualTo("7168");
    }

    @Test
    @DisplayName("Should handle custom regex model")
    void shouldHandleCustomRegex() {
        PinCredentialModel model = PinCredentialModel.create(
                "hash", "salt", "argon2", 5, "custom", "^[A-Z]{4}$", null);

        assertThat(model.getPinFormat()).isEqualTo("custom");
        assertThat(model.getCustomRegex()).isEqualTo("^[A-Z]{4}$");
    }

    @Test
    @DisplayName("Should return null for non-PIN credential type")
    void shouldReturnNullForNonPinType() {
        CredentialModel other = new CredentialModel();
        other.setType("password");
        assertThat(PinCredentialModel.createFromCredentialModel(other)).isNull();
    }

    @Test
    @DisplayName("Should return null for null model")
    void shouldReturnNullForNull() {
        assertThat(PinCredentialModel.createFromCredentialModel(null)).isNull();
    }
}
