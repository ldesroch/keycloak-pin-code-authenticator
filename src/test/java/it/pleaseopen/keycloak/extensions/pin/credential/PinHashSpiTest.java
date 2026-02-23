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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PinHashSpi Tests")
class PinHashSpiTest {

    private final PinHashSpi spi = new PinHashSpi();

    @Test
    @DisplayName("SPI name should be 'pin-hashing'")
    void spiNameShouldBePinHashing() {
        assertThat(spi.getName()).isEqualTo("pin-hashing");
    }

    @Test
    @DisplayName("SPI should not be internal")
    void spiShouldNotBeInternal() {
        assertThat(spi.isInternal()).isFalse();
    }

    @Test
    @DisplayName("Provider class should be PinHashProvider")
    void providerClassShouldBePinHashProvider() {
        assertThat(spi.getProviderClass()).isEqualTo(PinHashProvider.class);
    }

    @Test
    @DisplayName("Factory class should be PinHashProviderFactory")
    void factoryClassShouldBePinHashProviderFactory() {
        assertThat(spi.getProviderFactoryClass()).isEqualTo(PinHashProviderFactory.class);
    }
}
