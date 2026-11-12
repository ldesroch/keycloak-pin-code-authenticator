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
import it.pleaseopen.keycloak.extensions.pin.credential.PinCredentialProviderFactory;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code maxAttempts} lockout in {@link PinAuthenticator}.
 */
@DisplayName("PinAuthenticator maxAttempts Tests")
class PinAuthenticatorMaxAttemptsTest {

    private static final String CORRECT_PIN = "1234";
    private static final String WRONG_PIN = "9999";

    private final PinAuthenticator authenticator = new PinAuthenticator();

    private AuthenticationFlowContext context;
    private PinCredentialProvider pinProvider;
    private LoginFormsProvider form;
    private UserModel user;
    private MultivaluedHashMap<String, String> formData;
    private Map<String, String> userAttributes;

    @BeforeEach
    void setUp() {
        context = mock(AuthenticationFlowContext.class);
        pinProvider = mock(PinCredentialProvider.class);
        form = mock(LoginFormsProvider.class, RETURNS_SELF);
        formData = new MultivaluedHashMap<>();

        KeycloakSession session = mock(KeycloakSession.class);
        when(session.getProvider(CredentialProvider.class, PinCredentialProviderFactory.PROVIDER_ID))
                .thenReturn(pinProvider);

        HttpRequest httpRequest = mock(HttpRequest.class);
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        user = mockUserWithAttributes();

        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(mock(RealmModel.class));
        when(context.getUser()).thenReturn(user);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.getEvent()).thenReturn(mock(EventBuilder.class));
        when(context.form()).thenReturn(form);

        when(pinProvider.isConfiguredFor(any(), any(), eq(PinCredentialConstants.CREDENTIAL_TYPE))).thenReturn(true);
        when(pinProvider.isValid(any(), any(), any())).thenAnswer(inv ->
                CORRECT_PIN.equals(inv.getArgument(2, org.keycloak.credential.CredentialInput.class)
                        .getChallengeResponse()));
    }

    @Test
    @DisplayName("Wrong PIN below the limit should increment the counter and show the invalid PIN message")
    void wrongPinBelowLimitIncrementsCounter() {
        withMaxAttempts("3");
        submitPin(WRONG_PIN);

        authenticator.action(context);

        assertThat(userAttributes).containsEntry(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "1");
        verify(form).setError("invalidPinMessage");
        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any());
    }

    @Test
    @DisplayName("Wrong PIN reaching the limit should show the lockout message")
    void wrongPinReachingLimitShowsLockoutMessage() {
        withMaxAttempts("3");
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "2");
        submitPin(WRONG_PIN);

        authenticator.action(context);

        assertThat(userAttributes).containsEntry(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "3");
        verify(form).setError("pinMaxAttemptsReached");
    }

    @Test
    @DisplayName("Once locked, even the correct PIN should be refused without being validated")
    void lockedUserIsRefusedEvenWithCorrectPin() {
        withMaxAttempts("3");
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "3");
        submitPin(CORRECT_PIN);

        authenticator.action(context);

        verify(pinProvider, never()).isValid(any(), any(), any());
        verify(context, never()).success();
        verify(form).setError("pinMaxAttemptsReached");
        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any());
        assertThat(userAttributes).containsEntry(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "3");
    }

    @Test
    @DisplayName("Correct PIN should reset the counter")
    void correctPinResetsCounter() {
        withMaxAttempts("3");
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "2");
        submitPin(CORRECT_PIN);

        authenticator.action(context);

        verify(context).success();
        assertThat(userAttributes).doesNotContainKey(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS);
    }

    @Test
    @DisplayName("maxAttempts = 0 should never lock the PIN")
    void zeroMeansUnlimited() {
        withMaxAttempts("0");
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "50");
        submitPin(WRONG_PIN);

        authenticator.action(context);

        verify(form).setError("invalidPinMessage");
        verify(form, never()).setError("pinMaxAttemptsReached");

        submitPin(CORRECT_PIN);
        authenticator.action(context);

        verify(context).success();
    }

    @Test
    @DisplayName("Without authenticator config the documented default of 3 should apply")
    void defaultLimitAppliesWithoutConfig() {
        when(context.getAuthenticatorConfig()).thenReturn(null);
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "3");
        submitPin(CORRECT_PIN);

        authenticator.action(context);

        verify(pinProvider, never()).isValid(any(), any(), any());
        verify(form).setError("pinMaxAttemptsReached");
    }

    @Test
    @DisplayName("authenticate() should show the lockout message to a locked user")
    void authenticateShowsLockoutMessage() {
        withMaxAttempts("3");
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "3");

        authenticator.authenticate(context);

        verify(form).setError("pinMaxAttemptsReached");
        verify(context).challenge(any());
        verify(context, never()).attempted();
    }

    @Test
    @DisplayName("authenticate() should show the normal form when not locked")
    void authenticateShowsNormalFormWhenNotLocked() {
        withMaxAttempts("3");
        userAttributes.put(PinCredentialConstants.USER_ATTR_PIN_ATTEMPTS, "1");

        authenticator.authenticate(context);

        verify(form, never()).setError(anyString());
        verify(context).challenge(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void withMaxAttempts(String value) {
        AuthenticatorConfigModel config = new AuthenticatorConfigModel();
        Map<String, String> map = new HashMap<>();
        map.put(PinCredentialConstants.CONFIG_MAX_ATTEMPTS, value);
        config.setConfig(map);
        when(context.getAuthenticatorConfig()).thenReturn(config);
    }

    private void submitPin(String pin) {
        formData.putSingle("pin", pin);
    }

    private UserModel mockUserWithAttributes() {
        userAttributes = new HashMap<>();
        UserModel mockUser = mock(UserModel.class);
        when(mockUser.getId()).thenReturn("user-123");
        when(mockUser.getFirstAttribute(anyString())).thenAnswer(inv -> userAttributes.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> userAttributes.put(inv.getArgument(0), inv.getArgument(1)))
                .when(mockUser).setSingleAttribute(anyString(), anyString());
        doAnswer(inv -> userAttributes.remove(inv.getArgument(0, String.class)))
                .when(mockUser).removeAttribute(anyString());
        return mockUser;
    }
}
