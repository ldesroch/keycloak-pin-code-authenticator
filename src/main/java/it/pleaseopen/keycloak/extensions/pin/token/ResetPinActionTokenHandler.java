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
package it.pleaseopen.keycloak.extensions.pin.token;

import it.pleaseopen.keycloak.extensions.pin.action.ConfigurePinActionFactory;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.authentication.actiontoken.AbstractActionTokenHandler;
import org.keycloak.authentication.actiontoken.ActionTokenContext;
import org.keycloak.credential.CredentialModel;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.models.Constants;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.Response;

/**
 * Handles the {@link ResetPinActionToken} action token.
 *
 * <p>When the user clicks the PIN reset link in the email:
 * <ol>
 *   <li>Deletes the user's existing PIN credential</li>
 *   <li>Adds the CONFIGURE_PIN required action to the authentication session</li>
 *   <li>Redirects the user to configure a new PIN</li>
 * </ol>
 *
 * <p>Registered via {@code META-INF/services/org.keycloak.authentication.actiontoken.ActionTokenHandlerFactory}.
 * Since {@link AbstractActionTokenHandler} implements both handler and factory,
 * this single class serves both roles.
 */
public class ResetPinActionTokenHandler extends AbstractActionTokenHandler<ResetPinActionToken> {

    private static final Logger logger = Logger.getLogger(ResetPinActionTokenHandler.class);

    public ResetPinActionTokenHandler() {
        super(
                ResetPinActionToken.TOKEN_TYPE,
                ResetPinActionToken.class,
                "resetPinNotAllowed",
                EventType.CUSTOM_REQUIRED_ACTION,
                Errors.NOT_ALLOWED
        );
    }

    @Override
    public Response handleToken(ResetPinActionToken token,
                                ActionTokenContext<ResetPinActionToken> tokenContext) {
        var session = tokenContext.getSession();
        var realm = tokenContext.getRealm();
        var authSession = tokenContext.getAuthenticationSession();

        // Mark as executing action (same pattern as ExecuteActionsActionTokenHandler)
        authSession.setClientNote(Constants.KC_ACTION_EXECUTING, Boolean.TRUE.toString());

        UserModel user = authSession.getAuthenticatedUser();

        // Delete existing PIN credential
        PinCredentialProvider pinProvider = (PinCredentialProvider) session
                .getProvider(org.keycloak.credential.CredentialProvider.class,
                        PinCredentialProviderFactory.PROVIDER_ID);
        if (pinProvider != null) {
            CredentialModel existing = pinProvider.getPinCredential(realm, user);
            if (existing != null) {
                pinProvider.deleteCredential(realm, user, existing.getId());
                logger.debugf("Deleted existing PIN credential for user %s via action token",
                        user.getId());
            }
        }

        // Add CONFIGURE_PIN required action to both user and auth session
        user.addRequiredAction(ConfigurePinActionFactory.PROVIDER_ID);
        authSession.addRequiredAction(ConfigurePinActionFactory.PROVIDER_ID);

        // Redirect to configure PIN
        String nextAction = AuthenticationManager.nextRequiredAction(
                session, authSession, tokenContext.getRequest(), tokenContext.getEvent());
        return AuthenticationManager.redirectToRequiredActions(
                session, realm, authSession, tokenContext.getUriInfo(), nextAction);
    }

    @Override
    public boolean canUseTokenRepeatedly(ResetPinActionToken token,
                                         ActionTokenContext<ResetPinActionToken> tokenContext) {
        return false;
    }
}
