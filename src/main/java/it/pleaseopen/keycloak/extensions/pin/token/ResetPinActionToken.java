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

import org.keycloak.authentication.actiontoken.DefaultActionToken;

/**
 * Action token for PIN reset.
 *
 * <p>When processed by {@link ResetPinActionTokenHandler}, this token:
 * <ol>
 *   <li>Deletes the user's existing PIN credential</li>
 *   <li>Adds the CONFIGURE_PIN required action</li>
 *   <li>Redirects the user to configure a new PIN</li>
 * </ol>
 */
public class ResetPinActionToken extends DefaultActionToken {

    public static final String TOKEN_TYPE = "reset-pin";

    /**
     * Creates a new reset PIN action token.
     *
     * @param userId                        the user's ID
     * @param email                         the user's email for verification
     * @param absoluteExpirationInSecs      token expiration as absolute timestamp
     * @param compoundAuthenticationSessionId optional authentication session ID
     */
    public ResetPinActionToken(String userId, String email,
                               int absoluteExpirationInSecs,
                               String compoundAuthenticationSessionId) {
        super(userId, TOKEN_TYPE, absoluteExpirationInSecs, null, compoundAuthenticationSessionId);
        setEmail(email);
    }

    private ResetPinActionToken() {
        // Required for deserialization
    }
}
