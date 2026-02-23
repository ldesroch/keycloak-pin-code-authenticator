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

import org.jboss.logging.Logger;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialTypeMetadata;
import org.keycloak.credential.CredentialTypeMetadataContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Credential provider for PIN credentials.
 *
 * <p>Handles hashing with pluggable {@link PinHashProvider} implementations.
 * The active hash algorithm is resolved at runtime via the {@link PinHashSpi}
 * ({@code session.getProvider(PinHashProvider.class)}). This allows new
 * algorithms to be added by simply deploying a new {@link PinHashProviderFactory}
 * with a higher {@link PinHashProviderFactory#order() order}.</p>
 */
public class PinCredentialProvider implements CredentialProvider, CredentialInputValidator {

    private static final Logger logger = Logger.getLogger(PinCredentialProvider.class);

    private final KeycloakSession session;

    public PinCredentialProvider(KeycloakSession session) {
        this.session = session;
    }

    public String getType() {
        return PinCredentialConstants.CREDENTIAL_TYPE;
    }

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a new PIN credential using the active hash algorithm.
     * Any existing PIN credential is <b>not</b> removed here — callers must
     * handle that to enforce single-PIN-per-user.
     */
    public CredentialModel createCredential(RealmModel realm, UserModel user,
                                            String pin, String format, String customRegex) {
        if (pin == null || pin.isEmpty()) {
            throw new IllegalArgumentException("PIN cannot be empty");
        }
        if (!isValidPinFormat(pin, format, customRegex)) {
            throw new IllegalArgumentException("Invalid PIN format");
        }

        PinHashProvider hashProvider = getActiveHashProvider();

        logger.infof("Creating PIN credential for user %s [format=%s, algorithm=%s]",
                user.getId(), format, hashProvider.getAlgorithm());

        PinHashProvider.EncodedPin encoded = hashProvider.encode(pin, PinCredentialConstants.HASH_ITERATIONS);

        PinCredentialModel pinModel = PinCredentialModel.create(
                encoded.hash, encoded.salt,
                hashProvider.getAlgorithm(),
                PinCredentialConstants.HASH_ITERATIONS,
                format, customRegex,
                encoded.additionalParameters);

        CredentialModel credentialModel = pinModel.toCredentialModel();
        return user.credentialManager().createStoredCredential(credentialModel);
    }

    // ── Validate ─────────────────────────────────────────────────────────────

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PinCredentialConstants.CREDENTIAL_TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType) && isConfiguredFor(realm, user);
    }

    public boolean isConfiguredFor(RealmModel realm, UserModel user) {
        return user.credentialManager()
                .getStoredCredentialsByTypeStream(getType())
                .findAny()
                .isPresent();
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) return false;

        String rawPin = input.getChallengeResponse();
        if (rawPin == null || rawPin.isEmpty()) return false;

        CredentialModel storedCredential = getPinCredential(realm, user);
        if (storedCredential == null) return false;

        PinCredentialModel pinModel = PinCredentialModel.createFromCredentialModel(storedCredential);
        if (pinModel == null) return false;

        // Resolve the hash provider that matches the stored algorithm
        PinHashProvider verifyProvider = resolveProvider(pinModel.getAlgorithm());
        boolean valid = verifyProvider.verify(rawPin,
                pinModel.getHashedPin(), pinModel.getSalt(),
                pinModel.getHashIterations(), pinModel.getAdditionalParameters());

        if (valid) {
            logger.debugf("PIN verified for user %s", user.getId());
            // Transparent re-hash if algorithm/params changed
            PinHashProvider activeProvider = getActiveHashProvider();
            if (!activeProvider.policyMatch(pinModel.getAlgorithm(),
                    pinModel.getHashIterations(), pinModel.getAdditionalParameters())) {
                rehashPin(realm, user, rawPin, pinModel.getPinFormat(), pinModel.getCustomRegex(), storedCredential);
            }
        } else {
            logger.debugf("Invalid PIN attempt for user %s", user.getId());
        }
        return valid;
    }

    /**
     * Re-hash a PIN using the active algorithm and update the stored credential.
     */
    private void rehashPin(RealmModel realm, UserModel user, String rawPin,
                           String format, String customRegex, CredentialModel oldCredential) {
        PinHashProvider hashProvider = getActiveHashProvider();

        logger.debugf("Re-hashing PIN for user %s from %s to %s",
                user.getId(),
                PinCredentialModel.createFromCredentialModel(oldCredential).getAlgorithm(),
                hashProvider.getAlgorithm());

        PinHashProvider.EncodedPin encoded = hashProvider.encode(rawPin, PinCredentialConstants.HASH_ITERATIONS);
        PinCredentialModel newModel = PinCredentialModel.create(
                encoded.hash, encoded.salt,
                hashProvider.getAlgorithm(),
                PinCredentialConstants.HASH_ITERATIONS,
                format, customRegex,
                encoded.additionalParameters);

        CredentialModel updated = newModel.toCredentialModel();
        updated.setId(oldCredential.getId());
        updated.setCreatedDate(oldCredential.getCreatedDate());
        user.credentialManager().updateStoredCredential(updated);
    }

    // ── Lookup ───────────────────────────────────────────────────────────────

    public CredentialModel getPinCredential(RealmModel realm, UserModel user) {
        List<CredentialModel> creds = user.credentialManager()
                .getStoredCredentialsByTypeStream(getType())
                .collect(Collectors.toList());
        return creds.isEmpty() ? null : creds.get(0);
    }

    // ── Format validation ────────────────────────────────────────────────────

    /** Maximum allowed PIN length to prevent resource exhaustion via hash or regex. */
    private static final int MAX_PIN_LENGTH = 128;

    public boolean isValidPinFormat(String pin, String format, String customRegex) {
        if (pin == null || pin.length() > MAX_PIN_LENGTH) return false;

        if (PinCredentialConstants.FORMAT_CUSTOM.equals(format)) {
            if (customRegex == null || customRegex.isEmpty()) return false;
            return pin.matches(customRegex);
        }

        String regex = PinCredentialConstants.getRegexForFormat(format);
        return regex != null && pin.matches(regex);
    }

    // ── CredentialProvider interface ──────────────────────────────────────────

    @Override
    public CredentialModel createCredential(RealmModel realm, UserModel user, CredentialModel credentialModel) {
        return user.credentialManager().createStoredCredential(credentialModel);
    }

    @Override
    public boolean deleteCredential(RealmModel realm, UserModel user, String credentialId) {
        logger.debugf("Deleting PIN credential %s for user %s", credentialId, user.getId());
        return user.credentialManager().removeStoredCredentialById(credentialId);
    }

    @Override
    public CredentialModel getCredentialFromModel(CredentialModel model) {
        return model;
    }

    @Override
    public CredentialTypeMetadata getCredentialTypeMetadata(CredentialTypeMetadataContext ctx) {
        return CredentialTypeMetadata.builder()
                .type(getType())
                .category(CredentialTypeMetadata.Category.TWO_FACTOR)
                .displayName("PIN Code")
                .helpText("PIN code authentication")
                .removeable(true)
                .build(session);
    }

    @Override
    public void close() {
    }

    // ── Private ──────────────────────────────────────────────────────────────

    /**
     * Returns the default (active) PIN hash provider — the one with the highest
     * {@link PinHashProviderFactory#order()}.
     */
    private PinHashProvider getActiveHashProvider() {
        return session.getProvider(PinHashProvider.class);
    }

    /**
     * Resolves the hash provider for a given algorithm identifier.
     * Falls back to the default provider if the algorithm is unknown.
     */
    private PinHashProvider resolveProvider(String algorithm) {
        if (algorithm != null) {
            PinHashProvider provider = session.getProvider(PinHashProvider.class, algorithm);
            if (provider != null) {
                return provider;
            }
            logger.warnf("Unknown PIN hash algorithm '%s', falling back to default provider", algorithm);
        }
        return getActiveHashProvider();
    }
}
