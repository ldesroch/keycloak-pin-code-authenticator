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

import it.pleaseopen.keycloak.extensions.pin.action.ConfigurePinActionFactory;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import it.pleaseopen.keycloak.extensions.pin.token.ResetPinActionToken;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resources.LoginActionsService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JAX-RS resource for PIN reset.
 *
 * <p>Endpoint: {@code POST /realms/{realm}/pin-reset}
 *
 * <p>Requires an admin bearer token with realm-admin role.
 * Accepts JSON body: {@code {"userId": "<user-id>"}} or {@code {"username": "<username>"}}.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Deletes the user's existing PIN credential (if any)</li>
 *   <li>Adds the {@code CONFIGURE_PIN} required action</li>
 *   <li>Sends an execute-actions email so the user gets a link to configure a new PIN</li>
 * </ol>
 */
public class PinResetResource {

    private static final Logger logger = Logger.getLogger(PinResetResource.class);

    private static final int TOKEN_LIFESPAN_SECONDS = 12 * 60 * 60; // 12 hours

    private final KeycloakSession session;
    private final AuthenticationManager.AuthResult auth;

    public PinResetResource(KeycloakSession session) {
        this.session = session;
        this.auth = new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
    }

    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetPin(PinResetRequest request) {
        checkAdminAuth();

        RealmModel realm = session.getContext().getRealm();
        UserModel user = resolveUser(realm, request);

        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"User not found\"}")
                    .build();
        }

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"User has no email address configured\"}")
                    .build();
        }

        // 1. Delete existing PIN credentials (ensure single PIN)
        PinCredentialProvider pinProvider = (PinCredentialProvider) session
                .getProvider(org.keycloak.credential.CredentialProvider.class, PinCredentialProviderFactory.PROVIDER_ID);

        if (pinProvider != null) {
            CredentialModel existing = pinProvider.getPinCredential(realm, user);
            if (existing != null) {
                pinProvider.deleteCredential(realm, user, existing.getId());
                logger.debugf("Deleted existing PIN credential for user %s", user.getId());
            }
        }

        // 2. Add CONFIGURE_PIN required action
        user.addRequiredAction(ConfigurePinActionFactory.PROVIDER_ID);

        // 3. Send PIN reset email with custom action token
        try {
            int expiration = Time.currentTime() + TOKEN_LIFESPAN_SECONDS;
            ResetPinActionToken token = new ResetPinActionToken(
                    user.getId(),
                    user.getEmail(),
                    expiration,
                    null  // no auth session
            );

            var builder = LoginActionsService.actionTokenProcessor(
                    session.getContext().getUri());
            builder.queryParam("key", token.serialize(session, realm, session.getContext().getUri()));

            String link = builder.build(realm.getName()).toString();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("link", link);
            attributes.put("linkExpiration", TimeUnit.SECONDS.toMinutes(TOKEN_LIFESPAN_SECONDS));

            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setUser(user)
                    .send("pinResetEmailSubject", "pin-reset-email.ftl", attributes);

            logger.infof("PIN reset email sent to user %s", user.getId());

            return Response.noContent().build();

        } catch (EmailException e) {
            logger.error("Failed to send PIN reset email", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to send PIN reset email\"}")
                    .build();
        }
    }

    private void checkAdminAuth() {
        if (auth == null) {
            throw new NotAuthorizedException("Bearer");
        }
        if (auth.token().getRealmAccess() == null
                || !auth.token().getRealmAccess().isUserInRole("admin")) {
            throw new ForbiddenException("Requires realm admin role");
        }
    }

    private UserModel resolveUser(RealmModel realm, PinResetRequest request) {
        if (request == null) return null;
        if (request.userId != null && !request.userId.isEmpty()) {
            return session.users().getUserById(realm, request.userId);
        }
        if (request.username != null && !request.username.isEmpty()) {
            return session.users().getUserByUsername(realm, request.username);
        }
        return null;
    }

    /** Request body DTO. */
    public static class PinResetRequest {
        public String userId;
        public String username;
    }
}
