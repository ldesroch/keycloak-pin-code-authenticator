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

import it.pleaseopen.keycloak.extensions.pin.config.PinConfiguration;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for creating PIN authenticator instances.
 */
public class PinAuthenticatorFactory implements AuthenticatorFactory {

    private static final Logger logger = Logger.getLogger(PinAuthenticatorFactory.class);

    public static final String PROVIDER_ID = "pin-authenticator";
    private static final PinAuthenticator SINGLETON = new PinAuthenticator();
    
    @Override
    public String getId() {
        return PROVIDER_ID;
    }
    
    @Override
    public String getDisplayType() {
        return "PIN Code";
    }
    
    @Override
    public String getHelpText() {
        return "Validates a PIN code for additional authentication factor.";
    }
    
    @Override
    public String getReferenceCategory() {
        return "pin";
    }
    
    @Override
    public boolean isConfigurable() {
        return true;
    }
    
    @Override
    public boolean isUserSetupAllowed() {
        return true;
    }
    
    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
        };
    }
    
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name("pin.requiredAction")
                    .label("Force PIN Configuration")
                    .helpText("Require users to configure a PIN if they don't have one")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                .property()
                    .name("pin.resetEnabled")
                    .label("Enable PIN Reset")
                    .helpText("Allow users to request a PIN reset via email during authentication")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                .build();
                
    }
    
    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }
    
    @Override
    public void init(Config.Scope config) {
        // Initialize shared PIN configuration from Keycloak's native Config.Scope.
        // Keycloak calls: Config.scope("authenticator", "pin-authenticator") then factory.init(scope)
        // This reads env vars like KC_SPI_AUTHENTICATOR__PIN_AUTHENTICATOR__PIN_FORMAT
        PinConfiguration.init(config);
        logger.info("PinAuthenticatorFactory initialized with Config.Scope");
    }
    
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }
    
    @Override
    public void close() {
        // No resources to close
    }
}
