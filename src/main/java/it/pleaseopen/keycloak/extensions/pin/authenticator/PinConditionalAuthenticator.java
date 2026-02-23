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

import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialConstants;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Conditional authenticator that checks if a user has a PIN code credential configured.
 * This allows creating authentication flows with optional PIN authentication:
 * - Returns true if user has a PIN configured
 * - Returns false if user has no PIN configured
 * 
 * Use this in a conditional flow to require PIN only when configured:
 * <pre>
 * Browser Flow
 * ├─ Username/Password
 * └─ CONDITIONAL: PIN if Configured
 *     ├─ Condition: PIN Configured? (this authenticator)
 *     └─ PIN Authenticator (runs only if condition is true)
 * </pre>
 */
public class PinConditionalAuthenticator implements ConditionalAuthenticator {
    
    private static final Logger logger = Logger.getLogger(PinConditionalAuthenticator.class);
    
    public static final PinConditionalAuthenticator SINGLETON = new PinConditionalAuthenticator();
    
    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        
        // If no user in context, condition is false
        if (user == null) {
            logger.debugf("No user in context, PIN condition returns false");
            return false;
        }
        
        // Get PIN credential provider
        PinCredentialProvider pinProvider = (PinCredentialProvider) context.getSession()
                .getProvider(org.keycloak.credential.CredentialProvider.class, 
                           it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory.PROVIDER_ID);
        
        if (pinProvider == null) {
            logger.warnf("PIN credential provider not available, condition returns false");
            return false;
        }
        
        // Check if user has PIN configured
        boolean hasPin = pinProvider.isConfiguredFor(context.getRealm(), user, PinCredentialConstants.CREDENTIAL_TYPE);
        
        logger.debugf("User %s has PIN configured: %b", user.getId(), hasPin);
        
        return hasPin;
    }
    
    @Override
    public void action(AuthenticationFlowContext context) {
        // Not used for conditional authenticators
    }
    
    @Override
    public boolean requiresUser() {
        return true;
    }
    
    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions for conditional authenticators
    }
    
    @Override
    public void close() {
        // No resources to close
    }
}
