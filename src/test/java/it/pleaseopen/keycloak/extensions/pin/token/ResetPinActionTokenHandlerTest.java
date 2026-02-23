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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResetPinActionTokenHandler}.
 *
 * <p>Focuses on the handler's factory/SPI properties.
 * Behavioural tests (token handling with credential deletion + redirect)
 * are covered by the E2E test suite since {@code ActionTokenContext} cannot
 * be unit-mocked on Java 25+.
 */
@DisplayName("ResetPinActionTokenHandler Tests")
class ResetPinActionTokenHandlerTest {

    private ResetPinActionTokenHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResetPinActionTokenHandler();
    }

    // ── SPI / Factory properties ─────────────────────────────────────────────

    @Test
    @DisplayName("Handler ID should match TOKEN_TYPE 'reset-pin'")
    void handlerIdShouldMatchTokenType() {
        assertThat(handler.getId()).isEqualTo(ResetPinActionToken.TOKEN_TYPE);
        assertThat(handler.getId()).isEqualTo("reset-pin");
    }

    @Test
    @DisplayName("Token class should be ResetPinActionToken")
    void tokenClassShouldBeCorrect() {
        assertThat(handler.getTokenClass()).isEqualTo(ResetPinActionToken.class);
    }

    @Test
    @DisplayName("Event type should be CUSTOM_REQUIRED_ACTION")
    void eventTypeShouldBeCustomRequiredAction() {
        assertThat(handler.eventType()).isEqualTo(EventType.CUSTOM_REQUIRED_ACTION);
    }

    @Test
    @DisplayName("Default error message should be 'resetPinNotAllowed'")
    void defaultErrorMessageShouldBeCorrect() {
        assertThat(handler.getDefaultErrorMessage()).isEqualTo("resetPinNotAllowed");
    }

    @Test
    @DisplayName("Default event error should be 'not_allowed'")
    void defaultEventErrorShouldBeCorrect() {
        assertThat(handler.getDefaultEventError()).isEqualTo("not_allowed");
    }

    // ── Factory behaviour ────────────────────────────────────────────────────

    @Test
    @DisplayName("create() should return the handler itself (serves as both factory and handler)")
    void createShouldReturnItself() {
        assertThat(handler.create(null)).isSameAs(handler);
    }

    @Test
    @DisplayName("close and postInit should not throw")
    void closeAndPostInitShouldNotThrow() {
        handler.postInit(null);
        handler.close();
    }

    // ── Token usability ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Token should be single-use (canUseTokenRepeatedly returns false)")
    void tokenShouldBeSingleUse() {
        ResetPinActionToken token = new ResetPinActionToken(
                "user-123", "test@example.com", 9999999, null);
        // Our implementation ignores the context parameter, so passing null is safe
        assertThat(handler.canUseTokenRepeatedly(token, null)).isFalse();
    }
}
