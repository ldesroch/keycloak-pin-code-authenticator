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

import org.keycloak.provider.Provider;

import java.util.Map;

/**
 * SPI provider interface for PIN hashing.
 * Implementations handle encoding (hash + salt generation) and verification.
 *
 * <p>Follows Keycloak's {@code PasswordHashProvider} SPI pattern so that
 * hash algorithm implementations can be plugged in via
 * {@link PinHashProviderFactory}. The active algorithm is resolved at runtime
 * through {@code session.getProvider(PinHashProvider.class)}.
 * On successful verification, if the stored credential uses an outdated
 * algorithm/parameters, the credential is re-hashed transparently.</p>
 *
 * @see PinHashProviderFactory
 * @see PinHashSpi
 */
public interface PinHashProvider extends Provider {

    /** Algorithm identifier stored in credential data (e.g. "argon2"). */
    String getAlgorithm();

    /**
     * Encodes a raw PIN into a hash with a fresh salt.
     *
     * @return result containing base64-encoded hash, base64-encoded salt, and extra params
     */
    EncodedPin encode(String rawPin, int iterations);

    /**
     * Verifies a raw PIN against a stored hash + salt.
     */
    boolean verify(String rawPin, String storedHash, String storedSalt,
                   int iterations, Map<String, String> additionalParams);

    /**
     * Checks whether the stored credential matches the current algorithm configuration.
     * Return {@code false} if the credential should be re-hashed on next successful login.
     */
    boolean policyMatch(String algorithm, int iterations, Map<String, String> additionalParams);

    /** Result of encoding a PIN. */
    class EncodedPin {
        public final String hash;
        public final String salt;
        public final Map<String, String> additionalParameters;

        public EncodedPin(String hash, String salt, Map<String, String> additionalParameters) {
            this.hash = hash;
            this.salt = salt;
            this.additionalParameters = additionalParameters;
        }
    }

    /**
     * Default no-op close. Implementations can override if they hold resources.
     */
    @Override
    default void close() {
    }
}
