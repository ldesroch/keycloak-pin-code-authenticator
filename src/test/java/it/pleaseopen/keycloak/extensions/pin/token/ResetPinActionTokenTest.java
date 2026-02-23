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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResetPinActionToken}.
 */
@DisplayName("ResetPinActionToken Tests")
class ResetPinActionTokenTest {

    @Test
    @DisplayName("TOKEN_TYPE should be 'reset-pin'")
    void tokenTypeShouldBeResetPin() {
        assertThat(ResetPinActionToken.TOKEN_TYPE).isEqualTo("reset-pin");
    }

    @Test
    @DisplayName("Constructor should set userId as subject")
    void constructorShouldSetUserId() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);

        assertThat(token.getSubject()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Constructor should set email")
    void constructorShouldSetEmail() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);

        assertThat(token.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Constructor should set expiration")
    void constructorShouldSetExpiration() {
        int expiration = 1700000000;
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", expiration, null);

        assertThat(token.getExp()).isEqualTo(expiration);
    }

    @Test
    @DisplayName("Constructor should set action type to TOKEN_TYPE")
    void constructorShouldSetActionType() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);

        // The type field (from JsonWebToken) is set via DefaultActionTokenKey
        assertThat(token.getType()).isEqualTo("reset-pin");
    }

    @Test
    @DisplayName("Constructor should generate an action verification nonce")
    void constructorShouldGenerateNonce() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);

        // Passing null for nonce causes DefaultActionTokenKey to auto-generate a UUID
        assertThat(token.getActionVerificationNonce()).isNotNull();
    }

    @Test
    @DisplayName("Constructor should set compound authentication session ID")
    void constructorShouldSetCompoundAuthSessionId() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, "session-456");

        assertThat(token.getCompoundAuthenticationSessionId()).isEqualTo("session-456");
    }

    @Test
    @DisplayName("Constructor should handle null auth session ID")
    void constructorShouldHandleNullAuthSessionId() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);

        assertThat(token.getCompoundAuthenticationSessionId()).isNull();
    }

    @Test
    @DisplayName("Two tokens should have different nonces")
    void twoTokensShouldHaveDifferentNonces() {
        ResetPinActionToken token1 = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);
        ResetPinActionToken token2 = new ResetPinActionToken(
                "user-123", "user@example.com", 9999999, null);

        assertThat(token1.getActionVerificationNonce())
                .isNotEqualTo(token2.getActionVerificationNonce());
    }
}
