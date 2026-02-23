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

import org.keycloak.Config;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Factory for the PIN conditional authenticator.
 * Creates instances of the authenticator that checks if a user has a PIN configured.
 */
public class PinConditionalAuthenticatorFactory implements ConditionalAuthenticatorFactory {
    
    public static final String PROVIDER_ID = "pin-conditional";
    
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };
    
    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }
    
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }
    
    @Override
    public void close() {
        // No resources to close
    }
    
    @Override
    public String getId() {
        return PROVIDER_ID;
    }
    
    @Override
    public String getDisplayType() {
        return "Condition - PIN Configured";
    }
    
    @Override
    public String getReferenceCategory() {
        return "condition";
    }
    
    @Override
    public boolean isConfigurable() {
        return false;
    }
    
    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }
    
    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }
    
    @Override
    public String getHelpText() {
        return "Flow is executed only if the user has a PIN code credential configured. " +
               "Use this in conditional flows to make PIN authentication optional.";
    }
    
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // No configuration properties needed
        return null;
    }
    
    @Override
    public ConditionalAuthenticator getSingleton() {
        return PinConditionalAuthenticator.SINGLETON;
    }
}
