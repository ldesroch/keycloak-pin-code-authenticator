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
import org.keycloak.Config;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Argon2PinHashProviderFactory Tests")
class Argon2PinHashProviderFactoryTest {

    private Argon2PinHashProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new Argon2PinHashProviderFactory();
        // Initialize with an empty scope to use defaults
        factory.init(new EmptyConfigScope());
    }

    @Test
    @DisplayName("Factory ID should be 'argon2'")
    void factoryIdShouldBeArgon2() {
        assertThat(factory.getId()).isEqualTo("argon2");
    }

    @Test
    @DisplayName("Factory should create a PinHashProvider")
    void factoryShouldCreateProvider() {
        PinHashProvider provider = factory.create(null);

        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(Argon2PinHashProvider.class);
    }

    @Test
    @DisplayName("Created provider should use the default algorithm")
    void createdProviderShouldUseDefaultAlgorithm() {
        PinHashProvider provider = factory.create(null);

        assertThat(provider.getAlgorithm()).isEqualTo("argon2");
    }

    @Test
    @DisplayName("Created provider should produce valid encoded PINs")
    void createdProviderShouldEncodeAndVerify() {
        PinHashProvider provider = factory.create(null);

        PinHashProvider.EncodedPin encoded = provider.encode("1234", 5);
        assertThat(encoded.hash).isNotNull();
        assertThat(encoded.salt).isNotNull();
        assertThat(encoded.additionalParameters).isNotEmpty();

        boolean valid = provider.verify("1234", encoded.hash, encoded.salt, 5, encoded.additionalParameters);
        assertThat(valid).isTrue();

        boolean invalid = provider.verify("9999", encoded.hash, encoded.salt, 5, encoded.additionalParameters);
        assertThat(invalid).isFalse();
    }

    @Test
    @DisplayName("Created provider should match default policy")
    void createdProviderShouldMatchDefaultPolicy() {
        PinHashProvider provider = factory.create(null);

        PinHashProvider.EncodedPin encoded = provider.encode("1234", 5);
        boolean match = provider.policyMatch("argon2", 5, encoded.additionalParameters);
        assertThat(match).isTrue();
    }

    @Test
    @DisplayName("Factory should have order of 100")
    void factoryShouldHaveCorrectOrder() {
        assertThat(factory.order()).isEqualTo(100);
    }

    @Test
    @DisplayName("Factory should expose config metadata")
    void factoryShouldExposeConfigMetadata() {
        assertThat(factory.getConfigMetadata()).isNotNull();
        assertThat(factory.getConfigMetadata()).isNotEmpty();
    }

    @Test
    @DisplayName("Factory should create providers with custom config")
    void factoryShouldCreateProvidersWithCustomConfig() {
        Argon2PinHashProviderFactory customFactory = new Argon2PinHashProviderFactory();
        customFactory.init(new CustomConfigScope());

        PinHashProvider provider = customFactory.create(null);

        // Encode and verify should still work
        PinHashProvider.EncodedPin encoded = provider.encode("5678", 3);
        assertThat(provider.verify("5678", encoded.hash, encoded.salt, 3, encoded.additionalParameters)).isTrue();

        // Policy match should reflect custom config
        assertThat(provider.policyMatch("argon2", 3, encoded.additionalParameters)).isTrue();
        // Default iterations (5) should NOT match custom iterations (3)
        assertThat(provider.policyMatch("argon2", 5, encoded.additionalParameters)).isFalse();
    }

    @Test
    @DisplayName("close and postInit should not throw")
    void closeAndPostInitShouldNotThrow() {
        factory.postInit(null);
        factory.close();
    }

    // ── Test Config.Scope implementations ────────────────────────────────────

    /** Empty config scope — all methods return null/defaults */
    private static class EmptyConfigScope implements Config.Scope {
        @Override public String get(String key) { return null; }
        @Override public String get(String key, String defaultValue) { return defaultValue; }
        @Override public String[] getArray(String key) { return null; }
        @Override public Integer getInt(String key) { return null; }
        @Override public Integer getInt(String key, Integer defaultValue) { return defaultValue; }
        @Override public Long getLong(String key) { return null; }
        @Override public Long getLong(String key, Long defaultValue) { return defaultValue; }
        @Override public Boolean getBoolean(String key) { return null; }
        @Override public Boolean getBoolean(String key, Boolean defaultValue) { return defaultValue; }
        @Override public Config.Scope scope(String... scope) { return this; }
        @Override public Set<String> getPropertyNames() { return Set.of(); }
        @Override public Config.Scope root() { return this; }
    }

    /** Custom config scope with non-default values */
    private static class CustomConfigScope implements Config.Scope {
        @Override public String get(String key) {
            switch (key) {
                case "type": return "i";
                case "version": return "1.0";
                default: return null;
            }
        }
        @Override public String get(String key, String defaultValue) {
            String v = get(key);
            return v != null ? v : defaultValue;
        }
        @Override public String[] getArray(String key) { return null; }
        @Override public Integer getInt(String key) {
            switch (key) {
                case "hashLength": return 16;
                case "memory": return 4096;
                case "iterations": return 3;
                case "parallelism": return 2;
                default: return null;
            }
        }
        @Override public Integer getInt(String key, Integer defaultValue) {
            Integer v = getInt(key);
            return v != null ? v : defaultValue;
        }
        @Override public Long getLong(String key) { return null; }
        @Override public Long getLong(String key, Long defaultValue) { return defaultValue; }
        @Override public Boolean getBoolean(String key) { return null; }
        @Override public Boolean getBoolean(String key, Boolean defaultValue) { return defaultValue; }
        @Override public Config.Scope scope(String... scope) { return this; }
        @Override public Set<String> getPropertyNames() { return Set.of(); }
        @Override public Config.Scope root() { return this; }
    }
}
