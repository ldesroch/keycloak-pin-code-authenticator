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
package it.pleaseopen.keycloak.extensions.pin.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PinResetResourceProviderFactory}.
 */
@DisplayName("PinResetResourceProviderFactory Tests")
class PinResetResourceProviderFactoryTest {

    private PinResetResourceProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PinResetResourceProviderFactory();
    }

    @Test
    @DisplayName("Factory ID should be 'pin-reset'")
    void factoryIdShouldBePinReset() {
        assertThat(factory.getId()).isEqualTo("pin-reset");
    }

    @Test
    @DisplayName("ID constant should match getId()")
    void idConstantShouldMatchGetId() {
        assertThat(PinResetResourceProviderFactory.ID).isEqualTo(factory.getId());
    }

    @Test
    @DisplayName("create() should return a PinResetResourceProvider")
    void createShouldReturnProvider() {
        // Passing null session — the provider just stores it, no immediate usage
        var provider = factory.create(null);
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(PinResetResourceProvider.class);
    }

    @Test
    @DisplayName("init, postInit, and close should not throw")
    void lifecycleMethodsShouldNotThrow() {
        factory.init(null);
        factory.postInit(null);
        factory.close();
    }
}
