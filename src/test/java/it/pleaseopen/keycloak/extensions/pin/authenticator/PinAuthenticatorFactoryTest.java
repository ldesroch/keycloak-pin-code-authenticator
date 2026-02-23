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
package it.pleaseopen.keycloak.extensions.pin.authenticator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.keycloak.models.AuthenticationExecutionModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PinAuthenticatorFactory.
 */
@DisplayName("PinAuthenticatorFactory Tests")
class PinAuthenticatorFactoryTest {
    
    private final PinAuthenticatorFactory factory = new PinAuthenticatorFactory();
    
    @Test
    @DisplayName("Should have correct provider ID")
    void shouldHaveCorrectProviderId() {
        assertThat(factory.getId()).isEqualTo("pin-authenticator");
    }
    
    @Test
    @DisplayName("Should have correct display type")
    void shouldHaveCorrectDisplayType() {
        assertThat(factory.getDisplayType()).isEqualTo("PIN Code");
    }
    
    @Test
    @DisplayName("Should provide help text")
    void shouldProvideHelpText() {
        assertThat(factory.getHelpText()).isNotEmpty();
        assertThat(factory.getHelpText()).contains("PIN");
    }
    
    @Test
    @DisplayName("Should be configurable")
    void shouldBeConfigurable() {
        assertThat(factory.isConfigurable()).isTrue();
    }
    
    @Test
    @DisplayName("Should allow user setup")
    void shouldAllowUserSetup() {
        assertThat(factory.isUserSetupAllowed()).isTrue();
    }
    
    @Test
    @DisplayName("Should have correct requirement choices")
    void shouldHaveCorrectRequirementChoices() {
        AuthenticationExecutionModel.Requirement[] requirements = factory.getRequirementChoices();
        
        assertThat(requirements).containsExactlyInAnyOrder(
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        );
    }
    
    @Test
    @DisplayName("Should provide config properties")
    void shouldProvideConfigProperties() {
        assertThat(factory.getConfigProperties()).isNotEmpty();
        assertThat(factory.getConfigProperties()).hasSize(2);
        
        // Check pin.requiredAction property
        assertThat(factory.getConfigProperties().stream()
                .anyMatch(p -> p.getName().equals("pin.requiredAction"))).isTrue();
        
        // Check pin.resetEnabled property
        assertThat(factory.getConfigProperties().stream()
                .anyMatch(p -> p.getName().equals("pin.resetEnabled"))).isTrue();
    }
    
    @Test
    @DisplayName("Should create authenticator instance")
    void shouldCreateAuthenticatorInstance() {
        assertThat(factory.create(null)).isNotNull();
        assertThat(factory.create(null)).isInstanceOf(PinAuthenticator.class);
    }
    
    @Test
    @DisplayName("Should have correct reference category")
    void shouldHaveCorrectReferenceCategory() {
        assertThat(factory.getReferenceCategory()).isEqualTo("pin");
    }
}
