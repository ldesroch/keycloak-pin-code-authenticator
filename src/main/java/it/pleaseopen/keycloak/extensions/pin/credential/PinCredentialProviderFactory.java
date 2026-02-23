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

import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.Config;

/**
 * Factory for creating PIN credential provider instances.
 */
public class PinCredentialProviderFactory 
        implements CredentialProviderFactory<CredentialProvider> {
    
    public static final String PROVIDER_ID = "pin-credential-provider";
    
    @Override
    public String getId() {
        return PROVIDER_ID;
    }
    
    @Override
    public PinCredentialProvider create(KeycloakSession session) {
        return new PinCredentialProvider(session);
    }
    
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
        // No cleanup needed
    }
}
