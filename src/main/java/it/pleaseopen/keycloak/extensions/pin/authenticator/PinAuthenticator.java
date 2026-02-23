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
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialConstants;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialModel;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import it.pleaseopen.keycloak.extensions.pin.keyboard.KeyboardImageGenerator;
import it.pleaseopen.keycloak.extensions.pin.token.ResetPinActionToken;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.resources.LoginActionsService;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Authenticator for PIN code validation during authentication flow.
 * This authenticator displays a PIN input form and validates the entered PIN.
 */
public class PinAuthenticator implements Authenticator {
    
    private static final Logger logger = Logger.getLogger(PinAuthenticator.class);
    
    private static final String TPL_CODE = "pin-authenticator.ftl";
    private static final String FORM_PIN = "pin";
    private static final String FORM_PIN_COORDS = "pinCoords";
    private static final String AUTH_NOTE_COORD_MAP = "pin.keyboard.coordMap";
    private static final String ATTR_ATTEMPTS = "pin.attempts";
    private static final int TOKEN_LIFESPAN_SECONDS = 12 * 60 * 60; // 12 hours
    
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Check if user has PIN configured
        UserModel user = context.getUser();
        if (user == null) {
            context.attempted();
            return;
        }
        
        PinCredentialProvider pinProvider = (PinCredentialProvider) context.getSession()
                .getProvider(org.keycloak.credential.CredentialProvider.class, 
                           it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory.PROVIDER_ID);
        
        if (pinProvider == null || !pinProvider.isConfiguredFor(context.getRealm(), user, PinCredentialConstants.CREDENTIAL_TYPE)) {
            logger.debugf("User %s has no PIN configured, skipping PIN authentication", user.getId());
            context.attempted();
            return;
        }
        
        // Show PIN input form
        Response challenge = createForm(context, null);
        context.challenge(challenge);
    }
    
    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        
        // Check if this is a PIN reset request
        String resetPin = formData.getFirst("resetPin");
        if ("true".equals(resetPin)) {
            handlePinReset(context);
            return;
        }
        
        String pin = formData.getFirst(FORM_PIN);
        
        // If visual keyboard is enabled, resolve PIN from submitted coordinates
        PinConfiguration config = PinConfiguration.getInstance();
        if (config.isVisualKeyboardEnabled()) {
            String pinCoords = formData.getFirst(FORM_PIN_COORDS);
            if (pinCoords != null && !pinCoords.isEmpty()) {
                String coordMapStr = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_COORD_MAP);
                if (coordMapStr != null) {
                    Map<String, int[]> coordMap = KeyboardImageGenerator.deserializeCoordinateMap(coordMapStr);
                    StringBuilder resolvedPin = new StringBuilder();
                    // Format: "x1,y1|x2,y2|..."
                    String[] clicks = pinCoords.split("\\|");
                    for (String click : clicks) {
                        String[] xy = click.split(",");
                        if (xy.length == 2) {
                            try {
                                int cx = Integer.parseInt(xy[0].trim());
                                int cy = Integer.parseInt(xy[1].trim());
                                String digit = KeyboardImageGenerator.resolveDigit(cx, cy, coordMap);
                                if (digit != null) {
                                    resolvedPin.append(digit);
                                }
                            } catch (NumberFormatException e) {
                                // Ignore malformed coordinate
                            }
                        }
                    }
                    pin = resolvedPin.toString();
                    
                    if (config.isVisualKeyboardDebugEnabled()) {
                        String userId = context.getUser() != null ? context.getUser().getId() : "unknown";
                        logger.infof("[VISUAL-KB-DEBUG] Resolved PIN from coordinates for user '%s': length=%d, coords=%s",
                                     userId, pin.length(), pinCoords);
                    }
                }
            }
        }
        
        if (pin == null || pin.isEmpty()) {
            Response challenge = createForm(context, "missingPinMessage");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }
        
        // Validate PIN
        UserModel user = context.getUser();
        RealmModel realm = context.getRealm();
        
        // Create a credential input for PIN validation with correct type
        CredentialInput input = new UserCredentialModel(null, PinCredentialConstants.CREDENTIAL_TYPE, pin);
        
        PinCredentialProvider pinProvider = (PinCredentialProvider) context.getSession()
                .getProvider(org.keycloak.credential.CredentialProvider.class, 
                           it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory.PROVIDER_ID);
        
        if (pinProvider == null) {
            logger.error("PIN credential provider not available");
            Response challenge = createForm(context, "invalidPinMessage");
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge);
            return;
        }
        
        boolean valid = pinProvider.isValid(realm, user, input);
        
        if (valid) {
            context.success();
        } else {
            // Invalid PIN - let Keycloak handle brute force detection
            logger.debugf("Invalid PIN attempt for user %s", user.getId());
            context.getEvent().error(org.keycloak.events.Errors.INVALID_USER_CREDENTIALS);
            Response challenge = createForm(context, "invalidPinMessage");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
        }
    }
    
    @Override
    public boolean requiresUser() {
        return true;
    }
    
    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        PinCredentialProvider pinProvider = (PinCredentialProvider) session
                .getProvider(org.keycloak.credential.CredentialProvider.class, 
                           it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory.PROVIDER_ID);
        
        if (pinProvider == null) {
            return false;
        }
        
        return pinProvider.isConfiguredFor(realm, user, PinCredentialConstants.CREDENTIAL_TYPE);
    }
    
    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // Add required action to configure PIN if not set
        if (!configuredFor(session, realm, user) && getForcePinRequiredActionConfig(session)) {
            user.addRequiredAction("CONFIGURE_PIN");
        }
    }
    
    @Override
    public void close() {
        // No resources to close
    }
    
    /**
     * Creates the PIN input form.
     * The PIN format shown matches the user's STORED credential format (not global config).
     * Visual keyboard settings come from global Config.Scope configuration.
     */
    private Response createForm(AuthenticationFlowContext context, String error) {
        LoginFormsProvider form = context.form();
        
        if (error != null) {
            form.setError(error);
        }
        
        // Read user's stored PIN format from their credential
        String userPinFormat = getUserStoredPinFormat(context);
        form.setAttribute("pinFormat", userPinFormat);
        form.setAttribute("pinFormatHint", getFormatHint(userPinFormat));
        
        // Visual keyboard from global Config.Scope
        PinConfiguration config = PinConfiguration.getInstance();
        boolean showKeyboard = config.isVisualKeyboardEnabled();
        form.setAttribute("showVisualKeyboard", showKeyboard);
        
        // PIN reset enabled from authenticator config
        form.setAttribute("resetEnabled", isResetEnabled(context));
        
        // Generate shuffled keyboard image server-side and pass to template
        if (showKeyboard) {
            boolean obfuscate = config.isVisualKeyboardObfuscationEnabled();
            KeyboardImageGenerator.KeyboardImage kbImage = KeyboardImageGenerator.generate(obfuscate);
            
            // Store coordinate map in auth session notes (never sent to client)
            String coordMapStr = KeyboardImageGenerator.serializeCoordinateMap(kbImage.getCoordinateMap());
            context.getAuthenticationSession().setAuthNote(AUTH_NOTE_COORD_MAP, coordMapStr);
            
            // Pass Base64 image to template
            form.setAttribute("keyboardImage", kbImage.getBase64Png());
            form.setAttribute("keyboardWidth", KeyboardImageGenerator.IMAGE_WIDTH);
            form.setAttribute("keyboardHeight", KeyboardImageGenerator.IMAGE_HEIGHT);
            
            if (config.isVisualKeyboardDebugEnabled()) {
                String userId = context.getUser() != null ? context.getUser().getId() : "unknown";
                logger.infof("[VISUAL-KB-DEBUG] Keyboard coordinate map for user '%s': %s", userId, coordMapStr);
            }
        }
        
        return form.createForm(TPL_CODE);
    }
    
    /**
     * Reads the PIN format the user registered with from their stored credential.
     * Falls back to global config if no credential is found.
     */
    private String getUserStoredPinFormat(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return PinConfiguration.getInstance().getPinFormat();
        }
        
        PinCredentialProvider pinProvider = getPinProvider(context);
        if (pinProvider == null) {
            return PinConfiguration.getInstance().getPinFormat();
        }
        
        CredentialModel credential = pinProvider.getPinCredential(context.getRealm(), user);
        if (credential == null) {
            return PinConfiguration.getInstance().getPinFormat();
        }
        
        PinCredentialModel pinModel = PinCredentialModel.createFromCredentialModel(credential);
        if (pinModel == null || pinModel.getPinFormat() == null || pinModel.getPinFormat().isEmpty()) {
            return PinConfiguration.getInstance().getPinFormat();
        }
        
        return pinModel.getPinFormat();
    }

    /**
     * Gets the configured PIN requirement from authenticator configuration.
     */
    private Boolean getForcePinRequiredActionConfig(KeycloakSession session) {
        String realmAttr = session.getContext().getRealm().getAttribute("pin.requiredAction.force");
        if (realmAttr != null && !realmAttr.isEmpty()) {
            return Boolean.parseBoolean(realmAttr);
        }
        return PinCredentialConstants.DEFAULT_FORCE_PIN_REQUIRED_ACTION;
    }
    
    /**
     * Gets a user-friendly hint for the PIN format.
     */
    private String getFormatHint(String pinFormat) {
        switch (pinFormat) {
            case PinCredentialConstants.FORMAT_4_DIGITS:
                return "Enter your 4-digit PIN";
            case PinCredentialConstants.FORMAT_6_DIGITS:
                return "Enter your 6-digit PIN";
            case PinCredentialConstants.FORMAT_8_DIGITS:
                return "Enter your 8-digit PIN";
            case PinCredentialConstants.FORMAT_CUSTOM:
                return "Enter your PIN";
            default:
                return "Enter your PIN";
        }
    }
    
    /**
     * Gets the PIN credential provider.
     */
    private PinCredentialProvider getPinProvider(AuthenticationFlowContext context) {
        return (PinCredentialProvider) context.getSession()
                .getProvider(org.keycloak.credential.CredentialProvider.class,
                        PinCredentialProviderFactory.PROVIDER_ID);
    }
    
    /**
     * Checks if PIN reset is enabled in the authenticator configuration.
     */
    private boolean isResetEnabled(AuthenticationFlowContext context) {
        AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
        if (configModel != null && configModel.getConfig() != null) {
            return Boolean.parseBoolean(
                    configModel.getConfig().getOrDefault("pin.resetEnabled", "false"));
        }
        return false;
    }
    
    /**
     * Handles a PIN reset request from the authentication form.
     * Creates a {@link ResetPinActionToken}, sends a custom email with the
     * reset link, and shows an info page telling the user to check their inbox.
     */
    private void handlePinReset(AuthenticationFlowContext context) {
        if (!isResetEnabled(context)) {
            Response challenge = createForm(context, "pinResetNotEnabled");
            context.challenge(challenge);
            return;
        }
        
        UserModel user = context.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            Response challenge = createForm(context, "noEmailForPinReset");
            context.challenge(challenge);
            return;
        }
        
        try {
            KeycloakSession session = context.getSession();
            RealmModel realm = context.getRealm();
            
            int expiration = Time.currentTime() + TOKEN_LIFESPAN_SECONDS;
            ResetPinActionToken token = new ResetPinActionToken(
                    user.getId(), user.getEmail(), expiration, null);
            
            var builder = LoginActionsService.actionTokenProcessor(
                    session.getContext().getUri());
            builder.queryParam("key",
                    token.serialize(session, realm, session.getContext().getUri()));
            String link = builder.build(realm.getName()).toString();
            
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("link", link);
            attributes.put("linkExpiration",
                    TimeUnit.SECONDS.toMinutes(TOKEN_LIFESPAN_SECONDS));
            
            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setUser(user)
                    .send("pinResetEmailSubject", "pin-reset-email.ftl", attributes);
            
            logger.infof("PIN reset email sent to user %s", user.getId());
            
            LoginFormsProvider form = context.form();
            form.setInfo("pinResetLinkSent");
            Response response = form.createInfoPage();
            context.challenge(response);
            
        } catch (EmailException e) {
            logger.error("Failed to send PIN reset email", e);
            Response challenge = createForm(context, "pinResetEmailFailed");
            context.challenge(challenge);
        }
    }
}
