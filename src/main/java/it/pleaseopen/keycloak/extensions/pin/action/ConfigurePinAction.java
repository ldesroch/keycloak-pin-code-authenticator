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
package it.pleaseopen.keycloak.extensions.pin.action;

import it.pleaseopen.keycloak.extensions.pin.config.PinConfiguration;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialConstants;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProvider;
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import it.pleaseopen.keycloak.extensions.pin.keyboard.KeyboardImageGenerator;
import org.jboss.logging.Logger;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Required action for configuring or updating PIN code.
 * This action is triggered when a user needs to set up their PIN for the first time
 * or when the PIN needs to be updated.
 */
public class ConfigurePinAction implements RequiredActionProvider {
    
    private static final Logger logger = Logger.getLogger(ConfigurePinAction.class);
    
    private static final String TPL_CODE = "configure-pin.ftl";
    private static final String FORM_NEW_PIN = "newPin";
    private static final String FORM_CONFIRM_PIN = "confirmPin";
    private static final String FORM_NEW_PIN_COORDS = "newPinCoords";
    private static final String FORM_CONFIRM_PIN_COORDS = "confirmPinCoords";
    private static final String AUTH_NOTE_COORD_MAP = "pin.keyboard.coordMap";
    private static final String FORM_PIN_FORMAT = "pinFormat";
    private static final String FORM_CUSTOM_REGEX = "customRegex";
    
    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // Check if user has PIN configured
        UserModel user = context.getUser();
        PinCredentialProvider pinProvider = getPinProvider(context);
        
        if (pinProvider == null) {
            return;
        }
        
        if (!pinProvider.isConfiguredFor(context.getRealm(), user, PinCredentialConstants.CREDENTIAL_TYPE)) {
            user.addRequiredAction(ConfigurePinActionFactory.PROVIDER_ID);
        }
    }
    
    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Response challenge = createForm(context, null);
        context.challenge(challenge);
    }
    
    @Override
    public void processAction(RequiredActionContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        
        String newPin = formData.getFirst(FORM_NEW_PIN);
        String confirmPin = formData.getFirst(FORM_CONFIRM_PIN);
        
        // If visual keyboard is enabled, resolve PINs from submitted coordinates
        PinConfiguration config = PinConfiguration.getInstance();
        if (config.isVisualKeyboardEnabled()) {
            String coordMapStr = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_COORD_MAP);
            if (coordMapStr != null) {
                Map<String, int[]> coordMap = KeyboardImageGenerator.deserializeCoordinateMap(coordMapStr);
                
                String newPinCoords = formData.getFirst(FORM_NEW_PIN_COORDS);
                if (newPinCoords != null && !newPinCoords.isEmpty()) {
                    newPin = resolvePin(newPinCoords, coordMap);
                }
                
                String confirmPinCoords = formData.getFirst(FORM_CONFIRM_PIN_COORDS);
                if (confirmPinCoords != null && !confirmPinCoords.isEmpty()) {
                    confirmPin = resolvePin(confirmPinCoords, coordMap);
                }
                
                if (config.isVisualKeyboardDebugEnabled()) {
                    String userId = context.getUser() != null ? context.getUser().getId() : "unknown";
                    logger.infof("[VISUAL-KB-DEBUG] Configure PIN resolved from coordinates for user '%s': newPin length=%d, confirmPin length=%d",
                                 userId,
                                 newPin != null ? newPin.length() : 0,
                                 confirmPin != null ? confirmPin.length() : 0);
                }
            }
        }
        
        // Get PIN format from browser flow authenticator configuration
        String pinFormat = getConfiguredPinFormat(context);
        String customRegex = null;
        if (PinCredentialConstants.FORMAT_CUSTOM.equals(pinFormat)) {
            customRegex = getConfiguredCustomRegex(context);
        }
        
        // Validate inputs
        if (newPin == null || newPin.isEmpty()) {
            Response challenge = createForm(context, "missingPinMessage");
            context.challenge(challenge);
            return;
        }
        
        if (confirmPin == null || confirmPin.isEmpty()) {
            Response challenge = createForm(context, "missingPinMessage");
            context.challenge(challenge);
            return;
        }
        
        if (!newPin.equals(confirmPin)) {
            Response challenge = createForm(context, "pinRequiredActionMismatch");
            context.challenge(challenge);
            return;
        }
        
        // Get PIN provider
        PinCredentialProvider pinProvider = getPinProvider(context);
        if (pinProvider == null) {
            logger.error("PIN credential provider not available");
            Response challenge = createForm(context, "internalError");
            context.challenge(challenge);
            return;
        }
        
        // Validate PIN format
        if (!pinProvider.isValidPinFormat(newPin, pinFormat, customRegex)) {
            logger.errorf("PIN format validation failed for format=%s", pinFormat);
            Response challenge = createForm(context, "pinRequiredActionInvalidFormat");
            context.challenge(challenge);
            return;
        }
        
        try {
            // Delete existing PIN credential if any
            org.keycloak.credential.CredentialModel existing = pinProvider.getPinCredential(context.getRealm(), context.getUser());
            if (existing != null) {
                pinProvider.deleteCredential(context.getRealm(), context.getUser(), existing.getId());
            }
            
            // Create new PIN credential
            org.keycloak.credential.CredentialModel created = pinProvider.createCredential(
                    context.getRealm(),
                    context.getUser(),
                    newPin,
                    pinFormat,
                    customRegex
            );
            
            if (created != null) {
                context.success();
            } else {
                logger.error("Failed to create PIN credential - returned null");
                Response challenge = createForm(context, "internalError");
                context.challenge(challenge);
            }
        } catch (Exception e) {
            logger.error("Error creating PIN credential", e);
            Response challenge = createForm(context, "internalError");
            context.challenge(challenge);
        }
    }
    
    @Override
    public void close() {
        // No resources to close
    }
    
    /**
     * Creates the PIN configuration form.
     * PIN format comes from global Config.Scope (what format new PINs should use).
     */
    private Response createForm(RequiredActionContext context, String error) {
        LoginFormsProvider form = context.form();
        
        if (error != null) {
            form.setError(error);
        }
        
        PinConfiguration config = PinConfiguration.getInstance();
        
        String configuredFormat = config.getPinFormat();
        form.setAttribute("pinFormat", configuredFormat);
        form.setAttribute("pinFormatHint", getFormatHint(configuredFormat));
        
        boolean showKeyboard = config.isVisualKeyboardEnabled();
        form.setAttribute("showVisualKeyboard", showKeyboard);
        
        // Generate keyboard image server-side
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
                logger.infof("[VISUAL-KB-DEBUG] Configure PIN keyboard coordinate map for user '%s': %s",
                             userId, coordMapStr);
            }
        }
        
        return form.createForm(TPL_CODE);
    }
    
    /**
     * Resolves a PIN string from click coordinates using the keyboard coordinate map.
     *
     * @param coordsStr "x1,y1|x2,y2|..." format
     * @param coordMap  digit → [x, y, w, h]
     * @return the resolved PIN string
     */
    private String resolvePin(String coordsStr, Map<String, int[]> coordMap) {
        StringBuilder pin = new StringBuilder();
        String[] clicks = coordsStr.split("\\|");
        for (String click : clicks) {
            String[] xy = click.split(",");
            if (xy.length == 2) {
                try {
                    int cx = Integer.parseInt(xy[0].trim());
                    int cy = Integer.parseInt(xy[1].trim());
                    String digit = KeyboardImageGenerator.resolveDigit(cx, cy, coordMap);
                    if (digit != null) {
                        pin.append(digit);
                    }
                } catch (NumberFormatException e) {
                    // Ignore malformed coordinate
                }
            }
        }
        return pin.toString();
    }

    /**
     * Gets the configured PIN format from global Config.Scope configuration.
     */
    private String getConfiguredPinFormat(RequiredActionContext context) {
        return PinConfiguration.getInstance().getPinFormat();
    }
    
    /**
     * Gets the configured custom regex if format is custom.
     */
    private String getConfiguredCustomRegex(RequiredActionContext context) {
        String regex = PinConfiguration.getInstance().getCustomRegex();
        return (regex != null && !regex.isEmpty()) ? regex : null;
    }
    
    /**
     * Gets a user-friendly hint for the PIN format.
     */
    private String getFormatHint(String pinFormat) {
        switch (pinFormat) {
            case PinCredentialConstants.FORMAT_4_DIGITS:
                return "4 digits";
            case PinCredentialConstants.FORMAT_6_DIGITS:
                return "6 digits";
            case PinCredentialConstants.FORMAT_8_DIGITS:
                return "8 digits";
            case PinCredentialConstants.FORMAT_CUSTOM:
                return "Custom format";
            default:
                return pinFormat;
        }
    }
    
    /**
     * Gets the PIN credential provider.
     */
    private PinCredentialProvider getPinProvider(RequiredActionContext context) {
        return (PinCredentialProvider) context.getSession()
                .getProvider(org.keycloak.credential.CredentialProvider.class,
                        PinCredentialProviderFactory.PROVIDER_ID);
    }
}
